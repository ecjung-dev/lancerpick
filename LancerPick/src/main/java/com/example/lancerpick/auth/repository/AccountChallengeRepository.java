package com.example.lancerpick.auth.repository;

import com.example.lancerpick.auth.entity.AccountChallenge;
import com.example.lancerpick.auth.entity.ChallengeDeliveryStatus;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AccountChallengeRepository extends JpaRepository<AccountChallenge, Long> {

    /** 잠금 없이 토큰 해시로 회원 ID만 찾는다. 회원 → 토큰 잠금 순서를 지키려고, 회원을 먼저 잠그는 데 쓴다. */
    @Query("select c.memberId from AccountChallenge c where c.tokenHash = :tokenHash")
    Optional<Long> findMemberIdByTokenHash(@Param("tokenHash") byte[] tokenHash);

    /** 토큰 행 잠금(SELECT ... FOR UPDATE). 반드시 회원 행을 먼저 잠근 뒤에 호출한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AccountChallenge c where c.tokenHash = :tokenHash")
    Optional<AccountChallenge> findByTokenHashForUpdate(@Param("tokenHash") byte[] tokenHash);

    /**
     * 발송 결과 기록. PENDING/UNKNOWN의 상태 두 컬럼만 변경하며 인증·사용 상태는 건드리지 않는다. 늦게 온 실제 결과는 UNKNOWN을 확정할 수 있지만
     * SENT/FAILED를 덮어쓰지 않는다.
     */
    @Modifying
    @Query(
            "update AccountChallenge c set c.deliveryStatus = :status, c.lastErrorCode = :errorCode"
                + " where c.id = :id and c.deliveryStatus in"
                + " (com.example.lancerpick.auth.entity.ChallengeDeliveryStatus.PENDING, "
                + "com.example.lancerpick.auth.entity.ChallengeDeliveryStatus.UNKNOWN)")
    int recordDelivery(
            @Param("id") long id,
            @Param("status") ChallengeDeliveryStatus status,
            @Param("errorCode") String errorCode);

    /** Only IDs are read; recipients and token hashes never enter the reconciliation job. */
    @Query(
            "select c.id from AccountChallenge c where"
                + " c.deliveryStatus=com.example.lancerpick.auth.entity.ChallengeDeliveryStatus.PENDING"
                + " and c.issuedAt<=:cutoff order by c.issuedAt,c.id")
    List<Long> stalePendingIds(@Param("cutoff") LocalDateTime cutoff, Pageable page);

    @Modifying
    @Query(
            "update AccountChallenge c set"
                + " c.deliveryStatus=com.example.lancerpick.auth.entity.ChallengeDeliveryStatus.UNKNOWN,"
                + " c.lastErrorCode='MAIL_RESULT_UNCONFIRMED' where c.id in :ids and"
                + " c.deliveryStatus=com.example.lancerpick.auth.entity.ChallengeDeliveryStatus.PENDING"
                + " and c.issuedAt<=:cutoff")
    int markDeliveryUnknown(@Param("ids") List<Long> ids, @Param("cutoff") LocalDateTime cutoff);
}
