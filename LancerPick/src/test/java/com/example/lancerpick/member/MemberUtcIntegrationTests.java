package com.example.lancerpick.member;

import com.example.lancerpick.member.service.MemberService;
import com.example.lancerpick.support.IntegrationTest;
import com.example.lancerpick.support.TestMembers;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import static org.assertj.core.api.SoftAssertions.assertSoftly;

/** JPA 왕복만으로 숨겨지는 시간대 변환을 DB 원문으로 검사한다. */
@IntegrationTest
@Transactional
@Import(MemberUtcIntegrationTests.FixedClock.class)
class MemberUtcIntegrationTests {
    @Autowired MemberService members;
    @Autowired JdbcTemplate jdbc;

    @Test
    void signupPersistsUtcMicrosecondsInMemberAndInitialGrant() {
        // @Transactional 테스트라 가입 증명 행도 테스트가 끝나면 함께 롤백된다.
        var member = members.signup(TestMembers.signupRequest(jdbc, "utc-" + UUID.randomUUID() + "@example.com", "abcd1234", "시각 검사"));
        long id = Long.parseLong(member.id());
        assertSoftly(softly -> {
            for (String column : new String[]{"created_at", "updated_at"}) {
                softly.assertThat(raw("members", column, "id", id)).as("members." + column)
                        .isEqualTo("2026-11-02T06:00:00.123456");
            }
            softly.assertThat(raw("wallets", "updated_at", "member_id", id)).as("wallets.updated_at")
                    .isEqualTo("2026-11-02T06:00:00.123456");
            softly.assertThat(raw("wallet_entries", "created_at", "member_id", id)).as("wallet_entries.created_at")
                    .isEqualTo("2026-11-02T06:00:00.123456");
        });
    }

    private String raw(String table, String column, String key, long id) {
        return jdbc.queryForObject("SELECT DATE_FORMAT(" + column + ", '%Y-%m-%dT%H:%i:%s.%f') FROM "
                + table + " WHERE " + key + " = ?", String.class, id);
    }

    @TestConfiguration
    static class FixedClock {
        @Bean @Primary Clock utcClock() {
            return Clock.fixed(Instant.parse("2026-11-02T06:00:00.123456Z"), ZoneOffset.UTC);
        }
    }
}
