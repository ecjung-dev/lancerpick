package com.example.lancerpick.product;

import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.support.IntegrationTest;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@IntegrationTest
@AutoConfigureMockMvc
@Transactional
class ProductFavoritesIntegrationTests {
    private static final String LIST = "/api/me/favorites";
    private static final String ITEM = LIST + "/{id}";
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired EntityManager entityManager;

    record Principal(Long memberId) implements AuthenticatedMember {}

    @Test
    void privateFavoritesCanBeAddedTwiceAndRemovedEvenAfterHiding() throws Exception {
        long owner = member();
        long other = member();
        long product = product(other);
        var ownerSession = session(owner);
        var otherSession = session(other);

        mvc.perform(get(LIST)).andExpect(status().isUnauthorized());
        mvc.perform(put(ITEM, product).with(ownerSession)).andExpect(status().isForbidden());
        mvc.perform(get(ITEM, product).with(ownerSession)).andExpect(jsonPath("$.favorited").value(false));
        for (int i = 0; i < 2; i++) {
            mvc.perform(put(ITEM, product).with(ownerSession).with(csrf())).andExpect(status().isNoContent());
        }
        entityManager.flush();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM product_favorites WHERE member_id=? AND product_id=?",
                Long.class, owner, product)).isEqualTo(1L);
        mvc.perform(get(ITEM, product).with(ownerSession)).andExpect(jsonPath("$.favorited").value(true));
        mvc.perform(get(ITEM, product).with(otherSession)).andExpect(jsonPath("$.favorited").value(false));
        mvc.perform(get(LIST).with(otherSession).param("memberId", Long.toString(owner)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
        mvc.perform(get(LIST).with(ownerSession))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].id").value(Long.toString(product)))
                .andExpect(jsonPath("$.items[0].seller.email").doesNotExist());
        mvc.perform(get(LIST).with(ownerSession).param("page", "1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());

        for (String unavailable : new String[]{"is_hidden=1", "is_hidden=0, sale_status='STOPPED'"}) {
            jdbc.update("UPDATE products SET " + unavailable + " WHERE id=?", product);
            entityManager.clear();
            mvc.perform(get(LIST).with(ownerSession))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(0));
            mvc.perform(put(ITEM, product).with(ownerSession).with(csrf())).andExpect(status().isNotFound());
        }
        for (int i = 0; i < 2; i++) {
            mvc.perform(delete(ITEM, product).with(ownerSession).with(csrf())).andExpect(status().isNoContent());
        }
        mvc.perform(get(ITEM, product).with(ownerSession)).andExpect(jsonPath("$.favorited").value(false));
    }

    private org.springframework.test.web.servlet.request.RequestPostProcessor session(long memberId) {
        return authentication(UsernamePasswordAuthenticationToken.authenticated(
                new Principal(memberId), null, AuthorityUtils.createAuthorityList("ROLE_USER")));
    }

    private long member() {
        String email = UUID.randomUUID() + "@test.invalid";
        jdbc.update("INSERT INTO members(email,password_hash,display_name,role,created_at,updated_at) "
                + "VALUES (?,'fixture','회원','USER',UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))", email);
        return jdbc.queryForObject("SELECT id FROM members WHERE email=?", Long.class, email);
    }

    private long product(long seller) {
        String title = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO categories(name,display_order) VALUES (?,0)", title);
        long category = jdbc.queryForObject("SELECT id FROM categories WHERE name=?", Long.class, title);
        jdbc.update("""
                INSERT INTO products(seller_id,category_id,title,description,scope_description,preparation_description,
                    price,duration_days,created_at,updated_at)
                VALUES (?, ?, ?, '설명', '범위', '준비', 50000, 3, UTC_TIMESTAMP(6), UTC_TIMESTAMP(6))
                """, seller, category, title);
        return jdbc.queryForObject("SELECT id FROM products WHERE seller_id=? AND title=?", Long.class, seller, title);
    }
}
