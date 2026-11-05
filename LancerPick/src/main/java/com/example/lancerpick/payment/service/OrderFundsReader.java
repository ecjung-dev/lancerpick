package com.example.lancerpick.payment.service;

import com.example.lancerpick.payment.repository.OrderFundsRepository;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * 주문 명령이 V3 보관금 상태를 확인할 때 쓰는 읽기 전용 경계. 보관금 변경(보관·해제·반환)은 V3 금액 처리(J4)가 맡는다.
 * 호출자는 주문 잠금을 먼저 잡는다(OrderFundsRepository의 잠금 규칙).
 */
@Service
@Profile("!demo")
public class OrderFundsReader {
  private final OrderFundsRepository funds;

  public OrderFundsReader(OrderFundsRepository funds) {
    this.funds = funds;
  }

  /** 주문 총액 전부가 아직 보관(HELD) 중인지. */
  public boolean isFullyHeld(long orderId, long orderAmount) {
    return funds
        .findById(orderId)
        .filter(
            f ->
                "HELD".equals(f.getStatus())
                    && f.getAmount() == orderAmount
                    && f.getRemainingAmount() == orderAmount)
        .isPresent();
  }
}
