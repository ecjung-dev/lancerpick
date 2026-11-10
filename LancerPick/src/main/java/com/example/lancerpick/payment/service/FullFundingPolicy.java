package com.example.lancerpick.payment.service;

/** CONTRACT_V3: full order funding; this calculation does not move wallet balances. */
public final class FullFundingPolicy {
  /** V3 정상 완료 수수료율(basis point). 결제·정산 기록과 V7 DB CHECK의 10%와 같아야 한다. */
  public static final int FEE_RATE_BPS = 1_000;

  private FullFundingPolicy() {}

  public static Distribution completed(long amount) {
    requireAmount(amount);
    long fee = amount * FEE_RATE_BPS / 10_000;
    return new Distribution(amount, 0, fee, amount - fee);
  }

  public static Distribution cancelled(long amount) {
    requireAmount(amount);
    return new Distribution(amount, amount, 0, 0);
  }

  public static void requireAmount(long amount) {
    if (amount < 10000 || amount > 1000000)
      throw new IllegalArgumentException("Full funding amount out of range");
  }

  public record Distribution(long fundedAmount, long buyerRefund, long fee, long sellerPayout) {}
}
