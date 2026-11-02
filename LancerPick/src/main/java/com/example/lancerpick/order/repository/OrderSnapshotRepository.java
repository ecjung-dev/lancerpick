package com.example.lancerpick.order.repository;

import com.example.lancerpick.order.entity.OrderSnapshot;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderSnapshotRepository extends JpaRepository<OrderSnapshot, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from OrderSnapshot s where s.orderId = :orderId")
    Optional<OrderSnapshot> findForCreation(@Param("orderId") Long orderId);
}
