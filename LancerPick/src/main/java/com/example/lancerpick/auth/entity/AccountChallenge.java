package com.example.lancerpick.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 계정 복구용 일회용 증명(V5 account_challenges). 토큰 원문은 저장하지 않고 SHA-256 해시만 저장한다.
 * 이번 작업은 비밀번호 재설정(PASSWORD_RESET)만 만든다: member_id와 발급 당시 auth_version(credential_version)이 필수다.
 * 비밀번호가 한 번이라도 바뀌면 auth_version이 올라가므로, 그 전에 발급한 다른 재설정 링크는 버전 불일치로 쓸 수 없다.
 */
@Entity
@Table(name = "account_challenges")
@DynamicUpdate                                      // 바뀐 컬럼만 UPDATE한다. 사용 처리와 발송 결과 기록이 서로의 컬럼을 덮어쓰지 않게 한다.
@Getter                                             // [Lombok] 모든 필드의 getter(getId(), getMemberId() ...) 자동 생성
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // [Lombok] JPA가 쓰는 빈 생성자를 protected로 자동 생성
public class AccountChallenge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "purpose", nullable = false, length = 24, updatable = false)
    private ChallengePurpose purpose;

    @Column(name = "member_id", updatable = false)
    private Long memberId;

    @Column(name = "email", nullable = false, length = 254, updatable = false)
    private String email;                                 // 정규화된 이메일. 스키마가 요구하는 값(NOT NULL)

    @Column(name = "token_hash", nullable = false, updatable = false, columnDefinition = "BINARY(32)")
    private byte[] tokenHash;                             // SHA-256(토큰 원문 32바이트). 원문은 메일에만 있다

    @Column(name = "credential_version", updatable = false)
    private Long credentialVersion;                       // 발급 당시 members.auth_version

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;                       // UTC

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;                      // UTC

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;                     // 가입 인증용. 재설정에서는 쓰지 않는다

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "consumed_at")
    private LocalDateTime consumedAt;                     // 사용한 시각. 한 번 쓰면 다시 쓸 수 없다

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "delivery_status", nullable = false, length = 16)
    private ChallengeDeliveryStatus deliveryStatus;

    @Column(name = "last_error_code", length = 100)
    private String lastErrorCode;                         // 안전한 오류 코드만(메일 내용·토큰·예외 메시지 X)

    /** 비밀번호 재설정 링크 발급. 발송 결과는 처음에 PENDING이다. */
    public static AccountChallenge passwordReset(long memberId, String email, byte[] tokenHash,
            long credentialVersion, LocalDateTime issuedAt, LocalDateTime expiresAt) {
        AccountChallenge challenge = new AccountChallenge();
        challenge.purpose = ChallengePurpose.PASSWORD_RESET;
        challenge.memberId = memberId;
        challenge.email = email;
        challenge.tokenHash = tokenHash.clone();
        challenge.credentialVersion = credentialVersion;
        challenge.issuedAt = issuedAt;
        challenge.expiresAt = expiresAt;
        challenge.deliveryStatus = ChallengeDeliveryStatus.PENDING;
        return challenge;
    }

    /**
     * 재설정에 쓸 수 없는 이유. 쓸 수 있으면 null. 회원·이 행을 잠근 뒤에 호출해야 한다.
     * 이유는 서버 로그용 안전한 코드이고, 응답은 모두 같은 PASSWORD_RESET_TOKEN_INVALID다.
     */
    public String passwordResetRejection(long lockedMemberId, long currentAuthVersion, LocalDateTime now) {
        if (purpose != ChallengePurpose.PASSWORD_RESET) {
            return "WRONG_PURPOSE";
        }
        if (memberId == null || memberId != lockedMemberId) {
            return "MEMBER_MISMATCH";
        }
        if (consumedAt != null) {
            return "ALREADY_CONSUMED";
        }
        // 경계 시각은 만료로 본다(DB CHECK: consumed_at < expires_at).
        if (!now.isBefore(expiresAt)) {
            return "EXPIRED";
        }
        if (credentialVersion == null || credentialVersion != currentAuthVersion) {
            return "CREDENTIAL_CHANGED";
        }
        return null;
    }

    /** 사용 처리. passwordResetRejection이 null일 때만 호출한다. */
    public void consume(LocalDateTime now) {
        this.consumedAt = now;
    }

    public byte[] getTokenHash() {
        return tokenHash.clone();   // 배열은 바깥에서 바꾸지 못하게 복사본을 준다(Lombok getter 대신 직접 작성)
    }
}
