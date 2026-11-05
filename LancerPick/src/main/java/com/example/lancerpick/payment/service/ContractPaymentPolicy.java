package com.example.lancerpick.payment.service;

/** Immutable CONTRACT_V2 amounts. This does not authorize or execute a payment. */
public final class ContractPaymentPolicy {
  public static final int NORMAL_FEE_BPS = 1000;
  public static final int COMPENSATION_FEE_BPS = 100;
  public static final long MIN_TOTAL = 10_000;
  public static final long MAX_TOTAL = 1_000_000;

  private ContractPaymentPolicy() {}

  public static Installments installments(long total) {
    if (total < MIN_TOTAL || total > MAX_TOTAL) {
      throw new IllegalArgumentException("Contract total must be between 10000 and 1000000");
    }
    long deposit = total / 2;
    return new Installments(total, deposit, total - deposit);
  }

  public static Distribution completed(long total) {
    installments(total);
    return distribution(total, 0, total, NORMAL_FEE_BPS);
  }

  public static Distribution noResponse(long total) {
    long deposit = installments(total).deposit();
    long compensation = Math.multiplyExact(deposit, 3000) / 10_000;
    return distribution(deposit, deposit - compensation, compensation, COMPENSATION_FEE_BPS);
  }

  public static Distribution agreedCancellation(long total) {
    long deposit = installments(total).deposit();
    return distribution(deposit, deposit, 0, 0);
  }

  private static Distribution distribution(long paid, long refund, long gross, int rate) {
    long fee = Math.multiplyExact(gross, rate) / 10_000;
    return new Distribution(paid, refund, gross, rate, fee, gross - fee);
  }

  public record Installments(long total, long deposit, long balance) {}

  public record Distribution(
      long paid, long buyerRefund, long sellerGross, int feeRateBps, long fee, long sellerNet) {}
}
