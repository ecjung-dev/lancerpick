package com.example.lancerpick.notification.service;

import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.member.service.MemberChangePolicy;
import com.example.lancerpick.member.service.PasswordRecommendationQuery;
import com.example.lancerpick.notification.entity.OutboxEvent;
import com.example.lancerpick.notification.repository.OutboxEventRepository;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TreeMap;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * 비밀번호 변경 권고 알림(U6). 스케줄러·전체 회원 스캔 없이 로그인한 회원에게만 권고한다.
 *
 * <p>회차는 권고 기준일(가입·비밀번호 변경/재설정 시각, 기존 회원은 V11 도입 시각)부터 한국 달력 6개월 구간이 몇 번 지났는지로
 * 정한다. 이벤트 ID를 (회원, 기준일, 회차)의 이름 기반 UUID로 정해 같은 회차는 아웃박스 event_id UNIQUE로 한 번만 저장된다.
 * 오래 로그인하지 않았다가 들어오면 현재 회차 1건만 생긴다. 비밀번호를 바꾸면 기준일이 바뀌어 새 회차 계산이 시작된다.
 */
@Service
@Profile("!demo")
public class PasswordChangeRecommendations {
  public static final String AGGREGATE_MEMBER = "MEMBER";
  /** MEMBER 집계 페이로드 버전. 의미를 바꾸면 올리고 {@link OutboxDispatcher}가 이전 버전도 읽게 한다. */
  public static final int MEMBER_SCHEMA_VERSION = 1;

  public static final String PASSWORD_CHANGE_RECOMMENDED = "PASSWORD_CHANGE_RECOMMENDED";
  private static final DateTimeFormatter BASIS_FORMAT =
      DateTimeFormatter.ofPattern("uuuu-MM-dd'T'HH:mm:ss.SSSSSS");
  private static final Logger log = LoggerFactory.getLogger(PasswordChangeRecommendations.class);

  private final PasswordRecommendationQuery basis;
  private final MemberRepository members;
  private final OutboxEventRepository events;
  private final JsonMapper mapper;
  private final Clock clock;
  private final TransactionTemplate tx;

  public PasswordChangeRecommendations(
      PasswordRecommendationQuery basis,
      MemberRepository members,
      OutboxEventRepository events,
      JsonMapper mapper,
      Clock clock,
      PlatformTransactionManager transactions) {
    this.basis = basis;
    this.members = members;
    this.events = events;
    this.mapper = mapper;
    this.clock = clock;
    this.tx = new TransactionTemplate(transactions);
    tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  /**
   * 로그인 성공 뒤 호출한다. 권고는 부가 기능이라 실패해도 예외를 던지지 않고 로그만 남긴다(로그인 응답은 성공 유지).
   * 로그인 트랜잭션·세션 저장이 끝난 뒤 불러야 커밋 실패와 세션 성공이 어긋나지 않는다.
   */
  public void issueAfterLogin(long memberId) {
    try {
      issueIfDue(memberId);
    } catch (RuntimeException exception) {
      // 회원 ID와 예외 유형만 남긴다.
      log.warn(
          "Password change recommendation for member {} failed: {}",
          memberId,
          exception.getClass().getSimpleName());
    }
  }

  /** 현재 회차 권고가 아직 없으면 아웃박스 이벤트를 저장하고 true를 반환한다. 대상이 아니거나 이미 있으면 false. */
  public boolean issueIfDue(long memberId) {
    var now = now();
    // 대부분의 로그인은 권고 시점 전이다. 잠금 없는 조회로 먼저 거르고, 대상일 때만 회원 행을 잠근다.
    var current = basis.find(memberId).orElse(null);
    if (current == null || current.firstRecommendedAt().isAfter(now)) return false;
    // 비밀번호를 바꾸지 않은 회원은 계속 대상이라, 이번 회차가 이미 저장됐으면 잠그지 않고 끝낸다(잠금 아래에서 다시 확인).
    long round = MemberChangePolicy.elapsedSixMonthPeriods(current.basisAt(), now);
    if (events.existsByEventId(eventId(memberId, current.basisAt(), round))) return false;
    return Boolean.TRUE.equals(tx.execute(status -> issueLocked(memberId, now)));
  }

  private boolean issueLocked(long memberId, LocalDateTime now) {
    // 비밀번호 변경·재설정과 같은 회원 배타 잠금. 잠금 뒤 기준일을 다시 읽어 직전에 바뀐 비밀번호의 옛 기준으로 권고하지 않는다.
    var member = members.findByIdForUpdate(memberId).orElse(null);
    if (member == null) return false;
    var basisAt = member.getPasswordRecommendationBaseAt();
    long round = MemberChangePolicy.elapsedSixMonthPeriods(basisAt, now);
    if (round < 1) return false;
    String eventId = eventId(memberId, basisAt, round);
    // 디스패치 전 재로그인도 같은 eventId라 여기서 걸러진다. 최종 방어는 uk_outbox_event.
    if (events.existsByEventId(eventId)) return false;
    var payload = new TreeMap<String, Object>();
    payload.put("memberId", String.valueOf(memberId));
    payload.put("round", round);
    payload.put("basisAt", basisAt.atOffset(ZoneOffset.UTC).toString());
    payload.put("recipientIds", List.of(String.valueOf(memberId)));
    events.saveAndFlush(
        OutboxEvent.pending(
            eventId,
            PASSWORD_CHANGE_RECOMMENDED,
            MEMBER_SCHEMA_VERSION,
            AGGREGATE_MEMBER,
            memberId,
            mapper.writeValueAsString(payload),
            now));
    return true;
  }

  /** 같은 회원·기준일·회차는 항상 같은 ID. 구성 문자열을 바꾸면 이미 저장된 권고와 중복되므로 바꾸지 않는다. */
  static String eventId(long memberId, LocalDateTime basisAt, long round) {
    String source =
        PASSWORD_CHANGE_RECOMMENDED
            + ":"
            + memberId
            + ":"
            + basisAt.truncatedTo(ChronoUnit.MICROS).format(BASIS_FORMAT)
            + ":"
            + round;
    return UUID.nameUUIDFromBytes(source.getBytes(StandardCharsets.UTF_8)).toString();
  }

  private LocalDateTime now() {
    return UtcTimes.nowMicros(clock);
  }
}
