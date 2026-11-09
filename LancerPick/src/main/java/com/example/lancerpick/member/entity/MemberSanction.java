package com.example.lancerpick.member.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.Getter;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "member_sanctions")
@Getter
public class MemberSanction {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private long memberId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(nullable = false, length = 24, updatable = false)
    private SanctionKind kind;

    @Column(nullable = false, length = 1000, updatable = false)
    private String reason;

    @Column(name = "applied_by", nullable = false, updatable = false)
    private long appliedBy;

    @Column(
            name = "request_key",
            nullable = false,
            updatable = false,
            columnDefinition = "CHAR(36)")
    private String requestKey;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "starts_at", nullable = false, updatable = false)
    private LocalDateTime startsAt;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "ends_at", nullable = false, updatable = false)
    private LocalDateTime endsAt;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(name = "revoked_by")
    private Long revokedBy;

    @Column(name = "revoke_reason", length = 1000)
    private String revokeReason;

    @JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)
    @Column(name = "timer_stop_delivered_at")
    private LocalDateTime timerStopDeliveredAt;

    protected MemberSanction() {}

    public static MemberSanction apply(
            long memberId,
            SanctionKind kind,
            String reason,
            long adminId,
            UUID requestKey,
            LocalDateTime at,
            LocalDateTime until) {
        if (memberId < 1
                || adminId < 1
                || kind == null
                || kind == SanctionKind.NONE
                || requestKey == null
                || at == null
                || until == null
                || !until.isAfter(at)
                || reason == null
                || reason.isBlank()
                || reason.codePointCount(0, reason.length()) > 1000) {
            throw new IllegalArgumentException("Invalid sanction");
        }
        var s = new MemberSanction();
        s.memberId = memberId;
        s.kind = kind;
        s.reason = reason;
        s.appliedBy = adminId;
        s.requestKey = requestKey.toString();
        s.startsAt = at;
        s.endsAt = until;
        return s;
    }

    public boolean matches(long admin, SanctionKind type, String text, LocalDateTime until) {
        return appliedBy == admin && kind == type && reason.equals(text) && endsAt.equals(until);
    }

    public void revoke(long admin, String text, LocalDateTime at) {
        if (admin < 1
                || text == null
                || text.isBlank()
                || text.codePointCount(0, text.length()) > 1000
                || at == null
                || at.isBefore(startsAt)) {
            throw new IllegalArgumentException("Invalid revocation");
        }
        if (revokedAt == null) {
            revokedAt = at;
            revokedBy = admin;
            revokeReason = text;
        }
    }

    public void delivered(LocalDateTime at) {
        if (kind != SanctionKind.SECURITY_LOCK || at == null || at.isBefore(startsAt)) {
            throw new IllegalArgumentException("Invalid delivery time");
        }
        if (timerStopDeliveredAt == null) {
            timerStopDeliveredAt = at;
        }
    }
}
