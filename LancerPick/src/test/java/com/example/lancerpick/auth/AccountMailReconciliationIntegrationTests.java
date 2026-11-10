package com.example.lancerpick.auth;

import static org.assertj.core.api.Assertions.*;

import com.example.lancerpick.auth.entity.AccountChallenge;
import com.example.lancerpick.auth.entity.ChallengeDeliveryStatus;
import com.example.lancerpick.auth.mail.AccountMailDeliveryRecorder;
import com.example.lancerpick.auth.mail.AccountMailReconciliationService;
import com.example.lancerpick.auth.repository.AccountChallengeRepository;
import com.example.lancerpick.auth.service.AuthSubjectHasher;
import com.example.lancerpick.auth.service.OneTimeTokens;
import com.example.lancerpick.auth.service.SignupEmailService;
import com.example.lancerpick.support.FakeAccountMailSender;
import com.example.lancerpick.support.IntegrationTest;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
@Import({
  FakeAccountMailSender.Config.class,
  AccountMailReconciliationIntegrationTests.Config.class
})
class AccountMailReconciliationIntegrationTests {
  // Earlier than normal suite fixtures: the cleanup scan cannot consume their newer pending mail.
  private static final Instant BASE = Instant.parse("2010-01-01T00:00:00Z");

  static class Time extends Clock {
    volatile Instant now = BASE;

    public ZoneId getZone() {
      return ZoneOffset.UTC;
    }

    public Clock withZone(ZoneId zone) {
      return this;
    }

    public Instant instant() {
      return now;
    }
  }

  @TestConfiguration
  static class Config {
    @Bean
    @Primary
    Time reconciliationClock() {
      return new Time();
    }
  }

  @Autowired Time clock;
  @Autowired AccountMailReconciliationService reconciliation;
  @Autowired AccountChallengeRepository challenges;
  @Autowired AccountMailDeliveryRecorder recorder;
  @Autowired SignupEmailService signup;
  @Autowired FakeAccountMailSender sender;
  @Autowired AuthSubjectHasher hasher;
  @Autowired JdbcTemplate jdbc;
  final List<String> emails = new ArrayList<>();

  LocalDateTime now() {
    return LocalDateTime.ofInstant(clock.now, ZoneOffset.UTC);
  }

  String email() {
    String value = "mail-reconcile-" + UUID.randomUUID() + "@fixture.test";
    emails.add(value);
    return value;
  }

  AccountChallenge pending(LocalDateTime issued) {
    return challenges.saveAndFlush(
        AccountChallenge.signupEmail(
            email(), OneTimeTokens.issue().hash(), issued, issued.plusHours(2)));
  }

  AccountChallenge row(long id) {
    return challenges.findById(id).orElseThrow();
  }

  @BeforeEach
  void reset() {
    clock.now = BASE;
    sender.reset();
  }

  @AfterEach
  void clean() {
    for (String email : emails) {
      jdbc.update("DELETE FROM account_challenges WHERE email=?", email);
      jdbc.update(
          "DELETE FROM auth_request_limits WHERE subject_hmac=?",
          (Object) hasher.subject("SIGNUP_EMAIL", email));
    }
    sender.reset();
  }

  @Test
  void cutoffIsInclusiveBoundedAndPreservesAuthenticationStateEvenForExpiredUsedRows() {
    var old = pending(now().minusMinutes(40));
    var proof = OneTimeTokens.issue();
    old.verifyForSignup(proof.hash(), now().minusMinutes(35), now().minusMinutes(1));
    old.consume(now().minusMinutes(2));
    challenges.saveAndFlush(old);
    var exact = pending(now().minusMinutes(30));
    var fresh = pending(now().minusMinutes(30).plusNanos(1000));
    var sent = pending(now().minusMinutes(50));
    recorder.markSent(sent.getId());
    var failed = pending(now().minusMinutes(50));
    recorder.markFailed(failed.getId(), "MAIL_SEND_FAILED");
    assertThat(reconciliation.reconcile(30, 1)).isEqualTo(1);
    var cleaned = row(old.getId());
    assertThat(cleaned.getDeliveryStatus()).isEqualTo(ChallengeDeliveryStatus.UNKNOWN);
    assertThat(cleaned.getLastErrorCode()).isEqualTo("MAIL_RESULT_UNCONFIRMED");
    assertThat(cleaned.getTokenHash()).containsExactly(proof.hash());
    assertThat(cleaned.getVerifiedAt()).isEqualTo(old.getVerifiedAt());
    assertThat(cleaned.getConsumedAt()).isEqualTo(old.getConsumedAt());
    assertThat(cleaned.getExpiresAt()).isEqualTo(old.getExpiresAt());
    assertThat(reconciliation.reconcile(30, 100)).isEqualTo(1);
    assertThat(row(exact.getId()).getDeliveryStatus()).isEqualTo(ChallengeDeliveryStatus.UNKNOWN);
    assertThat(row(fresh.getId()).getDeliveryStatus()).isEqualTo(ChallengeDeliveryStatus.PENDING);
    assertThat(row(sent.getId()).getDeliveryStatus()).isEqualTo(ChallengeDeliveryStatus.SENT);
    assertThat(row(failed.getId()).getDeliveryStatus()).isEqualTo(ChallengeDeliveryStatus.FAILED);
    assertThat(reconciliation.reconcile(30, 100)).isZero();
    assertThat(sender.sent).isEmpty();
  }

  @Test
  void lateActualResultsFinalizeUnknownButNeverOverwriteTerminalResults() {
    var a = pending(now().minusMinutes(31));
    var b = pending(now().minusMinutes(31));
    assertThat(reconciliation.reconcile(30, 50)).isEqualTo(2);
    recorder.markSent(a.getId());
    recorder.markFailed(a.getId(), "MAIL_SEND_FAILED");
    recorder.markFailed(b.getId(), "MAIL_SEND_FAILED");
    recorder.markSent(b.getId());
    assertThat(row(a.getId()).getDeliveryStatus()).isEqualTo(ChallengeDeliveryStatus.SENT);
    assertThat(row(a.getId()).getLastErrorCode()).isNull();
    assertThat(row(b.getId()).getDeliveryStatus()).isEqualTo(ChallengeDeliveryStatus.FAILED);
    assertThat(row(b.getId()).getLastErrorCode()).isEqualTo("MAIL_SEND_FAILED");
    assertThat(reconciliation.reconcile(30, 50)).isZero();
  }

  @Test
  void aLateResultRacingWithCleanupAlwaysKeepsTheActualResult() throws Exception {
    var challenge = pending(now().minusMinutes(31));
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var first =
          pool.submit(() -> {
            gate.await();
            return reconciliation.reconcile(30, 50);
          });
      var second =
          pool.submit(() -> {
            gate.await();
            recorder.markSent(challenge.getId());
            return true;
          });
      gate.countDown();
      first.get(10, TimeUnit.SECONDS);
      second.get(10, TimeUnit.SECONDS);
    }
    assertThat(row(challenge.getId()).getDeliveryStatus()).isEqualTo(ChallengeDeliveryStatus.SENT);
    assertThat(row(challenge.getId()).getLastErrorCode()).isNull();
    assertThat(row(challenge.getId()).getTokenHash()).containsExactly(challenge.getTokenHash());
    assertThat(sender.sent).isEmpty();
  }

  @Test
  void unknownDoesNotResendAndUserRerequestStillHonorsTheExistingLimitAndOldLink() {
    String email = email();
    signup.requestVerification(email);
    var first = sender.to(email).getFirst();
    String token = FakeAccountMailSender.tokenIn(first);
    var before = row(first.challengeId());
    clock.now = BASE.plusSeconds(61);
    assertThat(reconciliation.reconcile(1, 50)).isEqualTo(1);
    assertThat(row(first.challengeId()).getDeliveryStatus())
        .isEqualTo(ChallengeDeliveryStatus.UNKNOWN);
    assertThat(sender.to(email)).hasSize(1);
    assertThat(row(first.challengeId()).getTokenHash()).containsExactly(before.getTokenHash());
    signup.requestVerification(email);
    assertThat(sender.to(email)).hasSize(2);
    signup.requestVerification(email); // Same response behavior, no immediate third send.
    assertThat(sender.to(email)).hasSize(2);
    assertThat(signup.verify(token)).isNotNull();
    assertThat(row(first.challengeId()).getVerifiedAt()).isNotNull();
  }

  @Test
  void overlappingCleanupBatchesOnlyTransitionEachRowOnce() throws Exception {
    var challenge = pending(now().minusMinutes(31));
    var gate = new CountDownLatch(1);
    try (var pool = Executors.newFixedThreadPool(2)) {
      var a =
          pool.submit(() -> {
            gate.await();
            return reconciliation.reconcile(30, 50);
          });
      var b =
          pool.submit(() -> {
            gate.await();
            return reconciliation.reconcile(30, 50);
          });
      gate.countDown();
      assertThat(a.get(10, TimeUnit.SECONDS) + b.get(10, TimeUnit.SECONDS)).isEqualTo(1);
    }
    assertThat(row(challenge.getId()).getDeliveryStatus())
        .isEqualTo(ChallengeDeliveryStatus.UNKNOWN);
  }
}
