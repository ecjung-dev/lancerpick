package com.example.lancerpick.member.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.text.TextNormalizer;
import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.member.dto.SecurityLockApplied;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.entity.MemberRole;
import com.example.lancerpick.member.entity.MemberSanction;
import com.example.lancerpick.member.entity.SanctionKind;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.member.repository.MemberSanctionRepository;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Own short member transaction; called by admin HTTP without an enclosing order/wallet transaction. */
@Service
@Profile("!demo")
@Transactional(propagation = Propagation.NEVER)
public class MemberSanctionCommandService {
    private final MemberRepository members;
    private final MemberSanctionRepository sanctions;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    private final TransactionTemplate tx;

    public MemberSanctionCommandService(
            MemberRepository members,
            MemberSanctionRepository sanctions,
            ApplicationEventPublisher events,
            Clock clock,
            PlatformTransactionManager manager) {
        this.members = members;
        this.sanctions = sanctions;
        this.events = events;
        this.clock = clock;
        tx = new TransactionTemplate(manager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    public long apply(
            long adminId,
            long memberId,
            SanctionKind kind,
            String rawReason,
            LocalDateTime until,
            UUID requestKey) {
        String reason = reason(rawReason);
        if (adminId < 1
                || memberId < 1
                || adminId == memberId
                || kind == null
                || kind == SanctionKind.NONE
                || until == null
                || requestKey == null) {
            throw invalid();
        }
        var end = until.truncatedTo(ChronoUnit.MICROS);
        return tx.execute(ignored -> {
            Member target = lockMembers(adminId, memberId);
            var old = sanctions.findByMemberIdAndRequestKey(memberId, requestKey.toString());
            if (old.isPresent()) {
                if (!old.get().matches(adminId, kind, reason, end)) {
                    throw new BusinessException(
                            HttpStatus.CONFLICT,
                            "SANCTION_REQUEST_CONFLICT",
                            "동일 요청 키의 제재 조건이 다릅니다.");
                }
                return old.get().getId();
            }
            var at = UtcTimes.nowMicros(clock);
            if (!end.isAfter(at) || at.isBefore(target.getUpdatedAt())) {
                throw invalid();
            }
            for (var active : sanctions.activeForMutation(memberId, kind, at)) {
                active.revoke(adminId, "새 제재 적용으로 교체", at);
            }
            var saved =
                    sanctions.saveAndFlush(
                            MemberSanction.apply(
                                    memberId, kind, reason, adminId, requestKey, at, end));
            if (kind == SanctionKind.SECURITY_LOCK) {
                target.invalidateSessions(at);
                members.flush();
                events.publishEvent(
                        new SecurityLockApplied(saved.getId(), adminId, memberId, reason, at));
            }
            return saved.getId();
        });
    }

    public void revoke(long adminId, long memberId, long sanctionId, String rawReason) {
        String reason = reason(rawReason);
        if (adminId < 1 || memberId < 1 || adminId == memberId || sanctionId < 1) {
            throw invalid();
        }
        tx.executeWithoutResult(ignored -> {
            lockMembers(adminId, memberId);
            var sanction =
                    sanctions
                            .findById(sanctionId)
                            .filter(s -> s.getMemberId() == memberId)
                            .orElseThrow(() ->
                                    new BusinessException(
                                            HttpStatus.NOT_FOUND,
                                            "RESOURCE_NOT_FOUND",
                                            "제재 기록을 찾을 수 없습니다."));
            sanction.revoke(adminId, reason, UtcTimes.nowMicros(clock));
            sanctions.flush();
        });
    }

    private Member lockMembers(long adminId, long memberId) {
        var role =
                members.findRoleById(adminId)
                        .orElseThrow(MemberSanctionCommandService::unauthorized);
        if (role != MemberRole.ADMIN) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "관리자만 제재를 처리할 수 있습니다.");
        }
        Member admin;
        Member target;
        if (adminId < memberId) {
            admin =
                    members.findByIdForShare(adminId)
                            .orElseThrow(MemberSanctionCommandService::unauthorized);
            target =
                    members.findByIdForUpdate(memberId)
                            .orElseThrow(MemberSanctionCommandService::missing);
        } else {
            target =
                    members.findByIdForUpdate(memberId)
                            .orElseThrow(MemberSanctionCommandService::missing);
            admin =
                    members.findByIdForShare(adminId)
                            .orElseThrow(MemberSanctionCommandService::unauthorized);
        }
        if (admin.getRole() != MemberRole.ADMIN || target.getRole() == MemberRole.ADMIN) {
            throw new BusinessException(
                    HttpStatus.FORBIDDEN, "FORBIDDEN", "관리자만 일반 회원의 제재를 처리할 수 있습니다.");
        }
        return target;
    }

    private static String reason(String raw) {
        var value = raw == null ? "" : TextNormalizer.normalize(raw);
        if (value.isEmpty() || value.codePointCount(0, value.length()) > 1000) {
            throw invalid();
        }
        return value;
    }

    private static BusinessException invalid() {
        return new BusinessException(
                HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "제재 대상·기간·사유·요청 키를 확인해 주세요.");
    }

    private static BusinessException unauthorized() {
        return new BusinessException(
                HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
    }

    private static BusinessException missing() {
        return new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "회원을 찾을 수 없습니다.");
    }
}
