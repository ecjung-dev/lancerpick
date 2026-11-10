package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.order.dto.OrderDetailResponse;
import com.example.lancerpick.order.entity.OrderHistory;
import com.example.lancerpick.notification.service.OrderEventPublisher;
import com.example.lancerpick.order.repository.OrderDetailQueries;
import com.example.lancerpick.order.repository.OrderHistoryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!demo")
public class OrderDecisionService {
  private final OrderCommandLocks locks;
  private final OrderHistoryRepository histories;
  private final OrderEventPublisher events;
  private final OrderDetailQueries details;
  private final Clock clock;
  private final NewTradeRestrictions restrictions;

  public OrderDecisionService(
      OrderCommandLocks locks,
      OrderHistoryRepository histories,
      OrderEventPublisher events,
      OrderDetailQueries details,
      Clock clock,
      NewTradeRestrictions restrictions) {
    this.locks = locks;
    this.histories = histories;
    this.events = events;
    this.details = details;
    this.clock = clock;
    this.restrictions = restrictions;
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public OrderDetailResponse accept(long memberId, String id) {
    return decide(memberId, id, null, true);
  }

  @Transactional(isolation = Isolation.READ_COMMITTED)
  public OrderDetailResponse reject(long memberId, String id, String reason) {
    return decide(memberId, id, reason, false);
  }

  private OrderDetailResponse decide(long memberId, String id, String reason, boolean accept) {
    long orderId = OrderIds.positive(id).orElseThrow(OrderDecisionService::invalidId);
    var order = locks.lockParticipantOrder(memberId, orderId);
    // 수락·거절은 결제 전 단계라 계약 주문도 같은 명령을 쓴다. 알 수 없는 정책만 막는다.
    if (!order.isContractPolicy())
      com.example.lancerpick.common.policy.TradePolicyVersion.requireLegacyCommand(
          order.getPolicyVersion());
    if (!order.getSellerId().equals(memberId)) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "판매자만 수락·거절할 수 있습니다.");
    }
    // U7: 수락만 새 거래 시작이다. 거절은 제한 중에도 허용한다. 판매자 회원 잠금(lockParticipantOrder) 뒤에 검사한다.
    if (accept) restrictions.requireSellerCanAccept(memberId);
    LocalDateTime at = UtcTimes.nowMicros(clock);
    OrderHistory history;
    if (accept) {
      order.accept(at);
      history = OrderHistory.accepted(orderId, memberId, at, order.getStatus());
    } else {
      order.reject(at);
      history = OrderHistory.rejected(orderId, memberId, at, reason);
    }
    // 변경 감지와 이력 저장을 응답 조회 전에 flush한다. 이후 실패도 같은 트랜잭션에서 롤백한다.
    events.publish(order, histories.saveAndFlush(history));
    var detail = details.forParticipant(orderId, memberId);
    return OrderAllowedActions.attach(detail, false);
  }

  private static BusinessException invalidId() {
    return new BusinessException(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "주문 ID는 양의 정수 범위여야 합니다.");
  }
}
