package com.example.lancerpick.order.repository;
import com.example.lancerpick.order.dto.OrderDetailResponse.Participant;
import com.example.lancerpick.order.dto.*;
import com.example.lancerpick.order.entity.*;
import com.example.lancerpick.member.entity.QMember;
import com.example.lancerpick.common.exception.BusinessException;
import com.querydsl.jpa.impl.JPAQueryFactory;
import com.querydsl.core.types.dsl.BooleanExpression;
import jakarta.persistence.EntityManager;
import java.time.*;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Repository;
@Repository @Profile("!demo")
public class OrderListQueries {
    private final JPAQueryFactory query;
    public OrderListQueries(EntityManager em){query=new JPAQueryFactory(em);}
    private BooleanExpression predicate(long member,OrderSide side,OrderStatus status){var o=QOrder.order;var condition=(side==OrderSide.BUYER?o.buyerId:o.sellerId).eq(member);return status==null?condition:condition.and(o.status.eq(status));}
    public long count(long member,OrderSide side,OrderStatus status){var o=QOrder.order;return query.select(o.id.count()).from(o).where(predicate(member,side,status)).fetchOne();}
    public List<OrderSummaryResponse> find(long member,OrderSide side,OrderStatus status,int size,long offset){
        var o=QOrder.order;var s=QOrderSnapshot.orderSnapshot;var m=QMember.member;var r=QRevisionRequest.revisionRequest;
        return query.select(o,s,m,r).from(o).leftJoin(s).on(s.orderId.eq(o.id)).join(m).on(m.id.eq(side==OrderSide.BUYER?o.sellerId:o.buyerId)).leftJoin(r).on(r.orderId.eq(o.id))
                .where(predicate(member,side,status)).orderBy(o.createdAt.desc(),o.id.desc()).limit(size).offset(offset).fetch().stream().map(tuple->{
                    var order=tuple.get(o);var snapshot=tuple.get(s);var other=tuple.get(m);var revision=tuple.get(r);
                    if(snapshot==null)throw new BusinessException(HttpStatus.INTERNAL_SERVER_ERROR,"DATA_INTEGRITY_ERROR","주문 조건 기록을 확인해야 합니다.");
                    LocalDateTime due=order.getPaidAt()==null?null:revision!=null?revision.getDueAt():order.getDueAt();
                    return new OrderSummaryResponse(order.getId().toString(),order.getStatus().name(),snapshot.getProductTitleSnapshot(),order.getPriceAmount(),new Participant(other.getId().toString(),other.getDisplayName()),order.getCreatedAt().atOffset(ZoneOffset.UTC),due==null?null:due.atOffset(ZoneOffset.UTC));
                }).toList();
    }
}
