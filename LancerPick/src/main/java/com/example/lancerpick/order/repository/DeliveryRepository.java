package com.example.lancerpick.order.repository;

import com.example.lancerpick.order.entity.Delivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface DeliveryRepository extends JpaRepository<Delivery, Long> {
    boolean existsByOrderIdAndSequenceNo(long orderId, int sequenceNo);

    /** 현재 검수 대상(가장 마지막 회차) 납품. */
    Optional<Delivery> findFirstByOrderIdOrderBySequenceNoDesc(long orderId);

    @Query(value = "select d.sequenceNo from Delivery d where d.id=:deliveryId and d.orderId=:orderId")
    Optional<Integer> sequenceForOrderDelivery(@Param("orderId") long orderId, @Param("deliveryId") long deliveryId);

    @Query(value = "select count(c) from CancellationRequest c where c.orderId=:orderId and c.status='PENDING'")
    long pendingCancellations(@Param("orderId") long orderId);

    @Query(value = "select count(r) from RevisionRequest r join Delivery d on d.id=r.deliveryId and d.orderId=r.orderId and d.sequenceNo=1 where r.orderId=:orderId")
    long revisions(@Param("orderId") long orderId);
}
