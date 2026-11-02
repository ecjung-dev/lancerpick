package com.example.lancerpick.wallet.dto;

/** GET /me/wallet에 연결할 응답 계약. 현재 MVP 잔액은 실제 현금이 아니다. */
public record WalletBalanceResponse(long balance, String currency, boolean isTest) {
    public static WalletBalanceResponse of(long balance) {
        return new WalletBalanceResponse(balance, "KRW", true);
    }
}
