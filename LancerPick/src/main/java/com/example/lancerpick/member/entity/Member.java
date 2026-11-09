package com.example.lancerpick.member.entity;

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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "members")
@Getter                                             // [Lombok] 모든 필드의 getter(getId(), getEmail() ...) 자동 생성
@NoArgsConstructor(access = AccessLevel.PROTECTED)  // [Lombok] JPA가 쓰는 빈 생성자를 protected로 자동 생성
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)   // DB의 AUTO_INCREMENT가 번호를 매김
    private Long id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;                                 // 정규화(공백 제거·소문자)된 값만 저장

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;                          // 원문 X, 인코더 결과({bcrypt}...)만 저장

    @Column(name = "display_name", nullable = false, length = 30)
    private String displayName;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "display_name_changed_at")
    private LocalDateTime displayNameChangedAt;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "password_recommendation_base_at", nullable = false)
    private LocalDateTime passwordRecommendationBaseAt;

    @Enumerated(EnumType.STRING)                          // USER/ADMIN을 글자 그대로 저장 (숫자 0,1로 저장 X)
    @JdbcTypeCode(SqlTypes.VARCHAR)                       // DB 컬럼이 VARCHAR임을 명시 (팀 물리 설계 규칙)
    @Column(name = "role", nullable = false, length = 10)
    private MemberRole role;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "email_verified_at")
    private LocalDateTime emailVerifiedAt;

    @Column(name = "auth_version", nullable = false)
    private long authVersion;

    // UTC LocalDateTime을 JVM 기본 시간대로 재해석하지 않고 DATETIME에 직접 저장한다.
    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;                      // UTC 기준

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;                      // UTC 기준

    /** 일반 회원가입용. 권한은 항상 USER로 고정한다. */
    public static Member create(String email, String passwordHash, String displayName, LocalDateTime now) {
        Member member = new Member();
        member.email = email;
        member.passwordHash = passwordHash;
        member.displayName = displayName;
        member.role = MemberRole.USER;
        member.createdAt = now;
        member.updatedAt = now;
        member.passwordChangedAt = now;
        member.passwordRecommendationBaseAt = now;
        return member;
    }

    /**
     * 이메일 인증을 마친 가입용(3인 설계 §5.1). email_verified_at에 가입 시각을 기록한다. 권한은 항상 USER다.
     * 인증 시각은 가입 증명을 소비한 같은 트랜잭션의 시각이다.
     */
    public static Member createVerified(String email, String passwordHash, String displayName, LocalDateTime now) {
        Member member = create(email, passwordHash, displayName, now);
        member.emailVerifiedAt = now;
        return member;
    }

    /** PATCH /api/me 표시 이름 수정용. */
    public void changeDisplayName(String displayName, LocalDateTime now) {
        this.displayName = displayName;
        this.displayNameChangedAt = now;
        this.updatedAt = now;
    }

    /**
     * POST /api/me/password 비밀번호 변경용. 인코더 결과만 받는다(원문 X).
     * auth_version을 1 올려서 이 회원의 기존 로그인 세션이 모두 폐기되게 한다(SessionAuthVersionFilter).
     */
    public void changePassword(String newPasswordHash, LocalDateTime now) {
        this.passwordHash = newPasswordHash;
        this.passwordChangedAt = now;
        this.passwordRecommendationBaseAt = now;
        this.authVersion += 1;
        this.updatedAt = now;
    }

    /** 보안 잠금으로 기존 인증을 폐기한다. 잠금 만료 시 버전을 되돌리지 않는다. */
    public void invalidateSessions(LocalDateTime now) {
        if (now == null || now.isBefore(updatedAt)) {
            throw new IllegalArgumentException("Invalid member time");
        }
        authVersion = Math.addExact(authVersion, 1L);
        updatedAt = now;
    }
}
