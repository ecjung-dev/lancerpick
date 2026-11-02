package com.example.lancerpick.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;

/** 구매용 테스트 잔액. 판매 수익은 이 지갑에 합산하지 않는다. */
@Entity
@Table(name = "wallets")
public class Wallet {
    public static final long INITIAL_GRANT_AMOUNT = 1_000_000L;

    @Id
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private long balance;

    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime updatedAt;

    protected Wallet() {}

    /** ID는 생성하지 않고 회원의 PK를 그대로 사용한다. */
    public static Wallet withInitialGrant(long memberId, LocalDateTime grantedAt) {
        Wallet wallet = new Wallet();
        wallet.memberId = memberId;
        wallet.balance = INITIAL_GRANT_AMOUNT;
        wallet.updatedAt = grantedAt;
        return wallet;
    }

    public Long getMemberId() { return memberId; }
    public long getBalance() { return balance; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
