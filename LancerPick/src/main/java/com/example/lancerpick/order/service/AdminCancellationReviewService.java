package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.common.text.TextNormalizer;
import com.example.lancerpick.inquiry.entity.SupportInquiry;
import com.example.lancerpick.inquiry.repository.SupportInquiryRepository;
import com.example.lancerpick.member.entity.MemberRole;
import com.example.lancerpick.notification.service.OrderEventPublisher;
import com.example.lancerpick.order.dto.AdminCancellationReviewResponse;
import com.example.lancerpick.order.entity.CancellationRequest;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderHistory;
import com.example.lancerpick.order.entity.OrderHistoryAction;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.order.repository.CancellationRequestRepository;
import com.example.lancerpick.order.repository.OrderHistoryRepository;
import com.example.lancerpick.order.repository.OrderRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Set;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 운영 문의(CANCELLATION_REVIEW)에 연결된 대기 취소 요청을 관리자가 승인/거절한다(K4). 이 서비스가 트랜잭션을 소유하고 관리자
 * 공유 잠금 → 주문 배타 잠금 → 문의 잠금 → (승인 시) 구매자 지갑 잠금 순서로 잠근다. 승인은 보관금 전액 반환·종료 기록
 * ADMIN_CANCELLATION, 거절은 취소 요청 REJECTED(ADMIN)이며 거절 시각이 다음 검수 기산점이 된다. 두 경우 모두 같은 트랜잭션에서
 * 문의를 종료한다.
 *
 * <p>관리자 API·화면(김창재)은 이 서비스를 그대로 호출한다. 같은 결정을 다시 보내면 현재 결과를 돌려주고, 판매자 승인·구매자
 * 철회가 먼저 끝났으면 409로 알린다.
 */
@Service
@Profile("!demo")
@Transactional(isolation = Isolation.READ_COMMITTED)
public class AdminCancellationReviewService {
  public enum Decision {
    APPROVED,
    REJECTED
  }

  private static final Set<OrderStatus> PAID_OPEN =
      Set.of(
          OrderStatus.IN_PROGRESS, OrderStatus.AWAITING_CONFIRMATION, OrderStatus.REVISION_IN_PROGRESS);

  private final OrderRepository orders;
  private final SupportInquiryRepository inquiries;
  private final CancellationRequestRepository requests;
  private final OrderHistoryRepository histories;
  private final OrderEventPublisher events;
  private final FullFundingService fullFunding;
  private final Clock clock;

  public AdminCancellationReviewService(
      OrderRepository orders,
      SupportInquiryRepository inquiries,
      CancellationRequestRepository requests,
      OrderHistoryRepository histories,
      OrderEventPublisher events,
      FullFundingService fullFunding,
      Clock clock) {
    this.orders = orders;
    this.inquiries = inquiries;
    this.requests = requests;
    this.histories = histories;
    this.events = events;
    this.fullFunding = fullFunding;
    this.clock = clock;
  }

  public AdminCancellationReviewResponse approve(long adminId, long inquiryId, String reason) {
    return review(adminId, inquiryId, Decision.APPROVED, reason);
  }

  public AdminCancellationReviewResponse reject(long adminId, long inquiryId, String reason) {
    return review(adminId, inquiryId, Decision.REJECTED, reason);
  }

  private AdminCancellationReviewResponse review(
      long adminId, long inquiryId, Decision decision, String rawReason) {
    String reason = rawReason == null ? "" : TextNormalizer.normalize(rawReason);
    if (inquiryId < 1 || reason.isEmpty() || reason.codePointCount(0, reason.length()) > 1000)
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "문의 ID와 처리 사유(공백 정리 후 1~1000자)를 확인해 주세요.");
    var admin =
        orders
            .findActorForShare(adminId)
            .orElseThrow(() ->
                new BusinessException(
                    HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."));
    if (admin.getRole() != MemberRole.ADMIN)
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "관리자만 취소 요청을 검토할 수 있습니다.");
    // 문의의 주문 ID는 바뀌지 않으므로 잠금 없이 읽고, 주문을 먼저 잠근 뒤 문의를 잠가 다시 확인한다.
    var target =
        inquiries.findTarget(inquiryId).orElseThrow(AdminCancellationReviewService::notFound);
    if (!SupportInquiry.CANCELLATION_REVIEW.equals(target.getType()))
      throw conflict("INQUIRY_NOT_CANCELLATION_REVIEW", "취소 검토 문의가 아닙니다.");
    var order =
        orders
            .findForSystemUpdate(target.getOrderId())
            .orElseThrow(AdminCancellationReviewService::integrity);
    var inquiry =
        inquiries.findForUpdate(inquiryId).orElseThrow(AdminCancellationReviewService::integrity);
    if (!inquiry.isCancellationReview() || !order.getId().equals(inquiry.getOrderId()))
      throw integrity();
    var request =
        requests
            .findById(inquiry.getCancellationRequestId())
            .filter(r -> r.getOrderId() == order.getId())
            .orElseThrow(AdminCancellationReviewService::integrity);
    if (inquiry.getRequesterId() != order.getBuyerId()
        || request.getRequestedBy() != order.getBuyerId()) throw integrity();
    if (!inquiry.isOpen()) return closedResult(order, inquiry, request, decision);
    if (!"PENDING".equals(request.getStatus()))
      throw conflict("REQUEST_ALREADY_RESOLVED", "취소 요청이 이미 처리되었습니다. 문의를 확인해 주세요.");
    if (!TradePolicyVersion.CONTRACT_V3.name().equals(order.getPolicyVersion()))
      throw conflict("ORDER_POLICY_NOT_READY", "결제 금액 보관 정책 주문만 관리자 취소 검토를 지원합니다.");
    if (order.getPaidAt() == null || !PAID_OPEN.contains(order.getStatus()))
      throw conflict("ORDER_STATE_CONFLICT", "결제 후 미완료 주문만 처리할 수 있습니다.");
    if (requests.findByOrderIdAndStatus(order.getId(), "PENDING").size() != 1) throw integrity();
    var at = now(order, request, inquiry);
    if (decision == Decision.APPROVED) {
      fullFunding.adminRefund(order, request, adminId, inquiryId, reason, at);
    } else {
      request.resolveByAdmin("REJECTED", adminId, reason, at);
      requests.flush();
      events.publish(
          order,
          histories.saveAndFlush(
              OrderHistory.cancellationRequestEvent(
                  order.getId(),
                  adminId,
                  OrderHistoryAction.CANCELLATION_REJECTED,
                  order.getStatus(),
                  at,
                  reason)));
    }
    inquiry.close(adminId, at);
    inquiries.flush();
    return result(order, inquiry, request);
  }

  /** 이미 종료된 문의: 같은 관리자 결정의 재전송이면 현재 결과를, 아니면 충돌을 알린다. */
  private AdminCancellationReviewResponse closedResult(
      Order order, SupportInquiry inquiry, CancellationRequest request, Decision decision) {
    if (!"ADMIN".equals(request.getResolutionSource())
        || !decision.name().equals(request.getStatus()))
      throw conflict("INQUIRY_CLOSED", "이미 종료된 문의입니다.");
    return result(order, inquiry, request);
  }

  private static AdminCancellationReviewResponse result(
      Order order, SupportInquiry inquiry, CancellationRequest request) {
    return new AdminCancellationReviewResponse(
        inquiry.getId().toString(),
        order.getId().toString(),
        request.getId().toString(),
        request.getStatus(),
        order.getStatus().name(),
        inquiry.getStatus(),
        inquiry.getResolvedAt().atOffset(ZoneOffset.UTC));
  }

  private LocalDateTime now(Order order, CancellationRequest request, SupportInquiry inquiry) {
    var at =
        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    if (at.isBefore(order.getUpdatedAt())
        || at.isBefore(request.getRequestedAt())
        || at.isBefore(inquiry.getUpdatedAt()))
      throw new IllegalStateException("Admin cancellation review clock moved backwards");
    return at;
  }

  private static BusinessException notFound() {
    return new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "문의를 찾을 수 없습니다.");
  }

  private static BusinessException integrity() {
    return new BusinessException(
        HttpStatus.INTERNAL_SERVER_ERROR, "DATA_INTEGRITY_ERROR", "문의·취소 요청 기록을 확인해야 합니다.");
  }

  private static BusinessException conflict(String code, String message) {
    return new BusinessException(HttpStatus.CONFLICT, code, message);
  }
}
