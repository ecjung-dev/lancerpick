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

    @Enumerated(EnumType.STRING)                          // USER/ADMIN을 글자 그대로 저장 (숫자 0,1로 저장 X)
    @JdbcTypeCode(SqlTypes.VARCHAR)                       // DB 컬럼이 VARCHAR임을 명시 (팀 물리 설계 규칙)
    @Column(name = "role", nullable = false, length = 10)
    private MemberRole role;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;                      // UTC 기준

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
        return member;
    }

    /** PATCH /api/me 표시 이름 수정용. */
    public void changeDisplayName(String displayName, LocalDateTime now) {
        this.displayName = displayName;
        this.updatedAt = now;
    }
}
