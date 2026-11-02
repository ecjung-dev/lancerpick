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
        return this.memberId == memberId && paymentId == null
                && type == WalletEntryType.INITIAL_GRANT
                && amountDelta == Wallet.INITIAL_GRANT_AMOUNT
                && balanceAfter == Wallet.INITIAL_GRANT_AMOUNT
                && initialGrantKey(memberId).equals(eventKey);
    }

    public Long getId() { return id; }
    public Long getMemberId() { return memberId; }
    public Long getPaymentId() { return paymentId; }
    public WalletEntryType getType() { return type; }
    public long getAmountDelta() { return amountDelta; }
    public long getBalanceAfter() { return balanceAfter; }
    public String getEventKey() { return eventKey; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
