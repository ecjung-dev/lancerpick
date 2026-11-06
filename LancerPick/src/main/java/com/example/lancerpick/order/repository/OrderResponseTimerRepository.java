package com.example.lancerpick.order.repository;

import com.example.lancerpick.order.entity.OrderResponseTimer;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrderResponseTimerRepository extends JpaRepository<OrderResponseTimer, Long> {
  boolean existsByOrderIdAndStatus(long orderId, String status);

  /** 주문 잠금 아래에서 호출한다. 활성 타이머는 uk_timer_active로 주문당 하나다. */
  Optional<OrderResponseTimer> findByOrderIdAndStatus(long orderId, String status);

  Optional<OrderResponseTimer> findFirstByOrderIdOrderByIdDesc(long orderId);

  long countByOrderIdAndTimerTypeAndDeliveryId(long orderId, String timerType, long deliveryId);

  /** 기산점 이후 관리자가 해제한 타이머가 있으면 같은 기산점으로 다시 시작하지 않는다. */
  @Query(
      "select max(t.endedAt) from OrderResponseTimer t"
          + " where t.orderId=:orderId and t.endReason='ADMIN_STOP'")
  Optional<LocalDateTime> latestAdminStopAt(@Param("orderId") long orderId);

  record DueTimer(long id, LocalDateTime expiresAt) {}

  /**
   * 만료 처리 대상을 (만료 시각, ID) 커서 이후부터 읽는다. 실패해 ACTIVE로 남은 타이머가 앞쪽에 있어도 같은 실행에서 뒤의 대상까지
   * 순회하기 위함이다. 엔티티를 영속성 컨텍스트에 올리지 않도록 값만 읽고, 주문 잠금 후 다시 확인한다.
   */
  @Query(
      "select new com.example.lancerpick.order.repository.OrderResponseTimerRepository$DueTimer("
          + "t.id, t.expiresAt) from OrderResponseTimer t where t.status='ACTIVE'"
          + " and t.expiresAt<=:now and (t.expiresAt>:afterAt or (t.expiresAt=:afterAt and"
          + " t.id>:afterId)) order by t.expiresAt asc, t.id asc")
  List<DueTimer> dueTimersAfter(
      @Param("now") LocalDateTime now,
      @Param("afterAt") LocalDateTime afterAt,
      @Param("afterId") long afterId,
      Pageable page);

  /**
   * 사전 안내 대상: 아직 만료되지 않았고 만료 시각이 noticeUntil 이하인 활성 타이머를 (만료 시각, ID) 커서 이후부터 읽는다.
   * 안내 여부와 활성 상태는 주문 잠금 후 다시 확인한다.
   */
  @Query(
      "select new com.example.lancerpick.order.repository.OrderResponseTimerRepository$DueTimer("
          + "t.id, t.expiresAt) from OrderResponseTimer t where t.status='ACTIVE'"
          + " and t.expiresAt>:now and t.expiresAt<=:noticeUntil and (t.expiresAt>:afterAt or"
          + " (t.expiresAt=:afterAt and t.id>:afterId)) order by t.expiresAt asc, t.id asc")
  List<DueTimer> noticeTimersAfter(
      @Param("now") LocalDateTime now,
      @Param("noticeUntil") LocalDateTime noticeUntil,
      @Param("afterAt") LocalDateTime afterAt,
      @Param("afterId") long afterId,
      Pageable page);

  @Query("select t.orderId from OrderResponseTimer t where t.id=:id")
  Optional<Long> orderIdOf(@Param("id") long id);
}
