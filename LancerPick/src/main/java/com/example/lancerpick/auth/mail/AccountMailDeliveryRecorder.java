package com.example.lancerpick.auth.mail;

import com.example.lancerpick.auth.entity.ChallengeDeliveryStatus;
import com.example.lancerpick.auth.repository.AccountChallengeRepository;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계정 메일 발송 결과를 account_challenges.delivery_status에 기록한다. 발급 트랜잭션이 커밋된 뒤 별도 짧은 트랜잭션으로 실행한다.
 * PENDING/UNKNOWN만 확정하므로 늦은 실제 결과는 수용하되, SENT/FAILED는 덮어쓰지 않는다.
 * 오류 코드는 대문자·숫자·밑줄의 안전한 코드만 받는다(예외 메시지·메일 내용 저장 방지).
 */
@Service
@Profile("!demo")
public class AccountMailDeliveryRecorder {
    private static final Pattern SAFE_CODE = Pattern.compile("^[A-Z0-9_]{1,100}$");

    private final AccountChallengeRepository challenges;

    public AccountMailDeliveryRecorder(AccountChallengeRepository challenges) {
        this.challenges = challenges;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(long challengeId) {
        challenges.recordDelivery(challengeId, ChallengeDeliveryStatus.SENT, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(long challengeId, String errorCode) {
        String code = errorCode != null && SAFE_CODE.matcher(errorCode).matches() ? errorCode : "MAIL_FAILED";
        challenges.recordDelivery(challengeId, ChallengeDeliveryStatus.FAILED, code);
    }
}
