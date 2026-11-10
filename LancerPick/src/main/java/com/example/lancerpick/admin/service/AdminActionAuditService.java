package com.example.lancerpick.admin.service;

import com.example.lancerpick.admin.dto.AdminActionReceipt;
import com.example.lancerpick.admin.entity.AdminAction;
import com.example.lancerpick.admin.entity.AdminActionLog;
import com.example.lancerpick.admin.entity.AdminActionStatus;
import com.example.lancerpick.admin.repository.AdminActionLogRepository;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.member.entity.MemberRole;
import com.example.lancerpick.member.repository.MemberRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** K6 내부 경계. 요청 커밋 후 업무를 실행하고 결과를 별도 커밋한다. 외부 호출은 하지 않는다. */
@Service
@Profile("!demo")
@Transactional(propagation = Propagation.NEVER)
public class AdminActionAuditService {
  public record BeginResult(AdminActionReceipt receipt, boolean execute) {}

  /** 외부 예외 메시지를 결과 코드로 전달하지 못하도록 허용 결과를 고정한다. */
  public enum Outcome {
    SUCCEEDED(AdminActionStatus.SUCCEEDED),
    BUSINESS_REJECTED(AdminActionStatus.REJECTED),
    EXECUTION_FAILED(AdminActionStatus.FAILED),
    RESULT_UNCONFIRMED(AdminActionStatus.UNKNOWN),
    TOPUP_CREDITED(AdminActionStatus.SUCCEEDED),
    TOPUP_RESOLVED_FAILED(AdminActionStatus.SUCCEEDED),
    TOPUP_RESULT_UNCONFIRMED(AdminActionStatus.UNKNOWN),
    TOPUP_STATE_CHANGED(AdminActionStatus.REJECTED),
    BATCH_RESTART_ACCEPTED(AdminActionStatus.SUCCEEDED),
    BATCH_RESTART_REJECTED(AdminActionStatus.REJECTED),
    BATCH_RESULT_UNCONFIRMED(AdminActionStatus.UNKNOWN),
    PDF_GENERATED(AdminActionStatus.SUCCEEDED),
    PDF_GENERATION_FAILED(AdminActionStatus.FAILED),
    PDF_STATE_CHANGED(AdminActionStatus.REJECTED),
    PDF_RESULT_UNCONFIRMED(AdminActionStatus.UNKNOWN);

    private final AdminActionStatus status;

    Outcome(AdminActionStatus status) {
      this.status = status;
    }
  }

  private final AdminActionLogRepository logs;
  private final MemberRepository members;
  private final AdminActionEligibility eligibility;
  private final Clock clock;
  private final Duration pendingTimeout;
  private final TransactionTemplate transaction;

  public AdminActionAuditService(
      AdminActionLogRepository logs,
      MemberRepository members,
      AdminActionEligibility eligibility,
      Clock clock,
      PlatformTransactionManager manager,
      @Value("${lancerpick.admin.action-audit.pending-timeout:PT10M}") Duration pendingTimeout) {
    if (pendingTimeout.compareTo(Duration.ofMinutes(1)) < 0
        || pendingTimeout.compareTo(Duration.ofMinutes(30)) > 0) {
      throw new IllegalArgumentException("Audit pending timeout must be between 1 and 30 minutes");
    }
    this.logs = logs;
    this.members = members;
    this.eligibility = eligibility;
    this.clock = clock;
    this.pendingTimeout = pendingTimeout;
    transaction = new TransactionTemplate(manager);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  public BeginResult begin(
      long adminId, AdminAction action, long targetId, String reason, UUID requestKey) {
    String normalized = reason == null ? "" : reason.strip();
    if (action == null
        || targetId < 1
        || requestKey == null
        || normalized.isBlank()
        || normalized.codePointCount(0, normalized.length()) > 1000) {
      throw new BusinessException(
          HttpStatus.BAD_REQUEST, "INVALID_ADMIN_ACTION", "재처리 요청과 사유를 확인해 주세요.");
    }
    try {
      return transaction.execute(
          ignored -> request(adminId, action, targetId, normalized, requestKey));
    } catch (DataIntegrityViolationException collision) {
      // UNIQUE 충돌 트랜잭션은 폐기한다. 새 트랜잭션에서 승자의 요청/진행 상태를 다시 읽는다.
      // 같은 키 재전송은 replay, 다른 키의 동일 대상 실행은 409가 된다.
      return transaction.execute(
          ignored -> request(adminId, action, targetId, normalized, requestKey));
    }
  }

  private BeginResult request(
      long adminId, AdminAction action, long targetId, String reason, UUID requestKey) {
    requireAdmin(adminId);
    var replay = logs.findByAdminIdAndRequestKey(adminId, requestKey.toString());
    if (replay.isPresent()) {
      var log = replay.get();
      if (!log.matches(action, targetId, reason))
        throw conflict("ADMIN_ACTION_REQUEST_CONFLICT", "같은 요청 키에 다른 재처리 조건을 사용할 수 없습니다.");
      return new BeginResult(AdminActionReceipt.from(log), false);
    }
    LocalDateTime now = now();
    var pending = logs.findPendingForUpdate(action, targetId);
    if (pending.isPresent()) {
      var log = pending.get();
      // 최초 replay 조회 이후 다른 요청이 커밋했을 수도 있다(READ_COMMITTED).
      if (log.getAdminId() == adminId && log.getRequestKey().equals(requestKey.toString())) {
        if (!log.matches(action, targetId, reason))
          throw conflict("ADMIN_ACTION_REQUEST_CONFLICT", "같은 요청 키에 다른 재처리 조건을 사용할 수 없습니다.");
        return new BeginResult(AdminActionReceipt.from(log), false);
      }
      if (log.getLeaseUntil().isAfter(now))
        throw conflict("ADMIN_ACTION_IN_PROGRESS", "진행 중인 재처리 요청이 있습니다.");
      log.complete(AdminActionStatus.UNKNOWN, "LEASE_EXPIRED", now);
      logs.flush(); // 생성 열 UNIQUE를 먼저 해제한 뒤 새 요청을 삽입한다.
    }
    Long orderId = eligibility.requireEligible(adminId, action, targetId);
    var log =
        logs.saveAndFlush(
            AdminActionLog.request(
                adminId,
                action,
                targetId,
                orderId,
                reason,
                requestKey.toString(),
                now,
                now.plus(pendingTimeout)));
    return new BeginResult(AdminActionReceipt.from(log), true);
  }

  /** 신뢰한 내부 실행 조율자 전용. 관리자 요청 본문에서 outcome을 받아 호출하지 않는다. */
  public AdminActionReceipt complete(long logId, Outcome outcome) {
    if (outcome == null) throw new IllegalArgumentException("Outcome is required");
    return transaction.execute(ignored -> {
      var log =
          logs.findByIdForUpdate(logId)
              .orElseThrow(() ->
                  new BusinessException(
                      HttpStatus.NOT_FOUND, "ADMIN_ACTION_NOT_FOUND", "관리자 작업 이력을 찾을 수 없습니다."));
      log.complete(outcome.status, outcome.name(), now());
      return AdminActionReceipt.from(log);
    });
  }

  public AdminActionReceipt inspect(long adminId, long logId) {
    return transaction.execute(ignored -> {
      requireAdmin(adminId);
      return AdminActionReceipt.from(
          logs.findById(logId)
              .orElseThrow(() ->
                  new BusinessException(
                      HttpStatus.NOT_FOUND, "ADMIN_ACTION_NOT_FOUND", "관리자 작업 이력을 찾을 수 없습니다.")));
    });
  }

  private void requireAdmin(long adminId) {
    var member =
        members
            .findByIdForShare(adminId)
            .orElseThrow(() ->
                new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요합니다."));
    if (member.getRole() != MemberRole.ADMIN)
      throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "관리자 권한이 필요합니다.");
  }

  private LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
  }

  private BusinessException conflict(String code, String message) {
    return new BusinessException(HttpStatus.CONFLICT, code, message);
  }
}
