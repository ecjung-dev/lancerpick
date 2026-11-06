package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.contract.repository.OrderContractRepository;
import com.example.lancerpick.notification.service.OrderEventPublisher;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.entity.OrderHistory;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.order.entity.RevisionRequest;
import com.example.lancerpick.order.repository.OrderHistoryRepository;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * V3 납기·재납품 기한 통지(E8). 결제·수정 요청과 같은 트랜잭션에서 SYSTEM 이력과 양측 알림 이벤트를 남긴다. 원천 키는 결제 ID·수정
 * 요청 ID로 정해져 UNIQUE 제약이 같은 사건의 중복 통지를 막는다. 최초 계약 PDF는 갱신하지 않는다.
 */
@Component
@Profile("!demo")
public class DeadlineNotices {
  private final OrderContractRepository contracts;
  private final OrderHistoryRepository histories;
  private final OrderEventPublisher events;
  private final JsonMapper mapper;

  public DeadlineNotices(
      OrderContractRepository contracts,
      OrderHistoryRepository histories,
      OrderEventPublisher events,
      JsonMapper mapper) {
    this.contracts = contracts;
    this.histories = histories;
    this.events = events;
    this.mapper = mapper;
  }

  /** 전액 결제 직후 최초 납기(결제 시각 + 소요일)를 통지한다. 다음 납품은 1회차다. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void deliveryDue(Order order, long paymentId) {
    if (!isFullFunding(order) || order.getStatus() != OrderStatus.IN_PROGRESS) return;
    record(
        order,
        OrderStatus.IN_PROGRESS,
        "DEADLINE:PAYMENT:" + paymentId,
        "DELIVERY_DUE",
        order.getPaidAt(),
        order.getDueAt(),
        1);
  }

  /** 수정 요청 직후 재납품 기한(수정 요청 시각 + 원래 소요일)을 통지한다. 재납품은 2회차다. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void redeliveryDue(Order order, RevisionRequest revision) {
    if (!isFullFunding(order) || order.getStatus() != OrderStatus.REVISION_IN_PROGRESS) return;
    if (revision.getId() == null) throw integrity();
    record(
        order,
        OrderStatus.REVISION_IN_PROGRESS,
        "DEADLINE:REVISION:" + revision.getId(),
        "REDELIVERY_DUE",
        revision.getRequestedAt(),
        revision.getDueAt(),
        2);
  }

  private void record(
      Order order,
      OrderStatus status,
      String sourceEventKey,
      String kind,
      LocalDateTime basisAt,
      LocalDateTime dueAt,
      int deliverySequence) {
    if (basisAt == null || dueAt == null || !dueAt.isAfter(basisAt)) throw integrity();
    var contract =
        contracts
            .findByOrderIdAndActiveSlot(order.getId(), 1)
            .filter(candidate -> "SIGNED".equals(candidate.getStatus()))
            .orElseThrow(DeadlineNotices::integrity);
    var details = new LinkedHashMap<String, Object>();
    details.put("kind", kind);
    details.put("basisAt", utc(basisAt));
    details.put("dueAt", utc(dueAt));
    details.put("deliverySequence", deliverySequence);
    details.put("contractVersion", contract.getVersion());
    var history =
        histories.saveAndFlush(
            OrderHistory.deadlineNotified(
                order.getId(), status, basisAt, sourceEventKey, mapper.writeValueAsString(details)));
    events.publish(order, history);
  }

  private static String utc(LocalDateTime value) {
    return value.atOffset(ZoneOffset.UTC).toString();
  }

  private static boolean isFullFunding(Order order) {
    return TradePolicyVersion.CONTRACT_V3.name().equals(order.getPolicyVersion());
  }

  private static BusinessException integrity() {
    return new BusinessException(
        HttpStatus.INTERNAL_SERVER_ERROR, "DATA_INTEGRITY_ERROR", "기한 통지 기록을 확인해야 합니다.");
  }
}
