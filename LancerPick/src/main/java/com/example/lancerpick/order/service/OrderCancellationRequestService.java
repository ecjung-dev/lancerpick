package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.text.TextNormalizer;
import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.notification.service.OrderEventPublisher;
import com.example.lancerpick.order.dto.CancellationResultResponse;
import com.example.lancerpick.order.dto.CreateCancellationRequest;
import com.example.lancerpick.order.entity.CancellationRequestStatus;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderHistory;
import com.example.lancerpick.order.entity.OrderHistoryAction;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.order.repository.CancellationRequestQueries;
import com.example.lancerpick.order.repository.OrderDetailQueries;
import com.example.lancerpick.order.repository.OrderHistoryRepository;
import com.example.lancerpick.order.repository.OrderRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.UUID;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!demo")
@Transactional(isolation = Isolation.READ_COMMITTED)
public class OrderCancellationRequestService {
  public enum Resolution {
    APPROVED,
    REJECTED,
    WITHDRAWN;

    CancellationRequestStatus status() {
      return CancellationRequestStatus.valueOf(name());
    }
  }

  private final OrderRepository orders;
  private final OrderCommandLocks locks;
  private final CancellationRequestQueries requests;
  private final OrderHistoryRepository histories;
  private final OrderEventPublisher events;
  private final OrderDetailQueries details;
  private final OrderCancellationService cancellations;
  private final ReviewTimers reviewTimers;
  private final Clock clock;

  public OrderCancellationRequestService(
      OrderRepository orders,
      OrderCommandLocks locks,
      CancellationRequestQueries requests,
      OrderHistoryRepository histories,
      OrderEventPublisher events,
      OrderDetailQueries details,
      OrderCancellationService cancellations,
      ReviewTimers reviewTimers,
      Clock clock) {
    this.orders = orders;
    this.locks = locks;
    this.requests = requests;
    this.histories = histories;
    this.events = events;
    this.details = details;
    this.cancellations = cancellations;
    this.reviewTimers = reviewTimers;
    this.clock = clock;
  }

  public Created create(long memberId, String id, CreateCancellationRequest input) {
    long orderId = positiveId(id);
    if (input == null) throw invalid();
    String reason = normalized(input.reason(), false);
    String key = uuid(input.requestKey());
    var order = lock(orderId, memberId);
    requireRole(order.getBuyerId().equals(memberId));
    var existing = requests.byKey(orderId, memberId, key);
    if (existing.isPresent()) {
      if (!existing.get().reason().equals(reason))
        throw conflict("IDEMPOTENCY_KEY_REUSED", "다른 사유에 사용한 요청 키입니다.");
      return new Created(response(order, memberId, existing.get().id()), false);
    }
    requirePaidOpen(order);
    if (requests.pending(orderId) > 0) throw conflict("CANCELLATION_PENDING", "대기 중인 취소 요청이 있습니다.");
    LocalDateTime at = now(order);
    // 취소 요청 대기 중에는 자동 확정하지 않는다. 거절·철회 시각이 다음 검수 기산점이 된다.
    reviewTimers.closeForBuyerResponse(order, memberId, at, ReviewTimers.BUYER_RESPONSE);
    long requestId = requests.insert(orderId, memberId, key, reason, at);
    events.publish(
        order,
        histories.saveAndFlush(
            OrderHistory.cancellationRequestEvent(
                orderId,
                memberId,
                OrderHistoryAction.CANCELLATION_REQUESTED,
                order.getStatus(),
                at,
                reason)));
    return new Created(response(order, memberId, requestId), true);
  }

  public CancellationResultResponse resolve(
      long memberId, String id, String requestId, Resolution resolution, String rawResponseReason) {
    long orderId = positiveId(id);
    long targetId = positiveId(requestId);
    String responseReason =
        resolution == Resolution.REJECTED ? normalized(rawResponseReason, true) : null;
    var order = lock(orderId, memberId);
    boolean buyer = order.getBuyerId().equals(memberId);
    requireRole(resolution == Resolution.WITHDRAWN ? buyer : !buyer);
    var request =
        requests.byOrder(orderId, targetId).orElseThrow(OrderCancellationRequestService::notFound);
    if (request.requestedBy() != order.getBuyerId()) throw integrity();
    if (request.status() != CancellationRequestStatus.PENDING) {
      if (request.status() != resolution.status())
        throw conflict("REQUEST_ALREADY_RESOLVED", "다른 방식으로 이미 처리한 요청입니다.");
      return response(order, memberId, targetId);
    }
    requirePaidOpen(order);
    if (requests.pending(orderId) != 1) throw integrity();
    LocalDateTime at = now(order);
    if (at.isBefore(request.requestedAt()))
      throw new IllegalStateException("Cancellation resolution clock moved backwards");
    if (resolution == Resolution.APPROVED) {
      // 기존 주문 취소·전액 환불·원장·대기 요청 승인 처리를 같은 트랜잭션에 참여시킨다.
      cancellations.cancel(memberId, id, request.reason());
      requests.clearApprovalResponseReason(targetId);
      if (!requests
          .byOrder(orderId, targetId)
          .orElseThrow(OrderCancellationRequestService::integrity)
          .status()
          .equals(CancellationRequestStatus.APPROVED)) throw integrity();
    } else {
      if (requests.resolve(targetId, resolution.status(), memberId, responseReason, at) != 1)
        throw integrity();
      var action =
          resolution == Resolution.REJECTED
              ? OrderHistoryAction.CANCELLATION_REJECTED
              : OrderHistoryAction.CANCELLATION_WITHDRAWN;
      events.publish(
          order,
          histories.saveAndFlush(
              OrderHistory.cancellationRequestEvent(
                  orderId, memberId, action, order.getStatus(), at, responseReason)));
    }
    return response(order, memberId, targetId);
  }

  // 404 문구가 주문 공통 문구와 달라 주문 조회는 직접 한다. 정책 검증(V3 또는 LEGACY_V1)은 저장소 조회가 맡는다.
  private Order lock(long orderId, long memberId) {
    locks.lockActor(memberId);
    return orders
        .findForFundedOrLegacyDecision(orderId, memberId)
        .orElseThrow(OrderCancellationRequestService::notFound);
  }

  private CancellationResultResponse response(Order order, long memberId, long requestId) {
    var request =
        requests
            .byOrder(order.getId(), requestId)
            .orElseThrow(OrderCancellationRequestService::integrity);
    var detail = details.forParticipant(order.getId(), memberId);
    return new CancellationResultResponse(
        request.response(),
        OrderAllowedActions.attach(detail, order.getBuyerId().equals(memberId)));
  }

  private LocalDateTime now(Order order) {
    LocalDateTime at = UtcTimes.nowMicros(clock);
    if (at.getYear() < 1000 || at.getYear() > 9999 || at.isBefore(order.getUpdatedAt()))
      throw new IllegalStateException("Invalid cancellation request clock");
    return at;
  }

  private static void requirePaidOpen(Order order) {
    if (order.getPaidAt() == null
        || !order.getStatus().isWorkOpen())
      throw conflict("ORDER_STATE_CONFLICT", "결제 후 미완료 주문만 취소 요청할 수 있습니다.");
  }

  private static void requireRole(boolean allowed) {
    if (!allowed)
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "이 요청을 처리할 권한이 없습니다.");
  }

  private static String normalized(String raw, boolean optional) {
    if (raw == null) {
      if (optional) return null;
      throw invalid();
    }
    String text = TextNormalizer.normalize(raw);
    if (text.isEmpty() || text.codePointCount(0, text.length()) > 1000) throw invalid();
    return text;
  }

  private static String uuid(String key) {
    if (key == null
        || !key.matches(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"))
      throw invalid();
    return UUID.fromString(key).toString();
  }

  private static long positiveId(String id) {
    return OrderIds.positive(id).orElseThrow(OrderCancellationRequestService::invalid);
  }

  private static BusinessException invalid() {
    return new BusinessException(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "취소 요청 입력값을 확인해 주세요.");
  }

  private static BusinessException notFound() {
    return new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "요청한 대상을 찾을 수 없습니다.");
  }

  private static BusinessException integrity() {
    return new BusinessException(
        HttpStatus.INTERNAL_SERVER_ERROR, "DATA_INTEGRITY_ERROR", "취소 요청 기록을 확인해야 합니다.");
  }

  private static BusinessException conflict(String code, String message) {
    return new BusinessException(HttpStatus.CONFLICT, code, message);
  }

  public record Created(CancellationResultResponse result, boolean created) {}
}
