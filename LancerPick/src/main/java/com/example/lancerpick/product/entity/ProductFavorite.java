package com.example.lancerpick.product.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "product_favorites")
@IdClass(ProductFavorite.Key.class)
public class ProductFavorite {
    public record Key(long memberId, long productId) implements Serializable {}

    @Id
    @Column(name = "member_id")
    private long memberId;

    @Id
    @Column(name = "product_id")
    private long productId;

    @Column(name = "created_at", nullable = false)
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    private LocalDateTime createdAt;

    protected ProductFavorite() {}

    public ProductFavorite(Key key, LocalDateTime createdAt) {
        this.memberId = key.memberId();
        this.productId = key.productId();
        this.createdAt = createdAt;
    }
}
