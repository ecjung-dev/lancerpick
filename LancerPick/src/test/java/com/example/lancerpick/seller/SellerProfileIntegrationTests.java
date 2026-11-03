package com.example.lancerpick.seller;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.seller.dto.SellerProfileRequest;
import com.example.lancerpick.seller.service.SellerProfileService;
import com.example.lancerpick.support.IntegrationTest;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 전용 MySQL에서 독립 트랜잭션 저장·재조회·동시 요청을 검사한다. */
@IntegrationTest
@AutoConfigureMockMvc
class SellerProfileIntegrationTests {
    @Autowired SellerProfileService service;
    @Autowired MemberRepository members;
    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired MockMvc mvc;
    @MockitoBean Clock clock;
    private long memberId;
    private final Instant firstTime = Instant.parse("2026-11-03T04:00:00.123456789Z");
    record TestMember(Long memberId) implements AuthenticatedMember {}

    @BeforeEach
    void fixture() {
        when(clock.instant()).thenReturn(firstTime);
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("""
                    INSERT INTO members(email,password_hash,display_name,role,created_at,updated_at)
                    VALUES (?, 'fixture', '판매자 😀', 'USER', ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, UUID.randomUUID() + "@test.invalid");
            statement.setObject(2, LocalDateTime.parse("2026-11-01T00:00:00"));
            statement.setObject(3, LocalDateTime.parse("2026-11-01T00:00:00"));
            return statement;
        }, key);
        memberId = key.getKey().longValue();
    }

    @AfterEach
    void cleanupOnlyOwnFixture() {
        jdbc.update("DELETE FROM seller_profiles WHERE member_id = ?", memberId);
        jdbc.update("DELETE FROM members WHERE id = ?", memberId);
    }

    @Test
    void realHttpCreateUpdateAndMissingRequiredFieldFollowTheContract() throws Exception {
        var authenticated = UsernamePasswordAuthenticationToken.authenticated(new TestMember(memberId), null,
                AuthorityUtils.createAuthorityList("ROLE_USER"));
        mvc.perform(get("/api/me/seller-profile").with(authentication(authenticated)))
                .andExpect(status().isNotFound());
        mvc.perform(put("/api/me/seller-profile").with(authentication(authenticated)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"introduction\":\"소개\",\"specialty\":\"디자인\",\"portfolioUrl\":\"https://example.com\"}"))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.id").value(Long.toString(memberId)));
        mvc.perform(put("/api/me/seller-profile").with(authentication(authenticated)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"introduction\":\"수정 소개\",\"specialty\":\"개발\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.introduction").value("수정 소개"));
        assertThat(service.getMine(memberId).portfolioUrl()).isNull();
        mvc.perform(put("/api/me/seller-profile").with(authentication(authenticated)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"specialty\":\"개발\"}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/sellers/" + memberId))
                .andExpect(status().isOk()).andExpect(jsonPath("$.introduction").value("수정 소개"))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void createUpdateAndUrlRemovalPersistWithUtcAndOriginalCreationTime() {
        assertNotFound(() -> service.getMine(memberId));
        assertThat(members.countSellerProfiles(memberId)).isZero();
        var created = service.save(memberId, new SellerProfileRequest("\u00a0 소개\r\n둘째 줄 ", " 디자인 😀 ", " https://example.com/work "));
        assertThat(created.created()).isTrue();
        assertThat(service.getPublic(Long.toString(memberId))).isEqualTo(created.profile());
        assertThat(created.profile().introduction()).isEqualTo("소개\n둘째 줄");
        assertThat(created.profile().displayName()).isEqualTo("판매자 😀");
        assertThat(members.countSellerProfiles(memberId)).isEqualTo(1);
        assertThat(time("created_at")).isEqualTo("2026-11-03 04:00:00.123456");
        assertThat(time("updated_at")).isEqualTo(time("created_at"));

        when(clock.instant()).thenReturn(firstTime.plusSeconds(60));
        var updated = service.save(memberId, new SellerProfileRequest("수정 소개", "개발", " "));
        assertThat(updated.created()).isFalse();
        assertThat(service.getMine(memberId).portfolioUrl()).isNull();
        assertThat(time("created_at")).isEqualTo("2026-11-03 04:00:00.123456");
        assertThat(time("updated_at")).isEqualTo("2026-11-03 04:01:00.123456");
        service.save(memberId, new SellerProfileRequest("소개", "디자인", "https://example.com"));
        service.save(memberId, new SellerProfileRequest("소개", "디자인", null));
        assertThat(service.getMine(memberId).portfolioUrl()).isNull();
    }

    @Test
    void invalidReplacementDoesNotChangePreviouslySavedProfile() {
        var before = service.save(memberId, new SellerProfileRequest("기존 소개", "디자인", "https://example.com")).profile();
        assertThatThrownBy(() -> service.save(memberId, new SellerProfileRequest(null, "개발", null)))
                .isInstanceOf(BusinessException.class);
        assertThat(service.getMine(memberId)).isEqualTo(before);
    }

    @Test
    void outerFailureRollsBackProfileCreation() {
        var tx = new TransactionTemplate(transactionManager);
        tx.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            service.save(memberId, new SellerProfileRequest("소개", "디자인", null));
            throw new IllegalStateException("rollback probe");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(members.countSellerProfiles(memberId)).isZero();
        assertThat(service.save(memberId, new SellerProfileRequest("재시도", "디자인", null)).created()).isTrue();
    }

    @Test
    void simultaneousFirstWritesCreateOnlyOneProfile() throws Exception {
        var pool = Executors.newFixedThreadPool(4);
        var start = new CountDownLatch(1);
        try {
            var futures = new ArrayList<java.util.concurrent.Future<SellerProfileService.SaveResult>>();
            for (int i = 0; i < 4; i++) {
                int index = i;
                futures.add(pool.submit(() -> {
                    start.await();
                    return service.save(memberId, new SellerProfileRequest("소개" + index, "분야" + index, null));
                }));
            }
            start.countDown();
            var results = new ArrayList<SellerProfileService.SaveResult>();
            for (var future : futures) results.add(future.get(20, TimeUnit.SECONDS));
            assertThat(results.stream().filter(SellerProfileService.SaveResult::created).count()).isEqualTo(1);
            assertThat(members.countSellerProfiles(memberId)).isEqualTo(1);
            var saved = service.getMine(memberId);
            String index = saved.introduction().substring(2);
            assertThat(saved.specialty()).isEqualTo("분야" + index);
        } finally {
            start.countDown();
            pool.shutdownNow();
            assertThat(pool.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "abc", "9223372036854775808", "1.5", ""})
    void invalidPublicIdIsRejected(String id) {
        assertThatThrownBy(() -> service.getPublic(id))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("VALIDATION_FAILED"));
    }

    @Test
    void missingMemberCannotCreateAProfile() {
        assertThatThrownBy(() -> service.save(Long.MAX_VALUE, new SellerProfileRequest("소개", "디자인", null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("AUTHENTICATION_REQUIRED"));
        assertNotFound(() -> service.getPublic(Long.toString(Long.MAX_VALUE)));
    }

    private String time(String column) {
        return jdbc.queryForObject("SELECT DATE_FORMAT(" + column + ", '%Y-%m-%d %H:%i:%s.%f') FROM seller_profiles WHERE member_id = ?",
                String.class, memberId);
    }

    private static void assertNotFound(org.assertj.core.api.ThrowableAssert.ThrowingCallable action) {
        assertThatThrownBy(action).isInstanceOfSatisfying(BusinessException.class,
                e -> assertThat(e.code()).isEqualTo("RESOURCE_NOT_FOUND"));
    }
}
