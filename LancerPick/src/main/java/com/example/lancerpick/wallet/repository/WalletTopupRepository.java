package com.example.lancerpick.wallet.repository;

import com.example.lancerpick.wallet.entity.WalletTopup;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletTopupRepository extends JpaRepository<WalletTopup, Long> {
    Optional<WalletTopup> findByIdAndMemberId(long id, long memberId);

    /** 승인 처리 서비스에서 트랜잭션 안에서 호출한다. memberId는 인증 정보에서 얻는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select topup from WalletTopup topup where topup.id = :id and topup.memberId = :memberId")
    Optional<WalletTopup> findOwnedByIdForUpdate(@Param("id") long id, @Param("memberId") long memberId);
}
