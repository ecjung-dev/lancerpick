package com.example.lancerpick.product.repository;

import com.example.lancerpick.member.entity.QMember;
import com.example.lancerpick.product.dto.CategoryResponse;
import com.example.lancerpick.product.dto.ProductSummaryResponse;
import com.example.lancerpick.product.entity.QCategory;
import com.example.lancerpick.product.entity.QProduct;
import com.example.lancerpick.product.entity.QProductFavorite;
import com.querydsl.jpa.impl.JPAQuery;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!demo")
public class ProductFavoriteQueries {
    private static final QProductFavorite f = QProductFavorite.productFavorite;
    private static final QProduct p = QProduct.product;
    private static final QCategory c = QCategory.category;
    private static final QMember m = QMember.member;
    private final JPAQueryFactory query;

    public ProductFavoriteQueries(EntityManager entityManager) {
        query = new JPAQueryFactory(entityManager);
    }

    public long count(long memberId) {
        return visible(query.select(p.id.count()), memberId).fetchOne();
    }

    public List<ProductSummaryResponse> find(long memberId, int size, long offset) {
        return visible(query.select(p.id, p.title, p.price, p.durationDays,
                        c.id, c.name, c.displayOrder, m.id, m.displayName), memberId)
                .orderBy(f.createdAt.desc(), f.productId.desc())
                .limit(size).offset(offset).fetch().stream()
                .map(row -> new ProductSummaryResponse(
                        row.get(p.id).toString(), row.get(p.title), row.get(p.price), row.get(p.durationDays),
                        new CategoryResponse(row.get(c.id).toString(), row.get(c.name), row.get(c.displayOrder)),
                        new ProductSummaryResponse.Seller(row.get(m.id).toString(), row.get(m.displayName))))
                .toList();
    }

    private <T> JPAQuery<T> visible(JPAQuery<T> selection, long memberId) {
        return selection.from(f).join(p).on(p.id.eq(f.productId))
                .join(c).on(c.id.eq(p.categoryId)).join(m).on(m.id.eq(p.sellerId))
                .where(f.memberId.eq(memberId), p.hidden.isFalse(), p.saleStatus.eq("ON_SALE"));
    }
}
