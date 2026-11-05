package com.example.lancerpick.notification.repository;

import com.example.lancerpick.notification.entity.OutboxEvent;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {
  /** 처리 후보만 조회한다. 실제 처리 여부는 lockById 후 상태를 다시 확인해 결정한다. */
  @Query(
      "select e.id from OutboxEvent e where e.status = 'PENDING' and e.nextAttemptAt <= :now"
          + " order by e.nextAttemptAt, e.id")
  List<Long> findDueIds(@Param("now") LocalDateTime now, Pageable page);

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select e from OutboxEvent e where e.id = :id")
  Optional<OutboxEvent> lockById(@Param("id") long id);

  List<OutboxEvent> findByAggregateTypeAndAggregateIdOrderByIdAsc(String aggregateType, long aggregateId);
}
