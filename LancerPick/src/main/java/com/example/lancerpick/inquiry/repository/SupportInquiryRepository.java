package com.example.lancerpick.inquiry.repository;

import com.example.lancerpick.inquiry.entity.SupportInquiry;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupportInquiryRepository extends JpaRepository<SupportInquiry, Long> {
  /** 잠그기 전에 문의 유형·주문만 확인한다. 엔티티를 영속성 컨텍스트에 올리지 않아 이후 잠금 조회가 최신 행을 읽는다. */
  @Query("select i.type as type, i.orderId as orderId from SupportInquiry i where i.id=:id")
  Optional<Target> findTarget(@Param("id") long id);

  interface Target {
    String getType();

    Long getOrderId();
  }

  /** 취소 검토 문의는 주문 잠금 뒤에 이 잠금을 얻는다(관리자 → 주문 → 문의 → 구매자 지갑). */
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("select i from SupportInquiry i where i.id=:id")
  Optional<SupportInquiry> findForUpdate(@Param("id") long id);
}
