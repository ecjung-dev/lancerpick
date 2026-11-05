package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.order.dto.CreateDeliveryRequest;
import com.example.lancerpick.order.dto.OrderDetailResponse;
import com.example.lancerpick.order.entity.Delivery;
import com.example.lancerpick.order.entity.OrderHistory;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.notification.service.OrderEventPublisher;
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
public class OrderDeliveryService {
  private final OrderRepository orders;
  private final DeliveryRepository deliveries;
  private final OrderHistoryRepository histories;
  private final OrderEventPublisher events;
  private final OrderDetailQueries details;
  private final Clock clock;

  public OrderDeliveryService(
      OrderRepository orders,
      DeliveryRepository deliveries,
      OrderHistoryRepository histories,
      OrderEventPublisher events,
      OrderDetailQueries details,
      Clock clock) {
    this.orders = orders;
    this.deliveries = deliveries;
    this.histories = histories;
    this.events = events;
    this.details = details;
    this.clock = clock;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public OrderDetailResponse submit(long memberId, String id, CreateDeliveryRequest request) {
    long orderId;
    try {
      if (id == null || !id.matches("[0-9]+")) throw invalidInput();
      orderId = Long.parseLong(id);
      if (orderId <= 0) throw invalidInput();
    } catch (NumberFormatException e) {
      throw invalidInput();
    }
    if (request == null
        || request.expectedSequenceNo() == null
        || request.expectedSequenceNo() < 1
        || request.expectedSequenceNo() > 2) throw invalidInput();
    if (orders.lockDecisionActor(memberId).isEmpty())
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
    var order =
        orders
            .findForLegacyDecision(orderId, memberId)
            .orElseThrow(() ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "주문을 찾을 수 없습니다."));
    if (!order.getSellerId().equals(memberId))
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "판매자만 납품할 수 있습니다.");
    int sequence = request.expectedSequenceNo();
    // 이미 제출한 회차는 현재 상태·취소 요청 검사보다 먼저 확인한다. 재전송은 항상 충돌이다.
    if (deliveries.existsByOrderIdAndSequenceNo(orderId, sequence))
      throw conflict("DELIVERY_ALREADY_SUBMITTED", "이미 제출한 납품 회차입니다.");
    OrderStatus from = order.getStatus();
    int allowed =
        from == OrderStatus.IN_PROGRESS ? 1 : from == OrderStatus.REVISION_IN_PROGRESS ? 2 : 0;
    if (sequence != allowed) throw conflict("DELIVERY_SEQUENCE_CONFLICT", "현재 상태가 허용하는 회차가 아닙니다.");
    if (deliveries.pendingCancellations(orderId) > 0)
      throw conflict("CANCELLATION_PENDING", "대기 중인 취소 요청을 먼저 처리해 주세요.");
    if (sequence == 2
        && (deliveries.revisions(orderId) != 1
            || !deliveries.existsByOrderIdAndSequenceNo(orderId, 1))) {
      throw new BusinessException(
          HttpStatus.INTERNAL_SERVER_ERROR, "DATA_INTEGRITY_ERROR", "수정 요청과 최초 납품 기록을 확인해야 합니다.");
    }
    LocalDateTime at =
        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    var delivery =
        Delivery.create(orderId, sequence, request.description(), request.resultUrl(), at);
    order.deliver(at);
    deliveries.saveAndFlush(delivery);
    events.publish(order, histories.saveAndFlush(OrderHistory.delivered(orderId, memberId, from, at)));
    var detail = details.forParticipant(orderId, memberId);
    return detail.withAllowedActions(
        OrderAllowedActions.actions(
            detail.policyVersion(), detail.status(),
            false,
            detail.revisionRequest(),
            detail.cancellationRequests(),
            detail.review()));
  }

  private static BusinessException invalidInput() {
    return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "납품 입력값을 확인해 주세요.");
  }

  private static BusinessException conflict(String code, String message) {
    return new BusinessException(HttpStatus.CONFLICT, code, message);
  }
}
