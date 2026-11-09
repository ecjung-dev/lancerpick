package com.example.lancerpick.order.repository;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.order.dto.OrderDetailResponse;
import com.example.lancerpick.order.dto.OrderDetailResponse.*;
import com.example.lancerpick.order.entity.*;
import com.example.lancerpick.member.entity.QMember;
import com.example.lancerpick.payment.entity.QPayment;
import com.example.lancerpick.review.entity.QReview;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
@Repository @Profile("!demo")
public class OrderDetailQueries {
    private final JPAQueryFactory query;
    private final EntityManager em;
    public OrderDetailQueries(EntityManager em){this.em=em;query=new JPAQueryFactory(em);}
    public OrderDetailResponse forParticipant(long orderId,long memberId){
        if(em.isJoinedToTransaction())em.flush();
        var o=QOrder.order;var s=QOrderSnapshot.orderSnapshot;var b=new QMember("buyer");var seller=new QMember("seller");
        var row=query.select(o,s,b,seller).from(o).join(b).on(b.id.eq(o.buyerId)).join(seller).on(seller.id.eq(o.sellerId)).leftJoin(s).on(s.orderId.eq(o.id))
                .where(o.id.eq(orderId).and(o.buyerId.eq(memberId).or(o.sellerId.eq(memberId)))).fetchOne();
        if(row==null)throw new BusinessException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","주문을 찾을 수 없습니다.");
        var order=row.get(o);var snap=row.get(s);var buyer=row.get(b);var vendor=row.get(seller);
        if(snap==null)throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,"DATA_INTEGRITY_ERROR","주문 조건 기록을 확인해야 합니다.");
        var d=QDelivery.delivery;
        var deliveries=query.selectFrom(d).where(d.orderId.eq(orderId)).orderBy(d.sequenceNo.asc(),d.id.asc()).fetch().stream()
                .map(v->new OrderDetailResponse.Delivery(id(v.getId()),v.getSequenceNo(),v.getDescription(),v.getResultUrl(),at(v.getSubmittedAt()))).toList();
        var r=QRevisionRequest.revisionRequest;
        var revision=query.selectFrom(r).where(r.orderId.eq(orderId)).fetchOne();
        OrderDetailResponse.RevisionRequest revisionResponse=revision==null?null:new OrderDetailResponse.RevisionRequest(id(revision.getId()),id(revision.getDeliveryId()),revision.getReason(),at(revision.getRequestedAt()),at(revision.getDueAt()));
        var c=QCancellationRequest.cancellationRequest;
        var cancellations=query.selectFrom(c).where(c.orderId.eq(orderId)).orderBy(c.requestedAt.asc(),c.id.asc()).fetch().stream()
                .map(v->new OrderDetailResponse.CancellationRequest(id(v.getId()),id(v.getOrderId()),v.getRequestKey(),v.getReason(),v.getStatus().name(),at(v.getRequestedAt()),at(v.getResolvedAt()),id(v.getResolvedBy()),v.getResponseReason())).toList();
        var h=QOrderHistory.orderHistory;
        var histories=query.selectFrom(h).where(h.orderId.eq(orderId)).orderBy(h.createdAt.asc(),h.id.asc()).fetch().stream()
                .map(v->new History(id(v.getId()),id(v.getActorId()),v.getAction().name(),v.getFromStatus()==null?null:v.getFromStatus().name(),v.getToStatus().name(),v.getReason(),at(v.getCreatedAt()))).toList();
        var p=QPayment.payment;var payment=query.selectFrom(p).where(p.orderId.eq(orderId)).fetchOne();
        Payment paymentResponse=payment==null?null:new Payment(id(payment.getId()),payment.getAmount(),payment.getFeeRateBps(),payment.getStatus().name(),at(payment.getPaidAt()),at(payment.getRefundedAt()));
        var rev=QReview.review;var review=query.selectFrom(rev).where(rev.orderId.eq(orderId)).fetchOne();
        Review reviewResponse=review==null?null:new Review(id(review.getId()),id(review.getOrderId()),buyer.getDisplayName(),review.getRating(),review.getContent(),at(review.getCreatedAt()));
        var revisionDue=revision==null?null:revision.getDueAt();
        var due=order.getPaidAt()==null?null:revisionDue!=null?revisionDue:order.getDueAt();
        return new OrderDetailResponse(id(order.getId()),order.getStatus().name(),new Participant(id(buyer.getId()),buyer.getDisplayName()),new Participant(id(vendor.getId()),vendor.getDisplayName()),id(order.getProductId()),
                new ProductSnapshot(snap.getProductTitleSnapshot(),snap.getDescriptionSnapshot(),snap.getScopeSnapshot(),snap.getPreparationSnapshot(),snap.getProductVersionSnapshot()),snap.getTermsSnapshot(),order.getPriceAmount(),order.getDurationDaysSnapshot(),snap.getRequirements(),at(order.getCreatedAt()),at(order.getAcceptedAt()),at(order.getBuyerConfirmedTermsAt()),at(order.getPaidAt()),at(order.getDueAt()),at(revisionDue),at(due),at(order.getCompletedAt()),at(order.getCancelledAt()),deliveries,revisionResponse,cancellations,histories,paymentResponse,reviewResponse,List.of(),order.getPolicyVersion());
    }
    private static String id(Long value){return value==null?null:value.toString();}
    private static OffsetDateTime at(LocalDateTime value){return value==null?null:value.atOffset(ZoneOffset.UTC);}
}
