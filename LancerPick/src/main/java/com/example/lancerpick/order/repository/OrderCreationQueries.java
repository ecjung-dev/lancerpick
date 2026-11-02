package com.example.lancerpick.order.repository;

import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 회원·상품 엔티티를 중복 정의하지 않는 주문 생성 전용 조회 경계. */
@Repository
@Profile("!demo")
public class OrderCreationQueries {
    private final JdbcTemplate jdbc;

    public OrderCreationQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public boolean lockBuyer(long buyerId) {
        return !jdbc.query("SELECT id FROM members WHERE id = ? FOR UPDATE",
                (rs, row) -> rs.getLong("id"), buyerId).isEmpty();
    }

    public Optional<ProductForOrder> lockProduct(long productId) {
        return jdbc.query("""
                SELECT id, seller_id, version, title, description, scope_description,
                    preparation_description, price, duration_days, sale_status, is_hidden
                FROM products WHERE id = ? FOR UPDATE
                """, (rs, row) -> new ProductForOrder(rs.getLong("id"), rs.getLong("seller_id"),
                rs.getLong("version"), rs.getString("title"), rs.getString("description"),
                rs.getString("scope_description"), rs.getString("preparation_description"),
                rs.getLong("price"), rs.getInt("duration_days"),
                rs.getString("sale_status"), rs.getBoolean("is_hidden")), productId)
                .stream().findFirst();
    }

    public record ProductForOrder(long id, long sellerId, long version, String title,
            String description, String scope, String preparation, long price, int durationDays,
            String saleStatus, boolean hidden) {}
}
