package com.example.lancerpick.wallet.batch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;

/** 충전 복구 Job 파라미터 계약. 검증기와 관리자 재시작 판단이 같은 입력에 같은 결론을 내는지 함께 본다. */
class TopupRecoveryJobParametersTests {
  private static final String RUN_ID = UUID.randomUUID().toString();

  @Test
  void acceptsServerRunIdAndStartTimeWithinUtcYear9999() {
    long last = Instant.parse("9999-12-31T23:59:59.999Z").toEpochMilli();
    assertThat(TopupRecoveryJobParameters.MAX_REQUESTED_AT_MILLIS).isEqualTo(last);
    for (long millis : new long[] {0, 1_789_000_000_000L, last}) {
      var parameters = valid(millis);
      assertThat(TopupRecoveryJobParameters.requestedAt(parameters))
          .contains(Instant.ofEpochMilli(millis));
      assertThatCode(() -> TopupRecoveryJobParameters.validate(parameters))
          .doesNotThrowAnyException();
    }
  }

  @Test
  void rejectsEverythingElseWithTheSameDecisionInValidatorAndAdminView() {
    long firstOfYear10000 = Instant.parse("+10000-01-01T00:00:00Z").toEpochMilli();
    var rejected =
        new JobParameters[] {
          new JobParameters(),
          new JobParametersBuilder().addString("run.id", RUN_ID).toJobParameters(),
          new JobParametersBuilder().addLong("requestedAtMillis", 0L).toJobParameters(),
          new JobParametersBuilder(valid(0))
              .addString("paymentKey", "sensitive-job-parameter")
              .toJobParameters(),
          new JobParametersBuilder()
              .addString("run.id", "not-a-uuid")
              .addLong("requestedAtMillis", 0L)
              .toJobParameters(),
          new JobParametersBuilder()
              .addLong("run.id", 1L)
              .addLong("requestedAtMillis", 0L)
              .toJobParameters(),
          new JobParametersBuilder()
              .addString("run.id", RUN_ID)
              .addString("requestedAtMillis", "0")
              .toJobParameters(),
          new JobParametersBuilder()
              .addString("run.id", RUN_ID)
              .addString("other", "0")
              .toJobParameters(),
          valid(-1),
          valid(firstOfYear10000),
          valid(Long.MAX_VALUE),
        };
    for (var parameters : rejected) {
      assertThat(TopupRecoveryJobParameters.requestedAt(parameters)).as(parameters.toString()).isEmpty();
      assertThatThrownBy(() -> TopupRecoveryJobParameters.validate(parameters))
          .as(parameters.toString())
          .isInstanceOf(InvalidJobParametersException.class)
          .hasMessage("Only a server run ID and start time are permitted");
    }
    assertThatThrownBy(() -> TopupRecoveryJobParameters.validate(null))
        .isInstanceOf(InvalidJobParametersException.class);
  }

  private static JobParameters valid(long millis) {
    return new JobParametersBuilder()
        .addString(TopupRecoveryJobParameters.RUN_ID, RUN_ID)
        .addLong(TopupRecoveryJobParameters.REQUESTED_AT_MILLIS, millis)
        .toJobParameters();
  }
}
