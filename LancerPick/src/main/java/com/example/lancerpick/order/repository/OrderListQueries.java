package com.example.lancerpick.order.repository;

import com.example.lancerpick.order.dto.OrderDetailResponse.Participant;
import com.example.lancerpick.order.dto.OrderSide;
import com.example.lancerpick.order.dto.OrderSummaryResponse;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.common.exception.BusinessException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.http.HttpStatus;

@Repository
@Profile("!demo")
public class OrderListQueries {
    private final JdbcTemplate jdbc;

    public OrderListQueries(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public long count(long memberId, OrderSide side, OrderStatus status) {
        var args = arguments(memberId, status);
        return jdbc.queryForObject("SELECT COUNT(*) FROM orders o WHERE " + predicate(side, status), Long.class, args.toArray());
    }

    public List<OrderSummaryResponse> find(long memberId, OrderSide side, OrderStatus status, int size, long offset) {
        // SQL 식별자는 서버 enum으로만 고르고 사용자 값은 바인딩한다.
        String counterparty = side == OrderSide.BUYER ? "seller_id" : "buyer_id";
        var args = arguments(memberId, status);
        args.add(size);
        args.add(offset);
        return jdbc.query("""
                SELECT o.id, o.status, snap.product_title_snapshot, o.price_amount,
                       m.id AS counterparty_id, m.display_name, o.created_at,
                       CASE WHEN o.paid_at IS NULL THEN NULL ELSE COALESCE(r.due_at,o.due_at) END AS current_due_at
                FROM orders o LEFT JOIN order_snapshots snap ON snap.order_id=o.id
                JOIN members m ON m.id=o.%s LEFT JOIN revision_requests r ON r.order_id=o.id
                WHERE %s ORDER BY o.created_at DESC,o.id DESC LIMIT ? OFFSET ?
                """.formatted(counterparty, predicate(side, status)), (rs, row) -> {
            if (rs.getString("product_title_snapshot") == null) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "DATA_INTEGRITY_ERROR", "주문 조건 기록을 확인해야 합니다.");
            }
            return new OrderSummaryResponse(
                Long.toString(rs.getLong("id")), rs.getString("status"), rs.getString("product_title_snapshot"),
                rs.getLong("price_amount"), new Participant(Long.toString(rs.getLong("counterparty_id")), rs.getString("display_name")),
                time(rs, "created_at"), time(rs, "current_due_at"));
        }, args.toArray());
    }

    private static String predicate(OrderSide side, OrderStatus status) {
        return "o." + (side == OrderSide.BUYER ? "buyer_id" : "seller_id") + "=?" + (status == null ? "" : " AND o.status=?");
    }

    private static ArrayList<Object> arguments(long memberId, OrderStatus status) {
        var args = new ArrayList<Object>();
        args.add(memberId);
        if (status != null) args.add(status.name());
        return args;
    }

    private static OffsetDateTime time(ResultSet rs, String column) throws SQLException {
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
