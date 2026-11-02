package com.example.lancerpick.order.repository;

import com.example.lancerpick.order.entity.OrderHistory;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderHistoryRepository extends JpaRepository<OrderHistory, Long> {
    List<OrderHistory> findByOrderIdOrderByCreatedAtAscIdAsc(Long orderId);
}
