package com.example.lancerpick.auth.repository;

import com.example.lancerpick.auth.entity.AuthRequestLimit;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AuthRequestLimitRepository extends JpaRepository<AuthRequestLimit, Long> {

    /**
     * 제한 행이 없으면 만든다. 이미 있으면(UNIQUE uk_auth_limit 충돌) 아무것도 하지 않는다.
     * 지갑 최초 생성(WalletRepository.insertInitial)과 같은 Hibernate HQL 충돌 처리라 네이티브 SQL을 쓰지 않는다.
     * 동시에 처음 들어온 요청 두 건이 각자 행을 만들어 카운트가 둘로 나뉘는 일을 막는다.
     */
    @Modifying(flushAutomatically = true)
    @Query("insert into AuthRequestLimit (scope, subjectHmac, windowStart, requestCount, expiresAt) "
            + "values (:scope, :subjectHmac, :windowStart, 0, :expiresAt) on conflict do nothing")
    int insertIfAbsent(@Param("scope") String scope, @Param("subjectHmac") byte[] subjectHmac,
            @Param("windowStart") LocalDateTime windowStart, @Param("expiresAt") LocalDateTime expiresAt);

    /** 제한 행 잠금(SELECT ... FOR UPDATE). 같은 이메일의 동시 요청은 여기서 한 줄로 선다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select l from AuthRequestLimit l "
            + "where l.scope = :scope and l.subjectHmac = :subjectHmac and l.windowStart = :windowStart")
    Optional<AuthRequestLimit> findForUpdate(@Param("scope") String scope, @Param("subjectHmac") byte[] subjectHmac,
            @Param("windowStart") LocalDateTime windowStart);
}
