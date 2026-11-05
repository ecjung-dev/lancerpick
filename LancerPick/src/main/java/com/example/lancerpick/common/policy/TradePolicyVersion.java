package com.example.lancerpick.common.policy;

import com.example.lancerpick.common.exception.BusinessException;
import org.springframework.http.HttpStatus;

/** Persisted identifiers are shared by orders, payments and API responses. */
public enum TradePolicyVersion {
  LEGACY_V1,
  CONTRACT_V2,
  CONTRACT_V3;

  public static boolean isContract(String policy) {
    return CONTRACT_V2.name().equals(policy) || CONTRACT_V3.name().equals(policy);
  }

  public static boolean isFullFunding(String policy) {
    return CONTRACT_V3.name().equals(policy);
  }

  /**
   * 금액이 움직이지 않는 결제 후 작업 명령(납품·수정 요청·재납품). LEGACY_V1과 CONTRACT_V3만 허용한다. CONTRACT_V2(분할
   * 결제)는 신규 범위에서 제외돼 계속 막는다.
   */
  public static void requireFulfillmentCommand(String policy) {
    if (!LEGACY_V1.name().equals(policy) && !isFullFunding(policy)) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "ORDER_POLICY_NOT_READY", "계약 주문의 전용 처리 경로가 아직 준비되지 않았습니다.");
    }
  }

  public static void requireLegacyCommand(String policy) {
    if (!LEGACY_V1.name().equals(policy)) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "ORDER_POLICY_NOT_READY", "계약 주문의 전용 처리 경로가 아직 준비되지 않았습니다.");
    }
  }
}
