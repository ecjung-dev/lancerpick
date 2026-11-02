package com.example.lancerpick.order.repository;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.order.dto.OrderDetailResponse;
import com.example.lancerpick.order.dto.OrderDetailResponse.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 생성 트랜잭션 내에서 저장된 현재 상세를 조회한다. 스냅샷을 현재 상품으로 재작성하지 않는다. */
@Repository
@Profile("!demo")
public class OrderDetailQueries {
    private final JdbcTemplate jdbc;

    public OrderDetailQueries(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    public OrderDetailResponse forParticipant(long orderId, long memberId) {
        var headers = jdbc.query("""
                SELECT o.*, b.display_name AS buyer_name, s.display_name AS seller_name,
                       snap.product_title_snapshot, snap.description_snapshot, snap.scope_snapshot,
                       snap.preparation_snapshot, snap.product_version_snapshot, snap.terms_snapshot, snap.requirements
                FROM orders o JOIN members b ON b.id=o.buyer_id JOIN members s ON s.id=o.seller_id
                LEFT JOIN order_snapshots snap ON snap.order_id=o.id
                WHERE o.id=? AND (o.buyer_id=? OR o.seller_id=?)
                """, (rs, row) -> {
            if (rs.getString("product_title_snapshot") == null) {
                throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR, "DATA_INTEGRITY_ERROR", "주문 조건 기록을 확인해야 합니다.");
            }
            return new Header(id(rs, "id"), rs.getString("status"),
                    new Participant(id(rs, "buyer_id"), rs.getString("buyer_name")),
                    new Participant(id(rs, "seller_id"), rs.getString("seller_name")), id(rs, "product_id"),
                    new ProductSnapshot(rs.getString("product_title_snapshot"), rs.getString("description_snapshot"),
                            rs.getString("scope_snapshot"), rs.getString("preparation_snapshot"), rs.getLong("product_version_snapshot")),
                    rs.getString("terms_snapshot"), rs.getLong("price_amount"), rs.getInt("duration_days_snapshot"),
                    rs.getString("requirements"), time(rs, "created_at"), time(rs, "accepted_at"),
                    time(rs, "buyer_confirmed_terms_at"), time(rs, "paid_at"), time(rs, "due_at"),
                    time(rs, "completed_at"), time(rs, "cancelled_at"));
        }, orderId, memberId, memberId);
        if (headers.isEmpty()) throw new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "주문을 찾을 수 없습니다.");
        Header h = headers.getFirst();
        var deliveries = jdbc.query("SELECT * FROM deliveries WHERE order_id=? ORDER BY sequence_no, id",
                (rs, row) -> new Delivery(id(rs, "id"), rs.getInt("sequence_no"), rs.getString("description"),
                        rs.getString("result_url"), time(rs, "submitted_at")), orderId);
        var revisions = jdbc.query("SELECT * FROM revision_requests WHERE order_id=?",
                (rs, row) -> new RevisionRequest(id(rs, "id"), id(rs, "delivery_id"), rs.getString("reason"),
                        time(rs, "requested_at"), time(rs, "due_at")), orderId);
        RevisionRequest revision = firstOrNull(revisions);
        var cancellations = jdbc.query("SELECT * FROM cancellation_requests WHERE order_id=? ORDER BY requested_at, id",
                (rs, row) -> new CancellationRequest(id(rs, "id"), id(rs, "order_id"), rs.getString("request_key"),
                        rs.getString("reason"), rs.getString("status"), time(rs, "requested_at"), time(rs, "resolved_at"),
                        id(rs, "resolved_by"), rs.getString("response_reason")), orderId);
        var histories = jdbc.query("SELECT * FROM order_histories WHERE order_id=? ORDER BY created_at, id",
                (rs, row) -> new History(id(rs, "id"), id(rs, "actor_id"), rs.getString("action"),
                        rs.getString("from_status"), rs.getString("to_status"), rs.getString("reason"), time(rs, "created_at")), orderId);
        Payment payment = firstOrNull(jdbc.query("SELECT * FROM payments WHERE order_id=?",
                (rs, row) -> new Payment(id(rs, "id"), rs.getLong("amount"), rs.getInt("fee_rate_bps"),
                        rs.getString("status"), time(rs, "paid_at"), time(rs, "refunded_at")), orderId));
        Review review = firstOrNull(jdbc.query("SELECT * FROM reviews WHERE order_id=?",
                (rs, row) -> new Review(id(rs, "id"), id(rs, "order_id"), h.buyer().displayName(), rs.getInt("rating"),
                        rs.getString("content"), time(rs, "created_at")), orderId));
        OffsetDateTime revisionDue = revision == null ? null : revision.dueAt();
        OffsetDateTime currentDue = h.paidAt() == null ? null : revisionDue != null ? revisionDue : h.originalDueAt();
        return new OrderDetailResponse(h.id(), h.status(), h.buyer(), h.seller(), h.productId(), h.snapshot(),
                h.terms(), h.price(), h.duration(), h.requirements(), h.createdAt(), h.acceptedAt(),
                h.termsAt(), h.paidAt(), h.originalDueAt(), revisionDue, currentDue, h.completedAt(), h.cancelledAt(),
                deliveries, revision, cancellations, histories, payment, review,
                List.of());
    }

    private static <T> T firstOrNull(List<T> values) { return values.isEmpty() ? null : values.getFirst(); }
    private static String id(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : Long.toString(value);
    }
    private static OffsetDateTime time(ResultSet rs, String column) throws SQLException {
        // DATETIME은 시간대가 없다. JDBC Timestamp의 JVM 시간대 변환을 거치지 않는다.
        LocalDateTime value = rs.getObject(column, LocalDateTime.class);
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
    private record Header(String id, String status, Participant buyer, Participant seller, String productId,
            ProductSnapshot snapshot, String terms, long price, int duration, String requirements,
            OffsetDateTime createdAt, OffsetDateTime acceptedAt, OffsetDateTime termsAt, OffsetDateTime paidAt,
            OffsetDateTime originalDueAt, OffsetDateTime completedAt, OffsetDateTime cancelledAt) {}
}
