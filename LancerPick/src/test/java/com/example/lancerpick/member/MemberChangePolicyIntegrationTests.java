package com.example.lancerpick.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lancerpick.auth.entity.AccountChallenge;
import com.example.lancerpick.auth.repository.AccountChallengeRepository;
import com.example.lancerpick.auth.service.OneTimeTokens;
import com.example.lancerpick.auth.service.PasswordResetService;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.member.dto.ChangePasswordRequest;
import com.example.lancerpick.member.dto.UpdateMeRequest;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.member.service.MemberChangePolicy;
import com.example.lancerpick.member.service.MemberService;
import com.example.lancerpick.member.service.PasswordRecommendationQuery;
import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.support.TestMembers;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

/** Real commits and concurrent requests: password history is independent of profile/session updates. */
@IntegrationTest
@AutoConfigureMockMvc
@Import(MemberChangePolicyIntegrationTests.TimeConfig.class)
class MemberChangePolicyIntegrationTests {
    private static final LocalDateTime START = LocalDateTime.parse("2026-11-09T06:00:00.123456");
    private static final String PASSWORD = "before1234";
    @Autowired MemberService service;
    @Autowired MemberRepository members;
    @Autowired PasswordRecommendationQuery recommendations;
    @Autowired PasswordResetService resets;
    @Autowired AccountChallengeRepository challenges;
    @Autowired JdbcTemplate jdbc;
    @Autowired PolicyClock clock;
    @Autowired MockMvc mvc;
    private long memberId;
    private String email;

    record Principal(Long memberId) implements AuthenticatedMember {}

    @BeforeEach
    void fixture() {
        clock.at(START);
        email = "change-policy-" + UUID.randomUUID() + "@example.com";
        var member = service.signup(TestMembers.signupRequest(jdbc, email, PASSWORD, "원래 이름"));
        memberId = Long.parseLong(member.id());
        assertThat(member.displayNameChangeAvailableAt()).isNull();
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM account_challenges WHERE member_id=?", memberId);
        jdbc.update("DELETE FROM wallet_entries WHERE member_id=?", memberId);
        jdbc.update("DELETE FROM wallets WHERE member_id=?", memberId);
        jdbc.update("DELETE FROM members WHERE id=?", memberId);
        TestMembers.deleteSignupProofs(jdbc, email);
    }

    @Test
    void firstRenameIsFreeAndNoopOrInvalidInputDoesNotMoveDates() {
        clock.at(START.plusDays(1));
        assertThat(service.updateMe(memberId, new UpdateMeRequest("  원래 이름  "))
                .displayNameChangeAvailableAt()).isNull();
        assertThat(members.findById(memberId).orElseThrow().getUpdatedAt()).isEqualTo(START);
        assertBusiness(() -> service.updateMe(memberId, new UpdateMeRequest("   ")), "VALIDATION_FAILED");
        assertThat(members.findById(memberId).orElseThrow().getDisplayNameChangedAt()).isNull();

        var response = service.updateMe(memberId, new UpdateMeRequest("  새 이름  "));
        assertThat(response.displayName()).isEqualTo("새 이름");
        assertThat(response.displayNameChangeAvailableAt())
                .isEqualTo(MemberChangePolicy.sixMonthsAfter(START.plusDays(1)).atOffset(ZoneOffset.UTC));
        var member = members.findById(memberId).orElseThrow();
        assertThat(member.getPasswordChangedAt()).isEqualTo(START);
        assertThat(member.getPasswordRecommendationBaseAt()).isEqualTo(START);
    }

    @Test
    void cooldownRejectsBeforeBoundaryButAllowsExactBoundaryAndSameNameDoesNotExtendIt() {
        service.updateMe(memberId, new UpdateMeRequest("첫 변경"));
        var deadline = MemberChangePolicy.sixMonthsAfter(START);
        clock.at(deadline.minusNanos(1_000));
        assertBusiness(() -> service.updateMe(memberId, new UpdateMeRequest("너무 빠름")),
                "DISPLAY_NAME_CHANGE_NOT_ALLOWED");
        service.updateMe(memberId, new UpdateMeRequest(" 첫 변경 "));
        var unchanged = members.findById(memberId).orElseThrow();
        assertThat(unchanged.getDisplayName()).isEqualTo("첫 변경");
        assertThat(unchanged.getDisplayNameChangedAt()).isEqualTo(START);
        assertThat(unchanged.getUpdatedAt()).isEqualTo(START);

        clock.at(deadline);
        var response = service.updateMe(memberId, new UpdateMeRequest("두 번째 변경"));
        assertThat(response.displayNameChangeAvailableAt())
                .isEqualTo(MemberChangePolicy.sixMonthsAfter(deadline).atOffset(ZoneOffset.UTC));
    }

    @Test
    void concurrentDifferentNamesCommitOnlyOneChange() throws Exception {
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var futures = java.util.stream.Stream.of("변경 A", "변경 B").map(name -> pool.submit(() -> {
                ready.countDown();
                if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                try {
                    service.updateMe(memberId, new UpdateMeRequest(name));
                    return "SAVED";
                } catch (BusinessException failure) {
                    return failure.code();
                }
            })).toList();
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            assertThat(java.util.List.of(futures.get(0).get(15, TimeUnit.SECONDS),
                    futures.get(1).get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder("SAVED", "DISPLAY_NAME_CHANGE_NOT_ALLOWED");
        }
        assertThat(members.findById(memberId).orElseThrow().getDisplayNameChangedAt()).isEqualTo(START);
    }

    @Test
    void httpReturnsNextDateAnd409WithoutChangingTheSavedName() throws Exception {
        service.updateMe(memberId, new UpdateMeRequest("첫 변경"));
        var auth = UsernamePasswordAuthenticationToken.authenticated(new Principal(memberId), null, java.util.List.of());
        mvc.perform(get("/api/me").with(authentication(auth)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.displayNameChangeAvailableAt").value("2027-05-09T06:00:00.123456Z"))
                .andExpect(jsonPath("$.passwordChangedAt").doesNotExist())
                .andExpect(jsonPath("$.passwordRecommendationBaseAt").doesNotExist());
        mvc.perform(patch("/api/me").with(authentication(auth)).with(csrf())
                        .contentType("application/json").content("{\"displayName\":\"두 번째 변경\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DISPLAY_NAME_CHANGE_NOT_ALLOWED"));
        assertThat(service.getMe(memberId).displayName()).isEqualTo("첫 변경");
    }

    @Test
    void successfulPasswordChangeMovesBothDatesButFailuresAndProfileChangesDoNot() {
        clock.at(START.plusDays(1));
        assertBusiness(() -> service.changePassword(memberId, 0L,
                new ChangePasswordRequest("wrong1234", "after5678")), "CURRENT_PASSWORD_INCORRECT");
        assertBusiness(() -> service.changePassword(memberId, 0L,
                new ChangePasswordRequest(PASSWORD, PASSWORD)), "PASSWORD_UNCHANGED");
        assertThat(recommendations.find(memberId).orElseThrow().basisAt()).isEqualTo(START);

        service.changePassword(memberId, 0L, new ChangePasswordRequest(PASSWORD, "after5678"));
        var changed = recommendations.find(memberId).orElseThrow();
        assertThat(changed.passwordChangedAt()).isEqualTo(START.plusDays(1));
        assertThat(changed.basisAt()).isEqualTo(START.plusDays(1));
        assertThat(changed.firstRecommendedAt())
                .isEqualTo(MemberChangePolicy.sixMonthsAfter(START.plusDays(1)));
        clock.at(START.plusDays(2));
        service.updateMe(memberId, new UpdateMeRequest("비밀번호와 독립"));
        var member = members.findById(memberId).orElseThrow();
        member.invalidateSessions(START.plusDays(3));
        members.saveAndFlush(member);
        assertThat(recommendations.find(memberId).orElseThrow()).isEqualTo(changed);
        assertThat(member.getAuthVersion()).isEqualTo(2);
    }

    @Test
    void passwordResetMovesDatesAndTokenReplayDoesNot() {
        var token = OneTimeTokens.issue();
        challenges.saveAndFlush(AccountChallenge.passwordReset(memberId, email, token.hash(), 0L,
                START, START.plusDays(2)));
        clock.at(START.plusDays(1));
        resets.resetPassword(token.value(), "after5678");
        var basis = recommendations.find(memberId).orElseThrow();
        assertThat(basis.passwordChangedAt()).isEqualTo(START.plusDays(1));
        assertThat(basis.basisAt()).isEqualTo(START.plusDays(1));
        clock.at(START.plusDays(1).plusHours(1));
        assertBusiness(() -> resets.resetPassword(token.value(), "again1234"), "PASSWORD_RESET_TOKEN_INVALID");
        assertThat(recommendations.find(memberId).orElseThrow()).isEqualTo(basis);
    }

    @Test
    void legacyUnknownPasswordDateIsDistinctFromRolloutBaseline() {
        jdbc.update("UPDATE members SET password_changed_at=NULL,password_recommendation_base_at=? WHERE id=?",
                START.plusDays(1), memberId);
        var basis = recommendations.find(memberId).orElseThrow();
        assertThat(basis.passwordChangedAt()).isNull();
        assertThat(basis.basisAt()).isEqualTo(START.plusDays(1));
        assertThat(basis.firstRecommendedAt()).isEqualTo(MemberChangePolicy.sixMonthsAfter(START.plusDays(1)));
        assertThat(recommendations.find(Long.MAX_VALUE)).isEmpty();
        assertThatThrownBy(() -> recommendations.find(0)).isInstanceOf(IllegalArgumentException.class);
    }

    private static void assertBusiness(Runnable action, String code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(BusinessException.class,
                failure -> assertThat(failure.code()).isEqualTo(code));
    }

    static class PolicyClock extends Clock {
        private final AtomicReference<Instant> instant = new AtomicReference<>();
        void at(LocalDateTime utc) { instant.set(utc.toInstant(ZoneOffset.UTC)); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(), zone); }
        @Override public Instant instant() { return instant.get(); }
    }

    @TestConfiguration
    static class TimeConfig {
        @Bean @Primary PolicyClock memberPolicyClock() {
            var clock = new PolicyClock();
            clock.at(START);
            return clock;
        }
    }
}
