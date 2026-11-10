package com.example.lancerpick.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.lancerpick.admin.dto.AdminFinanceResponses.BlockReason;
import com.example.lancerpick.admin.repository.AdminFinanceQueries;
import com.example.lancerpick.admin.repository.AdminFinanceQueries.TopupRow;
import com.example.lancerpick.admin.service.AdminAuthorization;
import com.example.lancerpick.admin.service.AdminFinanceQueryService;
import com.example.lancerpick.admin.service.AdminRecoveryJobQueries;
import com.example.lancerpick.wallet.config.TopupRecoveryProperties;
import com.example.lancerpick.wallet.entity.WalletTopupStatus;
import com.example.lancerpick.wallet.gateway.TossPaymentGateway;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.repository.JobRepository;

class AdminFinanceQueryServiceTests {
  @Test
  void disabledBatchStillAllowsInspectionAndDoesNotRemoveManualRecheckCandidate() {
    var now = Instant.parse("2026-11-06T00:03:00Z");
    var queries = mock(AdminFinanceQueries.class);
    when(queries.topup(2))
        .thenReturn(
            Optional.of(
                new TopupRow(
                    2,
                    3,
                    50000,
                    WalletTopupStatus.UNKNOWN,
                    null,
                    0,
                    now.minusSeconds(180),
                    now.minusSeconds(180),
                    null,
                    null,
                    null,
                    true)));
    var service =
        new AdminFinanceQueryService(
            queries,
            mock(AdminAuthorization.class),
            new AdminRecoveryJobQueries(mock(JobRepository.class), false),
            new TopupRecoveryProperties(120, 180, 8, 60),
            mock(TossPaymentGateway.class),
            Clock.fixed(now, ZoneOffset.UTC),
            false);
    var result = service.topup(1, 2);
    assertThat(result.automaticRecovery().reason()).isEqualTo(BlockReason.RECOVERY_DISABLED);
    assertThat(result.manualRecheck().eligible()).isTrue();
  }
}
