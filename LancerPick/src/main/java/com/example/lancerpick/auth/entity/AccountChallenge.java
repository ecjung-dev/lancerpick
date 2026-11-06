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
import java.util.Arrays;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * 계정 복구용 일회용 증명(V5 account_challenges). 토큰 원문은 저장하지 않고 SHA-256 해시만 저장한다.
 * - PASSWORD_RESET: member_id와 발급 당시 auth_version(credential_version)이 필수다.
 *   비밀번호가 한 번이라도 바뀌면 auth_version이 올라가므로, 그 전에 발급한 다른 재설정 링크는 버전 불일치로 쓸 수 없다.
 * - SIGNUP_EMAIL: 가입 전이라 member_id가 없다. 메일 링크로 인증하면 같은 행이 가입 증명(proof)으로 바뀌고(verified_at),
 *   가입할 때 증명을 사용 처리(consumed_at)하면서 회원·지갑을 함께 만든다.
 * 목적이 다르면 서로의 토큰으로 쓸 수 없다(각 확인 메서드가 purpose를 먼저 본다).
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

    // 가입 인증(SIGNUP_EMAIL)은 인증하는 순간 메일 링크 토큰의 해시를 가입 증명(proof)의 해시로 바꾼다(verifyForSignup).
    @Column(name = "token_hash", nullable = false, columnDefinition = "BINARY(32)")
    private byte[] tokenHash;                             // SHA-256(토큰 원문 32바이트). 원문은 메일·인증 응답에만 있다

    @Column(name = "credential_version", updatable = false)
    private Long credentialVersion;                       // 발급 당시 members.auth_version

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "issued_at", nullable = false, updatable = false)
    private LocalDateTime issuedAt;                       // UTC

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;                      // UTC. 가입 인증은 인증 시점에 증명 만료로 다시 정한다

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
     * 가입 이메일 인증 메일 발급(3인 설계 §5.1). 가입 전이라 member_id·credential_version은 없다(V5 CHECK).
     * 이미 가입된 주소에도 같은 행을 만든다(요청 단계에서 가입 여부를 드러내지 않음).
     */
    public static AccountChallenge signupEmail(String email, byte[] tokenHash, LocalDateTime issuedAt,
            LocalDateTime expiresAt) {
        AccountChallenge challenge = new AccountChallenge();
        challenge.purpose = ChallengePurpose.SIGNUP_EMAIL;
        challenge.email = email;
        challenge.tokenHash = tokenHash.clone();
        challenge.issuedAt = issuedAt;
        challenge.expiresAt = expiresAt;
        challenge.deliveryStatus = ChallengeDeliveryStatus.PENDING;
        return challenge;
    }

    /** 메일 링크 토큰으로 인증할 수 없는 이유. 인증할 수 있으면 null. 이 행을 잠근 뒤에 호출한다. */
    public String signupVerificationRejection(byte[] lockedTokenHash, LocalDateTime now) {
        if (purpose != ChallengePurpose.SIGNUP_EMAIL) {
            return "WRONG_PURPOSE";
        }
        // 잠금을 기다리는 동안 다른 요청이 먼저 인증하면 해시가 증명 해시로 바뀐다. 같은 링크 동시 인증은 한 번만 성공한다.
        if (verifiedAt != null || !Arrays.equals(tokenHash, lockedTokenHash)) {
            return "ALREADY_VERIFIED";
        }
        if (consumedAt != null) {
            return "ALREADY_CONSUMED";
        }
        if (!now.isBefore(expiresAt)) {
            return "EXPIRED";
        }
        return null;
    }

    /**
     * 인증 완료: 메일 링크 토큰을 더는 쓸 수 없게 하고, 같은 행을 가입 증명(proof)으로 바꾼다.
     * 증명 원문은 응답에만 있고 DB에는 해시만 남는다. 증명 만료는 인증 시점부터 다시 센다(입력할 시간 확보).
     */
    public void verifyForSignup(byte[] proofHash, LocalDateTime now, LocalDateTime proofExpiresAt) {
        this.tokenHash = proofHash.clone();
        this.verifiedAt = now;
        this.expiresAt = proofExpiresAt;
    }

    /** 가입 증명으로 가입할 수 없는 이유. 쓸 수 있으면 null. 이 행을 잠근 뒤에 호출한다. 응답은 모두 같은 400이다. */
    public String signupProofRejection(String normalizedEmail, LocalDateTime now) {
        if (purpose != ChallengePurpose.SIGNUP_EMAIL) {
            return "WRONG_PURPOSE";
        }
        if (verifiedAt == null) {
            return "NOT_VERIFIED";   // 메일 링크 토큰 자체를 증명으로 보낸 경우
        }
        if (consumedAt != null) {
            return "ALREADY_CONSUMED";
        }
        if (!now.isBefore(expiresAt)) {
            return "EXPIRED";
        }
        if (!email.equals(normalizedEmail)) {
            return "EMAIL_MISMATCH";
        }
        return null;
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

    /** 사용 처리. passwordResetRejection 또는 signupProofRejection이 null일 때만 호출한다. */
    public void consume(LocalDateTime now) {
        this.consumedAt = now;
    }

    public byte[] getTokenHash() {
        return tokenHash.clone();   // 배열은 바깥에서 바꾸지 못하게 복사본을 준다(Lombok getter 대신 직접 작성)
    }
}
