package com.example.lancerpick.payment.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@lombok.Getter
@Entity
@Table(name="payments")
public class Payment {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="order_id", nullable=false)
    private long orderId;
    @Column(name="amount", nullable=false)
    private long amount;
    @Column(name="fee_rate_bps", nullable=false)
    private int feeRateBps;
    @Column(name="status", nullable=false, length=10)
    private String status;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name="paid_at", nullable=false)
    private LocalDateTime paidAt;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name="refunded_at", nullable=true)
    private LocalDateTime refundedAt;
    protected Payment() {}
    public static Payment create(long orderId,long amount,LocalDateTime at) {
        if(orderId<=0 || amount<1 || amount>1_000_000 || at==null)throw new IllegalArgumentException("Invalid payment");
        var p=new Payment();p.orderId=orderId;p.amount=amount;p.feeRateBps=1000;p.status="PAID";p.paidAt=at;return p;
    }
    public void refund(LocalDateTime at){if(!"PAID".equals(status) || at==null || at.isBefore(paidAt))throw new IllegalStateException("Invalid refund");status="REFUNDED";refundedAt=at;}
    public long id(){return id;} public long amount(){return amount;} public String status(){return status;} public int rate(){return feeRateBps;}
    public long getOrderId(){return orderId;}

}
