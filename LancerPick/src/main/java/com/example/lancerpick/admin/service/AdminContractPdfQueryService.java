package com.example.lancerpick.admin.service;

import com.example.lancerpick.admin.dto.AdminContractPdfResponse;
import com.example.lancerpick.admin.dto.AdminContractPdfResponse.*;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.contract.entity.OrderContract;
import com.example.lancerpick.contract.repository.OrderContractRepository;
import com.example.lancerpick.member.entity.MemberRole;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.order.entity.CancellationRequestStatus;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.order.repository.CancellationRequestRepository;
import com.example.lancerpick.order.repository.OrderRepository;
import java.time.*;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 PDF 운영 조회. DB만 읽으며 파일 확인·생성·재처리·감사 기록을 실행하지 않는다. */
@Service
@Profile("!demo")
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
public class AdminContractPdfQueryService {
  private final MemberRepository members;
  private final OrderRepository orders;
  private final OrderContractRepository contracts;
  private final CancellationRequestRepository cancellations;
  private final Clock clock;
  private final Duration timeout;

  public AdminContractPdfQueryService(
      MemberRepository members,
      OrderRepository orders,
      OrderContractRepository contracts,
      CancellationRequestRepository cancellations,
      Clock clock,
      @Value("${contracts.generation-timeout:PT2M}") Duration timeout) {
    if (timeout.isNegative() || timeout.isZero() || timeout.compareTo(Duration.ofMinutes(30)) > 0)
      throw new IllegalArgumentException("Invalid PDF generation timeout");
    this.members = members;
    this.orders = orders;
    this.contracts = contracts;
    this.cancellations = cancellations;
    this.clock = clock;
    this.timeout = timeout;
  }

  /** version 생략 시 현재 계약, 지정 시 해당 버전의 메타데이터를 조회한다. */
  public AdminContractPdfResponse inspect(long adminId, long orderId, Integer version) {
    if (adminId < 1) throw error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
    var role =
        members
            .findRoleById(adminId)
            .orElseThrow(
                () -> error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."));
    if (role != MemberRole.ADMIN)
      throw error(HttpStatus.FORBIDDEN, "FORBIDDEN", "관리자만 조회할 수 있습니다.");
    if (orderId < 1 || (version != null && version < 1))
      throw error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "조회 조건을 확인해 주세요.");
    var order =
        orders
            .findById(orderId)
            .filter(o -> TradePolicyVersion.isContract(o.getPolicyVersion()))
            .orElseThrow(AdminContractPdfQueryService::missing);
    var contract =
        (version == null
                ? contracts.findByOrderIdAndActiveSlot(orderId, 1)
                : contracts.findByOrderIdAndVersion(orderId, version))
            .orElseThrow(AdminContractPdfQueryService::missing);
    var now =
        UtcTimes.nowMicros(clock);
    boolean current = Integer.valueOf(1).equals(contract.getActiveSlot());
    return new AdminContractPdfResponse(
        Long.toString(orderId),
        contract.getId().toString(),
        contract.getVersion(),
        contract.getStatus(),
        current,
        hasFileMetadata(contract),
        at(contract.getCreatedAt()),
        at(contract.getUpdatedAt()),
        at(now),
        candidate(order, contract, current, now));
  }

  private GenerationCandidate candidate(
      Order order, OrderContract contract, boolean current, LocalDateTime now) {
    if (!current) return blocked(BlockReason.NOT_CURRENT_VERSION);
    if (!Set.of(OrderStatus.AWAITING_CONTRACT, OrderStatus.AWAITING_PAYMENT)
            .contains(order.getStatus())
        || order.getAcceptedAt() == null
        || order.getPaidAt() != null) return blocked(BlockReason.ORDER_NOT_WRITABLE);
    if (!cancellations.findByOrderIdAndStatus(order.getId(), CancellationRequestStatus.PENDING).isEmpty())
      return blocked(BlockReason.CANCELLATION_PENDING);
    if (contract.getUpdatedAt().isAfter(now)) return blocked(BlockReason.CLOCK_BEFORE_UPDATE);
    return switch (contract.getStatus()) {
      case "DRAFT" -> eligible(GenerationKind.INITIAL_GENERATION);
      case "FAILED" -> eligible(GenerationKind.RETRY_FAILED);
      case "GENERATING" ->
          contract.getUpdatedAt().plus(timeout).isAfter(now)
              ? blocked(BlockReason.GENERATION_ACTIVE)
              : eligible(GenerationKind.RETRY_EXPIRED);
      case "READY" -> blocked(BlockReason.DOCUMENT_READY);
      case "SIGNED" -> blocked(BlockReason.DOCUMENT_SIGNED);
      default -> blocked(BlockReason.UNSUPPORTED_STATE);
    };
  }

  private static boolean hasFileMetadata(OrderContract c) {
    return c.getFileStorageKey() != null
        && !c.getFileStorageKey().isBlank()
        && "LOCAL".equals(c.getFileBackend())
        && c.getFileSize() != null
        && c.getFileSize() > 0
        && c.getFileSize()
            <= com.example.lancerpick.contract.document.PrivateContractStorage.MAX_BYTES
        && c.getFileHash() != null
        && c.getFileHash().length == 32;
  }

  private static GenerationCandidate blocked(BlockReason reason) {
    return new GenerationCandidate(false, GenerationKind.NONE, reason);
  }

  private static GenerationCandidate eligible(GenerationKind kind) {
    return new GenerationCandidate(true, kind, null);
  }

  private static OffsetDateTime at(LocalDateTime value) {
    return value.atOffset(ZoneOffset.UTC);
  }

  private static BusinessException missing() {
    return error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "계약서를 찾을 수 없습니다.");
  }

  private static BusinessException error(HttpStatus status, String code, String message) {
    return new BusinessException(status, code, message);
  }
}
