package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.order.dto.OrderDetailResponse;
import com.example.lancerpick.order.entity.OrderHistory;
import com.example.lancerpick.order.repository.OrderDetailQueries;
import com.example.lancerpick.order.repository.OrderHistoryRepository;
import com.example.lancerpick.order.repository.OrderRepository;
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
public class OrderDecisionService {
  private final OrderRepository orders;
  private final OrderHistoryRepository histories;
  private final OrderDetailQueries details;
  private final Clock clock;

  public OrderDecisionService(
      OrderRepository orders,
      OrderHistoryRepository histories,
      OrderDetailQueries details,
      Clock clock) {
    this.orders = orders;
    this.histories = histories;
    this.details = details;
    this.clock = clock;
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
    long orderId;
    try {
      if (id == null || !id.matches("[0-9]+")) throw invalidId();
      orderId = Long.parseLong(id);
      if (orderId <= 0) throw invalidId();
    } catch (NumberFormatException e) {
      throw invalidId();
    }
    if (orders.lockDecisionActor(memberId).isEmpty()) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
    }
    var order =
        orders
            .findForDecision(orderId, memberId)
            .orElseThrow(() ->
                new BusinessException(
                    HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "주문을 찾을 수 없습니다."));
    if (!order.getSellerId().equals(memberId)) {
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "판매자만 수락·거절할 수 있습니다.");
    }
    LocalDateTime at =
        LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    OrderHistory history;
    if (accept) {
      order.accept(at);
      history = OrderHistory.accepted(orderId, memberId, at);
    } else {
      order.reject(at);
      history = OrderHistory.rejected(orderId, memberId, at, reason);
    }
    // 변경 감지와 이력 저장을 응답 조회 전에 flush한다. 이후 실패도 같은 트랜잭션에서 롤백한다.
    histories.saveAndFlush(history);
    var detail = details.forParticipant(orderId, memberId);
    return detail.withAllowedActions(
        OrderAllowedActions.actions(
            detail.status(),
            false,
            detail.revisionRequest(),
            detail.cancellationRequests(),
            detail.review()));
  }

  private static BusinessException invalidId() {
    return new BusinessException(
        HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "주문 ID는 양의 정수 범위여야 합니다.");
  }
}
