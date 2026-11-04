package com.example.lancerpick.product;

import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.product.service.ProductManagementService;
import com.example.lancerpick.support.IntegrationTest;
import java.sql.Statement;
import java.util.UUID;
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
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class ProductManagementIntegrationTests {
    @Autowired JdbcTemplate jdbc;
    @Autowired ProductManagementService service;
    @Autowired MockMvc mvc;
    final JsonMapper json = new JsonMapper();
    long member;
    long category;
    record Principal(Long memberId) implements AuthenticatedMember {}
    @BeforeEach void fixture() {
        member = insert("INSERT INTO members(email,password_hash,display_name,role,created_at,updated_at) VALUES (?,'fixture','판매자','USER',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", UUID.randomUUID()+"@test.invalid");
        category = insert("INSERT INTO categories(name,display_order) VALUES (?,0)", UUID.randomUUID().toString());
        jdbc.update("INSERT INTO seller_profiles(member_id,introduction,specialty,created_at,updated_at) VALUES (?,'소개','분야',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", member);
    }
    String body() { return "{\"categoryId\":\""+category+"\",\"title\":\"  상품 😀  \",\"description\":\"설명\",\"scopeDescription\":\"범위\",\"preparationDescription\":\"준비\",\"priceAmount\":50000,\"durationDays\":3}"; }
    org.springframework.security.core.Authentication auth(long id) { return UsernamePasswordAuthenticationToken.authenticated(new Principal(id),null,AuthorityUtils.createAuthorityList("ROLE_USER")); }

    @Test void createAndPartialUpdateAndNoopKeepTheContract() throws Exception {
        var result = mvc.perform(post("/api/products").with(authentication(auth(member))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.title").value("상품 😀"))
                .andExpect(jsonPath("$.version").value(0)).andExpect(jsonPath("$.saleStatus").value("ON_SALE"))
                .andExpect(jsonPath("$.isHidden").value(false)).andReturn();
        String id = json.readTree(result.getResponse().getContentAsString()).get("id").asString();
        var original = service.getMine(member,id);
        var updated = service.patch(member,id,json.readTree("{\"title\":\"수정\",\"expectedVersion\":0}"));
        assertThat(updated.version()).isEqualTo(1);
        assertThat(updated.description()).isEqualTo(original.description());
        assertThat(updated.createdAt()).isEqualTo(original.createdAt());
        assertThat(service.patch(member,id,json.readTree("{\"title\":\"수정\",\"expectedVersion\":1}"))).isEqualTo(updated);
        mvc.perform(patch("/api/products/"+id).with(authentication(auth(member))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"수정\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_VERSION_CONFLICT"));
        assertThat(service.getMine(member,id)).isEqualTo(updated);
    }

    @ParameterizedTest
    @ValueSource(strings={"{\"title\":null,\"expectedVersion\":0}","{\"title\":\" \",\"expectedVersion\":0}","{\"priceAmount\":\"100\",\"expectedVersion\":0}","{\"durationDays\":1.5,\"expectedVersion\":0}","{\"expectedVersion\":0}","{\"title\":\"変更\"}","{\"saleStatus\":\"STOPPED\",\"expectedVersion\":0}","{\"title\":\"変更\",\"expectedVersion\":\"0\"}"})
    void invalidPatchNeverChangesProduct(String patchBody) throws Exception {
        var before = service.create(member,json.readTree(body()));
        mvc.perform(patch("/api/products/"+before.id()).with(authentication(auth(member))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(patchBody))
                .andExpect(status().isBadRequest());
        assertThat(service.getMine(member,before.id())).isEqualTo(before);
    }

    @Test void authCsrfOwnershipAndProfileAreEnforced() throws Exception {
        mvc.perform(post("/api/products").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/products").with(authentication(auth(member))).contentType(MediaType.APPLICATION_JSON).content(body())).andExpect(status().isForbidden());
        var product = service.create(member,json.readTree(body()));
        mvc.perform(get("/api/me/products/"+product.id()).with(authentication(auth(member+999)))).andExpect(status().isNotFound());
        mvc.perform(patch("/api/products/"+product.id()).with(authentication(auth(member+999))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content("{\"title\":\"타인\",\"expectedVersion\":0}"))
                .andExpect(status().isNotFound());
        jdbc.update("DELETE FROM seller_profiles WHERE member_id=?",member);
        // 실제 별도 요청에서는 새 persistence context에서 프로필 유무를 검사한다.
        jakartaPersistenceClear();
        mvc.perform(post("/api/products").with(authentication(auth(member))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body()))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("SELLER_PROFILE_REQUIRED"));
    }
    @Autowired jakarta.persistence.EntityManager em;
    void jakartaPersistenceClear() { em.clear(); }
    @Test void hiddenAndStoppedProductCanBeEditedOnlyByOwner() {
        var product=service.create(member,json.readTree(body()));
        em.clear(); jdbc.update("UPDATE products SET is_hidden=1,sale_status='STOPPED' WHERE id=?",product.id());
        var updated=service.patch(member,product.id(),json.readTree("{\"priceAmount\":90000,\"expectedVersion\":0}"));
        assertThat(updated.isHidden()).isTrue(); assertThat(updated.saleStatus()).isEqualTo("STOPPED");
        assertThat(updated.version()).isEqualTo(1);
    }
    @Test void saleStopResumeAndNoopRespectVersionAndVisibility() throws Exception {
        var before = service.create(member, json.readTree(body()));
        mvc.perform(patch("/api/products/" + before.id() + "/sale-status")
                        .with(authentication(auth(member))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"saleStatus\":\"STOPPED\",\"expectedVersion\":0}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.saleStatus").value("STOPPED"))
                .andExpect(jsonPath("$.version").value(1));
        var stopped = service.getMine(member, before.id());
        assertThat(stopped.createdAt()).isEqualTo(before.createdAt());
        assertThat(stopped.description()).isEqualTo(before.description());
        assertThat(service.changeSaleStatus(member, before.id(), json.readTree("{\"saleStatus\":\"STOPPED\",\"expectedVersion\":1}"))).isEqualTo(stopped);
        mvc.perform(get("/api/products/" + before.id())).andExpect(status().isNotFound());
        mvc.perform(patch("/api/products/" + before.id() + "/sale-status").with(authentication(auth(member))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"saleStatus\":\"STOPPED\",\"expectedVersion\":0}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PRODUCT_VERSION_CONFLICT"));
        var resumed = service.changeSaleStatus(member, before.id(), json.readTree("{\"saleStatus\":\"ON_SALE\",\"expectedVersion\":1}"));
        assertThat(resumed.version()).isEqualTo(2);
        mvc.perform(get("/api/products/" + before.id())).andExpect(status().isOk());
        em.clear();
        jdbc.update("UPDATE products SET is_hidden=1 WHERE id=?", before.id());
        service.changeSaleStatus(member, before.id(), json.readTree("{\"saleStatus\":\"STOPPED\",\"expectedVersion\":2}"));
        var hidden = service.changeSaleStatus(member, before.id(), json.readTree("{\"saleStatus\":\"ON_SALE\",\"expectedVersion\":3}"));
        assertThat(hidden.isHidden()).isTrue();
        assertThat(hidden.version()).isEqualTo(4);
        mvc.perform(get("/api/products/" + before.id())).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "null", "[]", "{\"saleStatus\":\"DELETED\",\"expectedVersion\":0}",
            "{\"saleStatus\":null,\"expectedVersion\":0}", "{\"saleStatus\":true,\"expectedVersion\":0}",
            "{\"saleStatus\":\"STOPPED\"}", "{\"saleStatus\":\"STOPPED\",\"expectedVersion\":null}",
            "{\"saleStatus\":\"STOPPED\",\"expectedVersion\":\"0\"}", "{\"saleStatus\":\"STOPPED\",\"expectedVersion\":-1}",
            "{\"saleStatus\":\"STOPPED\",\"expectedVersion\":1.5}", "{\"saleStatus\":\"STOPPED\",\"expectedVersion\":9223372036854775808}",
            "{\"saleStatus\":\"STOPPED\",\"expectedVersion\":0,\"isHidden\":false}"})
    void invalidSaleStatusRequestLeavesProductUnchanged(String body) throws Exception {
        var before = service.create(member, json.readTree(body()));
        mvc.perform(patch("/api/products/" + before.id() + "/sale-status").with(authentication(auth(member))).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(service.getMine(member, before.id())).isEqualTo(before);
    }

    @Test void saleStatusRequiresSessionCsrfAndOwnership() throws Exception {
        var before = service.create(member, json.readTree(body()));
        String endpoint = "/api/products/" + before.id() + "/sale-status";
        String body = "{\"saleStatus\":\"STOPPED\",\"expectedVersion\":0}";
        mvc.perform(patch(endpoint).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isUnauthorized());
        mvc.perform(patch(endpoint).with(authentication(auth(member))).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isForbidden());
        mvc.perform(patch(endpoint).with(authentication(auth(member + 999))).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isNotFound());
        assertThat(service.getMine(member, before.id())).isEqualTo(before);
    }

    @Autowired com.example.lancerpick.order.service.OrderCreationApiService orders;
    @Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED)
    @Test void stoppingOnlyBlocksNewOrdersAndKeepsExistingSnapshotsAndReplay() throws Exception {
        var product = service.create(member, json.readTree(body()));
        long buyer = insert("INSERT INTO members(email,password_hash,display_name,role,created_at,updated_at) VALUES (?,'fixture','구매자','USER',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", UUID.randomUUID() + "@test.invalid");
        var request = new com.example.lancerpick.order.dto.CreateOrderRequest(product.id(), 0L, "요구사항", UUID.randomUUID().toString());
        var created = orders.create(buyer, request);
        service.changeSaleStatus(member, product.id(), json.readTree("{\"saleStatus\":\"STOPPED\",\"expectedVersion\":0}"));
        var replay = orders.create(buyer, request);
        assertThat(replay.created()).isFalse();
        assertThat(replay.order().id()).isEqualTo(created.order().id());
        assertThat(replay.order().productSnapshot()).isEqualTo(created.order().productSnapshot());
        assertThat(replay.order().priceAmount()).isEqualTo(created.order().priceAmount());
        mvc.perform(post("/api/orders").with(authentication(auth(buyer))).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"productId\":\"" + product.id() + "\",\"expectedProductVersion\":1,\"requirements\":\"새 주문\",\"requestKey\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isConflict());
    }

    long insert(String sql,Object... args) {
        var key=new GeneratedKeyHolder(); jdbc.update(connection->{var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)statement.setObject(i+1,args[i]);return statement;},key);return key.getKey().longValue();
    }
}
