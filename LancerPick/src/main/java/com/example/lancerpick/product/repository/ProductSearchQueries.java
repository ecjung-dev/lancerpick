package com.example.lancerpick.product.repository;

import com.example.lancerpick.member.entity.QMember;
import com.example.lancerpick.product.dto.CategoryResponse;
import com.example.lancerpick.product.dto.ProductSummaryResponse;
import com.example.lancerpick.product.entity.QCategory;
import com.example.lancerpick.product.entity.QProduct;
import com.querydsl.core.BooleanBuilder;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

/** 공개 목록에 필요한 열만 기존 공유 Entity에서 조회한다. */
@Repository
@Profile("!demo")
public class ProductSearchQueries {
    private static final QProduct p = QProduct.product;
    private static final QCategory c = QCategory.category;
    private static final QMember m = QMember.member;
    private final JPAQueryFactory query;

    public ProductSearchQueries(EntityManager entityManager) {
        this.query = new JPAQueryFactory(entityManager);
    }

    public long count(String keyword, Long categoryId) {
        Long total = query.select(p.id.count()).from(p)
                .join(c).on(c.id.eq(p.categoryId))
                .join(m).on(m.id.eq(p.sellerId))
                .where(where(keyword, categoryId)).fetchOne();
        return total == null ? 0 : total;
    }

    public List<ProductSummaryResponse> find(String keyword, Long categoryId, int size, long offset) {
        return query.select(p.id, p.title, p.price, p.durationDays,
                        c.id, c.name, c.displayOrder, m.id, m.displayName)
                .from(p)
                .join(c).on(c.id.eq(p.categoryId))
                .join(m).on(m.id.eq(p.sellerId))
                .where(where(keyword, categoryId))
                .orderBy(p.createdAt.desc(), p.id.desc())
                .limit(size).offset(offset).fetch().stream()
                .map(row -> new ProductSummaryResponse(
                        row.get(p.id).toString(), row.get(p.title), row.get(p.price), row.get(p.durationDays),
                        new CategoryResponse(row.get(c.id).toString(), row.get(c.name), row.get(c.displayOrder)),
                        new ProductSummaryResponse.Seller(row.get(m.id).toString(), row.get(m.displayName))))
                .toList();
    }

    private static BooleanBuilder where(String keyword, Long categoryId) {
        var predicate = new BooleanBuilder(p.saleStatus.eq("ON_SALE").and(p.hidden.isFalse()));
        if (keyword != null && !keyword.isEmpty()) {
            String pattern = "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            predicate.and(p.title.like(pattern, '!').or(p.description.like(pattern, '!')));
        }
        if (categoryId != null) predicate.and(p.categoryId.eq(categoryId));
        return predicate;
    }
}
