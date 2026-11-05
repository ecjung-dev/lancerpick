package com.example.lancerpick.payment;

import static org.assertj.core.api.Assertions.*;

import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.order.service.NewOrderPolicy;
import com.example.lancerpick.payment.entity.OrderFunds;
import com.example.lancerpick.payment.service.FullFundingPolicy;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class FullFundingPolicyTests {
  @Test
  void newOrderTermsDoNotPromiseLegacyNoAutomaticConfirmationOrSeparateIncome() {
    var terms = new com.example.lancerpick.order.service.OrderTerms();
    assertThat(terms.forPolicy(TradePolicyVersion.CONTRACT_V3))
        .contains("전액", "자동 구매 확정", "구매에 사용할 수 있는 적립금")
        .doesNotContain("판매 수익은 분리");
    assertThat(terms.forPolicy(TradePolicyVersion.LEGACY_V1)).isEqualTo(terms.currentText());
    assertThat(terms.forPolicy(TradePolicyVersion.CONTRACT_V2)).isEqualTo(terms.currentText());
  }

  @Test
  void fullAmountIsConservedAndAutomaticCompletionHasSameNormalFee() {
    var r = FullFundingPolicy.completed(100001);
    assertThat(r.fee()).isEqualTo(10000);
    assertThat(r.sellerPayout()).isEqualTo(90001);
    assertThat(r.buyerRefund()).isZero();
    assertThat(r.fundedAmount()).isEqualTo(r.buyerRefund() + r.fee() + r.sellerPayout());
    assertThat(FullFundingPolicy.cancelled(100001).buyerRefund()).isEqualTo(100001);
    assertThatThrownBy(() -> FullFundingPolicy.completed(9999))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void resolvedFundsCannotBePaidAndRefundedTwice() {
    var at = LocalDateTime.of(2026, 11, 5, 0, 0);
    var f = OrderFunds.hold(1, 2, 10000, at);
    assertThatThrownBy(() -> f.release(at.minusSeconds(1)))
        .isInstanceOf(IllegalStateException.class);
    assertThat(f.getRemainingAmount()).isEqualTo(10000);
    f.release(at);
    assertThat(f.getRemainingAmount()).isZero();
    assertThatThrownBy(() -> f.refund(at)).isInstanceOf(IllegalStateException.class);
  }

  @Test
  void policyFlagsStayOffByDefaultAndOldV2IsExplicitlySeparate() {
    assertThat(new NewOrderPolicy(false, false).current()).isEqualTo(TradePolicyVersion.LEGACY_V1);
    assertThat(new NewOrderPolicy(true, false).current()).isEqualTo(TradePolicyVersion.CONTRACT_V2);
    assertThat(new NewOrderPolicy(false, true).current()).isEqualTo(TradePolicyVersion.CONTRACT_V3);
  }
}
