package com.example.lancerpick.wallet.repository;

import com.example.lancerpick.wallet.entity.Wallet;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WalletRepository extends JpaRepository<Wallet, Long> {
    /**
     * 지갑 생성 전에는 잠글 지갑 행이 없다. 초기 생성끼리는 먼저 회원 PK를 잠가 직렬화한다.
     * 회원 Entity를 중복 정의하지 않는다. 회원 담당자와 공유하는 초기화 전용 조회다.
     */
    @Query(value = "SELECT id FROM members WHERE id = :memberId FOR UPDATE", nativeQuery = true)
    Optional<Long> lockMemberForInitialization(@Param("memberId") long memberId);

    /**
     * 없는 지갑을 FOR UPDATE로 조회하면 서로 다른 회원도 같은 간격 잠금에 걸릴 수 있다.
     * 검증된 내부 값으로 먼저 INSERT한다. 반환값 1은 생성, 0은 기존 행 확인이 필요함을 뜻한다.
     * IGNORE의 0을 성공으로 간주하지 않고 서비스에서 실제 행과 지급 근거를 검사한다.
     */
    @Modifying(flushAutomatically = true)
    @Query(value = "INSERT IGNORE INTO wallets(member_id, balance, updated_at) VALUES (:memberId, :balance, :updatedAt)", nativeQuery = true)
    int insertInitialWalletIfAbsent(@Param("memberId") long memberId,
            @Param("balance") long balance, @Param("updatedAt") LocalDateTime updatedAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select wallet from Wallet wallet where wallet.memberId = :memberId")
    Optional<Wallet> findByMemberIdForUpdate(@Param("memberId") long memberId);

    /** 지갑 잠금 후 사용. Instant로 UTC 물리 시각을 저장한다. */
    @Modifying(flushAutomatically = true)
    @Query(value = "UPDATE wallets SET balance=:balance, updated_at=:at WHERE member_id=:memberId", nativeQuery = true)
    int updateTopupBalance(@Param("memberId") long memberId, @Param("balance") long balance, @Param("at") Instant at);
}
