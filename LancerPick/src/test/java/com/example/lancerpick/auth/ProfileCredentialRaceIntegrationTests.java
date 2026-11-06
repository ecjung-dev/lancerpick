package com.example.lancerpick.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doAnswer;

import com.example.lancerpick.auth.entity.AccountChallenge;
import com.example.lancerpick.auth.repository.AccountChallengeRepository;
import com.example.lancerpick.auth.service.PasswordResetService;
import com.example.lancerpick.auth.service.OneTimeTokens;
import com.example.lancerpick.member.dto.ChangePasswordRequest;
import com.example.lancerpick.member.dto.UpdateMeRequest;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.member.service.MemberService;
import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.support.TestMembers;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 서로 다른 실제 트랜잭션의 프로필 저장이 비밀번호·인증 버전을 되돌리지 않는지 검사한다. */
@IntegrationTest
class ProfileCredentialRaceIntegrationTests {
  private static final String OLD_PASSWORD = "before1234";
  private static final String NEW_PASSWORD = "after5678";

  @Autowired MemberService service;
  @Autowired PasswordResetService resets;
  @Autowired AccountChallengeRepository challenges;
  @Autowired PasswordEncoder encoder;
  @Autowired PlatformTransactionManager manager;
  @Autowired JdbcTemplate jdbc;
  @Autowired EntityManager entityManager;
  @MockitoSpyBean MemberRepository members;
  private long memberId;

  @AfterEach
  void cleanup() {
    jdbc.update("DELETE FROM account_challenges WHERE member_id=?", memberId);
    jdbc.update("DELETE FROM wallet_entries WHERE member_id=?", memberId);
    jdbc.update("DELETE FROM wallets WHERE member_id=?", memberId);
    jdbc.update("DELETE FROM members WHERE id=?", memberId);
    TestMembers.deleteSignupProofs(jdbc, "profile-race-%@example.com");
  }

  @ParameterizedTest
  @ValueSource(booleans = {false, true})
  void profileSaveDoesNotRestoreCredentialsAfterConcurrentChangeOrReset(boolean reset)
      throws Exception {
    String email = "profile-race-" + UUID.randomUUID() + "@example.com";
    memberId = Long.parseLong(service.signup(TestMembers.signupRequest(jdbc, email, OLD_PASSWORD, "이전 이름")).id());
    var token = OneTimeTokens.issue();
    if (reset) {
      var at = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
      challenges.saveAndFlush(AccountChallenge.passwordReset(memberId, email, token.hash(), 0L, at, at.plusMinutes(15)));
    }

    var credentialWritten = new CountDownLatch(1);
    var commitCredentials = new CountDownLatch(1);
    var releaseProfile = new CountDownLatch(1);
    var profileRead = new CountDownLatch(1);
    var lookupKind = new CompletableFuture<Boolean>();
    var profileThread = new AtomicReference<Thread>();
    // 기존 무잠금 조회에서는 과거 스냅샷을 읽은 뒤 멈춘다. 잠금 조회는 비밀번호 커밋을 기다린다.
    for (boolean locking : new boolean[] {false, true}) {
      var answer = (org.mockito.stubbing.Answer<Object>) invocation -> {
        boolean profile = Thread.currentThread() == profileThread.get();
        if (profile) lookupKind.complete(locking);
        Object result = java.util.Optional.ofNullable(locking
            ? entityManager.find(Member.class, memberId, LockModeType.PESSIMISTIC_WRITE)
            : entityManager.find(Member.class, memberId));
        if (profile) {
          profileRead.countDown();
          assertThat(releaseProfile.await(15, TimeUnit.SECONDS)).isTrue();
        }
        return result;
      };
      if (locking) doAnswer(answer).when(members).findByIdForUpdate(memberId);
      else doAnswer(answer).when(members).findById(memberId);
    }

    try (var pool = Executors.newFixedThreadPool(2)) {
      var credential = pool.submit(() -> new TransactionTemplate(manager).executeWithoutResult(ignored -> {
        if (reset) resets.resetPassword(token.value(), NEW_PASSWORD);
        else service.changePassword(memberId, 0L, new ChangePasswordRequest(OLD_PASSWORD, NEW_PASSWORD));
        credentialWritten.countDown();
        try {
          assertThat(commitCredentials.await(15, TimeUnit.SECONDS)).isTrue();
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          throw new IllegalStateException(e);
        }
      }));
      try {
        assertThat(credentialWritten.await(15, TimeUnit.SECONDS)).isTrue();
        var profile = pool.submit(() -> {
          profileThread.set(Thread.currentThread());
          return service.updateMe(memberId, new UpdateMeRequest("새 이름"));
        });
        if (!lookupKind.get(15, TimeUnit.SECONDS)) {
          assertThat(profileRead.await(15, TimeUnit.SECONDS)).isTrue();
        }
        commitCredentials.countDown();
        credential.get(15, TimeUnit.SECONDS);
        releaseProfile.countDown();
        assertThat(profile.get(15, TimeUnit.SECONDS).displayName()).isEqualTo("새 이름");
      } finally {
        commitCredentials.countDown();
        releaseProfile.countDown();
      }
    }
    var saved = members.findById(memberId).orElseThrow();
    assertThat(saved.getAuthVersion()).isEqualTo(1L);
    assertThat(encoder.matches(NEW_PASSWORD, saved.getPasswordHash())).isTrue();
    assertThat(encoder.matches(OLD_PASSWORD, saved.getPasswordHash())).isFalse();
    assertThat(saved.getDisplayName()).isEqualTo("새 이름");
    if (reset) assertThat(jdbc.queryForObject(
        "SELECT consumed_at IS NOT NULL FROM account_challenges WHERE member_id=?", Boolean.class, memberId)).isTrue();
  }
}
