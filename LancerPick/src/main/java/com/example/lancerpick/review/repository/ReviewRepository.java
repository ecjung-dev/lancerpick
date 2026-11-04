package com.example.lancerpick.review.repository;

import com.example.lancerpick.review.entity.Review;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewRepository extends JpaRepository<Review, Long> {
    boolean existsByOrderId(long orderId);
}
