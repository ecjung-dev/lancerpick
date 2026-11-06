package com.example.lancerpick.auth.service;

import com.example.lancerpick.auth.entity.AuthRequestLimit;
import com.example.lancerpick.auth.repository.AuthRequestLimitRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 인증 메일 요청 제한(3인 설계 7.1·7.3): 같은 목적+이메일에 최소 간격, 1시간 고정 구간당 최대 횟수.
 * MySQL의 짧은 트랜잭션·UNIQUE·행 잠금으로 센다. 가입하지 않은 이메일에도 같은 제한을 적용한다(존재 여부와 분리).
 *
 * auth_request_limits에 행을 두 개 쓴다(DB 변경 없음).
 * 1) 간격 행: window_start는 고정값(INTERVAL_ANCHOR), expires_at = 마지막 허용 시각 + 간격.
 *    이메일마다 행이 하나뿐이라 "최소 60초"를 구간 경계와 상관없이 정확히 지킨다.
 * 2) 1시간 행: window_start = 지금을 시(hour) 단위로 자른 값, request_count = 그 구간에 허용한 수.
 *    고정 구간이라 59분·61분처럼 두 구간에 걸치면 짧은 시간에 한도의 두 배까지 허용될 수 있다(간격 행이 최소 60초는 보장).
 *
 * 사용 순서: prepare(트랜잭션 밖에서 호출, 행 생성 후 커밋) → 업무 트랜잭션 안에서 tryAcquire.
 * 잠금 순서는 항상 간격 행 → 1시간 행이다. 간격 행이 이메일마다 하나라 같은 이메일의 요청은 여기서 한 줄로 선다.
 */
@Service
@Profile("!demo")
public class AuthRequestLimiter {
    /** 간격 행의 고정 window_start. 실제 시각이 아니라 "이메일마다 한 행"을 만들기 위한 값이다. */
    static final LocalDateTime INTERVAL_ANCHOR = LocalDateTime.of(2000, 1, 1, 0, 0);

    /** intervalScope·hourlyScope는 auth_request_limits.scope(최대 30자). */
    public record Policy(String intervalScope, Duration minInterval, String hourlyScope, int hourlyLimit) {
    }

    private static final Logger log = LoggerFactory.getLogger(AuthRequestLimiter.class);
    private static final int PREPARE_ATTEMPTS = 3;

    private final AuthRequestLimitRepository limits;
    private final TransactionTemplate separateTransaction;

    public AuthRequestLimiter(AuthRequestLimitRepository limits, PlatformTransactionManager transactionManager) {
        this.limits = limits;
        this.separateTransaction = new TransactionTemplate(transactionManager);
        this.separateTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 제한 행이 없으면 만들고 바로 커밋한다(별도 트랜잭션). 이미 있으면 아무것도 하지 않는다.
     * 행 생성을 업무 트랜잭션과 나누면, 처음 들어온 동시 요청들도 이미 있는 행을 잠그는 단순한 경로만 탄다.
     * 다른 트랜잭션 안에서 부르면 DB 연결을 두 개 잡으므로, 트랜잭션 밖에서만 호출한다.
     *
     * 같은 새 이메일로 요청이 동시에 처음 들어오면 MySQL이 같은 키의 "insert … on duplicate key update"끼리
     * 교착 상태(deadlock)로 판단해 한쪽을 롤백할 수 있다. 행 만들기는 몇 번 해도 결과가 같고(이미 있으면 아무것도 안 함),
     * 다시 시도할 때는 행이 이미 있어 같은 교착이 생기지 않으므로 교착 때만 최대 3번 시도한다.
     * 회원 조회 전 단계라 가입 여부와 무관하다(비밀번호 재설정·가입 인증 요청이 함께 쓴다).
     */
    public void prepare(Policy policy, byte[] subject, LocalDateTime now) {
        for (int attempt = 1; ; attempt++) {
            try {
                separateTransaction.executeWithoutResult(status -> insertRows(policy, subject, now));
                return;
            } catch (PessimisticLockingFailureException e) {
                if (attempt >= PREPARE_ATTEMPTS) {
                    throw e;
                }
                log.info("auth request limit row creation retried attempt={}", attempt);
            }
        }
    }

    private void insertRows(Policy policy, byte[] subject, LocalDateTime now) {
        // 처음 만든 간격 행은 이미 지난 시각으로 만료돼 있어 첫 요청은 통과한다(CHECK expires_at > window_start).
        limits.insertIfAbsent(policy.intervalScope(), subject, INTERVAL_ANCHOR, INTERVAL_ANCHOR.plusSeconds(1));
        LocalDateTime hourStart = hourStart(now);
        limits.insertIfAbsent(policy.hourlyScope(), subject, hourStart, hourStart.plusHours(1));
    }

    /**
     * 허용되면 두 행에 1회를 기록하고 true. 막히면 아무것도 바꾸지 않고 false.
     * 호출한 업무 트랜잭션에 참여한다(그 트랜잭션이 롤백되면 기록도 함께 취소된다).
     * now는 prepare에 넘긴 값과 같아야 한다(같은 1시간 구간을 가리키도록).
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean tryAcquire(Policy policy, byte[] subject, LocalDateTime now) {
        AuthRequestLimit interval = lock(policy.intervalScope(), subject, INTERVAL_ANCHOR);
        AuthRequestLimit hourly = lock(policy.hourlyScope(), subject, hourStart(now));

        if (interval.isCoolingDownAt(now) || hourly.isFull(policy.hourlyLimit())) {
            return false;
        }
        interval.recordIntervalUse(now, policy.minInterval());
        hourly.recordWindowUse();
        return true;   // 트랜잭션 종료 시 변경 감지로 UPDATE
    }

    private AuthRequestLimit lock(String scope, byte[] subject, LocalDateTime windowStart) {
        return limits.findForUpdate(scope, subject, windowStart)
                .orElseThrow(() -> new IllegalStateException("auth request limit row was not prepared: " + scope));
    }

    private static LocalDateTime hourStart(LocalDateTime now) {
        return now.truncatedTo(ChronoUnit.HOURS);
    }
}
