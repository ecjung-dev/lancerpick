package com.example.lancerpick.settlement.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.Getter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Getter
@Entity
@Table(name = "settlements")
public class Settlement {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "order_id", nullable = false)
    private long orderId;
    @Column(name = "gross_amount", nullable = false)
    private long grossAmount;
    @Column(name = "fee_rate_bps", nullable = false)
    private int feeRateBps;
    @Column(name = "fee_amount", nullable = false)
    private long feeAmount;
    @Column(name = "seller_amount", nullable = false)
    private long sellerAmount;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    protected Settlement() {}

    public static Settlement create(long orderId, long amount, int rate, LocalDateTime at) {
        if (orderId <= 0 || amount < 1 || amount > 1_000_000 || rate < 0 || rate > 10000 || at == null) {
            throw new IllegalArgumentException("Invalid settlement");
        }
        var settlement = new Settlement();
        settlement.orderId = orderId;
        settlement.grossAmount = amount;
        settlement.feeRateBps = rate;
        settlement.feeAmount = Math.multiplyExact(amount, rate) / 10000;
        settlement.sellerAmount = amount - settlement.feeAmount;
        settlement.createdAt = at;
        return settlement;
    }

    public long[] amounts() {
        return new long[] {grossAmount, feeRateBps, feeAmount, sellerAmount};
    }

    public Long getId() {
        return id;
    }

    public long getOrderId() {
        return orderId;
    }

    public long getGrossAmount() {
        return grossAmount;
    }

    public int getFeeRateBps() {
        return feeRateBps;
    }

    public long getFeeAmount() {
        return feeAmount;
    }

    public long getSellerAmount() {
        return sellerAmount;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
