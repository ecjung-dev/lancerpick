package com.example.lancerpick.order.repository;

import java.util.Optional;
import org.springframework.context.annotation.Profile;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.*;
import com.example.lancerpick.member.entity.QMember;
import com.example.lancerpick.product.entity.QProduct;
import org.springframework.stereotype.Repository;

/** 회원·상품 엔티티를 중복 정의하지 않는 주문 생성 전용 조회 경계. */
@Repository
@Profile("!demo")
public class OrderCreationQueries {
    private final JPAQueryFactory query;

    public OrderCreationQueries(EntityManager em) {
        this.query = new JPAQueryFactory(em);
    }

    public Optional<Long> findProductSellerId(long productId) {
        var p=QProduct.product;
        return Optional.ofNullable(query.select(p.sellerId).from(p).where(p.id.eq(productId)).fetchOne());
    }

    /** FK가 참조할 두 회원을 전역 ID 순서로 잠가 교차 구매의 잠금 역전을 막는다. */
    public boolean lockParticipants(long buyerId, Long sellerId) {
        if (sellerId == null || buyerId <= sellerId) {
            boolean buyerExists = lockMember(buyerId);
            if (sellerId != null && sellerId != buyerId) lockMember(sellerId);
            return buyerExists;
        }
        lockMember(sellerId);
        return lockMember(buyerId);
    }

    private boolean lockMember(long memberId) {
        var m=QMember.member;
        return query.selectFrom(m).where(m.id.eq(memberId)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne()!=null;
    }

    public Optional<ProductForOrder> lockProduct(long productId) {
        var p=QProduct.product;
        var product=query.selectFrom(p).where(p.id.eq(productId)).setLockMode(LockModeType.PESSIMISTIC_WRITE).fetchOne();
        return Optional.ofNullable(product).map(row->new ProductForOrder(row.getId(),row.getSellerId(),row.getVersion(),row.getTitle(),row.getDescription(),row.getScopeDescription(),row.getPreparationDescription(),row.getPrice(),row.getDurationDays(),row.getSaleStatus(),row.isHidden()));
    }

    public record ProductForOrder(long id, long sellerId, long version, String title,
            String description, String scope, String preparation, long price, int durationDays,
            String saleStatus, boolean hidden) {}
}
