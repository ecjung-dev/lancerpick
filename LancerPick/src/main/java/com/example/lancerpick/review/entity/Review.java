package com.example.lancerpick.review.entity;
import jakarta.persistence.*;
import java.time.LocalDateTime;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
@lombok.Getter @Entity @Table(name="reviews")
public class Review {
    @Id
    @GeneratedValue(strategy=GenerationType.IDENTITY)
    @Column(name="id") private Long id;
    @Column(name="order_id") private Long orderId;
    @Column(name="rating") private int rating;
    @Column(name="content") private String content;
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name="created_at") private LocalDateTime createdAt;
    protected Review() {}

    public static Review create(long orderId, int rating, String content, LocalDateTime createdAt) {
        Review review = new Review();
        review.orderId = orderId;
        review.rating = rating;
        review.content = content;
        review.createdAt = createdAt;
        return review;
    }

}
