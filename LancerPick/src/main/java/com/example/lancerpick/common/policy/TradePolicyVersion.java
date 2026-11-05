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

  public static void requireLegacyCommand(String policy) {
    if (!LEGACY_V1.name().equals(policy)) {
      throw new BusinessException(
          HttpStatus.CONFLICT, "ORDER_POLICY_NOT_READY", "계약 주문의 전용 처리 경로가 아직 준비되지 않았습니다.");
    }
  }
}
