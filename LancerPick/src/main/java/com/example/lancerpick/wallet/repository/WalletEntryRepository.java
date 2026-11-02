package com.example.lancerpick.wallet.repository;

import com.example.lancerpick.wallet.entity.WalletEntry;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletEntryRepository extends JpaRepository<WalletEntry, Long> {
    /** 잠금 조회로 앞선 요청의 커밋 결과를 읽는다. 기존 스냅샷의 오래된 결과로 재지급하지 않는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select entry from WalletEntry entry where entry.eventKey = :eventKey")
    Optional<WalletEntry> findByEventKeyForUpdate(@Param("eventKey") String eventKey);

    long countByMemberId(long memberId);

    /**
     * payment_id → payments.order_id로 조회한다. 초기 지급은 결제 참조가 없어 LEFT JOIN한다.
     * offset은 long으로 전달해 큰 page 요청에서 JPA의 int 오프셋 변환 오류를 피한다.
     */
    @Query(value = """
            SELECT entry.id AS id, entry.type AS type,
                   entry.amount_delta AS amountDelta, entry.balance_after AS balanceAfter,
                   payment.order_id AS orderId, entry.created_at AS createdAt
              FROM wallet_entries entry
              LEFT JOIN payments payment ON payment.id = entry.payment_id
             WHERE entry.member_id = :memberId
             ORDER BY entry.created_at DESC, entry.id DESC
             LIMIT :pageSize OFFSET :pageOffset
            """, nativeQuery = true)
    List<WalletEntryView> findHistory(@Param("memberId") long memberId,
            @Param("pageSize") int pageSize, @Param("pageOffset") long pageOffset);
}
