package com.example.lancerpick.review.repository;

import com.example.lancerpick.member.entity.QMember;
import com.example.lancerpick.order.entity.QOrder;
import com.example.lancerpick.product.entity.QProduct;
import com.example.lancerpick.review.dto.PublicReviewResponse;
import com.example.lancerpick.review.entity.QReview;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!demo")
public class ReviewQueries {
    private final JPAQueryFactory query;
    private static final QReview r = QReview.review;
    private static final QOrder o = QOrder.order;
    private static final QMember m = QMember.member;
    private static final QProduct p = QProduct.product;

    public ReviewQueries(EntityManager em) { query = new JPAQueryFactory(em); }

    public boolean isPublicProduct(long id) {
        return query.select(p.id).from(p).where(p.id.eq(id), p.hidden.isFalse(),
                p.saleStatus.eq("ON_SALE")).fetchFirst() != null;
    }

    public long count(long productId) {
        Long total = query.select(r.id.count()).from(r).join(o).on(o.id.eq(r.orderId))
                .where(o.productId.eq(productId)).fetchOne();
        return total == null ? 0 : total;
    }

    public List<PublicReviewResponse> find(long productId, int size, long offset) {
        return query.select(r.id, m.displayName, r.rating, r.content, r.createdAt)
                .from(r).join(o).on(o.id.eq(r.orderId)).join(m).on(m.id.eq(o.buyerId))
                .where(o.productId.eq(productId)).orderBy(r.createdAt.desc(), r.id.desc())
                .limit(size).offset(offset).fetch().stream()
                .map(row -> new PublicReviewResponse(row.get(r.id).toString(), row.get(m.displayName),
                        row.get(r.rating), row.get(r.content), row.get(r.createdAt).atOffset(ZoneOffset.UTC)))
                .toList();
    }
}
