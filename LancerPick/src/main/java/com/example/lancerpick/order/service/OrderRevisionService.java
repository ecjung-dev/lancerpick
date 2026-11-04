package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.order.dto.CreateRevisionRequest;
import com.example.lancerpick.order.dto.OrderDetailResponse;
import com.example.lancerpick.order.entity.OrderHistory;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.order.entity.RevisionRequest;
import com.example.lancerpick.order.repository.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!demo")
public class OrderRevisionService {
  private final OrderRepository orders;
  private final DeliveryRepository deliveries;
  private final RevisionRequestRepository revisions;
  private final OrderHistoryRepository histories;
  private final OrderDetailQueries details;
  private final Clock clock;

  public OrderRevisionService(
      OrderRepository orders,
      DeliveryRepository deliveries,
      RevisionRequestRepository revisions,
      OrderHistoryRepository histories,
      OrderDetailQueries details,
      Clock clock) {
    this.orders = orders;
    this.deliveries = deliveries;
    this.revisions = revisions;
    this.histories = histories;
    this.details = details;
    this.clock = clock;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public OrderDetailResponse request(long memberId, String id, CreateRevisionRequest input) {
    long orderId = positiveId(id);
    if (input == null) throw invalidInput();
    long deliveryId = positiveId(input.deliveryId());
    if (orders.lockDecisionActor(memberId).isEmpty())
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
    var order =
        orders.findForDecision(orderId, memberId).orElseThrow(() -> notFound("주문을 찾을 수 없습니다."));
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
    LocalDateTime at =
        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    var revision =
        RevisionRequest.create(
            orderId, deliveryId, input.reason(), at, order.getDurationDaysSnapshot());
    order.requestRevision(at);
    revisions.saveAndFlush(revision);
    histories.saveAndFlush(
        OrderHistory.revisionRequested(orderId, memberId, at, revision.getReason()));
    var detail = details.forParticipant(orderId, memberId);
    return detail.withAllowedActions(
        OrderAllowedActions.actions(
            detail.status(),
            true,
            detail.revisionRequest(),
            detail.cancellationRequests(),
            detail.review()));
  }

  private static long positiveId(String id) {
    try {
      if (id == null || !id.matches("[0-9]+")) throw invalidInput();
      long value = Long.parseLong(id);
      if (value <= 0) throw invalidInput();
      return value;
    } catch (NumberFormatException e) {
      throw invalidInput();
    }
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
