package com.example.lancerpick.wallet.batch;

import com.example.lancerpick.wallet.config.TopupRecoveryProperties;
import com.example.lancerpick.wallet.repository.TopupRecoveryQueries;
import com.example.lancerpick.wallet.service.TopupRecoveryService;
import com.example.lancerpick.wallet.service.TopupRecoveryTransactions.Result;
import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

/** 한 호출에 한 충전만 처리하고 완료한 ID를 체크포인트로 남긴다. 파라미터/컨텍스트에 키·원문은 없다. */
@RequiredArgsConstructor
@Slf4j
public class TopupRecoveryTasklet implements Tasklet {
  private final TopupRecoveryQueries queries;
  private final TopupRecoveryService recovery;
  private final TopupRecoveryProperties properties;
  private final Clock clock;

  @Override
  public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
    var step = contribution.getStepExecution();
    var context = step.getExecutionContext();
    try {
      if (!context.containsKey("highestId")) {
        context.putLong("highestId", queries.highestId());
      }
      long lastId = context.getLong("lastId", 0);
      long startedAt = step.getJobParameters().getLong("requestedAtMillis");
      Instant staleBefore = Instant.ofEpochMilli(startedAt).minus(properties.staleAge());
      var next =
          queries.nextId(
              lastId,
              context.getLong("highestId"),
              clock.instant(),
              staleBefore,
              properties.maxAttempts());
      if (next.isEmpty()) {
        return RepeatStatus.FINISHED;
      }
      long id = next.get();
      Result result = recovery.recover(id, staleBefore);
      context.putLong("lastId", id);
      String counter = result.name().toLowerCase(Locale.ROOT);
      context.putLong(counter, context.getLong(counter, 0) + 1);
      contribution.incrementReadCount();
      if (result == Result.COMPLETED) {
        contribution.incrementWriteCount(1);
      } else {
        contribution.incrementFilterCount();
      }
      return RepeatStatus.CONTINUABLE;
    } catch (RuntimeException failure) {
      // Batch는 실패 예외를 메타데이터에 저장하므로 제공자·DB 예외 원문/원인을 넘기지 않는다.
      log.warn("Top-up recovery step failed code=RECOVERY_EXECUTION_FAILED");
      throw new IllegalStateException("RECOVERY_EXECUTION_FAILED");
    }
  }
}
