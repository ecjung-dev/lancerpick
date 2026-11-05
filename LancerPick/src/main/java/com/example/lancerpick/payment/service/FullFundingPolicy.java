package com.example.lancerpick.payment.service;

/** CONTRACT_V3: full order funding; this calculation does not move wallet balances. */
public final class FullFundingPolicy {
  private FullFundingPolicy() {}

  public static Distribution completed(long amount) {
    requireAmount(amount);
    long fee = amount * 1000 / 10000;
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
