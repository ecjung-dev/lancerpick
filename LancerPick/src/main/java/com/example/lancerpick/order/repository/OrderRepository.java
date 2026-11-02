package com.example.lancerpick.order.repository;

import com.example.lancerpick.order.entity.Order;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByBuyerIdAndRequestKey(Long buyerId, String requestKey);
}
