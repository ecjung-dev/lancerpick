package com.example.lancerpick.order.service;

import com.example.lancerpick.common.policy.TradePolicyVersion;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 새 주문에 고정할 정책 버전을 정한다. V2 전용 명령·화면·실제 DB 경합 검증(G1) 전에는 설정을 켜지 않으므로 기본값은 LEGACY_V1이다. 기존 주문의 정책은
 * 바꾸지 않는다.
 */
@Component
public class NewOrderPolicy {
  private final boolean contractV2CreationEnabled;
  private final boolean fullFundingEnabled;

  @org.springframework.beans.factory.annotation.Autowired
  public NewOrderPolicy(
      @Value("${lancerpick.order.contract-v2-creation-enabled:false}")
          boolean contractV2CreationEnabled,
      @Value("${lancerpick.order.full-funding-creation-enabled:false}")
          boolean fullFundingEnabled) {
    this.contractV2CreationEnabled = contractV2CreationEnabled;
    this.fullFundingEnabled = fullFundingEnabled;
  }

  public NewOrderPolicy(boolean legacyV2Flag) {
    this(legacyV2Flag, false);
  }

  public TradePolicyVersion current() {
    if (fullFundingEnabled) return TradePolicyVersion.CONTRACT_V3;
    return contractV2CreationEnabled
        ? TradePolicyVersion.CONTRACT_V2
        : TradePolicyVersion.LEGACY_V1;
  }
}
