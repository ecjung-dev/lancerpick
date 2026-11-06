package com.example.lancerpick.wallet.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("lancerpick.wallet.topup-recovery")
public record TopupRecoveryProperties(
    @Min(120) @Max(86400) long staleSeconds,
    @Min(120) @Max(3600) long leaseSeconds,
    @Min(1) @Max(100) int maxAttempts,
    @Min(60) @Max(3600) long retrySeconds) {
  public Duration staleAge() {
    return Duration.ofSeconds(staleSeconds);
  }

  public Duration leaseDuration() {
    return Duration.ofSeconds(leaseSeconds);
  }

  public Duration retryDelay(int attempts) {
    long multiplier = 1L << Math.min(Math.max(attempts - 1, 0), 6);
    return Duration.ofSeconds(Math.min(3600, Math.multiplyExact(retrySeconds, multiplier)));
  }
}
