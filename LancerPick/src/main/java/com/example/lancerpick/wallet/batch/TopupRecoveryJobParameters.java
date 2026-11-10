package com.example.lancerpick.wallet.batch;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;

/**
 * 충전 복구 Job 실행 파라미터 계약. 서버가 만든 실행 ID({@code run.id}, UUID)와 요청 시각({@code requestedAtMillis}, epoch
 * 밀리초)만 허용한다. 요청 시각은 0 이상이고 UTC 9999년 이내여야 한다. Job 검증기(시작·재시작)와 관리자 재시작 가능 판단이 같은 규칙을 쓴다.
 */
public final class TopupRecoveryJobParameters {
  public static final String RUN_ID = "run.id";
  public static final String REQUESTED_AT_MILLIS = "requestedAtMillis";

  /** UTC 9999-12-31T23:59:59.999Z. */
  static final long MAX_REQUESTED_AT_MILLIS =
      Instant.parse("9999-12-31T23:59:59.999Z").toEpochMilli();

  private TopupRecoveryJobParameters() {}

  /** 계약에 맞으면 요청 시각을, 키 누락·추가 키·타입 불일치·UUID 아님·범위 밖이면 빈 값을 돌려준다. */
  public static Optional<Instant> requestedAt(JobParameters parameters) {
    try {
      if (parameters.parameters().size() != 2) return Optional.empty();
      UUID.fromString(parameters.getString(RUN_ID));
      Long millis = parameters.getLong(REQUESTED_AT_MILLIS);
      if (millis == null || millis < 0 || millis > MAX_REQUESTED_AT_MILLIS) return Optional.empty();
      return Optional.of(Instant.ofEpochMilli(millis));
    } catch (RuntimeException invalid) {
      return Optional.empty();
    }
  }

  /** Job 검증기. 시작과 저장된 파라미터로 재시작할 때 모두 적용된다. */
  public static void validate(JobParameters parameters) throws InvalidJobParametersException {
    if (requestedAt(parameters).isEmpty()) {
      throw new InvalidJobParametersException("Only a server run ID and start time are permitted");
    }
  }
}
