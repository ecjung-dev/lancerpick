package com.example.lancerpick.order.repository;

import com.example.lancerpick.order.entity.Order;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {
    Optional<Order> findByBuyerIdAndRequestKey(Long buyerId, String requestKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.buyerId = :buyerId and o.requestKey = :requestKey")
    Optional<Order> findForCreation(@Param("buyerId") Long buyerId, @Param("requestKey") String requestKey);
}
