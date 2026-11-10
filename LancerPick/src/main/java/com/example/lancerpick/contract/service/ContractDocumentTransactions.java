package com.example.lancerpick.contract.service;

import com.example.lancerpick.admin.entity.AdminAction;
import com.example.lancerpick.admin.service.AdminActionExecutionGuard;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.contract.dto.ContractResponse;
import com.example.lancerpick.contract.entity.OrderContract;
import com.example.lancerpick.contract.repository.OrderContractRepository;
import com.example.lancerpick.member.entity.MemberRole;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.order.repository.*;
import com.example.lancerpick.order.service.OrderCommandLocks;
import java.time.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

/** Only short DB transactions. PDF rendering/storage must be outside this bean. */
@Service
@Profile("!demo")
@Transactional(isolation = Isolation.READ_COMMITTED)
public class ContractDocumentTransactions {
  private final OrderRepository orders;
  private final OrderCommandLocks locks;
  private final OrderContractRepository contracts;
  private final CancellationRequestRepository cancellations;
  private final OrderContractService descriptions;
  private final Clock clock;
  private final Duration timeout;
  private final AdminActionExecutionGuard adminGuard;

  public ContractDocumentTransactions(
      OrderRepository orders,
      OrderCommandLocks locks,
      OrderContractRepository contracts,
      CancellationRequestRepository cancellations,
      OrderContractService descriptions,
      Clock clock,
      AdminActionExecutionGuard adminGuard,
      @Value("${contracts.generation-timeout:PT2M}") Duration timeout) {
    this.orders = orders;
    this.locks = locks;
    this.contracts = contracts;
    this.cancellations = cancellations;
    this.descriptions = descriptions;
    this.clock = clock;
    this.adminGuard = adminGuard;
    this.timeout = timeout;
    if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(30)) > 0)
      throw new IllegalArgumentException("Invalid PDF generation timeout");
  }

  public record Job(
      long memberId,
      long orderId,
      long contractId,
      int version,
      String key,
      String canonical,
      boolean administrator) {
    public Job(
        long memberId, long orderId, long contractId, int version, String key, String canonical) {
      this(memberId, orderId, contractId, version, key, canonical, false);
    }
  }

  public record Claim(ContractResponse response, Job job) {}

  public record Applied(boolean stored, boolean discardObsoleteFile, ContractResponse response) {}

  public record FileReference(String key, long size, byte[] hash) {
    public FileReference {
      hash = hash.clone();
    }

    @Override
    public byte[] hash() {
      return hash.clone();
    }
  }

  @Transactional(readOnly = true)
  public ContractResponse read(long memberId, long orderId) {
    return read(memberId, orderId, null);
  }

  @Transactional(readOnly = true)
  public ContractResponse read(long memberId, long orderId, Integer version) {
    owned(memberId, orderId, false);
    return descriptions.describe(selected(orderId, version));
  }

  @Transactional(readOnly = true)
  public FileReference file(long memberId, long orderId) {
    return file(memberId, orderId, null);
  }

  @Transactional(readOnly = true)
  public FileReference file(long memberId, long orderId, Integer version) {
    owned(memberId, orderId, false);
    var c = selected(orderId, version);
    if (!Set.of("READY", "SIGNED", "SUPERSEDED").contains(c.getStatus()) || c.getFileHash() == null)
      throw conflict("계약서 파일이 아직 준비되지 않았습니다.");
    if (!"LOCAL".equals(c.getFileBackend()))
      throw new IllegalStateException("Unsupported contract storage backend");
    return new FileReference(c.getFileStorageKey(), c.getFileSize(), c.getFileHash());
  }

  public Claim claim(long memberId, long orderId, int version) {
    var order = owned(memberId, orderId, true);
    if (!order.getSellerId().equals(memberId))
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "판매자만 계약서를 생성할 수 있습니다.");
    return claim(memberId, order, version, false);
  }

  public Claim claimForAdmin(long adminId, long logId, long contractId) {
    adminGuard.require(adminId, logId, AdminAction.CONTRACT_PDF_REGENERATE, contractId);
    var orderId =
        contracts.findOrderIdById(contractId).orElseThrow(ContractDocumentTransactions::missing);
    var order = adminOwned(adminId, orderId);
    // 주문 잠금 전에는 불변 orderId만 읽는다. 변경 가능한 계약 상태를 영속성 컨텍스트에 미리 올리지 않는다.
    var selected = current(orderId);
    if (!selected.getId().equals(contractId)) throw conflict("최신 계약을 확인해 주세요.");
    return claim(adminId, order, selected.getVersion(), true);
  }

  private Claim claim(long memberId, Order order, int version, boolean administrator) {
    long orderId = order.getId();
    if (!writable(order)) throw conflict("현재 주문에서는 계약서를 생성할 수 없습니다.");
    var c = current(orderId);
    if (c.getVersion() != version) throw conflict("계약 버전을 다시 확인해 주세요.");
    var response = descriptions.describe(c); // verifies canonical snapshot before generating
    if (Set.of("READY", "SIGNED").contains(c.getStatus())) return new Claim(response, null);
    var at = now();
    if ("GENERATING".equals(c.getStatus()) && c.getUpdatedAt().isAfter(at.minus(timeout)))
      return new Claim(response, null);
    if ("DRAFT".equals(c.getStatus())) c.startGeneration(at);
    else c.retryGeneration(at); // fresh key fences off a stale/failed worker
    contracts.flush();
    return new Claim(
        descriptions.describe(c),
        new Job(
            memberId,
            orderId,
            c.getId(),
            c.getVersion(),
            c.getGenerationKey(),
            response.canonicalContent(),
            administrator));
  }

  public Applied complete(Job job, String key, long size, byte[] hash) {
    if (!Objects.equals(key, job.key() + ".pdf")
        || size < 1
        || size > com.example.lancerpick.contract.document.PrivateContractStorage.MAX_BYTES
        || hash == null
        || hash.length != 32) throw new IllegalArgumentException("Invalid PDF completion metadata");
    var order =
        job.administrator()
            ? adminOwned(job.memberId(), job.orderId())
            : owned(job.memberId(), job.orderId(), true);
    var c = current(job.orderId());
    if (c.getId() != job.contractId() || !c.getGenerationKey().equals(job.key()))
      return rejected(c, key);
    if (Set.of("READY", "SIGNED").contains(c.getStatus())) {
      boolean same =
          Objects.equals(c.getFileStorageKey(), key)
              && Objects.equals(c.getFileSize(), size)
              && java.security.MessageDigest.isEqual(c.getFileHash(), hash);
      if (!same) throw new IllegalStateException("Published contract metadata mismatch");
      return new Applied(true, false, descriptions.describe(c));
    }
    if (!"GENERATING".equals(c.getStatus())) return rejected(c, key);
    if (!writable(order)) {
      c.failGeneration(job.key(), now());
      contracts.flush();
      return rejected(c, key);
    }
    c.completeGeneration(job.key(), key, "LOCAL", size, hash, now());
    contracts.flush();
    return new Applied(true, false, descriptions.describe(c));
  }

  private Applied rejected(OrderContract current, String fileKey) {
    // Even a replaced version may still reference the file (e.g. a replayed completion).
    return new Applied(
        false, !contracts.existsByFileStorageKey(fileKey), descriptions.describe(current));
  }

  public void fail(Job job) {
    if (job.administrator()) adminOwned(job.memberId(), job.orderId());
    else owned(job.memberId(), job.orderId(), true);
    var c = current(job.orderId());
    if (c.getId() == job.contractId()) {
      c.failGeneration(job.key(), now());
      contracts.flush();
    }
  }

  private Order owned(long memberId, long orderId, boolean lock) {
    // 문서 경로는 회원 없음도 401이 아닌 404로 숨기는 기존 계약을 유지한다(R1, §14.1).
    var order =
        lock
            ? locks.lockParticipantOrderHidingActor(
                memberId, orderId, ContractDocumentTransactions::missing)
            : orders.findById(orderId).orElseThrow(ContractDocumentTransactions::missing);
    if ((!order.getBuyerId().equals(memberId) && !order.getSellerId().equals(memberId))
        || !TradePolicyVersion.isContract(order.getPolicyVersion())) throw missing();
    return order;
  }

  private Order adminOwned(long adminId, long orderId) {
    var member =
        orders
            .findActorForShare(adminId)
            .orElseThrow(() ->
                new BusinessException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."));
    if (member.getRole() != MemberRole.ADMIN)
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "관리자 권한이 필요합니다.");
    var order =
        orders.findForSystemUpdate(orderId).orElseThrow(ContractDocumentTransactions::missing);
    if (!TradePolicyVersion.isContract(order.getPolicyVersion())) throw missing();
    return order;
  }

  private boolean writable(Order o) {
    return Set.of(OrderStatus.AWAITING_PAYMENT, OrderStatus.AWAITING_CONTRACT)
            .contains(o.getStatus())
        && o.getAcceptedAt() != null
        && o.getPaidAt() == null
        && cancellations
            .findByOrderIdAndStatus(
                o.getId(), com.example.lancerpick.order.entity.CancellationRequestStatus.PENDING)
            .isEmpty();
  }

  private OrderContract current(long id) {
    return contracts
        .findByOrderIdAndActiveSlot(id, 1)
        .orElseThrow(ContractDocumentTransactions::missing);
  }

  private OrderContract selected(long orderId, Integer version) {
    return version == null
        ? current(orderId)
        : contracts
            .findByOrderIdAndVersion(orderId, version)
            .orElseThrow(ContractDocumentTransactions::missing);
  }

  private LocalDateTime now() {
    return UtcTimes.nowMicros(clock);
  }

  private static BusinessException missing() {
    return new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "계약서를 찾을 수 없습니다.");
  }

  private static BusinessException conflict(String text) {
    return new BusinessException(HttpStatus.CONFLICT, "CONTRACT_CONFLICT", text);
  }
}
