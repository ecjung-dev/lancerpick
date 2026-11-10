package com.example.lancerpick.member;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.member.config.SecurityLockRetrySchedulingConfiguration;
import com.example.lancerpick.member.dto.SecurityLockApplied;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.entity.SanctionKind;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.member.repository.MemberSanctionRepository;
import com.example.lancerpick.member.service.MemberSanctionCommandService;
import com.example.lancerpick.member.service.MemberSanctionQuery;
import com.example.lancerpick.member.service.SecurityLockDelivery;
import com.example.lancerpick.member.service.SecurityLockTimerStopper;
import com.example.lancerpick.support.IntegrationTest;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.*;
import org.springframework.transaction.support.*;

import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;

@IntegrationTest
@AutoConfigureMockMvc
@DirtiesContext
class MemberSanctionIntegrationTests {
    static final Instant BASE = Instant.parse("2026-11-09T00:00:00Z");

    static class Time extends Clock {
        Instant value = BASE;

        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        public Clock withZone(ZoneId zone) {
            return this;
        }

        public Instant instant() {
            return value;
        }
    }

    static class Stopper implements SecurityLockTimerStopper {
        final AtomicInteger calls = new AtomicInteger();
        final AtomicBoolean fail = new AtomicBoolean();
        final java.util.concurrent.atomic.AtomicLong failedId =
                new java.util.concurrent.atomic.AtomicLong(-1);
        JdbcTemplate jdbc;

        public void stop(SecurityLockApplied event) {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT COUNT(*) FROM member_sanctions WHERE id=?",
                                    Long.class,
                                    event.sanctionId()))
                    .isEqualTo(1L);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT auth_version FROM members WHERE id=?",
                                    Long.class,
                                    event.memberId()))
                    .isGreaterThan(0);
            calls.incrementAndGet();
            if (fail.get() || failedId.get() == event.sanctionId())
                throw new IllegalStateException("injected failure");
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        Time sanctionClock() {
            return new Time();
        }

        // 주문 영역의 실제 구현체(SecurityLockTimerStops) 대신 이 대역이 전달받도록 우선한다.
        @Bean
        @Primary
        Stopper testStopper(JdbcTemplate jdbc) {
            var s = new Stopper();
            s.jdbc = jdbc;
            return s;
        }
    }

    @Autowired MemberSanctionCommandService commands;
    @Autowired MemberSanctionQuery query;
    @Autowired MemberSanctionRepository sanctions;
    @Autowired MemberRepository members;
    @Autowired SecurityLockDelivery delivery;
    @Autowired JdbcTemplate jdbc;
    @Autowired Time clock;
    @Autowired Stopper stopper;
    @Autowired MockMvc mvc;
    @Autowired PasswordEncoder encoder;
    @Autowired PlatformTransactionManager manager;
    long admin, target, other;
    String email;

    @BeforeEach
    void setup() {
        clock.value = BASE;
        stopper.calls.set(0);
        stopper.fail.set(false);
        stopper.failedId.set(-1);
        admin = member();
        target = member();
        other = member();
        jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", admin);
        email = members.findById(target).orElseThrow().getEmail();
    }

    long member() {
        return members.saveAndFlush(
                        Member.create(
                                UUID.randomUUID() + "@sanction.test",
                                encoder.encode("abcd1234"),
                                "fixture",
                                LocalDateTime.ofInstant(BASE.minusSeconds(86400), ZoneOffset.UTC)))
                .getId();
    }

    @AfterEach
    void cleanup() {
        for (long id : new long[] {target, other, admin})
            jdbc.update("DELETE FROM member_sanctions WHERE member_id=?", id);
        for (long id : new long[] {target, other, admin}) members.deleteById(id);
    }

    LocalDateTime end() {
        return LocalDateTime.ofInstant(BASE.plusSeconds(3600), ZoneOffset.UTC);
    }

    long apply(SanctionKind kind) {
        return commands.apply(admin, target, kind, "security check", end(), UUID.randomUUID());
    }

    @Test
    void restrictionExpiresExactlyAndGeneralRestrictionDoesNotRevokeSessions() {
        apply(SanctionKind.GENERAL_RESTRICTION);
        assertThat(query.restriction(target).kind()).isEqualTo(SanctionKind.GENERAL_RESTRICTION);
        assertThat(members.findAuthVersionById(target)).contains(0L);
        assertThat(stopper.calls).hasValue(0);
        clock.value = BASE.plusSeconds(3599);
        assertThat(query.restriction(target).blocksNewActivity()).isTrue();
        clock.value = BASE.plusSeconds(3600);
        assertThat(query.restriction(target).kind()).isEqualTo(SanctionKind.NONE);
        assertThat(sanctions.count()).isEqualTo(1);
    }

    @Test
    void securityLockWinsAndHistorySurvivesRevocationAndExpiration() {
        apply(SanctionKind.GENERAL_RESTRICTION);
        long lock = apply(SanctionKind.SECURITY_LOCK);
        var state = query.restriction(target);
        assertThat(state.securityLocked()).isTrue();
        assertThat(state.adminId()).isEqualTo(admin);
        assertThat(stopper.calls).hasValue(1);
        assertThat(sanctions.findById(lock).orElseThrow().getTimerStopDeliveredAt()).isNotNull();
        commands.revoke(admin, target, lock, "resolved");
        assertThat(query.restriction(target).kind()).isEqualTo(SanctionKind.GENERAL_RESTRICTION);
        assertThat(query.restriction(target).latestSecurityLock().sanctionId()).isEqualTo(lock);
        clock.value = BASE.plusSeconds(3601);
        assertThat(query.restriction(target).kind()).isEqualTo(SanctionKind.NONE);
        assertThat(query.restriction(target).latestSecurityLock().adminId()).isEqualTo(admin);
        assertThat(members.findAuthVersionById(target)).contains(1L);
    }

    @Test
    void failedAfterCommitDeliveryRemainsRetryableEvenAfterUnlock() {
        stopper.fail.set(true);
        long id = apply(SanctionKind.SECURITY_LOCK);
        assertThat(sanctions.findById(id).orElseThrow().getTimerStopDeliveredAt()).isNull();
        assertThat(query.restriction(target).securityLocked()).isTrue();
        clock.value = BASE.plusSeconds(3601);
        stopper.fail.set(false);
        assertThat(query.restriction(target).securityLocked()).isFalse();
        assertThat(delivery.retryPending(10)).isEqualTo(1);
        assertThat(delivery.retryPending(10)).isZero();
        assertThat(stopper.calls).hasValue(2);
    }

    @Test
    void sameRequestIsIdempotentAndDifferentPayloadConflicts() {
        UUID key = UUID.randomUUID();
        long id = commands.apply(admin, target, SanctionKind.SECURITY_LOCK, "reason", end(), key);
        assertThat(commands.apply(admin, target, SanctionKind.SECURITY_LOCK, "reason", end(), key))
                .isEqualTo(id);
        assertThat(members.findAuthVersionById(target)).contains(1L);
        assertThat(stopper.calls).hasValue(1);
        assertThatThrownBy(() ->
                        commands.apply(
                                admin, target, SanctionKind.SECURITY_LOCK, "changed", end(), key))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        e -> assertThat(e.code()).isEqualTo("SANCTION_REQUEST_CONFLICT"));
    }

    @Test
    void scheduledBatchesPassAnOlderFailureAndRecoverRevokedExpiredHistoryAfterWrapping() {
        stopper.fail.set(true);
        long old = apply(SanctionKind.SECURITY_LOCK);
        long recent = apply(SanctionKind.SECURITY_LOCK);
        assertThat(sanctions.findById(old).orElseThrow().getRevokedAt()).isNotNull();
        stopper.fail.set(false);
        stopper.failedId.set(old);
        clock.value = BASE.plusSeconds(3601);
        var job = new SecurityLockRetrySchedulingConfiguration(delivery, 1);
        job.retry(); // Old failed row consumes only one bounded page.
        job.retry(); // The newer pending row is not starved.
        assertThat(sanctions.findById(old).orElseThrow().getTimerStopDeliveredAt()).isNull();
        assertThat(sanctions.findById(recent).orElseThrow().getTimerStopDeliveredAt()).isNotNull();
        job.retry(); // Empty page wraps back to the start.
        stopper.failedId.set(-1);
        job.retry();
        var recovered = sanctions.findById(old).orElseThrow();
        assertThat(recovered.getTimerStopDeliveredAt()).isNotNull();
        assertThat(recovered.getRevokedAt()).isNotNull();
        var firstMarker = recovered.getTimerStopDeliveredAt();
        clock.value = BASE.plusSeconds(3602);
        Integer repeated =
                new org.springframework.transaction.support.TransactionTemplate(manager)
                        .execute(ignored ->
                                sanctions.markTimerStopDelivered(
                                        old, LocalDateTime.ofInstant(clock.value, ZoneOffset.UTC)));
        assertThat(repeated).isZero();
        assertThat(sanctions.findById(old).orElseThrow().getTimerStopDeliveredAt())
                .isEqualTo(firstMarker);
        job.retry();
        job.retry();
        assertThat(stopper.calls).hasValue(5);
        assertThat(query.restriction(target).kind()).isEqualTo(SanctionKind.NONE);
        assertThat(members.findAuthVersionById(target)).contains(2L);
    }

    @Test
    void authorizationValidationAndCallerTransactionsCannotChangeState() {
        for (long actor : new long[] {other, target})
            assertThatThrownBy(() ->
                            commands.apply(
                                    actor,
                                    target,
                                    SanctionKind.SECURITY_LOCK,
                                    "reason",
                                    end(),
                                    UUID.randomUUID()))
                    .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() ->
                        commands.apply(
                                admin,
                                admin,
                                SanctionKind.SECURITY_LOCK,
                                "reason",
                                end(),
                                UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() ->
                        commands.apply(
                                admin,
                                target,
                                SanctionKind.NONE,
                                "reason",
                                end(),
                                UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() ->
                        commands.apply(
                                admin,
                                target,
                                SanctionKind.SECURITY_LOCK,
                                " ",
                                end(),
                                UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() ->
                        commands.apply(
                                admin,
                                target,
                                SanctionKind.SECURITY_LOCK,
                                "reason",
                                LocalDateTime.ofInstant(BASE, ZoneOffset.UTC),
                                UUID.randomUUID()))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() ->
                        new TransactionTemplate(manager)
                                .execute(ignored -> apply(SanctionKind.SECURITY_LOCK)))
                .isInstanceOf(IllegalTransactionStateException.class);
        assertThat(sanctions.count()).isZero();
        assertThat(members.findAuthVersionById(target)).contains(0L);
    }

    @Test
    void simultaneousSameRequestOnlyInvalidatesOnce() throws Exception {
        UUID key = UUID.randomUUID();
        var pool = Executors.newFixedThreadPool(2);
        var start = new CountDownLatch(1);
        try {
            Callable<Long> call =
                    () -> {
                        start.await();
                        return commands.apply(
                                admin, target, SanctionKind.SECURITY_LOCK, "reason", end(), key);
                    };
            var a = pool.submit(call);
            var b = pool.submit(call);
            start.countDown();
            assertThat(a.get(15, TimeUnit.SECONDS)).isEqualTo(b.get(15, TimeUnit.SECONDS));
            assertThat(sanctions.count()).isEqualTo(1);
            assertThat(members.findAuthVersionById(target)).contains(1L);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void loginBlockedOldSessionInvalidatedAndExpiryRequiresFreshLogin() throws Exception {
        var login =
                mvc.perform(
                                post("/api/auth/login")
                                        .with(csrf())
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                "{\"email\":\""
                                                        + email
                                                        + "\",\"password\":\"abcd1234\"}"))
                        .andExpect(status().isOk())
                        .andReturn();
        var session = (MockHttpSession) login.getRequest().getSession(false);
        apply(SanctionKind.GENERAL_RESTRICTION);
        mvc.perform(get("/api/me").session(session)).andExpect(status().isOk());
        apply(SanctionKind.SECURITY_LOCK);
        mvc.perform(get("/api/me").session(session)).andExpect(status().isUnauthorized());
        mvc.perform(
                        post("/api/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\"}"))
                .andExpect(status().isForbidden());
        clock.value = BASE.plusSeconds(3600);
        mvc.perform(
                        post("/api/auth/login")
                                .with(csrf())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"email\":\"" + email + "\",\"password\":\"abcd1234\"}"))
                .andExpect(status().isOk());
        assertThat(members.findAuthVersionById(target)).contains(1L);
    }

    @Test
    void failureToAdvanceAuthenticationRollsBackSanctionAndDoesNotPublish() {
        jdbc.update("UPDATE members SET auth_version=? WHERE id=?", Long.MAX_VALUE, target);
        assertThatThrownBy(() -> apply(SanctionKind.SECURITY_LOCK))
                .isInstanceOf(ArithmeticException.class);
        assertThat(sanctions.count()).isZero();
        assertThat(members.findAuthVersionById(target)).contains(Long.MAX_VALUE);
        assertThat(stopper.calls).hasValue(0);
    }

    @Test
    void queryDoesNotAcquireAnAdditionalMemberLock() throws Exception {
        var acquired = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newSingleThreadExecutor();
        try {
            var writer =
                    pool.submit(() ->
                            new TransactionTemplate(manager)
                                    .executeWithoutResult(ignored -> {
                                        members.findByIdForUpdate(target).orElseThrow();
                                        acquired.countDown();
                                        try {
                                            if (!release.await(10, TimeUnit.SECONDS))
                                                throw new IllegalStateException("test timeout");
                                        } catch (InterruptedException e) {
                                            Thread.currentThread().interrupt();
                                            throw new IllegalStateException(e);
                                        }
                                    }));
            assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();
            assertThat(query.restriction(target).kind()).isEqualTo(SanctionKind.NONE);
            release.countDown();
            writer.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }
}
