package com.example.lancerpick.order.repository;

import com.example.lancerpick.order.entity.OrderSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderSnapshotRepository extends JpaRepository<OrderSnapshot, Long> {
}
