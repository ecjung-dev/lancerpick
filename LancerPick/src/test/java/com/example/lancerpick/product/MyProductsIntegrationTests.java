package com.example.lancerpick.product;

import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.support.IntegrationTest;
import java.sql.Statement;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class MyProductsIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    long owner;
    long other;
    long category;
    record Principal(Long memberId) implements AuthenticatedMember {}
    org.springframework.security.core.Authentication auth(long memberId) {
        return UsernamePasswordAuthenticationToken.authenticated(new Principal(memberId), null, AuthorityUtils.createAuthorityList("ROLE_USER"));
    }
    @BeforeEach void fixture() {
        owner = member(); other = member();
        category = insert("INSERT INTO categories(name,display_order) VALUES (?,0)", UUID.randomUUID().toString());
    }
    long member() {
        return insert("INSERT INTO members(email,password_hash,display_name,role,created_at,updated_at) VALUES (?,'fixture','판매자','USER',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", UUID.randomUUID() + "@test.invalid");
    }
    long product(long memberId, String title, String status, boolean hidden) {
        return insert("""
                INSERT INTO products(seller_id,category_id,title,description,scope_description,preparation_description,
                    price,duration_days,sale_status,is_hidden,version,created_at,updated_at)
                VALUES (?, ?, ?, '설명', '범위', '준비', 50000, 3, ?, ?, 2, '2026-11-04 00:00:00', '2026-11-04 01:00:00')
                """, memberId, category, title, status, hidden);
    }
    @Test void onlyOwnerProductsIncludingStoppedHiddenArePagedNewestFirst() throws Exception {
        product(owner, "공개 상품", "ON_SALE", false);
        long stopped = product(owner, "중지 상품", "STOPPED", false);
        long hidden = product(owner, "숨김 상품", "ON_SALE", true);
        product(other, "타인 비공개 상품", "STOPPED", true);
        mvc.perform(get("/api/me/products").with(authentication(auth(owner))).param("sellerId", Long.toString(other)).param("size", "2"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.totalElements").value(3)).andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(Long.toString(hidden)))
                .andExpect(jsonPath("$.items[0].seller.id").value(Long.toString(owner)))
                .andExpect(jsonPath("$.items[0].isHidden").value(true))
                .andExpect(jsonPath("$.items[0].version").value(2))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-11-04T00:00:00Z"))
                .andExpect(jsonPath("$.items[0].seller.email").doesNotExist())
                .andExpect(jsonPath("$.items[0].description").doesNotExist())
                .andExpect(jsonPath("$.items[1].id").value(Long.toString(stopped)))
                .andExpect(jsonPath("$.items[1].saleStatus").value("STOPPED"));
        mvc.perform(get("/api/me/products").with(authentication(auth(owner))).param("page", "1").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(1));
        mvc.perform(get("/api/me/products").with(authentication(auth(other))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].title").value("타인 비공개 상품"));
    }
    @Test void anonymousCannotListButMemberWithoutProfileGetsEmptyPage() throws Exception {
        mvc.perform(get("/api/me/products")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/me/products").with(authentication(auth(owner))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalElements").value(0)).andExpect(jsonPath("$.totalPages").value(0));
    }
    @ParameterizedTest
    @CsvSource({"page,-1", "page,abc", "page,2147483648", "size,0", "size,101", "size,1.5"})
    void invalidPageFails(String key, String value) throws Exception {
        mvc.perform(get("/api/me/products").with(authentication(auth(owner))).param(key, value))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
    @Test void beyondLastPageKeepsTotalsWithoutOffsetOverflow() throws Exception {
        product(owner, "상품", "ON_SALE", false);
        mvc.perform(get("/api/me/products").with(authentication(auth(owner))).param("page", "2147483647").param("size", "100"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(1)).andExpect(jsonPath("$.totalPages").value(1));
    }
    long insert(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < args.length; index++) statement.setObject(index + 1, args[index]);
            return statement;
        }, key);
        return key.getKey().longValue();
    }
}
