package com.example.lancerpick.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 인증 요청 제한 카운터(V5 auth_request_limits). 이메일 원문 대신 서버 비밀키 HMAC(subject_hmac)으로 구분한다.
 * 한 행은 (scope, subject_hmac, window_start)마다 하나다(UNIQUE uk_auth_limit).
 * 행 생성은 AuthRequestLimitRepository.insertIfAbsent, 변경은 FOR UPDATE로 잠근 뒤 이 클래스의 메서드로만 한다.
 */
@Entity
@Table(name = "auth_request_limits")
@Getter                                             // [Lombok] 모든 필드의 getter 자동 생성
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // [Lombok] JPA가 쓰는 빈 생성자를 protected로 자동 생성
public class AuthRequestLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "scope", nullable = false, length = 30, updatable = false)
    private String scope;

    @Column(name = "subject_hmac", nullable = false, updatable = false, columnDefinition = "BINARY(32)")
    private byte[] subjectHmac;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "window_start", nullable = false, updatable = false)
    private LocalDateTime windowStart;                    // UTC

    @Column(name = "request_count", nullable = false)
    private int requestCount;                             // 허용된(실제 처리한) 요청 수

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;                      // UTC. 이 시각 전까지 이 행의 제한이 유효하다

    /** 간격 제한 행: expires_at(= 마지막 허용 시각 + 간격)이 아직 지나지 않았으면 막힌다. */
    public boolean isCoolingDownAt(LocalDateTime now) {
        return now.isBefore(expiresAt);
    }

    /** 간격 제한 행에 허용 1회를 기록한다. 다음 허용은 now + interval부터다. */
    public void recordIntervalUse(LocalDateTime now, Duration interval) {
        this.requestCount += 1;
        this.expiresAt = now.plus(interval);
    }

    /** 고정 구간 행: 이미 허용한 수가 한도 이상이면 막힌다. */
    public boolean isFull(int limit) {
        return requestCount >= limit;
    }

    /** 고정 구간 행에 허용 1회를 기록한다. */
    public void recordWindowUse() {
        this.requestCount += 1;
    }
}
