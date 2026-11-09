package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.notification.service.OrderEventPublisher;
import com.example.lancerpick.order.dto.CreateRevisionRequest;
import com.example.lancerpick.order.dto.OrderDetailResponse;
import com.example.lancerpick.order.entity.OrderHistory;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.order.entity.RevisionRequest;
import com.example.lancerpick.order.repository.DeliveryRepository;
import com.example.lancerpick.order.repository.OrderDetailQueries;
import com.example.lancerpick.order.repository.OrderHistoryRepository;
import com.example.lancerpick.order.repository.RevisionRequestRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!demo")
public class OrderRevisionService {
  private final OrderCommandLocks locks;
  private final DeliveryRepository deliveries;
  private final RevisionRequestRepository revisions;
  private final OrderHistoryRepository histories;
  private final OrderEventPublisher events;
  private final OrderDetailQueries details;
  private final OrderFundingGuard funding;
  private final ReviewTimers reviewTimers;
  private final DeadlineNotices deadlineNotices;
  private final Clock clock;

  public OrderRevisionService(
      OrderCommandLocks locks,
      DeliveryRepository deliveries,
      RevisionRequestRepository revisions,
      OrderHistoryRepository histories,
      OrderEventPublisher events,
      OrderDetailQueries details,
      OrderFundingGuard funding,
      ReviewTimers reviewTimers,
      DeadlineNotices deadlineNotices,
      Clock clock) {
    this.locks = locks;
    this.deliveries = deliveries;
    this.revisions = revisions;
    this.histories = histories;
    this.events = events;
    this.details = details;
    this.funding = funding;
    this.reviewTimers = reviewTimers;
    this.deadlineNotices = deadlineNotices;
    this.clock = clock;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public OrderDetailResponse request(long memberId, String id, CreateRevisionRequest input) {
    long orderId = positiveId(id);
    if (input == null) throw invalidInput();
    long deliveryId = positiveId(input.deliveryId());
    var order = locks.lockFulfillmentOrder(memberId, orderId);
    if (!order.getBuyerId().equals(memberId))
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "구매자만 수정 요청할 수 있습니다.");
    // 이미 사용한 수정 권한은 상태가 재납품 확인 대기로 바뀌어도 되살아나지 않는다.
    if (revisions.existsByOrderId(orderId))
      throw conflict("REVISION_ALREADY_USED", "수정 요청은 주문당 한 번만 가능합니다.");
    if (order.getStatus() != OrderStatus.AWAITING_CONFIRMATION)
      throw conflict("ORDER_STATE_CONFLICT", "납품 확인 대기 주문만 수정 요청할 수 있습니다.");
    int sequence =
        deliveries
            .sequenceForOrderDelivery(orderId, deliveryId)
            .orElseThrow(() -> notFound("납품을 찾을 수 없습니다."));
    if (sequence != 1) throw conflict("REVISION_ALREADY_USED", "최초 납품에만 수정 요청할 수 있습니다.");
    if (deliveries.pendingCancellations(orderId) > 0)
      throw conflict("CANCELLATION_PENDING", "대기 중인 취소 요청을 먼저 처리해 주세요.");
    funding.requireHeldForWork(order);
    LocalDateTime at = UtcTimes.nowMicros(clock);
    // 수정 요청은 구매자의 검수 응답이다. 활성 자동 확정 타이머를 끝내고, 재납품이 새 기산점이 된다.
    reviewTimers.closeForBuyerResponse(order, memberId, at, ReviewTimers.BUYER_RESPONSE);
    var revision =
        RevisionRequest.create(
            orderId, deliveryId, input.reason(), at, order.getDurationDaysSnapshot());
    order.requestRevision(at);
    revisions.saveAndFlush(revision);
    events.publish(
        order,
        histories.saveAndFlush(
            OrderHistory.revisionRequested(orderId, memberId, at, revision.getReason())));
    deadlineNotices.redeliveryDue(order, revision);
    var detail = details.forParticipant(orderId, memberId);
    return detail.withAllowedActions(
        OrderAllowedActions.actions(
            detail.policyVersion(),
            detail.status(),
            true,
            detail.revisionRequest(),
            detail.cancellationRequests(),
            detail.review()));
  }

  private static long positiveId(String id) {
    return OrderIds.positive(id).orElseThrow(OrderRevisionService::invalidInput);
  }

  private static BusinessException invalidInput() {
    return new BusinessException(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "수정 요청 입력값을 확인해 주세요.");
  }

  private static BusinessException notFound(String message) {
    return new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", message);
  }

  private static BusinessException conflict(String code, String message) {
    return new BusinessException(HttpStatus.CONFLICT, code, message);
  }
}
