package com.example.lancerpick.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 적립금 원장. 수정·삭제 메서드와 공개 쓰기 API를 제공하지 않는다. */
@lombok.Getter
@Entity
@Table(name = "wallet_entries")
public class WalletEntry {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    // Payment Entity가 생기기 전에도 V1의 FK 컬럼을 정확히 매핑한다.
    @Column(name = "payment_id")
    private Long paymentId;

    @Column(name = "topup_id")
    private Long topupId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 16)
    private WalletEntryType type;

    @Column(name = "amount_delta", nullable = false)
    private long amountDelta;

    @Column(name = "balance_after", nullable = false)
    private long balanceAfter;

    @Column(name = "event_key", nullable = false, length = 100)
    private String eventKey;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private LocalDateTime createdAt;

    protected WalletEntry() {}

    public static String initialGrantKey(long memberId) {
        return "INITIAL_GRANT:" + memberId;
    }

    public static WalletEntry initialGrant(long memberId, LocalDateTime grantedAt) {
        WalletEntry entry = new WalletEntry();
        entry.memberId = memberId;
        entry.type = WalletEntryType.INITIAL_GRANT;
        entry.amountDelta = Wallet.INITIAL_GRANT_AMOUNT;
        entry.balanceAfter = Wallet.INITIAL_GRANT_AMOUNT;
        entry.eventKey = initialGrantKey(memberId);
        entry.createdAt = grantedAt;
        return entry;
    }

    public boolean isInitialGrantFor(long memberId) {
        return this.memberId == memberId && paymentId == null && topupId == null
                && type == WalletEntryType.INITIAL_GRANT
                && amountDelta == Wallet.INITIAL_GRANT_AMOUNT
                && balanceAfter == Wallet.INITIAL_GRANT_AMOUNT
                && initialGrantKey(memberId).equals(eventKey);
    }

    public Long getId() { return id; }
    public static WalletEntry payment(long memberId, long paymentId, long amount, long balanceAfter, LocalDateTime at) {
        if (memberId <= 0 || paymentId <= 0 || amount < 1 || amount > 1_000_000 || balanceAfter < 0 || at == null) throw new IllegalArgumentException("Invalid payment entry");
        var entry = new WalletEntry();
        entry.memberId = memberId;
        entry.paymentId = paymentId;
        entry.type = WalletEntryType.PAYMENT;
        entry.amountDelta = -amount;
        entry.balanceAfter = balanceAfter;
        entry.eventKey = "PAYMENT:" + paymentId;
        entry.createdAt = at;
        return entry;
    }
    public Long getMemberId() { return memberId; }
    public static WalletEntry refund(long memberId, long paymentId, long amount, long balanceAfter, LocalDateTime at) {
        var entry = payment(memberId, paymentId, amount, balanceAfter, at);
        entry.type = WalletEntryType.REFUND;
        entry.amountDelta = amount;
        entry.eventKey = "REFUND:" + paymentId;
        return entry;
    }
    public Long getPaymentId() { return paymentId; }
    public Long getTopupId() { return topupId; }
    public WalletEntryType getType() { return type; }
    public long getAmountDelta() { return amountDelta; }
    public long getBalanceAfter() { return balanceAfter; }
    public String getEventKey() { return eventKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public static WalletEntry topup(long member,long topup,long amount,long balance,String key,LocalDateTime at){if(member<=0 || topup<=0 || amount<1 || amount>1000000 || balance<0 || at==null || !key.equals("TOPUP:"+topup))throw new IllegalArgumentException("Invalid topup entry");var e=new WalletEntry();e.memberId=member;e.topupId=topup;e.type=WalletEntryType.TOPUP;e.amountDelta=amount;e.balanceAfter=balance;e.eventKey=key;e.createdAt=at;return e;}
}
