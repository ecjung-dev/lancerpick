package com.example.lancerpick.admin.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@lombok.Getter @Entity @Table(name="product_moderation_logs")
public class ProductModerationLog {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id") private Long id;
    @Column(name="product_id") private Long productId;
    @Column(name="admin_id") private Long adminId;
    @Column(name="action") private String action;
    @Column(name="reason") private String reason;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name="created_at") private LocalDateTime createdAt;
    protected ProductModerationLog() {}
    public static ProductModerationLog create(long product,long admin,String action,String reason,LocalDateTime at){var log=new ProductModerationLog();log.productId=product;log.adminId=admin;log.action=action;log.reason=reason;log.createdAt=at;return log;}

}
