package com.example.lancerpick.order.repository;

import com.example.lancerpick.order.entity.Order;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {
    // 이력 actor FK의 회원 공유 잠금을 주문 잠금보다 먼저 얻는다. 생성 재전송의 회원→주문 순서와 맞춘다.
    @Lock(LockModeType.PESSIMISTIC_READ)
    @Query("select m from Member m where m.id=:memberId")
    Optional<com.example.lancerpick.member.entity.Member> findActorForShare(@Param("memberId") long memberId);
    default Optional<Long> lockDecisionActor(long memberId){return findActorForShare(memberId).map(com.example.lancerpick.member.entity.Member::getId);}

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.id=:orderId and (o.buyerId=:memberId or o.sellerId=:memberId)")
    Optional<Order> findForDecision(@Param("orderId") long orderId, @Param("memberId") long memberId);

    Optional<Order> findByBuyerIdAndRequestKey(Long buyerId, String requestKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from Order o where o.buyerId = :buyerId and o.requestKey = :requestKey")
    Optional<Order> findForCreation(@Param("buyerId") Long buyerId, @Param("requestKey") String requestKey);
}
