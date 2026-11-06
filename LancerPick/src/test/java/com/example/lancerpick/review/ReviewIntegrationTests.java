package com.example.lancerpick.review;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.order.dto.CreateOrderRequest;
import com.example.lancerpick.order.service.OrderCreationService;
import com.example.lancerpick.review.dto.ReviewRequest;
import com.example.lancerpick.review.service.ReviewService;
import com.example.lancerpick.support.IntegrationTest;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
@AutoConfigureMockMvc
class ReviewIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @Autowired ReviewService reviews;
    @Autowired OrderCreationService creation;
    long buyer, seller, stranger, product, category, order;
    List<Long> orderIds = new ArrayList<>();
    record Principal(Long memberId) implements AuthenticatedMember {}

    @BeforeEach void setup() {
        buyer = member(); seller = member(); stranger = member();
        category = insert("INSERT INTO categories(name,display_order) VALUES (?,0)", UUID.randomUUID().toString());
        product = insert("""
                INSERT INTO products(seller_id,category_id,title,description,scope_description,preparation_description,
                price,duration_days,created_at,updated_at) VALUES (?,?,'상품','설명','범위','준비',50000,3,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))
                """, seller, category);
        order = newOrder();
    }
    long newOrder() {
        long id = creation.create(buyer, new CreateOrderRequest(Long.toString(product), 0L,
                "요구사항", UUID.randomUUID().toString())).orderId();
        orderIds.add(id);
        return id;
    }
    void complete(long id) {
        jdbc.update("""
                UPDATE orders SET status='COMPLETED',accepted_at=created_at,buyer_confirmed_terms_at=created_at,
                paid_at=created_at,due_at=DATE_ADD(created_at,INTERVAL 3 DAY),completed_at=created_at,updated_at=created_at WHERE id=?
                """, id);
    }
    @AfterEach void cleanup() {
        for (long id : orderIds) {
            jdbc.update("DELETE FROM reviews WHERE order_id=?", id);
            jdbc.update("DELETE FROM order_histories WHERE order_id=?", id);
            jdbc.update("DELETE FROM order_snapshots WHERE order_id=?", id);
            jdbc.update("DELETE FROM orders WHERE id=?", id);
        }
        jdbc.update("DELETE FROM products WHERE id=?", product);
        jdbc.update("DELETE FROM categories WHERE id=?", category);
        for (long id : new long[] {buyer, seller, stranger}) jdbc.update("DELETE FROM members WHERE id=?", id);
    }
    @Test void buyerWritesOnceAndOrderDetailNoLongerOffersWrite() throws Exception {
        complete(order);
        mvc.perform(post("/api/orders/{id}/review", order).with(authentication(auth(buyer))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5,\"content\":\"　좋아요\\r\\n감사해요　\"}"))
                .andExpect(status().isCreated()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.orderId").value(Long.toString(order)))
                .andExpect(jsonPath("$.id").isString()).andExpect(jsonPath("$.content").value("좋아요\n감사해요"))
                .andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.endsWith("Z")));
        assertThatThrownBy(() -> reviews.write(buyer, "" + order, new ReviewRequest(4, null)))
                .isInstanceOfSatisfying(BusinessException.class, e -> assertThat(e.code()).isEqualTo("REVIEW_ALREADY_EXISTS"));
        mvc.perform(get("/api/orders/{id}", order).with(authentication(auth(buyer))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.review.rating").value(5))
                .andExpect(jsonPath("$.allowedActions").isEmpty());
    }
    @Test void permissionsAndOrderStatusAreEnforced() throws Exception {
        mvc.perform(post("/api/orders/{id}/review", order).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/orders/{id}/review", order).with(authentication(auth(buyer))).contentType(MediaType.APPLICATION_JSON).content("{\"rating\":5}"))
                .andExpect(status().isForbidden());
        expectWrite(buyer, "{\"rating\":5}", 409);
        complete(order);
        expectWrite(seller, "{\"rating\":5}", 403);
        expectWrite(stranger, "{\"rating\":5}", 404);
        expectWrite(buyer, "{\"rating\":5,\"content\":null}", 201);
    }
    @Test void invalidInputsAndUnicodeLimitsDoNotWrite() throws Exception {
        complete(order);
        for (String body : List.of("{}", "{\"rating\":0}", "{\"rating\":6}", "{\"rating\":1.5}",
                "{\"rating\":\"5\"}", "{\"rating\":true}", "{\"rating\":5,\"content\":\"　\"}",
                "{\"rating\":5,\"content\":\"" + "😀".repeat(2001) + "\"}")) {
            expectWrite(buyer, body, 400);
        }
        expectWrite(buyer, "{\"rating\":1,\"content\":\"" + "😀".repeat(2000) + "\"}", 201);
    }
    @Test void publicListPaginatesDeterministicallyAndNeverExposesOrderOrMemberIds() throws Exception {
        complete(order);
        reviews.write(buyer, "" + order, new ReviewRequest(5, "<script>text</script>"));
        long second = newOrder(); complete(second);
        var later = reviews.write(buyer, "" + second, new ReviewRequest(1, null));
        jdbc.update("UPDATE reviews SET created_at='2026-11-04 01:00:00' WHERE order_id IN (?,?)", order, second);
        mvc.perform(get("/api/products/{id}/reviews", product).param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.summary.reviewCount").value(2))
                .andExpect(jsonPath("$.summary.averageRating").value(3.0))
                .andExpect(jsonPath("$.totalPages").value(2)).andExpect(jsonPath("$.items[0].id").value(later.id()))
                .andExpect(jsonPath("$.items[0].orderId").doesNotExist())
                .andExpect(jsonPath("$.items[0].authorId").doesNotExist())
                .andExpect(jsonPath("$.items[0].content").value(org.hamcrest.Matchers.nullValue()));
        mvc.perform(get("/api/products/{id}/reviews", product).param("page", "2147483647"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty()).andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(get("/api/products/{id}/reviews", product).param("rating", "1").param("size", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.summary.reviewCount").value(2))
                .andExpect(jsonPath("$.summary.averageRating").value(3.0))
                .andExpect(jsonPath("$.items[0].id").value(later.id()));
        mvc.perform(get("/api/products/{id}/reviews", product).param("rating", "4"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.totalElements").value(0))
                .andExpect(jsonPath("$.summary.reviewCount").value(2));
        for (String[] param : new String[][] {{"page", "-1"}, {"size", "101"}, {"size", "0"}, {"page", "abc"},
                {"rating", "0"}, {"rating", "6"}, {"rating", ""}, {"rating", "1.5"}, {"rating", "0x5"}}) {
            mvc.perform(get("/api/products/{id}/reviews", product).param(param[0], param[1])).andExpect(status().isBadRequest());
        }
    }
    @Test void hiddenOrStoppedPublicListIs404ButBuyerCanStillWriteAndReadOrderReview() throws Exception {
        mvc.perform(get("/api/products/{id}/reviews", product)).andExpect(status().isOk())
                .andExpect(jsonPath("$.items").isEmpty())
                .andExpect(jsonPath("$.summary.reviewCount").value(0))
                .andExpect(jsonPath("$.summary.averageRating").value(org.hamcrest.Matchers.nullValue()));
        for (String field : List.of("is_hidden=1", "sale_status='STOPPED'")) {
            jdbc.update("UPDATE products SET " + field + " WHERE id=?", product);
            mvc.perform(get("/api/products/{id}/reviews", product)).andExpect(status().isNotFound());
            mvc.perform(get("/api/products/{id}/reviews", product).param("rating", "5")).andExpect(status().isNotFound());
            jdbc.update("UPDATE products SET is_hidden=0,sale_status='ON_SALE' WHERE id=?", product);
        }
        jdbc.update("UPDATE products SET is_hidden=1,sale_status='STOPPED' WHERE id=?", product);
        complete(order);
        expectWrite(buyer, "{\"rating\":4}", 201);
    }
    @Test void simultaneousSubmissionsCreateExactlyOneReview() throws Exception {
        complete(order);
        var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(3)) {
            var jobs = new ArrayList<Future<String>>();
            for (int i = 0; i < 3; i++) jobs.add(pool.submit(() -> {
                start.await();
                try { reviews.write(buyer, "" + order, new ReviewRequest(5, null)); return "CREATED"; }
                catch (BusinessException e) { return e.code(); }
            }));
            start.countDown();
            var results = new ArrayList<String>();
            for (var job : jobs) results.add(job.get(30, TimeUnit.SECONDS));
            assertThat(results).containsExactlyInAnyOrder("CREATED", "REVIEW_ALREADY_EXISTS", "REVIEW_ALREADY_EXISTS");
        }
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM reviews WHERE order_id=?", Long.class, order)).isEqualTo(1);
    }
    void expectWrite(long memberId, String body, int status) throws Exception {
        mvc.perform(post("/api/orders/{id}/review", order).with(authentication(auth(memberId))).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().is(status));
    }
    UsernamePasswordAuthenticationToken auth(long id) {
        return UsernamePasswordAuthenticationToken.authenticated(new Principal(id), null, List.of());
    }
    long member() {
        return insert("INSERT INTO members(email,password_hash,display_name,role,created_at,updated_at) VALUES (?,'fixture','후기 회원','USER',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", UUID.randomUUID() + "@test.invalid");
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
