package com.example.lancerpick.product;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.order.service.NewOrderPolicy;
import com.example.lancerpick.product.dto.ProductSearchOptions;
import com.example.lancerpick.product.dto.ProductSummaryResponse;
import com.example.lancerpick.product.repository.ProductSearchQueries;
import com.example.lancerpick.product.service.ProductManagementService;
import com.example.lancerpick.product.service.ProductSearchService;
import com.example.lancerpick.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.sql.Statement;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;

/**
 * 판매 중인 상품 가격이 현재 신규 주문 정책의 최소 주문 금액(CONTRACT_V3 10,000원)을 따르는지 확인한다.
 * 테스트 프로필은 생성 스위치가 꺼져 있으므로 같은 컨텍스트의 빈으로 V3/LEGACY 정책 서비스를 직접 만든다.
 */
@IntegrationTest
@Transactional
class ProductOrderMinimumPriceIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager em;
    @Autowired Clock clock;
    @Autowired ProductSearchQueries queries;
    final JsonMapper json = new JsonMapper();
    final NewOrderPolicy v3 = new NewOrderPolicy(false, true);
    final NewOrderPolicy legacy = new NewOrderPolicy(false, false);
    ProductManagementService v3Products;
    ProductManagementService legacyProducts;
    long seller;
    long category;
    String token;

    @BeforeEach void fixture() {
        v3Products = new ProductManagementService(em, clock, v3);
        legacyProducts = new ProductManagementService(em, clock, legacy);
        seller = insert("INSERT INTO members(email,password_hash,display_name,role,created_at,updated_at) VALUES (?,'fixture','판매자','USER',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", UUID.randomUUID() + "@test.invalid");
        category = insert("INSERT INTO categories(name,display_order) VALUES (?,0)", UUID.randomUUID().toString());
        jdbc.update("INSERT INTO seller_profiles(member_id,introduction,specialty,created_at,updated_at) VALUES (?,'소개','분야',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", seller);
        token = "최소금액" + UUID.randomUUID().toString().substring(0, 8);
    }

    String body(long price) {
        return "{\"categoryId\":\"" + category + "\",\"title\":\"" + token + "\",\"description\":\"설명\",\"scopeDescription\":\"범위\",\"preparationDescription\":\"준비\",\"priceAmount\":" + price + ",\"durationDays\":3}";
    }

    /** 스위치를 켜기 전에 등록된 저가 상품을 흉내 낸다. */
    long legacyCheapProduct(String saleStatus) {
        return insert("INSERT INTO products(seller_id,category_id,title,description,scope_description,preparation_description,price,duration_days,sale_status,is_hidden,version,created_at,updated_at) VALUES (?,?,?,'설명','범위','준비',999,1,?,0,0,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))",
                seller, category, token, saleStatus);
    }

    @Test void createBelowMinimumIsRejectedOnlyUnderV3() {
        assertThatThrownBy(() -> v3Products.create(seller, json.readTree(body(9_999))))
                .isInstanceOfSatisfying(BusinessException.class, e -> {
                    assertThat(e.code()).isEqualTo("PRODUCT_PRICE_BELOW_ORDER_MINIMUM");
                    assertThat(e.getMessage()).contains("10,000원");
                });
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM products WHERE seller_id=?", Long.class, seller)).isZero();
        assertThat(v3Products.create(seller, json.readTree(body(10_000))).priceAmount()).isEqualTo(10_000);
        assertThat(legacyProducts.create(seller, json.readTree(body(999))).priceAmount()).isEqualTo(999);
    }

    @Test void onSaleCheapProductMustRaisePriceOnEdit() {
        long id = legacyCheapProduct("ON_SALE");
        assertThatThrownBy(() -> v3Products.patch(seller, String.valueOf(id), json.readTree("{\"title\":\"제목만\",\"expectedVersion\":0}")))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("PRODUCT_PRICE_BELOW_ORDER_MINIMUM"));
        em.clear();
        assertThat(v3Products.getMine(seller, String.valueOf(id)).title()).isEqualTo(token);
        var fixed = v3Products.patch(seller, String.valueOf(id), json.readTree("{\"priceAmount\":10000,\"expectedVersion\":0}"));
        assertThat(fixed.priceAmount()).isEqualTo(10_000);
        assertThat(fixed.version()).isEqualTo(1);
    }

    @Test void stoppedCheapProductCanBeEditedButNotResumedUntilPriceIsRaised() {
        long id = legacyCheapProduct("STOPPED");
        var edited = v3Products.patch(seller, String.valueOf(id), json.readTree("{\"title\":\"중지 중 수정\",\"expectedVersion\":0}"));
        assertThat(edited.priceAmount()).isEqualTo(999);
        assertThatThrownBy(() -> v3Products.changeSaleStatus(seller, String.valueOf(id), json.readTree("{\"saleStatus\":\"ON_SALE\",\"expectedVersion\":1}")))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("PRODUCT_PRICE_BELOW_ORDER_MINIMUM"));
        em.clear();
        assertThat(v3Products.getMine(seller, String.valueOf(id)).saleStatus()).isEqualTo("STOPPED");
        v3Products.patch(seller, String.valueOf(id), json.readTree("{\"priceAmount\":15000,\"expectedVersion\":1}"));
        var resumed = v3Products.changeSaleStatus(seller, String.valueOf(id), json.readTree("{\"saleStatus\":\"ON_SALE\",\"expectedVersion\":2}"));
        assertThat(resumed.saleStatus()).isEqualTo("ON_SALE");
    }

    @Test void publicListHidesUnorderableProductsButDetailStillExplains() {
        long cheap = legacyCheapProduct("ON_SALE");
        long orderable = v3Products.create(seller, json.readTree(body(10_000))).id().transform(Long::parseLong);
        var v3Search = new ProductSearchService(queries, v3);
        var legacySearch = new ProductSearchService(queries, legacy);

        var v3Page = v3Search.search(token, null, 0, 20, ProductSearchOptions.DEFAULT);
        assertThat(v3Page.items()).extracting(ProductSummaryResponse::id).containsExactly(String.valueOf(orderable));
        assertThat(v3Page.totalElements()).isEqualTo(1);
        assertThat(legacySearch.search(token, null, 0, 20, ProductSearchOptions.DEFAULT).items())
                .extracting(ProductSummaryResponse::id).containsExactlyInAnyOrder(String.valueOf(cheap), String.valueOf(orderable));

        var detail = v3Search.getPublicDetail(String.valueOf(cheap));
        assertThat(detail.priceAmount()).isEqualTo(999);
        assertThat(detail.minimumOrderAmount()).isEqualTo(10_000L);
    }

    long insert(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int i = 0; i < args.length; i++) statement.setObject(i + 1, args[i]);
            return statement;
        }, key);
        return key.getKey().longValue();
    }
}
