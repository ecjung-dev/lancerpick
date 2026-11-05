package com.example.lancerpick.order.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

/**
 * CONTRACT_V2 주문 생성 시 고정하는 거래 비율·응답 기한. 금액·소요일은 orders에만 둔다.
 * 값은 V6 ck_trade_terms_v2와 같아야 하며 생성 후 변경하지 않는다.
 */
@lombok.Getter
@Entity
@Table(name = "order_trade_terms")
public class OrderTradeTerms implements Persistable<Long> {
    public static final int DEPOSIT_RATE_BPS = 5000;
    public static final int COMPENSATION_RATE_BPS = 3000;
    public static final int NORMAL_FEE_RATE_BPS = 1000;
    public static final int COMPENSATION_FEE_RATE_BPS = 100;
    public static final int REVIEW_WAIT_HOURS = 48;
    public static final int RESPONSE_WINDOW_HOURS = 168;

    // 주문 ID를 공유 PK로 쓰므로 신규 저장을 merge(SELECT 후 INSERT)로 처리하지 않는다.
    @Transient
    private boolean newEntity = true;

    @Id
    @Column(name = "order_id", nullable = false, updatable = false)
    private Long orderId;

    @Column(name = "deposit_rate_bps", nullable = false, updatable = false)
    private int depositRateBps;

    @Column(name = "compensation_rate_bps", nullable = false, updatable = false)
    private int compensationRateBps;

    @Column(name = "normal_fee_rate_bps", nullable = false, updatable = false)
    private int normalFeeRateBps;

    @Column(name = "compensation_fee_rate_bps", nullable = false, updatable = false)
    private int compensationFeeRateBps;

    @Column(name = "review_wait_hours", nullable = false, updatable = false)
    private int reviewWaitHours;

    @Column(name = "response_window_hours", nullable = false, updatable = false)
    private int responseWindowHours;

    protected OrderTradeTerms() {
    }

    public static OrderTradeTerms contractV2(Long orderId) {
        if (orderId == null || orderId <= 0) {
            throw new IllegalArgumentException("Trade terms require a persisted order id");
        }
        OrderTradeTerms terms = new OrderTradeTerms();
        terms.orderId = orderId;
        terms.depositRateBps = DEPOSIT_RATE_BPS;
        terms.compensationRateBps = COMPENSATION_RATE_BPS;
        terms.normalFeeRateBps = NORMAL_FEE_RATE_BPS;
        terms.compensationFeeRateBps = COMPENSATION_FEE_RATE_BPS;
        terms.reviewWaitHours = REVIEW_WAIT_HOURS;
        terms.responseWindowHours = RESPONSE_WINDOW_HOURS;
        return terms;
    }

    @Override
    public Long getId() {
        return orderId;
    }

    @Override
    public boolean isNew() {
        return newEntity;
    }

    @PostLoad
    @PostPersist
    private void markExisting() {
        newEntity = false;
    }
}
