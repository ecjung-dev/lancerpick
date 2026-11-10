package com.example.lancerpick.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.lancerpick.admin.dto.AdminContractPdfResponse;
import com.example.lancerpick.admin.dto.AdminContractPdfResponse.GenerationCandidate;
import com.example.lancerpick.admin.dto.AdminContractPdfResponse.GenerationKind;
import com.example.lancerpick.admin.dto.AdminFinanceResponses;
import com.example.lancerpick.admin.dto.AdminFinanceResponses.BlockReason;
import com.example.lancerpick.admin.dto.AdminFinanceResponses.Decision;
import com.example.lancerpick.admin.entity.AdminAction;
import com.example.lancerpick.admin.service.AdminActionEligibility;
import com.example.lancerpick.admin.service.AdminContractPdfQueryService;
import com.example.lancerpick.admin.service.AdminFinanceQueryService;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.contract.entity.OrderContract;
import com.example.lancerpick.contract.repository.OrderContractRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AdminActionEligibilityTests {
  private final AdminFinanceQueryService finance = mock(AdminFinanceQueryService.class);
  private final AdminContractPdfQueryService pdf = mock(AdminContractPdfQueryService.class);
  private final OrderContractRepository contracts = mock(OrderContractRepository.class);
  private final AdminActionEligibility service =
      new AdminActionEligibility(finance, pdf, contracts);

  @Test
  void topupUsesManualDecisionAndDoesNotRequireAutomaticRecovery() {
    var response = mock(AdminFinanceResponses.Topup.class);
    when(finance.topup(1, 2)).thenReturn(response);
    when(response.manualRecheck()).thenReturn(Decision.ready());
    assertThat(service.requireEligible(1, AdminAction.TOPUP_RECHECK, 2)).isNull();
    when(response.manualRecheck()).thenReturn(Decision.blocked(BlockReason.LEASE_ACTIVE));
    assertThatThrownBy(() -> service.requireEligible(1, AdminAction.TOPUP_RECHECK, 2))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.code()).isEqualTo("ADMIN_ACTION_NOT_ELIGIBLE"));
  }

  @Test
  void jobUsesRestartCandidate() {
    var response = mock(AdminFinanceResponses.RecoveryJob.class);
    when(finance.recoveryJob(1, 2)).thenReturn(response);
    when(response.restart()).thenReturn(Decision.blocked(BlockReason.JOB_RUNNING));
    assertThatThrownBy(() -> service.requireEligible(1, AdminAction.TOPUP_RECOVERY_RESTART, 2))
        .isInstanceOf(BusinessException.class);
    when(response.restart()).thenReturn(Decision.ready());
    assertThat(service.requireEligible(1, AdminAction.TOPUP_RECOVERY_RESTART, 2)).isNull();
  }

  @Test
  void pdfUsesPersistedOrderAndVersionAndRejectsMissingContract() {
    var contract = mock(OrderContract.class);
    when(contracts.findById(2L)).thenReturn(Optional.of(contract));
    when(contract.getOrderId()).thenReturn(9L);
    when(contract.getVersion()).thenReturn(3);
    var response = mock(AdminContractPdfResponse.class);
    when(pdf.inspect(1, 9, 3)).thenReturn(response);
    when(response.generationCandidate())
        .thenReturn(new GenerationCandidate(true, GenerationKind.RETRY_FAILED, null));
    assertThat(service.requireEligible(1, AdminAction.CONTRACT_PDF_REGENERATE, 2)).isEqualTo(9);
    when(contracts.findById(2L)).thenReturn(Optional.empty());
    assertThatThrownBy(() -> service.requireEligible(1, AdminAction.CONTRACT_PDF_REGENERATE, 2))
        .isInstanceOfSatisfying(
            BusinessException.class,
            ex -> assertThat(ex.code()).isEqualTo("ADMIN_ACTION_TARGET_NOT_FOUND"));
  }
}
