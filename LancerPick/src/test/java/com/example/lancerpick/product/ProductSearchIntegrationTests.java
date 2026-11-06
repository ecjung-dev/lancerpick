package com.example.lancerpick.product;

import com.example.lancerpick.product.dto.ProductSearchOptions;

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

        var page = service.search(token, Long.toString(category), 0, 20, ProductSearchOptions.DEFAULT);
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
        assertThat(service.search(null, Long.toString(category), 0, 1, ProductSearchOptions.DEFAULT).items().getFirst().id())
                .isEqualTo(Long.toString(second));
        var page = service.search(" ", Long.toString(category), 1, 1, ProductSearchOptions.DEFAULT);
        assertThat(page.items().getFirst().id()).isEqualTo(Long.toString(first));
        assertThat(page.totalElements()).isEqualTo(2);
        assertThat(page.totalPages()).isEqualTo(2);
        var beyond = service.search(null, Long.toString(category), Integer.MAX_VALUE, 100, ProductSearchOptions.DEFAULT);
        assertThat(beyond.items()).isEmpty();
        assertThat(beyond.totalElements()).isEqualTo(2);
        assertThat(service.search(token + "absent", null, 0, 20, ProductSearchOptions.DEFAULT).items()).isEmpty();
        assertThat(service.search(null, Long.toString(Long.MAX_VALUE), 0, 20, ProductSearchOptions.DEFAULT).totalElements()).isZero();
    }

    @Test
    void publicDetailReturnsContractAndUtcWithoutPrivateData() throws Exception {
        long id = product("상세 상품", "첫 줄\n<script>문자 그대로</script>", category, "ON_SALE", false, NOW);
        mvc.perform(get("/api/products/" + id)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(Long.toString(id)))
                .andExpect(jsonPath("$.title").value("상세 상품"))
                .andExpect(jsonPath("$.description").value("첫 줄\n<script>문자 그대로</script>"))
                .andExpect(jsonPath("$.scopeDescription").value("scope"))
                .andExpect(jsonPath("$.preparationDescription").value("preparation"))
                .andExpect(jsonPath("$.priceAmount").value(50000))
                .andExpect(jsonPath("$.durationDays").value(3))
                .andExpect(jsonPath("$.category.id").value(Long.toString(category)))
                .andExpect(jsonPath("$.seller.id").value(Long.toString(seller)))
                .andExpect(jsonPath("$.version").value(0))
                .andExpect(jsonPath("$.saleStatus").value("ON_SALE"))
                .andExpect(jsonPath("$.createdAt").value("2026-11-03T01:00:00Z"))
                .andExpect(jsonPath("$.updatedAt").value("2026-11-03T01:00:00Z"))
                .andExpect(jsonPath("$.isHidden").doesNotExist())
                .andExpect(jsonPath("$.seller.email").doesNotExist())
                .andExpect(jsonPath("$.seller.passwordHash").doesNotExist());
    }

    @Test
    void stoppedHiddenAndMissingDetailsAreIndistinguishable() throws Exception {
        long hidden = product(token, "秘密", category, "ON_SALE", true, NOW);
        long stopped = product(token, "秘密", category, "STOPPED", false, NOW);
        for (long id : new long[] {hidden, stopped, Long.MAX_VALUE}) {
            mvc.perform(get("/api/products/" + id)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                    .andExpect(jsonPath("$.description").doesNotExist());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!", "\\", "' OR 1=1 --"})
    void wildcardEscapeAndSqlCharactersAreLiteralSearchText(String special) {
        long id = product(token + special, "설명", category, "ON_SALE", false, NOW);
        product(token + "ordinary", "설명", category, "ON_SALE", false, NOW);
        var page = service.search(token + special, Long.toString(category), 0, 20, ProductSearchOptions.DEFAULT);
        assertThat(page.totalElements()).isEqualTo(1);
        assertThat(page.items().getFirst().id()).isEqualTo(Long.toString(id));
    }

    @Test
    void combinesInclusiveBoundsAndKeepsPriceTiesStableAcrossPages() throws Exception {
        long cheap = product(token, "저가", category, "ON_SALE", false, NOW);
        long firstTie = product(token, "동가", category, "ON_SALE", false, NOW);
        long lastTie = product(token, "동가", category, "ON_SALE", false, NOW);
        long slow = product(token, "기간 초과", category, "ON_SALE", false, NOW);
        product(token, "숨김", category, "ON_SALE", true, NOW);
        product(token, "판매 중지", category, "STOPPED", false, NOW);
        jdbc.update("UPDATE products SET price=10000 WHERE id=?", cheap);
        jdbc.update("UPDATE products SET duration_days=4 WHERE id=?", slow);

        mvc.perform(get("/api/products").param("keyword", token).param("categoryId", Long.toString(category))
                        .param("minPrice", "10000").param("maxPrice", "50000").param("maxDurationDays", "3")
                        .param("sort", "PRICE_ASC").param("size", "2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andExpect(jsonPath("$.items[0].id").value(Long.toString(cheap)))
                .andExpect(jsonPath("$.items[1].id").value(Long.toString(lastTie)));
        var ascending = ProductSearchOptions.parse("10000", "50000", "3", "PRICE_ASC", null);
        assertThat(service.search(token, Long.toString(category), 1, 2, ascending).items())
                .extracting(item -> item.id()).containsExactly(Long.toString(firstTie));
        var descending = ProductSearchOptions.parse("10000", "50000", "3", "PRICE_DESC", null);
        assertThat(service.search(token, Long.toString(category), 0, 4, descending).items())
                .extracting(item -> item.id())
                .containsExactly(Long.toString(lastTie), Long.toString(firstTie), Long.toString(cheap));
    }

    @Test
    void sellerFilterReturnsOnlyThatSellersPublicOnSaleProducts() throws Exception {
        long otherSeller = insert("INSERT INTO members(email,password_hash,display_name,role,created_at,updated_at) VALUES (?, 'fixture', '다른 판매자', 'USER', ?, ?)",
                "other-" + token + "@test.invalid", NOW, NOW);
        long listed = product(token, "공개", category, "ON_SALE", false, NOW);
        product(token, "숨김", category, "ON_SALE", true, NOW);
        product(token, "중지", category, "STOPPED", false, NOW);
        long other = product(token, "다른 판매자 상품", category, "ON_SALE", false, NOW);
        jdbc.update("UPDATE products SET seller_id=? WHERE id=?", otherSeller, other);

        mvc.perform(get("/api/products").param("sellerId", Long.toString(seller)).param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(Long.toString(listed)))
                .andExpect(jsonPath("$.items[0].seller.id").value(Long.toString(seller)))
                .andExpect(jsonPath("$.items[0].seller.email").doesNotExist())
                .andExpect(jsonPath("$.items[0].seller.role").doesNotExist());
        mvc.perform(get("/api/products").param("sellerId", Long.toString(seller)).param("page", "1").param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items").isEmpty());
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
