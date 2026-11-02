package com.example.lancerpick.wallet.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** 충전 신청 저장 구조. 신청 생성은 적립금 지급이나 외부 승인을 수행하지 않는다. */
@Entity
@Table(name = "wallet_topups")
public class WalletTopup {
    public static final long MAX_AMOUNT = 1_000_000L;
    private static final Pattern ORDER_ID = Pattern.compile("[A-Za-z0-9_=-]{6,64}");

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "member_id", nullable = false)
    private Long memberId;
    @Column(name = "toss_order_id", nullable = false, length = 64)
    private String tossOrderId;
    @Column(nullable = false)
    private long amount;
    @Column(nullable = false, columnDefinition = "CHAR(3)")
    private String currency;
    @Column(name = "is_test", nullable = false)
    private boolean test;
    @Column(name = "payment_key", length = 200)
    private String paymentKey;
    @Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 20)
    private WalletTopupStatus status;
    @Column(name = "approval_request_key", nullable = false, columnDefinition = "CHAR(36)")
    private String approvalRequestKey;
    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;
    @Column(name = "created_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false, columnDefinition = "DATETIME(6)")
    private Instant updatedAt;
    @Column(name = "processing_started_at", columnDefinition = "DATETIME(6)")
    private Instant processingStartedAt;
    @Column(name = "approved_at", columnDefinition = "DATETIME(6)")
    private Instant approvedAt;
    @Column(name = "completed_at", columnDefinition = "DATETIME(6)")
    private Instant completedAt;

    protected WalletTopup() {}

    /** 서비스에서 검증한 금액·서버 생성 식별자·UTC 시각만 전달한다. */
    public static WalletTopup request(long memberId, String tossOrderId, long amount,
            UUID approvalRequestKey, Instant createdAt) {
        if (memberId <= 0 || amount < 1 || amount > MAX_AMOUNT
                || tossOrderId == null || !ORDER_ID.matcher(tossOrderId).matches()) {
            throw new IllegalArgumentException("Invalid top-up request");
        }
        Objects.requireNonNull(approvalRequestKey, "approvalRequestKey");
        Objects.requireNonNull(createdAt, "createdAt");
        int year = createdAt.atOffset(ZoneOffset.UTC).getYear();
        if (year < 1000 || year > 9999) {
            throw new IllegalArgumentException("Top-up time is outside MySQL DATETIME range");
        }
        var topup = new WalletTopup();
        topup.memberId = memberId;
        topup.tossOrderId = tossOrderId;
        topup.amount = amount;
        topup.currency = "KRW";
        topup.test = true;
        topup.status = WalletTopupStatus.READY;
        topup.approvalRequestKey = approvalRequestKey.toString();
        topup.createdAt = createdAt.truncatedTo(ChronoUnit.MICROS);
        topup.updatedAt = topup.createdAt;
        return topup;
    }

    public Long getId() { return id; }

    public void startProcessing(String paymentKey, Instant now) {
        if (status != WalletTopupStatus.READY) throw new IllegalStateException("Top-up is not ready");
        this.paymentKey = Objects.requireNonNull(paymentKey);
        this.status = WalletTopupStatus.PROCESSING;
        this.updatedAt = monotonic(now);
        this.processingStartedAt = updatedAt;
    }

    public void markUnknown(String safeCode, Instant now) {
        if (status == WalletTopupStatus.PROCESSING || status == WalletTopupStatus.UNKNOWN) {
            status = WalletTopupStatus.UNKNOWN;
            lastErrorCode = safeCode;
            updatedAt = monotonic(now);
        }
    }

    public void markFailed(Instant now) {
        if (status == WalletTopupStatus.PROCESSING || status == WalletTopupStatus.UNKNOWN) {
            status = WalletTopupStatus.FAILED;
            lastErrorCode = "PROVIDER_TERMINAL_STATE";
            updatedAt = monotonic(now);
        }
    }

    public void complete(Instant approvedAt, Instant now) {
        if (status != WalletTopupStatus.PROCESSING && status != WalletTopupStatus.UNKNOWN) {
            throw new IllegalStateException("Top-up is not processing");
        }
        this.approvedAt = approvedAt.truncatedTo(ChronoUnit.MICROS);
        this.completedAt = monotonic(now.isBefore(this.approvedAt) ? this.approvedAt : now);
        this.updatedAt = completedAt;
        this.status = WalletTopupStatus.COMPLETED;
        this.lastErrorCode = null;
    }

    private Instant monotonic(Instant now) {
        Instant value = now.truncatedTo(ChronoUnit.MICROS);
        return value.isBefore(updatedAt) ? updatedAt : value;
    }
    public Long getMemberId() { return memberId; }
    public String getTossOrderId() { return tossOrderId; }
    public long getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public boolean isTest() { return test; }
    public String getPaymentKey() { return paymentKey; }
    public WalletTopupStatus getStatus() { return status; }
    public String getApprovalRequestKey() { return approvalRequestKey; }
    public String getLastErrorCode() { return lastErrorCode; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public Instant getProcessingStartedAt() { return processingStartedAt; }
    public Instant getApprovedAt() { return approvedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
