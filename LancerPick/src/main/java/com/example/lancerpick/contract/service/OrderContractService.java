package com.example.lancerpick.contract.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.contract.dto.ContractResponse;
import com.example.lancerpick.contract.entity.*;
import com.example.lancerpick.contract.repository.*;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.notification.service.OrderEventPublisher;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderHistory;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.order.repository.*;
import com.example.lancerpick.order.service.OrderCommandLocks;
import com.example.lancerpick.payment.service.ContractPaymentPolicy;
import java.io.IOException;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** No HTTP activation yet. Internal contract commands serialize on the parent order. */
@Service
@Profile("!demo")
@Transactional(isolation = Isolation.READ_COMMITTED)
public class OrderContractService {
  private final OrderRepository orders;
  private final OrderCommandLocks locks;
  private final OrderSnapshotRepository snapshots;
  private final CancellationRequestRepository cancellations;
  private final MemberRepository members;
  private final OrderContractRepository contracts;
  private final ContractSignatureRepository signatures;
  private final OrderHistoryRepository histories;
  private final OrderEventPublisher events;
  private final JsonMapper mapper;
  private final Clock clock;
  private final String standardBody;
  private final String fullFundingBody;

  public OrderContractService(
      OrderRepository orders,
      OrderCommandLocks locks,
      OrderSnapshotRepository snapshots,
      CancellationRequestRepository cancellations,
      MemberRepository members,
      OrderContractRepository contracts,
      ContractSignatureRepository signatures,
      OrderHistoryRepository histories,
      OrderEventPublisher events,
      JsonMapper mapper,
      Clock clock) {
    this.orders = orders;
    this.locks = locks;
    this.snapshots = snapshots;
    this.cancellations = cancellations;
    this.members = members;
    this.contracts = contracts;
    this.signatures = signatures;
    this.histories = histories;
    this.events = events;
    this.mapper = mapper;
    this.clock = clock;
    try (var stream = new ClassPathResource("contracts/standard-v1.txt").getInputStream()) {
      standardBody =
          new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
              .replace("\r\n", "\n")
              .replace("\r", "\n");
    } catch (IOException e) {
      throw new IllegalStateException("Missing standard contract", e);
    }
    try (var stream =
        new ClassPathResource("contracts/standard-v2-full-funding.txt").getInputStream()) {
      fullFundingBody =
          new String(stream.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
              .replace("\r\n", "\n")
              .replace("\r", "\n");
    } catch (IOException e) {
      throw new IllegalStateException("Missing full funding contract", e);
    }
  }

  public ContractResponse prepare(
      long memberId, long orderId, int expectedVersion, String specialTerms) {
    var order = lock(memberId, orderId, OrderStatus.AWAITING_CONTRACT);
    if (!order.getSellerId().equals(memberId))
      throw error(HttpStatus.FORBIDDEN, "FORBIDDEN", "판매자만 초안을 작성할 수 있습니다.");
    // 보안 잠금 판매자의 거래 진전 명령은 회원 잠금 아래 재검사한다(인증 필터를 통과한 진행 중 요청 포함).
    locks.requireNotSecurityLocked(memberId);
    if (specialTerms == null
        || specialTerms.codePointCount(0, specialTerms.length()) > 5000
        || expectedVersion < 0)
      throw error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "특약은 5000자 이하여야 합니다.");
    var snapshot = snapshots.findById(orderId).orElseThrow(() -> conflict("주문 조건이 없습니다."));
    var split = installments(order);
    var content = new LinkedHashMap<String, Object>();
    boolean full = "CONTRACT_V3".equals(order.getPolicyVersion());
    String template = full ? "standard-v2-full-funding" : "standard-v1";
    content.put("templateVersion", template);
    content.put("standardBody", full ? fullFundingBody : standardBody);
    if (full) content.put("policyVersion", order.getPolicyVersion());
    content.put("orderId", Long.toString(orderId));
    content.put("buyerId", order.getBuyerId().toString());
    content.put("sellerId", order.getSellerId().toString());
    content.put("buyerName", partyName(order.getBuyerId()));
    content.put("sellerName", partyName(order.getSellerId()));
    content.put("productTitle", snapshot.getProductTitleSnapshot());
    content.put("description", snapshot.getDescriptionSnapshot());
    content.put("scope", snapshot.getScopeSnapshot());
    content.put("preparation", snapshot.getPreparationSnapshot());
    content.put("originalTerms", snapshot.getTermsSnapshot());
    content.put("requirements", snapshot.getRequirements());
    content.put("total", split.total());
    if (full) {
      content.put("fundingMode", "FULL_HOLD");
      content.put("feeRateBps", 1000);
    } else {
      content.put("deposit", split.deposit());
      content.put("balance", split.balance());
    }
    content.put("durationDays", order.getDurationDaysSnapshot());
    content.put("specialTerms", specialTerms);
    String canonical = mapper.writeValueAsString(new TreeMap<>(content));
    var current = contracts.findByOrderIdAndActiveSlot(orderId, 1).orElse(null);
    if (current != null && current.matches(OrderContract.hash(canonical))) return response(current);
    int version = current == null ? 0 : current.getVersion();
    if (version != expectedVersion || (current != null && "SIGNED".equals(current.getStatus())))
      throw conflict("계약 버전을 다시 확인해 주세요.");
    var at = now();
    if (current != null) {
      current.supersede(at);
      contracts.flush();
    }
    var saved =
        contracts.saveAndFlush(
            OrderContract.draft(
                orderId, version + 1, template, specialTerms, canonical, memberId, at));
    events.publish(
        order,
        histories.saveAndFlush(
            OrderHistory.contractCreated(orderId, memberId, at, saved.getVersion())));
    return response(saved);
  }

  public ContractResponse sign(
      long memberId, long orderId, int version, String contentHash, String fileHash) {
    // 계약 성립 후 주문은 결제 대기(AWAITING_PAYMENT)가 되므로, 그 상태에서는 같은 서명의 재요청만 허용한다.
    var order =
        lock(memberId, orderId, OrderStatus.AWAITING_CONTRACT, OrderStatus.AWAITING_PAYMENT);
    var contract =
        contracts.findByOrderIdAndActiveSlot(orderId, 1).orElseThrow(() -> conflict("계약이 없습니다."));
    byte[] expectedContent = parseHash(contentHash), expectedFile = parseHash(fileHash);
    if (contract.getVersion() != version
        || !contract.matches(expectedContent)
        || contract.getFileHash() == null
        || !java.security.MessageDigest.isEqual(contract.getFileHash(), expectedFile))
      throw conflict("서명할 문서가 변경되었습니다.");
    String role = order.getBuyerId().equals(memberId) ? "BUYER" : "SELLER";
    if ("SELLER".equals(role)) locks.requireNotSecurityLocked(memberId);
    var existing = signatures.findByContractIdOrderByIdAsc(contract.getId());
    if (existing.stream().anyMatch(s -> s.getMemberId() == memberId)) return response(contract);
    if (order.getStatus() != OrderStatus.AWAITING_CONTRACT || !"READY".equals(contract.getStatus()))
      throw conflict("서명 가능한 계약이 아닙니다.");
    var at = now();
    var signature = signatures.saveAndFlush(ContractSignature.sign(contract, memberId, role, at));
    if (existing.isEmpty()) {
      events.publishFirstContractSignature(order, contract, signature);
    }
    if (signatures.findByContractIdOrderByIdAsc(contract.getId()).size() == 2) {
      // 계약 확정 시점에도 최소 계약 금액을 다시 확인한다(생성 후 정책·데이터 변경 대비).
      installments(order);
      contract.signComplete(at);
      order.contractSigned(at);
      orders.flush();
      events.publish(
          order,
          histories.saveAndFlush(
              OrderHistory.contractSigned(orderId, memberId, at, contract.getVersion())));
    }
    contracts.flush();
    return response(contract);
  }

  private Order lock(long memberId, long orderId, OrderStatus... allowed) {
    // V2 계약 주문도 다루므로 정책 필터가 있는 lockFulfillmentOrder가 아니라 당사자 잠금만 쓰고 정책은 아래에서 검사한다.
    var order = locks.lockParticipantOrder(memberId, orderId);
    if (!com.example.lancerpick.common.policy.TradePolicyVersion.isContract(
            order.getPolicyVersion())
        || !Arrays.asList(allowed).contains(order.getStatus())
        || order.getAcceptedAt() == null
        || order.getPaidAt() != null
        || !cancellations.findByOrderIdAndStatus(orderId, com.example.lancerpick.order.entity.CancellationRequestStatus.PENDING).isEmpty())
      throw conflict("계약을 처리할 수 없는 주문입니다.");
    return order;
  }

  private static ContractPaymentPolicy.Installments installments(Order order) {
    long total = order.getPriceAmount();
    if (total < ContractPaymentPolicy.MIN_TOTAL || total > ContractPaymentPolicy.MAX_TOTAL)
      throw error(
          HttpStatus.CONFLICT,
          "CONTRACT_MINIMUM_AMOUNT_NOT_MET",
          "계약 금액은 10,000원 이상 1,000,000원 이하여야 합니다.");
    return ContractPaymentPolicy.installments(total);
  }

  public ContractResponse prepareHttp(long actor, long orderId, int version, String terms) {
    return prepare(actor, orderId, version, terms);
  }

  public ContractResponse signHttp(
      long actor, long orderId, int version, String contentHash, String fileHash) {
    return sign(actor, orderId, version, contentHash, fileHash);
  }

  private ContractResponse response(OrderContract c) {
    return new ContractResponse(
        c.getId().toString(),
        c.getVersion(),
        c.getStatus(),
        c.getSpecialTerms(),
        canonicalContent(c),
        HexFormat.of().formatHex(c.getContentHash()),
        c.getFileHash() == null ? null : HexFormat.of().formatHex(c.getFileHash()),
        signatures.findByContractIdOrderByIdAsc(c.getId()).stream()
            .map(s ->
                new ContractResponse.Signature(
                    Long.toString(s.getMemberId()),
                    s.getPartyRole(),
                    s.getSignedAt().atOffset(ZoneOffset.UTC)))
            .toList());
  }

  /** Internal response assembly. Authorization belongs to the calling transaction service. */
  @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
  public ContractResponse describe(OrderContract contract) {
    return response(contract);
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
  }

  // MySQL JSON may reorder keys; reconstruct the flat, sorted-key representation.
  private String canonicalContent(OrderContract contract) {
    String value =
        mapper.writeValueAsString(mapper.readValue(contract.getCanonicalContent(), TreeMap.class));
    if (!contract.matches(OrderContract.hash(value)))
      throw new IllegalStateException("Contract snapshot integrity failure");
    return value;
  }

  private static byte[] parseHash(String value) {
    if (value == null || !value.matches("[0-9a-f]{64}"))
      throw error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "해시는 64자리 소문자 16진수여야 합니다.");
    return HexFormat.of().parseHex(value);
  }

  private String partyName(Long memberId) {
    return members
        .findById(memberId)
        .orElseThrow(() ->
            error(HttpStatus.INTERNAL_SERVER_ERROR, "DATA_INTEGRITY_ERROR", "거래 당사자 기록을 확인해야 합니다."))
        .getDisplayName();
  }

  private static BusinessException conflict(String message) {
    return error(HttpStatus.CONFLICT, "CONTRACT_CONFLICT", message);
  }

  private static BusinessException error(HttpStatus status, String code, String message) {
    return new BusinessException(status, code, message);
  }
}
