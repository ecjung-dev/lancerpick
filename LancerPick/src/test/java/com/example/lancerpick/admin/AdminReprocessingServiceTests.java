package com.example.lancerpick.admin;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.lancerpick.admin.dto.AdminActionReceipt;
import com.example.lancerpick.admin.entity.AdminAction;
import com.example.lancerpick.admin.service.*;
import com.example.lancerpick.admin.service.AdminActionAuditService.BeginResult;
import com.example.lancerpick.admin.service.AdminActionAuditService.Outcome;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.contract.service.ContractDocumentService;
import com.example.lancerpick.wallet.service.AdminTopupRecheckService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.http.HttpStatus;

class AdminReprocessingServiceTests {
  private final AdminActionAuditService audit = mock(AdminActionAuditService.class);
  private final AdminTopupRecheckService topups = mock(AdminTopupRecheckService.class);
  private final AdminBatchRestartService batch = mock(AdminBatchRestartService.class);
  private final ContractDocumentService documents = mock(ContractDocumentService.class);
  private final AdminReprocessingService service =
      new AdminReprocessingService(audit, topups, batch, documents);
  private final AdminActionReceipt receipt = mock(AdminActionReceipt.class);
  private final UUID key = UUID.randomUUID();

  @BeforeEach
  void start() {
    when(receipt.id()).thenReturn("3");
    when(audit.begin(eq(1L), any(), eq(2L), eq("사유"), eq(key)))
        .thenReturn(new BeginResult(receipt, true));
    when(audit.complete(anyLong(), any())).thenReturn(receipt);
  }

  @Test
  void unconfirmedReplayNeverExecutesAnyBusinessService() {
    when(audit.begin(1, AdminAction.TOPUP_RECHECK, 2, "사유", key))
        .thenReturn(new BeginResult(receipt, false));
    assertThat(service.recheckTopup(1, 2, "사유", key).replay()).isTrue();
    verifyNoInteractions(topups, batch, documents);
    verify(audit, never()).complete(anyLong(), any());
  }

  @Test
  void batchConclusiveRestartRejectionDiffersFromUnknownCommunicationOutcome() throws Exception {
    when(batch.restart(1, 3, 2)).thenThrow(new JobRestartException("private-batch-details"));
    service.restartTopupRecovery(1, 2, "사유", key);
    verify(audit).complete(3, Outcome.BATCH_RESTART_REJECTED);
  }

  @Test
  void uncertainBatchExceptionIsNotReportedAsDefiniteFailure() throws Exception {
    when(batch.restart(1, 3, 2)).thenThrow(new IllegalStateException("private-database-details"));
    service.restartTopupRecovery(1, 2, "사유", key);
    verify(audit).complete(3, Outcome.BATCH_RESULT_UNCONFIRMED);
  }

  @Test
  void pdfDefiniteRenderFailureAndUncertainCommitHaveDifferentResults() {
    doThrow(
            new BusinessException(
                HttpStatus.SERVICE_UNAVAILABLE, "CONTRACT_GENERATION_FAILED", "private"))
        .when(documents)
        .generateForAdmin(1, 3, 2);
    service.regenerateContractPdf(1, 2, "사유", key);
    verify(audit).complete(3, Outcome.PDF_GENERATION_FAILED);
    doThrow(new IllegalStateException("commit result unknown"))
        .when(documents)
        .generateForAdmin(1, 3, 2);
    service.regenerateContractPdf(1, 2, "사유", key);
    verify(audit).complete(3, Outcome.PDF_RESULT_UNCONFIRMED);
  }

  @Test
  void outcomeCommitFailureDoesNotExecuteOrRecordWorkAgain() {
    when(topups.recheck(1, 3, 2)).thenReturn(Outcome.TOPUP_CREDITED);
    when(audit.complete(3, Outcome.TOPUP_CREDITED))
        .thenThrow(new IllegalStateException("audit commit unavailable"));
    assertThatThrownBy(() -> service.recheckTopup(1, 2, "사유", key))
        .isInstanceOf(IllegalStateException.class);
    verify(topups, times(1)).recheck(1, 3, 2);
    verify(audit, times(1)).complete(anyLong(), any());
  }
}
