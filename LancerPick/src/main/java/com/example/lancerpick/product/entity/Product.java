package com.example.lancerpick.product.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@lombok.Getter @Entity @Table(name="products")
public class Product {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id") private Long id;
    @Column(name="seller_id") private Long sellerId;
    @Column(name="category_id") private Long categoryId;
    @Column(name="title") private String title;
    @Column(name="description") private String description;
    @Column(name="scope_description") private String scopeDescription;
    @Column(name="preparation_description") private String preparationDescription;
    @Column(name="price") private long price;
    @Column(name="duration_days") private int durationDays;
    @Column(name="sale_status") private String saleStatus;
    @Column(name="is_hidden") private boolean hidden;
    @Column(name="version") private long version;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name="created_at") private LocalDateTime createdAt;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name="updated_at") private LocalDateTime updatedAt;
    protected Product() {}

    public boolean moderate(boolean hidden,long expected,LocalDateTime at){if(version!=expected)return false;if(at==null || at.isBefore(updatedAt))throw new IllegalStateException("Invalid product clock");this.hidden=hidden;version=Math.incrementExact(version);updatedAt=at;return true;}
}
