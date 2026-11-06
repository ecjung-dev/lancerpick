package com.example.lancerpick.order.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@lombok.Getter
@Entity
@Table(name="cancellation_requests")
public class CancellationRequest {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY)
    private Long id;
    @Column(name="order_id", nullable=false)
    private long orderId;
    @Column(name="requested_by", nullable=false)
    private long requestedBy;
    @Column(name="request_key", nullable=false, length=36, columnDefinition="char(36)")
    private String requestKey;
    @Column(name="reason", nullable=false, length=1000)
    private String reason;
    @Column(name="status", nullable=false, length=16)
    private String status;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name="requested_at", nullable=false)
    private LocalDateTime requestedAt;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name="resolved_at", nullable=true)
    private LocalDateTime resolvedAt;
    @Column(name="resolved_by", nullable=true)
    private Long resolvedBy;
    @Column(name="response_reason", nullable=true, length=1000)
    private String responseReason;
    @Column(name="resolution_source", length=10)
    private String resolutionSource;
    protected CancellationRequest() {}
    public void approve(long actor,String response,LocalDateTime at){if(!"PENDING".equals(status) || actor<=0 || at==null || at.isBefore(requestedAt))throw new IllegalStateException("Invalid cancellation approval");status="APPROVED";resolvedBy=actor;responseReason=response;resolvedAt=at;resolutionSource="SELLER";}

    public static CancellationRequest create(long order,long buyer,String key,String reason,LocalDateTime at){var r=new CancellationRequest();r.orderId=order;r.requestedBy=buyer;r.requestKey=key;r.reason=reason;r.status="PENDING";r.requestedAt=at;return r;}
    public void resolve(String status,long actor,String reason,LocalDateTime at){if(!"PENDING".equals(this.status) || !java.util.Set.of("APPROVED","REJECTED","WITHDRAWN").contains(status) || actor<=0 || at==null || at.isBefore(requestedAt))throw new IllegalStateException("Invalid resolution");this.status=status;this.resolvedBy=actor;this.responseReason=reason;this.resolvedAt=at;this.resolutionSource="WITHDRAWN".equals(status)?"BUYER":"SELLER";}
    /** 운영 문의를 검토한 관리자의 승인/거절(K4). 관리자 사유를 응답 사유로 남긴다. */
    public void resolveByAdmin(String status,long admin,String reason,LocalDateTime at){if(!"PENDING".equals(this.status) || !java.util.Set.of("APPROVED","REJECTED").contains(status) || admin<=0 || reason==null || at==null || at.isBefore(requestedAt))throw new IllegalStateException("Invalid admin resolution");this.status=status;this.resolvedBy=admin;this.responseReason=reason;this.resolvedAt=at;this.resolutionSource="ADMIN";}
    public void clearApprovalResponseReason(){if("APPROVED".equals(status))responseReason=null;}
}
