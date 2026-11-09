package com.example.lancerpick.member.service;

import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.member.entity.SanctionKind;
import com.example.lancerpick.member.repository.MemberSanctionRepository;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

/** Caller owns any member locks. Projections avoid reusing stale managed sanction entities. */
@Service
@Profile("!demo")
@Transactional(readOnly = true, isolation = Isolation.READ_COMMITTED)
public class MemberSanctionQuery {
    public record SecurityLock(
            long sanctionId,
            long adminId,
            String reason,
            LocalDateTime appliedAt,
            LocalDateTime endsAt) {}

    public record Restriction(
            SanctionKind kind,
            Long sanctionId,
            Long adminId,
            String reason,
            LocalDateTime endsAt,
            SecurityLock latestSecurityLock) {
        public boolean blocksNewActivity() {
            return kind != SanctionKind.NONE;
        }

        public boolean securityLocked() {
            return kind == SanctionKind.SECURITY_LOCK;
        }
    }

    private final MemberSanctionRepository repository;
    private final Clock clock;

    public MemberSanctionQuery(MemberSanctionRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public Restriction restriction(long memberId) {
        if (memberId < 1) {
            throw new IllegalArgumentException("Invalid member ID");
        }
        var now = UtcTimes.nowMicros(clock);
        var page = PageRequest.of(0, 1);
        var history =
                repository.latestSecurityLock(memberId, page).stream()
                        .findFirst()
                        .map(s ->
                                new SecurityLock(
                                        s.getId(),
                                        s.getAppliedBy(),
                                        s.getReason(),
                                        s.getStartsAt(),
                                        s.getEndsAt()))
                        .orElse(null);
        for (var kind :
                new SanctionKind[] {SanctionKind.SECURITY_LOCK, SanctionKind.GENERAL_RESTRICTION}) {
            var current = repository.current(memberId, kind, now, page);
            if (!current.isEmpty()) {
                var s = current.getFirst();
                return new Restriction(
                        kind, s.getId(), s.getAppliedBy(), s.getReason(), s.getEndsAt(), history);
            }
        }
        return new Restriction(SanctionKind.NONE, null, null, null, null, history);
    }
}
