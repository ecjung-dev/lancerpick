package com.example.lancerpick.order.repository;

import com.example.lancerpick.order.entity.RevisionRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RevisionRequestRepository extends JpaRepository<RevisionRequest, Long> {
    boolean existsByOrderId(long orderId);
}
