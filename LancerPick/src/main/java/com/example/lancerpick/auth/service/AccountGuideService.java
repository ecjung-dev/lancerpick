package com.example.lancerpick.auth.service;

import com.example.lancerpick.auth.mail.AccountGuideMailContent;
import com.example.lancerpick.auth.mail.AccountMail;
import com.example.lancerpick.auth.mail.AccountMailDispatcher;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.member.dto.EmailRules;
import com.example.lancerpick.member.repository.MemberRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 아이디 안내(K1, 3인 설계 1절: 아이디 찾기는 "이메일이 아이디"라는 안내로 한정).
 * 입력한 주소가 가입한 이메일이면 그 주소로만 안내 메일을 보낸다. 다른 주소·화면에 가입 여부를 알려 주지 않는다.
 * 잊어버린 이메일을 찾아 주는 기능이 아니다(표시 이름으로 이메일 찾기·마스킹 목록 없음).
 *
 * 흐름은 비밀번호 재설정 요청(PasswordResetService.requestReset)과 같다.
 *  1) 이메일 정규화. 형식이 틀리면 400(가입 여부와 무관한 입력 오류).
 *  2) 요청 제한 행 준비(별도 커밋) → 짧은 트랜잭션: 제한 확인 → 회원 조회. 토큰·DB 기록은 만들지 않는다.
 *  3) 커밋 뒤 트랜잭션 밖에서 발송기에 넘긴다. 결과는 DB에 남지 않는다(AccountMail.untracked).
 * 가입 여부·제한 초과·발송 실패와 상관없이 호출한 쪽은 같은 202를 돌려준다.
 */
@Service
@Profile("!demo")
public class AccountGuideService {
    /** 이메일별 최소 60초 간격, 1시간당 5회(재설정과 같은 값, scope는 30자 이하·재설정과 별도). */
    static final AuthRequestLimiter.Policy REQUEST_POLICY = new AuthRequestLimiter.Policy(
            "ACCOUNT_GUIDE_EMAIL_INTERVAL", Duration.ofSeconds(60), "ACCOUNT_GUIDE_EMAIL_HOUR", 5);
    private static final String HMAC_PURPOSE = "ACCOUNT_GUIDE";

    private final MemberRepository members;
    private final AuthRequestLimiter limiter;
    private final AuthSubjectHasher hasher;
    private final AccountGuideMailContent mailContent;
    private final AccountMailDispatcher mailDispatcher;
    private final Clock clock;
    private final TransactionTemplate transaction;

    public AccountGuideService(MemberRepository members, AuthRequestLimiter limiter, AuthSubjectHasher hasher,
            AccountGuideMailContent mailContent, AccountMailDispatcher mailDispatcher, Clock clock,
            PlatformTransactionManager transactionManager) {
        this.members = members;
        this.limiter = limiter;
        this.hasher = hasher;
        this.mailContent = mailContent;
        this.mailDispatcher = mailDispatcher;
        this.clock = clock;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    /** POST /api/auth/account-guide-requests. 트랜잭션 밖에서 호출된다(메일은 커밋 뒤에 넘긴다). */
    public void requestGuide(String rawEmail) {
        String email = EmailRules.normalize(rawEmail).orElseThrow(AccountGuideService::invalidEmail);
        byte[] subject = hasher.subject(HMAC_PURPOSE, email);
        LocalDateTime now = UtcTimes.nowMicros(clock);

        limiter.prepare(REQUEST_POLICY, subject, now);   // 별도 커밋
        Optional<AccountMail> mail = transaction.execute(status -> {
            if (!limiter.tryAcquire(REQUEST_POLICY, subject, now)) return Optional.empty();   // 제한 초과: 보내지 않음
            // 가입하지 않은 이메일이면 보내지 않는다. 응답은 같다.
            return members.findByEmail(email).map(member -> mailContent.build(email));
        });
        // 발송 실패는 응답에 드러내지 않는다(dispatch는 예외를 던지지 않음).
        mail.ifPresent(mailDispatcher::dispatch);
    }

    private static BusinessException invalidEmail() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력값을 확인해 주세요.");
    }
}
