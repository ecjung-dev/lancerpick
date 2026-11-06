package com.example.lancerpick.order.repository;
import com.example.lancerpick.order.entity.CancellationRequest;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
public interface CancellationRequestRepository extends JpaRepository<CancellationRequest,Long>{List<CancellationRequest> findByOrderIdAndStatus(long orderId,String status);

  /** 거절·철회된 취소 요청의 마지막 처리 시각. 자동 구매 확정 검수 기산점 계산에 사용한다. */
  @Query("select max(c.resolvedAt) from CancellationRequest c where c.orderId=:orderId and c.status in ('REJECTED','WITHDRAWN')")
  Optional<LocalDateTime> latestReopenedAt(@Param("orderId") long orderId);
}
