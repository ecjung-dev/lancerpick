package com.example.lancerpick.auth.service;

import com.example.lancerpick.auth.entity.AccountChallenge;
import com.example.lancerpick.auth.mail.AccountMail;
import com.example.lancerpick.auth.mail.AccountMailDispatcher;
import com.example.lancerpick.auth.mail.ExistingEmailMailContent;
import com.example.lancerpick.auth.repository.AccountChallengeRepository;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.time.UtcTimes;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 기존 회원 이메일 인증 전환(K1, 3인 설계 §5.2). 이메일 인증 없이 가입한 회원이 로그인한 뒤 자기 주소를 인증한다.
 * 검증 증거 없이 email_verified_at을 채우지 않으며, 다른 계정과 합치거나 회원·지갑을 새로 만들지 않는다.
 *
 * 요청(requestVerification) — 로그인한 회원 본인 주소로만 보낸다.
 *  이미 인증됨 409 → 요청 제한 행 준비(별도 커밋) → 짧은 트랜잭션: 제한 확인 → 회원 잠금·재확인 → 토큰 해시 저장
 *  → 커밋 뒤 발송기에 넘김. 제한 초과면 발급하지 않고 같은 202(화면이 60초 잠금으로 안내).
 *
 * 확인(verify) — 한 트랜잭션. 로그인한 회원 잠금 → 토큰 행 잠금(비밀번호 재설정과 같은 순서)
 *  → 목적·회원·사용 여부·만료·발급 당시 auth_version·주소 재확인 → email_verified_at 기록 + 토큰 사용 처리.
 *  다른 계정으로 로그인한 상태에서 연 링크는 그 계정을 인증하지 않고 거절한다.
 */
@Service
@Profile("!demo")
public class ExistingEmailVerificationService {
    private static final Logger log = LoggerFactory.getLogger(ExistingEmailVerificationService.class);

    /** 링크 유효 시간(3인 설계 7.1, 가입 인증·재설정과 같은 15분). */
    static final Duration TOKEN_TTL = Duration.ofMinutes(15);
    /** 이메일별 최소 60초 간격, 1시간당 5회. scope는 30자 이하, 다른 메일 제한과 별도. */
    static final AuthRequestLimiter.Policy REQUEST_POLICY = new AuthRequestLimiter.Policy(
            "EXISTING_EMAIL_INTERVAL", Duration.ofSeconds(60), "EXISTING_EMAIL_HOUR", 5);
    private static final String HMAC_PURPOSE = "EXISTING_EMAIL";

    private final MemberRepository members;
    private final AccountChallengeRepository challenges;
    private final AuthRequestLimiter limiter;
    private final AuthSubjectHasher hasher;
    private final ExistingEmailMailContent mailContent;
    private final AccountMailDispatcher mailDispatcher;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public ExistingEmailVerificationService(MemberRepository members, AccountChallengeRepository challenges,
            AuthRequestLimiter limiter, AuthSubjectHasher hasher, ExistingEmailMailContent mailContent,
            AccountMailDispatcher mailDispatcher, Clock clock, PlatformTransactionManager transactionManager) {
        this.members = members;
        this.challenges = challenges;
        this.limiter = limiter;
        this.hasher = hasher;
        this.mailContent = mailContent;
        this.mailDispatcher = mailDispatcher;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** POST /api/me/email-verification-requests. 트랜잭션 밖에서 호출된다(메일은 커밋 뒤에 넘긴다). */
    public void requestVerification(long memberId) {
        Member current = members.findById(memberId).orElseThrow(ExistingEmailVerificationService::authenticationRequired);
        if (current.getEmailVerifiedAt() != null) throw alreadyVerified();
        String email = current.getEmail();
        byte[] subject = hasher.subject(HMAC_PURPOSE, email);
        LocalDateTime now = now();

        limiter.prepare(REQUEST_POLICY, subject, now);   // 별도 커밋
        Optional<AccountMail> mail = transaction.execute(status -> {
            if (!limiter.tryAcquire(REQUEST_POLICY, subject, now)) return Optional.empty();   // 제한 초과: 같은 202
            // 잠근 뒤 다시 확인한다. 그사이 다른 탭에서 인증을 끝냈으면 새 링크를 만들지 않는다.
            Member member = members.findByIdForUpdate(memberId).orElseThrow(ExistingEmailVerificationService::authenticationRequired);
            if (member.getEmailVerifiedAt() != null) return Optional.empty();
            OneTimeTokens.Issued token = OneTimeTokens.issue();
            AccountChallenge challenge = AccountChallenge.existingEmail(member.getId(), member.getEmail(), token.hash(),
                    member.getAuthVersion(), now, now.plus(TOKEN_TTL));
            challenges.saveAndFlush(challenge);
            // 토큰 원문은 이 메일 객체(메모리)에만 있다. DB·로그에 남기지 않는다.
            return Optional.of(mailContent.build(challenge.getId(), member.getEmail(), token.value(), TOKEN_TTL));
        });
        mail.ifPresent(mailDispatcher::dispatch);   // 발송 실패는 응답에 드러내지 않는다
    }

    /**
     * POST /api/me/email-verifications. 토큰이 틀림·만료·사용됨·다른 계정·발급 후 비밀번호 변경이면 모두 같은 400.
     * 같은 토큰이 동시에 두 번 오면 회원 잠금에서 한 줄로 서고, 늦은 쪽은 "이미 사용됨"으로 400을 받는다.
     */
    @Transactional
    public void verify(long memberId, String token) {
        byte[] tokenHash = OneTimeTokens.hashOf(token).orElseThrow(() -> invalid("MALFORMED", null));
        Member member = members.findByIdForUpdate(memberId).orElseThrow(ExistingEmailVerificationService::authenticationRequired);
        AccountChallenge challenge = challenges.findByTokenHashForUpdate(tokenHash).orElseThrow(() -> invalid("UNKNOWN", null));

        LocalDateTime now = now();
        String rejection = challenge.existingEmailRejection(member.getId(), member.getAuthVersion(), member.getEmail(), now);
        if (rejection != null) throw invalid(rejection, challenge.getId());

        member.verifyEmail(now);   // 이미 인증됐으면 처음 시각 유지
        challenge.consume(now);
    }

    private LocalDateTime now() {
        return UtcTimes.nowMicros(clock);
    }

    /** 원인은 안전한 코드로 로그에만 남기고(토큰 X), 응답은 가입 인증과 같은 코드 하나로 통일한다. */
    private static BusinessException invalid(String reason, Long challengeId) {
        log.info("existing email verification rejected reason={} challengeId={}", reason, challengeId);
        return new BusinessException(HttpStatus.BAD_REQUEST, "EMAIL_VERIFICATION_INVALID",
                "이메일 인증 링크가 만료됐거나 유효하지 않습니다. 인증 메일을 다시 요청해 주세요.");
    }

    private static BusinessException alreadyVerified() {
        return new BusinessException(HttpStatus.CONFLICT, "EMAIL_ALREADY_VERIFIED", "이미 인증된 이메일입니다.");
    }

    private static BusinessException authenticationRequired() {
        return new BusinessException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
    }
}
