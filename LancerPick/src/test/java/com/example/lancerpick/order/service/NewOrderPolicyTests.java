package com.example.lancerpick.order.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.order.entity.OrderTradeTerms;
import org.junit.jupiter.api.Test;

class NewOrderPolicyTests {
  @Test
  void contractCreationIsClosedUnlessExplicitlyEnabled() {
    assertThat(new NewOrderPolicy(false).current()).isEqualTo(TradePolicyVersion.LEGACY_V1);
    assertThat(new NewOrderPolicy(true).current()).isEqualTo(TradePolicyVersion.CONTRACT_V2);
  }

  @Test
  void contractTradeTermsMatchFixedV2Values() {
    var terms = OrderTradeTerms.contractV2(7L);
    assertThat(terms.getOrderId()).isEqualTo(7L);
    assertThat(terms.getDepositRateBps()).isEqualTo(5000);
    assertThat(terms.getCompensationRateBps()).isEqualTo(3000);
    assertThat(terms.getNormalFeeRateBps()).isEqualTo(1000);
    assertThat(terms.getCompensationFeeRateBps()).isEqualTo(100);
    assertThat(terms.getReviewWaitHours()).isEqualTo(48);
    assertThat(terms.getResponseWindowHours()).isEqualTo(168);
    assertThat(terms.isNew()).isTrue();
  }
}
