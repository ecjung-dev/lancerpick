package com.example.lancerpick.admin.service;

import com.example.lancerpick.admin.dto.AdminActionReceipt;
import com.example.lancerpick.admin.entity.AdminAction;
import com.example.lancerpick.admin.service.AdminActionAuditService.Outcome;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.contract.service.ContractDocumentService;
import com.example.lancerpick.wallet.service.AdminTopupRecheckService;
import java.util.UUID;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 관리자 Controller 연결 입구. 감사 커밋 → 업무 실행 → 결과 커밋을 순서대로 조율한다. */
@Service
@Profile("!demo")
@Transactional(propagation = Propagation.NEVER)
public class AdminReprocessingService {
  public record Result(AdminActionReceipt receipt, boolean replay) {}

  private final AdminActionAuditService audit;
  private final AdminTopupRecheckService topups;
  private final AdminBatchRestartService batch;
  private final ContractDocumentService documents;

  public AdminReprocessingService(
      AdminActionAuditService audit,
      AdminTopupRecheckService topups,
      AdminBatchRestartService batch,
      ContractDocumentService documents) {
    this.audit = audit;
    this.topups = topups;
    this.batch = batch;
    this.documents = documents;
  }

  public Result recheckTopup(long adminId, long topupId, String reason, UUID requestKey) {
    return execute(
        adminId,
        AdminAction.TOPUP_RECHECK,
        topupId,
        reason,
        requestKey,
        id -> topups.recheck(adminId, id, topupId));
  }

  public Result restartTopupRecovery(
      long adminId, long executionId, String reason, UUID requestKey) {
    return execute(
        adminId,
        AdminAction.TOPUP_RECOVERY_RESTART,
        executionId,
        reason,
        requestKey,
        id -> batch.restart(adminId, id, executionId));
  }

  /** contractId는 특정 계약 버전의 PK다. API에서 주문/버전을 쓰면 먼저 해당 계약 ID로 해석한다. */
  public Result regenerateContractPdf(
      long adminId, long contractId, String reason, UUID requestKey) {
    return execute(
        adminId,
        AdminAction.CONTRACT_PDF_REGENERATE,
        contractId,
        reason,
        requestKey,
        id -> {
          documents.generateForAdmin(adminId, id, contractId);
          return Outcome.PDF_GENERATED;
        });
  }

  private Result execute(
      long adminId, AdminAction action, long targetId, String reason, UUID requestKey, Work work) {
    var start = audit.begin(adminId, action, targetId, reason, requestKey);
    if (!start.execute()) return new Result(start.receipt(), true);
    long logId = Long.parseLong(start.receipt().id());
    Outcome result;
    try {
      result = work.run(logId);
    } catch (BusinessException rejected) {
      if (action == AdminAction.CONTRACT_PDF_REGENERATE
          && "CONTRACT_GENERATION_FAILED".equals(rejected.code()))
        result = Outcome.PDF_GENERATION_FAILED;
      else if (rejected.status().is4xxClientError())
        result =
            switch (action) {
              case TOPUP_RECHECK -> Outcome.TOPUP_STATE_CHANGED;
              case TOPUP_RECOVERY_RESTART -> Outcome.BATCH_RESTART_REJECTED;
              case CONTRACT_PDF_REGENERATE -> Outcome.PDF_STATE_CHANGED;
            };
      else result = unknown(action);
    } catch (JobExecutionAlreadyRunningException
        | JobInstanceAlreadyCompleteException
        | JobRestartException
        | InvalidJobParametersException rejectedRestart) {
      result = Outcome.BATCH_RESTART_REJECTED;
    } catch (Exception uncertain) {
      // 원문을 응답/감사에 저장하지 않는다. 커밋이나 재시작 접수 여부가 불명확할 수 있다.
      result = unknown(action);
    }
    // 이 커밋 자체의 실패를 업무 실패로 다시 기록하지 않는다. 같은 키는 재실행 없이 기존 이력을 반환한다.
    return new Result(audit.complete(logId, result), false);
  }

  private Outcome unknown(AdminAction action) {
    return switch (action) {
      case TOPUP_RECHECK -> Outcome.TOPUP_RESULT_UNCONFIRMED;
      case TOPUP_RECOVERY_RESTART -> Outcome.BATCH_RESULT_UNCONFIRMED;
      case CONTRACT_PDF_REGENERATE -> Outcome.PDF_RESULT_UNCONFIRMED;
    };
  }

  @FunctionalInterface
  private interface Work {
    Outcome run(long logId) throws Exception;
  }
}
