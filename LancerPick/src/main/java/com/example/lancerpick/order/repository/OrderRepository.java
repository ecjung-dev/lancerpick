package com.example.lancerpick.order.repository;

import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.order.entity.Order;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderRepository extends JpaRepository<Order, Long> {
  // 이력 actor FK의 회원 공유 잠금을 주문 잠금보다 먼저 얻는다. 생성 재전송의 회원→주문 순서와 맞춘다.
  @Lock(LockModeType.PESSIMISTIC_READ)
  @Query("select m from Member m where m.id=:memberId")
  Optional<com.example.lancerpick.member.entity.Member> findActorForShare(
      @Param("memberId") long memberId);

  default Optional<Long> lockDecisionActor(long memberId) {
    return findActorForShare(memberId).map(com.example.lancerpick.member.entity.Member::getId);
  }

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query(
      "select o from Order o where o.id=:orderId and (o.buyerId=:memberId or o.sellerId=:memberId)")
  Optional<Order> findForDecision(@Param("orderId") long orderId, @Param("memberId") long memberId);

  default Optional<Order> findForLegacyDecision(long orderId, long memberId) {
    var order = findForDecision(orderId, memberId);
    order.ifPresent(value ->
        com.example.lancerpick.common.policy.TradePolicyVersion.requireLegacyCommand(
            value.getPolicyVersion()));
    return order;
  }

  default Optional<Order> findForFulfillment(long orderId, long memberId) {
    var order = findForDecision(orderId, memberId);
    order.ifPresent(
        value -> TradePolicyVersion.requireFulfillmentCommand(value.getPolicyVersion()));
    return order;
  }

  default Optional<Order> findForFundedOrLegacyDecision(long orderId, long memberId) {
    var order = findForDecision(orderId, memberId);
    order.ifPresent(value -> {
      if (!value.isFullFunding()) {
        TradePolicyVersion.requireLegacyCommand(value.getPolicyVersion());
      }
    });
    return order;
  }

  /** 회원 실행자가 없는 시스템 처리(자동 구매 확정)와 관리자 처리의 주문 배타 잠금. 당사자 조건이 없으므로 호출자가 권한을 확인한다. */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Order o where o.id=:orderId")
  Optional<Order> findForSystemUpdate(@Param("orderId") long orderId);

  Optional<Order> findByBuyerIdAndRequestKey(Long buyerId, String requestKey);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select o from Order o where o.buyerId = :buyerId and o.requestKey = :requestKey")
  Optional<Order> findForCreation(
      @Param("buyerId") Long buyerId, @Param("requestKey") String requestKey);
}
