package com.example.lancerpick.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 구매용 테스트 잔액. 판매 수익은 이 지갑에 합산하지 않는다. */
@lombok.Getter
@Entity
@Table(name = "wallets")
public class Wallet {
    public static final long INITIAL_GRANT_AMOUNT = 1_000_000L;

    @Id
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(nullable = false)
    private long balance;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
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
    /** 구매자 지갑의 배타 잠금을 획득한 트랜잭션에서 호출한다. */
    public void debit(long amount, LocalDateTime at) {
        if (amount < 1 || amount > 1_000_000 || at == null || at.isBefore(updatedAt)) throw new IllegalArgumentException("Invalid wallet debit");
        if (balance < amount) throw new com.example.lancerpick.common.exception.BusinessException(
                org.springframework.http.HttpStatus.CONFLICT, "INSUFFICIENT_BALANCE", "적립금 잔액이 부족합니다.");
        balance -= amount;
        updatedAt = at;
    }
    public long getBalance() { return balance; }
    public void refund(long amount, LocalDateTime at) {
        if (amount < 1 || amount > 1_000_000 || at == null || at.isBefore(updatedAt)) throw new IllegalArgumentException("Invalid wallet refund");
        balance = Math.addExact(balance, amount);
        updatedAt = at;
    }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void applyTopupBalance(long balance,LocalDateTime at){if(balance<this.balance || at==null || at.isBefore(updatedAt))throw new IllegalArgumentException("Invalid topup balance");this.balance=balance;updatedAt=at;}
}
