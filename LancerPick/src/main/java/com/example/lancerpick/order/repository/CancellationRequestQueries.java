package com.example.lancerpick.order.repository;

import com.example.lancerpick.order.dto.OrderDetailResponse.CancellationRequest;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

/** 주문 잠금 안에서만 쓰며 기존 직접 취소의 JDBC 갱신과 같은 DB 원문을 읽는다. */
@Repository
@Profile("!demo")
public class CancellationRequestQueries {
    private final JdbcTemplate jdbc;
    private static final RowMapper<Row> MAPPER = (rs, index) -> new Row(rs.getLong("id"), rs.getLong("order_id"),
            rs.getLong("requested_by"), rs.getString("request_key"), rs.getString("reason"), rs.getString("status"),
            time(rs, "requested_at"), time(rs, "resolved_at"), rs.getObject("resolved_by", Long.class), rs.getString("response_reason"));

    public CancellationRequestQueries(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public Optional<Row> byKey(long orderId, long buyerId, String key) {
        return jdbc.query("SELECT * FROM cancellation_requests WHERE order_id=? AND requested_by=? AND request_key=?",
                MAPPER, orderId, buyerId, key).stream().findFirst();
    }
    public Optional<Row> byOrder(long orderId, long id) {
        return jdbc.query("SELECT * FROM cancellation_requests WHERE order_id=? AND id=?", MAPPER, orderId, id).stream().findFirst();
    }
    public long pending(long orderId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM cancellation_requests WHERE order_id=? AND status='PENDING'", Long.class, orderId);
    }
    public long insert(long orderId, long buyerId, String key, String reason, LocalDateTime at) {
        var generated = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement("INSERT INTO cancellation_requests(order_id,requested_by,request_key,reason,requested_at) VALUES (?,?,?,?,?)", Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, orderId); statement.setLong(2, buyerId); statement.setString(3, key);
            statement.setString(4, reason); statement.setObject(5, at);
            return statement;
        }, generated);
        return generated.getKey().longValue();
    }
    public int resolve(long id, String status, long actor, String reason, LocalDateTime at) {
        return jdbc.update("UPDATE cancellation_requests SET status=?,resolved_at=?,resolved_by=?,response_reason=? WHERE id=? AND status='PENDING'",
                status, at, actor, reason, id);
    }
    public void clearApprovalResponseReason(long id) {
        jdbc.update("UPDATE cancellation_requests SET response_reason=NULL WHERE id=? AND status='APPROVED'", id);
    }
    private static LocalDateTime time(ResultSet rs, String column) throws SQLException { return rs.getObject(column, LocalDateTime.class); }

    public record Row(long id, long orderId, long requestedBy, String requestKey, String reason, String status,
            LocalDateTime requestedAt, LocalDateTime resolvedAt, Long resolvedBy, String responseReason) {
        public CancellationRequest response() {
            return new CancellationRequest(Long.toString(id), Long.toString(orderId), requestKey, reason, status,
                    requestedAt.atOffset(ZoneOffset.UTC), resolvedAt == null ? null : resolvedAt.atOffset(ZoneOffset.UTC),
                    resolvedBy == null ? null : resolvedBy.toString(), responseReason);
        }
    }
}
