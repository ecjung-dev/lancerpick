package com.example.lancerpick.admin.service;

import com.example.lancerpick.admin.entity.AdminAction;
import com.example.lancerpick.admin.service.AdminActionAuditService.Outcome;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.wallet.service.TopupRecoveryJobService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/** 후보 재검사 트랜잭션을 끝낸 다음 Spring Batch의 재시작/중복 실행 검사를 사용한다. */
@Service
@Profile("!demo")
@Transactional(propagation = Propagation.NEVER)
public class AdminBatchRestartService {
  private final AdminActionExecutionGuard guard;
  private final AdminFinanceQueryService finance;
  private final ObjectProvider<TopupRecoveryJobService> jobs;
  private final TransactionTemplate transaction;

  public AdminBatchRestartService(
      AdminActionExecutionGuard guard,
      AdminFinanceQueryService finance,
      ObjectProvider<TopupRecoveryJobService> jobs,
      PlatformTransactionManager manager) {
    this.guard = guard;
    this.finance = finance;
    this.jobs = jobs;
    transaction = new TransactionTemplate(manager);
    transaction.setIsolationLevel(
        org.springframework.transaction.TransactionDefinition.ISOLATION_READ_COMMITTED);
  }

  public Outcome restart(long adminId, long logId, long executionId) throws Exception {
    transaction.executeWithoutResult(ignored -> {
      guard.require(adminId, logId, AdminAction.TOPUP_RECOVERY_RESTART, executionId);
      if (!finance.recoveryJob(adminId, executionId).restart().eligible())
        throw new BusinessException(
            HttpStatus.CONFLICT, "ADMIN_ACTION_NOT_ELIGIBLE", "현재 재시작할 수 없는 작업입니다.");
    });
    var service = jobs.getIfAvailable();
    if (service == null) return Outcome.BATCH_RESTART_REJECTED;
    service.restart(executionId);
    // Job 종료/지급 완료가 아닌 재시작 호출 수락 결과다. 최신 실행 상태는 기존 조회 API로 확인한다.
    return Outcome.BATCH_RESTART_ACCEPTED;
  }
}
