package com.example.lancerpick.order.repository;

import com.example.lancerpick.order.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    boolean existsByOrderIdAndSequenceNo(long orderId, int sequenceNo);

    @Query(value = "SELECT sequence_no FROM deliveries WHERE id=:deliveryId AND order_id=:orderId", nativeQuery = true)
    Optional<Integer> sequenceForOrderDelivery(@Param("orderId") long orderId, @Param("deliveryId") long deliveryId);

    @Query(value = "SELECT COUNT(*) FROM cancellation_requests WHERE order_id=:orderId AND status='PENDING'", nativeQuery = true)
    long pendingCancellations(@Param("orderId") long orderId);

    @Query(value = "SELECT COUNT(*) FROM revision_requests r JOIN deliveries d ON d.id=r.delivery_id AND d.order_id=r.order_id AND d.sequence_no=1 WHERE r.order_id=:orderId", nativeQuery = true)
    long revisions(@Param("orderId") long orderId);
}
