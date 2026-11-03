package com.example.lancerpick.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.support.IntegrationTest;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 실제 Security 필터 → AdminProductController → Service → MySQL 검사.
 * 로그인 자체는 테스트 principal로 대체한다(로그인은 AuthLoginIntegrationTests에서 검사).
 * 이 테스트가 만든 회원·카테고리·상품·운영 이력만 정리한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class AdminProductApiIntegrationTests {
    private static final LocalDateTime OLDER = LocalDateTime.of(2026, 11, 1, 0, 0);
    private static final LocalDateTime NEWER = LocalDateTime.of(2026, 11, 2, 0, 0);

    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;

    record TestMember(Long memberId) implements AuthenticatedMember {}

    long admin, user, seller, category, onSale, hiddenStopped;
    String token;

    @BeforeEach
    void fixtures() {
        admin = member("ADMIN", "관리자");
        user = member("USER", "일반회원");
        seller = member("USER", "판매자");
        category = insert("INSERT INTO categories(name,display_order) VALUES (?,0)", "admin-it-" + UUID.randomUUID());
        // 검색 결과를 이 테스트 데이터로만 좁히기 위한 고유 문자열
        token = "admintok" + UUID.randomUUID().toString().substring(0, 8);
        onSale = product(token + "%off 랜딩", "설명", "ON_SALE", false, OLDER);
        hiddenStopped = product(token + "-plain 로고", "설명 " + token, "STOPPED", true, NEWER);
    }

    @AfterEach
    void cleanup() {
        jdbc.update("DELETE FROM product_moderation_logs WHERE product_id IN (?,?)", onSale, hiddenStopped);
        jdbc.update("DELETE FROM products WHERE id IN (?,?)", onSale, hiddenStopped);
        jdbc.update("DELETE FROM categories WHERE id=?", category);
        jdbc.update("DELETE FROM members WHERE id IN (?,?,?)", admin, user, seller);
    }

    // ---------- 권한 ----------

    @Test
    void anonymousIs401AndNormalUserIs403OnEveryAdminEndpoint() throws Exception {
        mvc.perform(get("/api/admin/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/products").with(as(user, "ROLE_USER")))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/api/admin/products/" + onSale).with(as(user, "ROLE_USER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/products/" + onSale + "/moderation-logs").with(as(user, "ROLE_USER")))
                .andExpect(status().isForbidden());
        visibility(user, "ROLE_USER", onSale, true, "사유", 0).andExpect(status().isForbidden());
        assertThat(hidden(onSale)).isFalse();
    }

    @Test
    void sessionStillSaysAdminButDbRoleWasRemovedIs403AndNothingChanges() throws Exception {
        jdbc.update("UPDATE members SET role='USER' WHERE id=?", admin);
        visibility(onSale, true, "사유", 0)
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        assertThat(hidden(onSale)).isFalse();
        assertThat(logCount(onSale)).isZero();
    }

    @Test
    void patchWithoutCsrfIsForbidden() throws Exception {
        mvc.perform(patch("/api/admin/products/" + onSale + "/visibility").with(as(admin, "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(true, "사유", 0)))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
        assertThat(hidden(onSale)).isFalse();
    }

    // ---------- 목록 ----------

    @Test
    void listReturnsSummaryFieldsNewestFirstWithoutDetailFields() throws Exception {
        adminGet("/api/admin/products?keyword=" + token)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.items[0].id").value(Long.toString(hiddenStopped)))
                .andExpect(jsonPath("$.items[1].id").value(Long.toString(onSale)))
                .andExpect(jsonPath("$.items[0].isHidden").value(true))
                .andExpect(jsonPath("$.items[0].saleStatus").value("STOPPED"))
                .andExpect(jsonPath("$.items[0].version").value(0))
                .andExpect(jsonPath("$.items[0].priceAmount").value(100000))
                .andExpect(jsonPath("$.items[0].durationDays").value(3))
                .andExpect(jsonPath("$.items[0].category.id").value(Long.toString(category)))
                .andExpect(jsonPath("$.items[0].seller.id").value(Long.toString(seller)))
                .andExpect(jsonPath("$.items[0].seller.displayName").value("판매자"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-11-02T00:00:00Z"))
                .andExpect(jsonPath("$.items[0].description").doesNotExist())
                .andExpect(jsonPath("$.items[0].seller.email").doesNotExist());
    }

    @Test
    void filtersAreCombinedWithAnd() throws Exception {
        adminGet("/api/admin/products?keyword=" + token + "&isHidden=true&saleStatus=STOPPED")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(Long.toString(hiddenStopped)));
        adminGet("/api/admin/products?keyword=" + token + "&isHidden=true&saleStatus=ON_SALE")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(0));
        adminGet("/api/admin/products?keyword=" + token + "&isHidden=false")
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(Long.toString(onSale)));
    }

    @Test
    void keywordSearchesTitleOrDescriptionAndTreatsPercentAsPlainText() throws Exception {
        // 두 번째 상품은 설명에만 token이 한 번 더 들어 있다. % 는 와일드카드가 아니라 글자 그대로다.
        mvc.perform(get("/api/admin/products").param("keyword", "  " + token + "%off  ").with(as(admin, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].id").value(Long.toString(onSale)));
        mvc.perform(get("/api/admin/products").param("keyword", token + "_plain").with(as(admin, "ROLE_ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }

    @Test
    void pageOutOfRangeReturnsEmptyItemsWithTotals() throws Exception {
        adminGet("/api/admin/products?keyword=" + token + "&page=5&size=1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.totalPages").value(2));
    }

    @ParameterizedTest
    @ValueSource(strings = {"isHidden=yes", "isHidden=", "isHidden=TRUE", "saleStatus=DELETED", "saleStatus=",
            "page=-1", "size=0", "size=101", "page=abc"})
    void invalidQueryIs400(String query) throws Exception {
        adminGet("/api/admin/products?" + query)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void keywordOver100CodePointsIs400() throws Exception {
        mvc.perform(get("/api/admin/products").param("keyword", "가".repeat(101)).with(as(admin, "ROLE_ADMIN")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(get("/api/admin/products").param("keyword", "   ").with(as(admin, "ROLE_ADMIN")))
                .andExpect(status().isOk());
    }

    // ---------- 상세·이력 ----------

    @Test
    void detailIncludesDescriptionAndIsHidden() throws Exception {
        adminGet("/api/admin/products/" + hiddenStopped)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(Long.toString(hiddenStopped)))
                .andExpect(jsonPath("$.isHidden").value(true))
                .andExpect(jsonPath("$.description").value("설명 " + token))
                .andExpect(jsonPath("$.scopeDescription").value("범위"))
                .andExpect(jsonPath("$.preparationDescription").value("준비"))
                .andExpect(jsonPath("$.saleStatus").value("STOPPED"))
                .andExpect(jsonPath("$.version").value(0));
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0", "-1", "9223372036854775808"})
    void invalidProductIdIs400(String id) throws Exception {
        adminGet("/api/admin/products/" + id)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void missingProductIs404() throws Exception {
        adminGet("/api/admin/products/999999999999")
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        adminGet("/api/admin/products/999999999999/moderation-logs").andExpect(status().isNotFound());
        visibility(999_999_999_999L, true, "사유", 0).andExpect(status().isNotFound());
    }

    // ---------- 숨김·복구 ----------

    @Test
    void hideChangesOnlyHiddenAndVersionAndRecordsOneLog() throws Exception {
        LocalDateTime before = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        visibility(onSale, true, "  부적절한 내용  ", 0)
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.isHidden").value(true))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.saleStatus").value("ON_SALE"));

        assertThat(hidden(onSale)).isTrue();
        assertThat(jdbc.queryForObject("SELECT sale_status FROM products WHERE id=?", String.class, onSale)).isEqualTo("ON_SALE");
        assertThat(jdbc.queryForObject("SELECT updated_at FROM products WHERE id=?", LocalDateTime.class, onSale))
                .isAfterOrEqualTo(before);
        assertThat(logCount(onSale)).isEqualTo(1);
        var log = jdbc.queryForMap("SELECT admin_id, action, reason FROM product_moderation_logs WHERE product_id=?", onSale);
        assertThat(((Number) log.get("admin_id")).longValue()).isEqualTo(admin);
        assertThat(log.get("action")).isEqualTo("HIDE");
        assertThat(log.get("reason")).isEqualTo("부적절한 내용");
    }

    @Test
    void sameStateResendReturnsCurrentDetailEvenWithOldVersionAndAddsNothing() throws Exception {
        visibility(onSale, true, "첫 요청", 0).andExpect(status().isOk());
        visibility(onSale, true, "재전송", 0)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isHidden").value(true))
                .andExpect(jsonPath("$.version").value(1));
        assertThat(version(onSale)).isEqualTo(1);
        assertThat(logCount(onSale)).isEqualTo(1);
    }

    @Test
    void staleVersionIs409AndLeavesStateAndLogsUnchanged() throws Exception {
        visibility(onSale, true, "숨김", 0).andExpect(status().isOk()); // version 0 → 1
        visibility(onSale, false, "복구", 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("PRODUCT_VERSION_CONFLICT"));
        assertThat(hidden(onSale)).isTrue();
        assertThat(version(onSale)).isEqualTo(1);
        assertThat(logCount(onSale)).isEqualTo(1);
    }

    @Test
    void restoreThenLogsAreNewestFirst() throws Exception {
        visibility(onSale, true, "숨김 사유", 0).andExpect(status().isOk());
        visibility(onSale, false, "복구 사유", 1)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isHidden").value(false))
                .andExpect(jsonPath("$.version").value(2));

        adminGet("/api/admin/products/" + onSale + "/moderation-logs")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].action").value("RESTORE"))
                .andExpect(jsonPath("$.items[0].reason").value("복구 사유"))
                .andExpect(jsonPath("$.items[0].adminId").value(Long.toString(admin)))
                .andExpect(jsonPath("$.items[0].id").isString())
                .andExpect(jsonPath("$.items[1].action").value("HIDE"));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"hidden\":true,\"expectedVersion\":0}",
            "{\"hidden\":true,\"reason\":\"   \",\"expectedVersion\":0}",
            "{\"hidden\":\"true\",\"reason\":\"사유\",\"expectedVersion\":0}",
            "{\"hidden\":1,\"reason\":\"사유\",\"expectedVersion\":0}",
            "{\"hidden\":true,\"reason\":123,\"expectedVersion\":0}",
            "{\"hidden\":true,\"reason\":\"사유\",\"expectedVersion\":\"0\"}",
            "{\"hidden\":true,\"reason\":\"사유\",\"expectedVersion\":-1}",
            "{\"hidden\":true,\"reason\":\"사유\",\"expectedVersion\":0.5}",
            "{\"hidden\":null,\"reason\":\"사유\",\"expectedVersion\":0}",
            "{\"hidden\":true,\"reason\":\"사유\"}"
    })
    void invalidBodyIs400AndChangesNothing(String json) throws Exception {
        mvc.perform(patch("/api/admin/products/" + onSale + "/visibility").with(as(admin, "ROLE_ADMIN")).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        assertThat(hidden(onSale)).isFalse();
        assertThat(logCount(onSale)).isZero();
    }

    @Test
    void reasonOver1000CodePointsIs400() throws Exception {
        visibility(onSale, true, "가".repeat(1001), 0).andExpect(status().isBadRequest());
        visibility(onSale, true, "가".repeat(1000), 0).andExpect(status().isOk());
    }

    // ---------- 도우미 ----------

    private ResultActions adminGet(String url) throws Exception {
        return mvc.perform(get(url).with(as(admin, "ROLE_ADMIN")));
    }

    private ResultActions visibility(long productId, boolean hidden, String reason, long expectedVersion) throws Exception {
        return visibility(admin, "ROLE_ADMIN", productId, hidden, reason, expectedVersion);
    }

    private ResultActions visibility(long memberId, String role, long productId, boolean hidden, String reason,
            long expectedVersion) throws Exception {
        return mvc.perform(patch("/api/admin/products/" + productId + "/visibility")
                .with(as(memberId, role)).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body(hidden, reason, expectedVersion)));
    }

    private static String body(boolean hidden, String reason, long expectedVersion) {
        return "{\"hidden\":" + hidden + ",\"reason\":\"" + reason + "\",\"expectedVersion\":" + expectedVersion + "}";
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor as(long memberId, String role) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(new TestMember(memberId), null,
                AuthorityUtils.createAuthorityList(role)));
    }

    private boolean hidden(long productId) {
        return Boolean.TRUE.equals(jdbc.queryForObject("SELECT is_hidden FROM products WHERE id=?", Boolean.class, productId));
    }

    private long version(long productId) {
        return jdbc.queryForObject("SELECT version FROM products WHERE id=?", Long.class, productId);
    }

    private long logCount(long productId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM product_moderation_logs WHERE product_id=?", Long.class, productId);
    }

    private long member(String role, String displayName) {
        return insert("INSERT INTO members(email,password_hash,display_name,role,created_at,updated_at) VALUES (?,'fixture',?,?,?,?)",
                UUID.randomUUID() + "@admin-it.test", displayName, role, OLDER, OLDER);
    }

    private long product(String title, String description, String saleStatus, boolean hidden, LocalDateTime createdAt) {
        return insert("""
                INSERT INTO products(seller_id,category_id,title,description,scope_description,preparation_description,
                    price,duration_days,sale_status,is_hidden,created_at,updated_at)
                VALUES (?,?,?,?,'범위','준비',100000,3,?,?,?,?)
                """, seller, category, title, description, saleStatus, hidden, createdAt, createdAt);
    }

    private long insert(String sql, Object... values) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            return statement;
        }, key);
        return key.getKey().longValue();
    }
}
