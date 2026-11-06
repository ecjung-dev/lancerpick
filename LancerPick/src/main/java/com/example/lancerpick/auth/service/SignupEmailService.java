package com.example.lancerpick.auth.service;

import com.example.lancerpick.auth.dto.SignupEmailVerificationResponse;
import com.example.lancerpick.auth.entity.AccountChallenge;
import com.example.lancerpick.auth.mail.AccountMail;
import com.example.lancerpick.auth.mail.AccountMailDispatcher;
import com.example.lancerpick.auth.mail.SignupEmailMailContent;
import com.example.lancerpick.auth.repository.AccountChallengeRepository;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.member.dto.EmailRules;
import com.example.lancerpick.member.repository.MemberRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
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
 * 가입 이메일 인증(3인 설계 §5.1, 4인 설계 §4 1번). 회원·지갑 생성은 MemberService.signup이 증명을 소비하며 한다.
 *
 * 요청(requestVerification) — 가입 여부와 상관없이 같은 결과(202)로 끝난다.
 *  이메일 정규화 → 요청 제한 행 준비(별도 커밋) → 업무 트랜잭션: 요청 제한 확인 → 메일 토큰 해시 저장 → 커밋 뒤 메일.
 *  이미 가입된 주소에도 같은 메일을 보낸다. 가입 여부는 메일을 받은 본인이 인증한 뒤에만 알려 준다(3인 설계 §5.2).
 *  비밀번호는 이 단계에서 받지도 저장하지도 않고, 회원·지갑·적립금도 만들지 않는다.
 *
 * 인증(verify) — 메일 링크 토큰을 잠그고 확인한 뒤, 같은 행을 가입 증명(proof)으로 바꾼다.
 *  링크 토큰은 그 순간 쓸 수 없게 되고, 증명 원문은 응답에만 담긴다(DB에는 해시만).
 */
@Service
@Profile("!demo")
public class SignupEmailService {
    private static final Logger log = LoggerFactory.getLogger(SignupEmailService.class);

    /** 메일 링크 유효 시간(3인 설계 7.1: 가입 인증 15분). */
    static final Duration LINK_TTL = Duration.ofMinutes(15);
    /** 인증한 뒤 비밀번호·표시 이름을 입력할 시간. 증명 만료는 인증 시점부터 센다(2026-11-06 결정). */
    static final Duration PROOF_TTL = Duration.ofMinutes(30);
    /** 이메일+목적별 최소 60초 간격, 1시간당 5회(3인 설계 7.1). 재설정과 따로 센다. scope는 30자 이하. */
    static final AuthRequestLimiter.Policy REQUEST_POLICY = new AuthRequestLimiter.Policy(
            "SIGNUP_EMAIL_INTERVAL", Duration.ofSeconds(60), "SIGNUP_EMAIL_HOUR", 5);
    private static final String HMAC_PURPOSE = "SIGNUP_EMAIL";

    private final MemberRepository members;
    private final AccountChallengeRepository challenges;
    private final AuthRequestLimiter limiter;
    private final AuthSubjectHasher hasher;
    private final SignupEmailMailContent mailContent;
    private final AccountMailDispatcher mailDispatcher;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public SignupEmailService(MemberRepository members, AccountChallengeRepository challenges,
            AuthRequestLimiter limiter, AuthSubjectHasher hasher, SignupEmailMailContent mailContent,
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

    /**
     * POST /api/auth/signup-email-requests. 트랜잭션 밖에서 호출된다(메일은 커밋 뒤에 넘긴다).
     * 정상 종료하면 컨트롤러는 항상 같은 202를 돌려준다. 이메일 형식 오류만 400이다.
     */
    public void requestVerification(String rawEmail) {
        String email = EmailRules.normalize(rawEmail).orElseThrow(SignupEmailService::invalidEmail);
        byte[] subject = hasher.subject(HMAC_PURPOSE, email);
        LocalDateTime now = now();

        limiter.prepare(REQUEST_POLICY, subject, now);
        Optional<AccountMail> mail = transaction.execute(status -> issue(email, subject, now));

        // 커밋이 끝난 뒤에만 여기 온다. 발송 실패는 응답에 드러내지 않는다(dispatch는 예외를 던지지 않음).
        mail.ifPresent(mailDispatcher::dispatch);
    }

    private Optional<AccountMail> issue(String email, byte[] subject, LocalDateTime now) {
        if (!limiter.tryAcquire(REQUEST_POLICY, subject, now)) {
            return Optional.empty();   // 제한 초과: 발급하지 않는다. 응답은 같은 202
        }
        OneTimeTokens.Issued token = OneTimeTokens.issue();
        AccountChallenge challenge = AccountChallenge.signupEmail(email, token.hash(), now, now.plus(LINK_TTL));
        challenges.saveAndFlush(challenge);
        // 토큰 원문은 이 메일 객체(메모리)에만 있다. DB·로그에 남기지 않는다.
        return Optional.of(mailContent.build(challenge.getId(), email, token.value(), LINK_TTL));
    }

    /**
     * POST /api/auth/signup-email-verifications. 메일 링크 토큰을 확인하고 가입 증명을 발급한다.
     * 틀림·만료·이미 인증·목적 다름은 모두 같은 400 SIGNUP_EMAIL_TOKEN_INVALID.
     * 같은 링크로 동시에 두 번 인증하면 행 잠금에서 한 줄로 서고, 늦은 쪽은 "이미 인증됨"으로 400을 받는다.
     */
    @Transactional
    public SignupEmailVerificationResponse verify(String token) {
        byte[] tokenHash = OneTimeTokens.hashOf(token).orElseThrow(() -> rejected("MALFORMED", null));
        AccountChallenge challenge = challenges.findByTokenHashForUpdate(tokenHash)
                .orElseThrow(() -> rejected("UNKNOWN", null));

        LocalDateTime now = now();
        String rejection = challenge.signupVerificationRejection(tokenHash, now);
        if (rejection != null) {
            throw rejected(rejection, challenge.getId());
        }

        OneTimeTokens.Issued proof = OneTimeTokens.issue();
        LocalDateTime proofExpiresAt = now.plus(PROOF_TTL);
        challenge.verifyForSignup(proof.hash(), now, proofExpiresAt);
        // 메일을 받은 본인임이 확인됐으므로 여기서만 가입 여부를 알려 준다(3인 설계 §5.2). 가입 API도 409로 한 번 더 막는다.
        boolean alreadyRegistered = members.existsByEmail(challenge.getEmail());
        return new SignupEmailVerificationResponse(challenge.getEmail(), proof.value(),
                proofExpiresAt.atOffset(ZoneOffset.UTC), alreadyRegistered);
    }

    private LocalDateTime now() {
        return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    /** 내부 원인은 안전한 코드로 로그에만 남기고(토큰 X), 응답은 하나로 통일한다. */
    private static BusinessException rejected(String reason, Long challengeId) {
        log.info("signup email verification rejected reason={} challengeId={}", reason, challengeId);
        return new BusinessException(HttpStatus.BAD_REQUEST, "SIGNUP_EMAIL_TOKEN_INVALID",
                "유효하지 않거나 만료된 인증 링크입니다. 인증 메일을 다시 요청해 주세요.");
    }

    private static BusinessException invalidEmail() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력값을 확인해 주세요.");
    }
}
