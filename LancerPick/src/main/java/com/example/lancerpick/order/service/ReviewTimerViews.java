package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.member.service.MemberSanctionQuery;
import com.example.lancerpick.order.dto.ReviewTimerResponse;
import com.example.lancerpick.order.entity.CancellationRequestStatus;
import com.example.lancerpick.order.entity.Delivery;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderResponseTimer;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.order.repository.CancellationRequestRepository;
import com.example.lancerpick.order.repository.DeliveryRepository;
import com.example.lancerpick.order.repository.OrderClosureRepository;
import com.example.lancerpick.order.repository.OrderResponseTimerRepository;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * 자동 구매 확정 타이머의 기산점·시작 가능 판단과 응답 변환. {@link ReviewTimerService}의 조회·명령 트랜잭션 안에서 호출되며
 * 잠금이나 상태 변경을 하지 않는다. 기산점은 마지막 납품 시각과 거절·철회된 취소 요청의 처리 시각 중 늦은 값이다.
 */
@Component
@Profile("!demo")
public class ReviewTimerViews {
  private final OrderResponseTimerRepository timers;
  private final DeliveryRepository deliveries;
  private final CancellationRequestRepository cancellations;
  private final OrderClosureRepository closures;
  private final MemberSanctionQuery sanctions;

  ReviewTimerViews(
      OrderResponseTimerRepository timers,
      DeliveryRepository deliveries,
      CancellationRequestRepository cancellations,
      OrderClosureRepository closures,
      MemberSanctionQuery sanctions) {
    this.timers = timers;
    this.deliveries = deliveries;
    this.cancellations = cancellations;
    this.closures = closures;
    this.sanctions = sanctions;
  }

  ReviewTimerResponse view(Order order, long memberId, LocalDateTime now) {
    long orderId = order.getId();
    var active = timers.findByOrderIdAndStatus(orderId, OrderResponseTimer.ACTIVE);
    if (active.isPresent()) return response("ACTIVE", active.get(), false);
    if (order.getStatus() == OrderStatus.COMPLETED) {
      var closure = closures.findByOrderId(orderId).orElse(null);
      if (closure != null
          && "AUTO_CONFIRMED".equals(closure.getReason())
          && closure.getSourceTimerId() != null) {
        var timer = timers.findById(closure.getSourceTimerId()).orElseThrow(ReviewTimerViews::integrity);
        return response("AUTO_CONFIRMED", timer, false);
      }
      return empty("NOT_IN_REVIEW");
    }
    if (order.getStatus() != OrderStatus.AWAITING_CONFIRMATION) return empty("NOT_IN_REVIEW");
    if (!cancellations.findByOrderIdAndStatus(orderId, CancellationRequestStatus.PENDING).isEmpty())
      return empty("CANCELLATION_PENDING");
    var basis = basisAt(order, currentDelivery(order));
    var eligible = OrderResponseTimer.eligibleAt(basis);
    if (adminStoppedSince(orderId, basis))
      return new ReviewTimerResponse("ADMIN_STOPPED", utc(basis), utc(eligible), null, null, false);
    if (now.isBefore(eligible))
      return new ReviewTimerResponse("WAITING", utc(basis), utc(eligible), null, null, false);
    boolean canStart =
        order.getSellerId() == memberId
            && !sanctions.restriction(order.getBuyerId()).securityLocked();
    return new ReviewTimerResponse("STARTABLE", utc(basis), utc(eligible), null, null, canStart);
  }

  Delivery currentDelivery(Order order) {
    return deliveries
        .findFirstByOrderIdOrderBySequenceNoDesc(order.getId())
        .orElseThrow(ReviewTimerViews::integrity);
  }

  LocalDateTime basisAt(Order order, Delivery delivery) {
    var basis = delivery.getSubmittedAt();
    var reopened = cancellations.latestReopenedAt(order.getId()).orElse(null);
    return reopened != null && reopened.isAfter(basis) ? reopened : basis;
  }

  boolean adminStoppedSince(long orderId, LocalDateTime basis) {
    return timers.latestAdminStopAt(orderId).map(at -> !at.isBefore(basis)).orElse(false);
  }

  private static ReviewTimerResponse response(String status, OrderResponseTimer timer, boolean canStart) {
    return new ReviewTimerResponse(
        status,
        utc(timer.getBasisAt()),
        utc(timer.getEligibleAt()),
        utc(timer.getStartedAt()),
        utc(timer.getExpiresAt()),
        canStart);
  }

  private static ReviewTimerResponse empty(String status) {
    return new ReviewTimerResponse(status, null, null, null, null, false);
  }

  private static OffsetDateTime utc(LocalDateTime value) {
    return value == null ? null : value.atOffset(ZoneOffset.UTC);
  }

  private static BusinessException integrity() {
    return new BusinessException(
        HttpStatus.INTERNAL_SERVER_ERROR, "DATA_INTEGRITY_ERROR", "검수 타이머 기록을 확인해야 합니다.");
  }
}
