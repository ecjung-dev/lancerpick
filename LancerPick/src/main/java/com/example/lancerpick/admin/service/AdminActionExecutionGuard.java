package com.example.lancerpick.admin.service;

import com.example.lancerpick.admin.entity.AdminAction;
import com.example.lancerpick.admin.entity.AdminActionStatus;
import com.example.lancerpick.admin.repository.AdminActionLogRepository;
import com.example.lancerpick.common.exception.BusinessException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 업무 claim 트랜잭션에서 회원 → 감사 요청 → 업무 대상 순서로 잠근다. */
@Component
@Profile("!demo")
@Transactional(propagation = Propagation.MANDATORY)
public class AdminActionExecutionGuard {
  private final AdminAuthorization authorization;
  private final AdminActionLogRepository logs;
  private final Clock clock;

  public AdminActionExecutionGuard(
      AdminAuthorization authorization, AdminActionLogRepository logs, Clock clock) {
    this.authorization = authorization;
    this.logs = logs;
    this.clock = clock;
  }

  public void require(long adminId, long logId, AdminAction action, long targetId) {
    authorization.lockAdmin(adminId);
    var log = logs.findByIdForUpdate(logId).orElseThrow(() -> rejected());
    if (log.getAdminId() != adminId
        || log.getAction() != action
        || log.getTargetId() != targetId
        || log.getStatus() != AdminActionStatus.REQUESTED
        || !log.getLeaseUntil().isAfter(LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))) {
      throw rejected();
    }
  }

  private BusinessException rejected() {
    return new BusinessException(
        HttpStatus.CONFLICT,
        "ADMIN_ACTION_EXECUTION_STALE",
        "유효한 재처리 요청이 아닙니다. 작업 이력과 대상 상태를 다시 확인해 주세요.");
  }
}
