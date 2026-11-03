package com.example.lancerpick.order.repository;
import com.example.lancerpick.order.dto.OrderDetailResponse.CancellationRequest;
import com.example.lancerpick.order.entity.QCancellationRequest;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
@Repository @Profile("!demo")
public class CancellationRequestQueries {
    private final EntityManager em;private final JPAQueryFactory query;
    public CancellationRequestQueries(EntityManager em){this.em=em;query=new JPAQueryFactory(em);}
    private Optional<Row> response(com.example.lancerpick.order.entity.CancellationRequest value){return Optional.ofNullable(value).map(r->new Row(r.getId(),r.getOrderId(),r.getRequestedBy(),r.getRequestKey(),r.getReason(),r.getStatus(),r.getRequestedAt(),r.getResolvedAt(),r.getResolvedBy(),r.getResponseReason()));}
    public Optional<Row> byKey(long order,long buyer,String key){var r=QCancellationRequest.cancellationRequest;return response(query.selectFrom(r).where(r.orderId.eq(order),r.requestedBy.eq(buyer),r.requestKey.eq(key)).fetchOne());}
    public Optional<Row> byOrder(long order,long id){var r=QCancellationRequest.cancellationRequest;return response(query.selectFrom(r).where(r.orderId.eq(order),r.id.eq(id)).fetchOne());}
    public long pending(long order){var r=QCancellationRequest.cancellationRequest;return query.select(r.id.count()).from(r).where(r.orderId.eq(order),r.status.eq("PENDING")).fetchOne();}
    public long insert(long order,long buyer,String key,String reason,LocalDateTime at){var r=com.example.lancerpick.order.entity.CancellationRequest.create(order,buyer,key,reason,at);em.persist(r);em.flush();return r.getId();}
    public int resolve(long id,String status,long actor,String reason,LocalDateTime at){var r=em.find(com.example.lancerpick.order.entity.CancellationRequest.class,id);if(r==null || !"PENDING".equals(r.getStatus()))return 0;r.resolve(status,actor,reason,at);em.flush();return 1;}
    public void clearApprovalResponseReason(long id){var r=em.find(com.example.lancerpick.order.entity.CancellationRequest.class,id);if(r!=null){r.clearApprovalResponseReason();em.flush();}}
    public record Row(long id, long orderId, long requestedBy, String requestKey, String reason, String status,
            LocalDateTime requestedAt, LocalDateTime resolvedAt, Long resolvedBy, String responseReason) {
        public CancellationRequest response() {
            return new CancellationRequest(Long.toString(id), Long.toString(orderId), requestKey, reason, status,
                    requestedAt.atOffset(ZoneOffset.UTC), resolvedAt == null ? null : resolvedAt.atOffset(ZoneOffset.UTC),
                    resolvedBy == null ? null : resolvedBy.toString(), responseReason);
        }
    }
}
