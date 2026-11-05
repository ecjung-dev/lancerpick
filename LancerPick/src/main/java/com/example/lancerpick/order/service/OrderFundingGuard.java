package com.example.lancerpick.order.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.order.entity.Order;
import com.example.lancerpick.payment.service.OrderFundsReader;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * V3 결제 후 작업 명령의 보관금 확인. V3 주문이 작업 상태인데 전액 보관(HELD)이 없으면 결제 단계가 어긋난 데이터라
 * 무결성 오류로 막는다. LEGACY_V1은 기존 결제 행 기준이라 확인하지 않는다. 주문 잠금 아래에서 호출한다.
 */
@Component
@Profile("!demo")
class OrderFundingGuard {
  private final OrderFundsReader funds;

  OrderFundingGuard(OrderFundsReader funds) {
    this.funds = funds;
  }

  void requireHeldForWork(Order order) {
    if (!TradePolicyVersion.isFullFunding(order.getPolicyVersion())) return;
    if (!funds.isFullyHeld(order.getId(), order.getPriceAmount()))
      throw new BusinessException(
          HttpStatus.INTERNAL_SERVER_ERROR, "DATA_INTEGRITY_ERROR", "주문 대금 보관 기록을 확인해야 합니다.");
  }
}
