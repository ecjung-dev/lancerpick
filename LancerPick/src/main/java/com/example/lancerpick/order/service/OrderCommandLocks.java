package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.order.repository.OrderRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 명령의 공통 잠금 순서: 실행 회원 공유 잠금 → 당사자 주문 배타 잠금. 정책 검증은 하지 않으며 호출자가 명시적으로 수행한다.
 * 잠금이 호출 서비스의 트랜잭션에 묶이도록 기존 트랜잭션 안에서만 실행한다.
 */
@Component
@Profile("!demo")
@Transactional(propagation = Propagation.MANDATORY)
public class OrderCommandLocks {
  private final OrderRepository orders;

  public OrderCommandLocks(OrderRepository orders) {
    this.orders = orders;
  }

  /** 실행 회원을 공유 잠금한다. 회원이 없으면 401. */
  public void lockActor(long memberId) {
    if (orders.lockDecisionActor(memberId).isEmpty()) {
      throw new BusinessException(
          HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
    }
  }

  /** 실행 회원 잠금 후 당사자 주문을 배타 잠금한다. 당사자가 아니거나 주문이 없으면 404. 정책은 검사하지 않는다. */
  public Order lockParticipantOrder(long memberId, long orderId) {
    lockActor(memberId);
    return orders.findForDecision(orderId, memberId).orElseThrow(OrderCommandLocks::orderNotFound);
  }

  /** {@link #lockParticipantOrder}와 같고, 납품·수정 요청 정책(LEGACY_V1·CONTRACT_V3)을 저장소에서 함께 검증한다. */
  public Order lockFulfillmentOrder(long memberId, long orderId) {
    lockActor(memberId);
    return orders
        .findForFulfillment(orderId, memberId)
        .orElseThrow(OrderCommandLocks::orderNotFound);
  }

  private static BusinessException orderNotFound() {
    return new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "주문을 찾을 수 없습니다.");
  }
}
