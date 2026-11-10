package com.example.lancerpick.auth.service;

import com.example.lancerpick.auth.entity.AccountChallenge;
import com.example.lancerpick.auth.mail.AccountMail;
import com.example.lancerpick.auth.mail.AccountMailDispatcher;
import com.example.lancerpick.auth.mail.PasswordResetMailContent;
import com.example.lancerpick.auth.repository.AccountChallengeRepository;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.member.dto.EmailRules;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 로그아웃 상태 비밀번호 재설정(3인 설계 6.1·7, 4인 설계 §4 2번).
 *
 * 요청(requestReset) — 가입 여부와 상관없이 같은 결과(202)로 끝난다.
 *  1) 이메일 정규화. 형식이 틀리면 400(가입 여부와 무관한 입력 오류).
 *  2) 요청 제한 행 준비(별도 커밋) → 업무 트랜잭션: 요청 제한 확인 → 회원 조회 → 있으면 토큰 해시 저장.
 *     제한에 걸리거나 회원이 없으면 아무것도 발급하지 않고 끝난다(응답은 같다).
 *  3) 커밋 뒤 트랜잭션 밖에서 메일을 발송기에 넘긴다. SMTP 통신을 DB 트랜잭션 안에서 하지 않는다.
 *  새 요청이 왔다고 기존 유효 링크를 지우지 않는다(남이 계속 요청해 복구를 방해하지 못하게). 대신 요청 수를 제한한다.
 *
 * 실행(resetPassword) — 한 트랜잭션.
 *  회원 행 잠금 → 토큰 행 잠금(순서 고정) → 목적·사용 여부·만료·발급 당시 auth_version 재확인
 *  → 비밀번호 해시 변경 + auth_version + 1(Member.changePassword 재사용) → 토큰 사용 처리.
 *  auth_version이 오르므로 기존 세션은 SessionAuthVersionFilter가 폐기하고, 다른 재설정 링크도 쓸 수 없게 된다.
 *  지갑을 다시 만들거나 적립금을 지급하지 않는다.
 */
@Service
@Profile("!demo")
public class PasswordResetService {
    private static final Logger log = LoggerFactory.getLogger(PasswordResetService.class);

    /** 링크 유효 시간(3인 설계 7.1). */
    static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    /** 이메일+목적별 최소 60초 간격, 1시간당 5회(3인 설계 7.1). scope는 30자 이하. */
    static final AuthRequestLimiter.Policy REQUEST_POLICY = new AuthRequestLimiter.Policy(
            "PASSWORD_RESET_EMAIL_INTERVAL", Duration.ofSeconds(60), "PASSWORD_RESET_EMAIL_HOUR", 5);
    private static final String HMAC_PURPOSE = "PASSWORD_RESET";

    private final MemberRepository members;
    private final AccountChallengeRepository challenges;
    private final AuthRequestLimiter limiter;
    private final AuthSubjectHasher hasher;
    private final PasswordResetMailContent mailContent;
    private final AccountMailDispatcher mailDispatcher;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public PasswordResetService(MemberRepository members, AccountChallengeRepository challenges,
            AuthRequestLimiter limiter, AuthSubjectHasher hasher, PasswordResetMailContent mailContent,
            AccountMailDispatcher mailDispatcher, PasswordEncoder passwordEncoder, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.members = members;
        this.challenges = challenges;
        this.limiter = limiter;
        this.hasher = hasher;
        this.mailContent = mailContent;
        this.mailDispatcher = mailDispatcher;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /**
     * POST /api/auth/password-reset-requests. 트랜잭션 밖에서 호출된다(메일은 커밋 뒤에 넘겨야 하므로 메서드에 @Transactional 없음).
     * 정상 종료하면 컨트롤러는 항상 같은 202를 돌려준다.
     */
    public void requestReset(String rawEmail) {
        String email = EmailRules.normalize(rawEmail).orElseThrow(PasswordResetService::invalidEmail);
        byte[] subject = hasher.subject(HMAC_PURPOSE, email);
        LocalDateTime now = now();

        limiter.prepare(REQUEST_POLICY, subject, now);   // 별도 커밋, 교착 시 재시도는 limiter가 한다
        Optional<AccountMail> mail = transaction.execute(status -> issue(email, subject, now));

        // 커밋이 끝난 뒤에만 여기 온다. 발송 실패는 응답에 드러내지 않는다(dispatch는 예외를 던지지 않음).
        mail.ifPresent(mailDispatcher::dispatch);
    }

    private Optional<AccountMail> issue(String email, byte[] subject, LocalDateTime now) {
        if (!limiter.tryAcquire(REQUEST_POLICY, subject, now)) {
            return Optional.empty();   // 제한 초과: 발급하지 않는다. 응답은 같은 202(3인 설계 6.1 2번)
        }
        Member member = members.findByEmail(email).orElse(null);
        if (member == null) {
            return Optional.empty();   // 가입하지 않은 이메일: 발급하지 않는다. 응답은 같은 202
        }
        OneTimeTokens.Issued token = OneTimeTokens.issue();
        AccountChallenge challenge = AccountChallenge.passwordReset(member.getId(), email, token.hash(),
                member.getAuthVersion(), now, now.plus(TOKEN_TTL));
        challenges.saveAndFlush(challenge);
        // 토큰 원문은 이 메일 객체(메모리)에만 있다. DB·로그에 남기지 않는다.
        return Optional.of(mailContent.build(challenge.getId(), email, token.value(), TOKEN_TTL));
    }

    /**
     * POST /api/auth/password-resets. 토큰이 틀림·만료·사용됨·버전 불일치면 모두 같은 400 PASSWORD_RESET_TOKEN_INVALID.
     * 같은 토큰이 동시에 두 번 오면 회원 잠금에서 한 줄로 서고, 늦은 쪽은 "이미 사용됨"을 보고 400을 받는다.
     * newPassword 형식은 DTO(PasswordRules)가 먼저 검사한다.
     */
    @Transactional
    public void resetPassword(String token, String newPassword) {
        byte[] tokenHash = OneTimeTokens.hashOf(token).orElseThrow(() -> rejected("MALFORMED", null));

        // 1) 잠그지 않고 회원 ID만 찾는다 → 2) 회원 잠금 → 3) 토큰 잠금. 잠금 순서: 회원 → 토큰(3인 설계 7.1).
        Long memberId = challenges.findMemberIdByTokenHash(tokenHash).orElseThrow(() -> rejected("UNKNOWN", null));
        Member member = members.findByIdForUpdate(memberId).orElseThrow(() -> rejected("MEMBER_MISSING", null));
        AccountChallenge challenge = challenges.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> rejected("UNKNOWN", null));

        // 잠근 뒤의 최신 값으로 다시 확인한다. 잠그기 전에 확인하면 동시 요청 두 건이 모두 통과할 수 있다.
        LocalDateTime now = now();
        String rejection = challenge.passwordResetRejection(member.getId(), member.getAuthVersion(), now);
        if (rejection != null) {
            throw rejected(rejection, challenge.getId());
        }

        member.changePassword(passwordEncoder.encode(newPassword), now);   // 해시 변경 + auth_version + 1
        challenge.consume(now);
        // 트랜잭션 종료 시 두 행을 함께 UPDATE한다. 하나라도 실패하면 둘 다 롤백된다.
    }

    private LocalDateTime now() {
        return UtcTimes.nowMicros(clock);
    }

    /** 내부 원인은 안전한 코드로 로그에만 남기고(토큰 X), 응답은 하나로 통일한다. */
    private static BusinessException rejected(String reason, Long challengeId) {
        log.info("password reset rejected reason={} challengeId={}", reason, challengeId);
        return new BusinessException(HttpStatus.BAD_REQUEST, "PASSWORD_RESET_TOKEN_INVALID",
                "유효하지 않거나 만료된 링크입니다. 비밀번호 재설정을 다시 요청해 주세요.");
    }

    private static BusinessException invalidEmail() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력값을 확인해 주세요.");
    }
}
