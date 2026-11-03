package com.example.lancerpick.product;

import com.example.lancerpick.product.service.ProductSearchService;
import com.example.lancerpick.support.IntegrationTest;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** TEST_DB_* 전용 실제 MySQL 조회. fixture는 롤백하며 개인·공유 개발 데이터를 쓰지 않는다. */
@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class ProductSearchIntegrationTests {
    private static final LocalDateTime NOW = LocalDateTime.parse("2026-11-03T01:00:00");
    @Autowired JdbcTemplate jdbc;
    @Autowired ProductSearchService service;
    @Autowired MockMvc mvc;
    private long seller;
    private long category;
    private long otherCategory;
    private String token;

    @BeforeEach
    void fixtures() {
        token = UUID.randomUUID().toString();
        seller = insert("INSERT INTO members(email,password_hash,display_name,role,created_at,updated_at) VALUES (?, 'fixture', '판매자', 'USER', ?, ?)",
                token + "@test.invalid", NOW, NOW);
        category = insert("INSERT INTO categories(name,display_order) VALUES (?,0)", "search-" + token);
        otherCategory = insert("INSERT INTO categories(name,display_order) VALUES (?,1)", "other-" + token);
    }

    @Test
    void matchesTitleOrDescriptionButNeverHiddenOrStoppedProducts() throws Exception {
        long title = product(token + " 제목", "설명", category, "ON_SALE", false, NOW);
        long description = product("제목", token + " 설명", category, "ON_SALE", false, NOW.plusSeconds(1));
        product(token, token, category, "ON_SALE", true, NOW);
        product(token, token, category, "STOPPED", false, NOW);
        product("관련 없음", "관련 없음", category, "ON_SALE", false, NOW);
        product(token, token, otherCategory, "ON_SALE", false, NOW);

        var page = service.search(token, Long.toString(category), 0, 20);
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.items()).extracting(item -> item.id())
                .containsExactly(Long.toString(description), Long.toString(title));
        mvc.perform(get("/api/products").param("keyword", token).param("categoryId", Long.toString(category)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].id").value(Long.toString(description)))
                .andExpect(jsonPath("$.items[0].priceAmount").value(50000))
                .andExpect(jsonPath("$.items[0].category.id").value(Long.toString(category)))
                .andExpect(jsonPath("$.items[0].seller.id").value(Long.toString(seller)))
                .andExpect(jsonPath("$.items[0].seller.displayName").value("판매자"))
                .andExpect(jsonPath("$.items[0].seller.email").doesNotExist())
                .andExpect(jsonPath("$.items[0].description").doesNotExist());
    }

    @Test
    void sameTimestampUsesDescendingIdAndPagingKeepsFilteredTotals() {
        long first = product(token, "설명", category, "ON_SALE", false, NOW);
        long second = product(token, "설명", category, "ON_SALE", false, NOW);
        assertThat(service.search(null, Long.toString(category), 0, 1).items().getFirst().id())
                .isEqualTo(Long.toString(second));
        var page = service.search(" ", Long.toString(category), 1, 1);
        assertThat(page.items().getFirst().id()).isEqualTo(Long.toString(first));
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
        var beyond = service.search(null, Long.toString(category), Integer.MAX_VALUE, 100);
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.totalElements()).isEqualTo(2);
        assertThat(service.search(token + "absent", null, 0, 20).items()).isEmpty();
        assertThat(service.search(null, Long.toString(Long.MAX_VALUE), 0, 20).totalElements()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!", "\\", "' OR 1=1 --"})
    void wildcardEscapeAndSqlCharactersAreLiteralSearchText(String special) {
        long id = product(token + special, "설명", category, "ON_SALE", false, NOW);
        product(token + "ordinary", "설명", category, "ON_SALE", false, NOW);
        var page = service.search(token + special, Long.toString(category), 0, 20);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items().getFirst().id()).isEqualTo(Long.toString(id));
    }

    private long product(String title, String description, long categoryId, String status, boolean hidden, LocalDateTime at) {
        return insert("""
                INSERT INTO products(seller_id,category_id,title,description,scope_description,
                  preparation_description,price,duration_days,sale_status,is_hidden,created_at,updated_at)
                VALUES (?, ?, ?, ?, 'scope', 'preparation', 50000, 3, ?, ?, ?, ?)
                """, seller, categoryId, title, description, status, hidden, at, at);
    }

    private long insert(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return key.getKey().longValue();
    }
}
