package com.example.lancerpick.admin.repository;

import com.example.lancerpick.admin.dto.AdminProductDetailResponse;
import com.example.lancerpick.admin.dto.AdminProductSummaryResponse;
import com.example.lancerpick.admin.dto.AdminProductSummaryResponse.Category;
import com.example.lancerpick.admin.dto.AdminProductSummaryResponse.Seller;
import com.example.lancerpick.admin.dto.ModerationLogResponse;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * 관리자 상품 운영 전용 SQL.
 * 상품(Product) 엔티티는 상품 담당자 영역이므로 여기서 중복 정의하지 않고 products 테이블을 직접 조회·잠근다.
 * 사용자 입력은 항상 ? 로 바인딩한다. SQL 문자열에 직접 이어 붙이지 않는다.
 */
@Repository
@Profile("!demo")
public class AdminProductQueries {
    private static final String PRODUCT_COLUMNS = """
            SELECT p.id, p.title, p.price, p.duration_days, p.sale_status, p.is_hidden, p.version,
                   p.created_at, p.updated_at, c.id AS category_id, c.name AS category_name,
                   m.id AS seller_id, m.display_name AS seller_display_name
            """;
    private static final String PRODUCT_FROM = """
            FROM products p
            JOIN categories c ON c.id = p.category_id
            JOIN members m ON m.id = p.seller_id
            """;

    private final JdbcTemplate jdbc;

    public AdminProductQueries(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 목록 필터. null 항목은 조건을 걸지 않는다. */
    public record ProductFilter(String keyword, Boolean hidden, String saleStatus) {}

    /** 숨김 변경 판단에 필요한 현재 값. */
    public record ProductVisibility(boolean hidden, long version) {}

    // ---------- 목록 ----------

    public long count(ProductFilter filter) {
        var args = new ArrayList<Object>();
        String where = where(filter, args);
        return jdbc.queryForObject("SELECT COUNT(*) " + PRODUCT_FROM + where, Long.class, args.toArray());
    }

    public List<AdminProductSummaryResponse> find(ProductFilter filter, int size, long offset) {
        var args = new ArrayList<Object>();
        String where = where(filter, args);
        args.add(size);
        args.add(offset);
        // API 9절: createdAt 내림차순, 같은 시각은 id 내림차순으로 순서를 고정한다.
        return jdbc.query(PRODUCT_COLUMNS + PRODUCT_FROM + where + " ORDER BY p.created_at DESC, p.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> new AdminProductSummaryResponse(
                        Long.toString(rs.getLong("id")), rs.getString("title"), rs.getLong("price"),
                        rs.getInt("duration_days"), category(rs), seller(rs), rs.getString("sale_status"),
                        rs.getBoolean("is_hidden"), rs.getLong("version"),
                        time(rs, "created_at"), time(rs, "updated_at")),
                args.toArray());
    }

    private static String where(ProductFilter filter, List<Object> args) {
        var sql = new StringBuilder(" WHERE 1=1");
        if (filter.keyword() != null) {
            // 제목 또는 설명만 OR로 묶고, 나머지 필터와는 AND로 결합한다.
            sql.append(" AND (p.title LIKE ? ESCAPE '!' OR p.description LIKE ? ESCAPE '!')");
            String pattern = "%" + escapeLike(filter.keyword()) + "%";
            args.add(pattern);
            args.add(pattern);
        }
        if (filter.hidden() != null) {
            sql.append(" AND p.is_hidden = ?");
            args.add(filter.hidden());
        }
        if (filter.saleStatus() != null) {
            sql.append(" AND p.sale_status = ?");
            args.add(filter.saleStatus());
        }
        return sql.toString();
    }

    /** %·_·이스케이프 문자(!)를 일반 글자로 검색하도록 앞에 !를 붙인다. */
    static String escapeLike(String keyword) {
        return keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_");
    }

    // ---------- 상세 ----------

    public Optional<AdminProductDetailResponse> findDetail(long productId) {
        return jdbc.query("""
                SELECT p.id, p.title, p.price, p.duration_days, p.description, p.scope_description,
                       p.preparation_description, p.version, p.sale_status, p.is_hidden, p.created_at, p.updated_at,
                       c.id AS category_id, c.name AS category_name,
                       m.id AS seller_id, m.display_name AS seller_display_name
                """ + PRODUCT_FROM + " WHERE p.id = ?",
                (rs, row) -> new AdminProductDetailResponse(
                        Long.toString(rs.getLong("id")), rs.getString("title"), rs.getLong("price"),
                        rs.getInt("duration_days"), category(rs), seller(rs), rs.getString("description"),
                        rs.getString("scope_description"), rs.getString("preparation_description"),
                        rs.getLong("version"), rs.getString("sale_status"), rs.getBoolean("is_hidden"),
                        time(rs, "created_at"), time(rs, "updated_at")),
                productId).stream().findFirst();
    }

    public boolean exists(long productId) {
        return !jdbc.query("SELECT id FROM products WHERE id = ?", (rs, row) -> rs.getLong("id"), productId).isEmpty();
    }

    // ---------- 숨김·복구 ----------

    /** 상품 행을 잠근다(FOR UPDATE). 같은 상품의 판매자 수정·다른 관리자 요청은 이 트랜잭션이 끝날 때까지 기다린다. */
    public Optional<ProductVisibility> lockVisibility(long productId) {
        return jdbc.query("SELECT is_hidden, version FROM products WHERE id = ? FOR UPDATE",
                (rs, row) -> new ProductVisibility(rs.getBoolean("is_hidden"), rs.getLong("version")),
                productId).stream().findFirst();
    }

    /** 판매 상태는 건드리지 않는다. version 조건을 함께 걸어 잠금이 없어도 덮어쓰기가 일어나지 않게 한다. */
    public int updateVisibility(long productId, boolean hidden, long currentVersion, LocalDateTime at) {
        return jdbc.update("""
                UPDATE products SET is_hidden = ?, version = version + 1, updated_at = ?
                WHERE id = ? AND version = ?
                """, hidden, at, productId, currentVersion);
    }

    public void insertModerationLog(long productId, long adminId, String action, String reason, LocalDateTime at) {
        jdbc.update("""
                INSERT INTO product_moderation_logs (product_id, admin_id, action, reason, created_at)
                VALUES (?, ?, ?, ?, ?)
                """, productId, adminId, action, reason, at);
    }

    // ---------- 운영 이력 ----------

    public long countLogs(long productId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM product_moderation_logs WHERE product_id = ?",
                Long.class, productId);
    }

    public List<ModerationLogResponse> findLogs(long productId, int size, long offset) {
        return jdbc.query("""
                SELECT id, admin_id, action, reason, created_at FROM product_moderation_logs
                WHERE product_id = ? ORDER BY created_at DESC, id DESC LIMIT ? OFFSET ?
                """, (rs, row) -> new ModerationLogResponse(
                        Long.toString(rs.getLong("id")), Long.toString(rs.getLong("admin_id")),
                        rs.getString("action"), rs.getString("reason"), time(rs, "created_at")),
                productId, size, offset);
    }

    // ---------- 관리자 확인 ----------

    /** 세션 발급 후 권한이 바뀐 경우를 막기 위해 DB의 현재 role을 다시 읽는다. */
    public Optional<String> findMemberRole(long memberId) {
        return jdbc.query("SELECT role FROM members WHERE id = ?", (rs, row) -> rs.getString("role"), memberId)
                .stream().findFirst();
    }

    private static Category category(ResultSet rs) throws SQLException {
        return new Category(Long.toString(rs.getLong("category_id")), rs.getString("category_name"));
    }

    private static Seller seller(ResultSet rs) throws SQLException {
        return new Seller(Long.toString(rs.getLong("seller_id")), rs.getString("seller_display_name"));
    }

    private static OffsetDateTime time(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
