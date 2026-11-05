package com.example.lancerpick.auth.repository;

import com.example.lancerpick.auth.entity.AccountChallenge;
import com.example.lancerpick.auth.entity.ChallengeDeliveryStatus;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccountChallengeRepository extends JpaRepository<AccountChallenge, Long> {

    /** 잠금 없이 토큰 해시로 회원 ID만 찾는다. 회원 → 토큰 잠금 순서를 지키려고, 회원을 먼저 잠그는 데 쓴다. */
    @Query("select c.memberId from AccountChallenge c where c.tokenHash = :tokenHash")
    Optional<Long> findMemberIdByTokenHash(@Param("tokenHash") byte[] tokenHash);

    /** 토큰 행 잠금(SELECT ... FOR UPDATE). 반드시 회원 행을 먼저 잠근 뒤에 호출한다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from AccountChallenge c where c.tokenHash = :tokenHash")
    Optional<AccountChallenge> findByTokenHashForUpdate(@Param("tokenHash") byte[] tokenHash);

    /**
     * 발송 결과 기록. 발송 상태 두 컬럼만 바꾸고(사용 시각 등은 건드리지 않음), 아직 PENDING인 행만 바꾼다.
     * 늦게 도착한 결과가 이미 기록된 결과를 덮어쓰지 않는다. 바뀐 행 수를 돌려준다.
     */
    @Modifying
    @Query("update AccountChallenge c set c.deliveryStatus = :status, c.lastErrorCode = :errorCode "
            + "where c.id = :id and c.deliveryStatus = com.example.lancerpick.auth.entity.ChallengeDeliveryStatus.PENDING")
    int recordDelivery(@Param("id") long id, @Param("status") ChallengeDeliveryStatus status,
            @Param("errorCode") String errorCode);
}
