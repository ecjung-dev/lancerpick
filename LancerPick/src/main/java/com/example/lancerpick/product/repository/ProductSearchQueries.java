package com.example.lancerpick.product.repository;

import com.example.lancerpick.member.entity.QMember;
import com.example.lancerpick.product.dto.CategoryResponse;
import com.example.lancerpick.product.dto.ProductSummaryResponse;
import com.example.lancerpick.product.dto.ProductSearchOptions;
import com.example.lancerpick.product.dto.ProductDetailResponse;
import java.time.ZoneOffset;
import java.util.Optional;
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

    public long count(String keyword, Long categoryId, ProductSearchOptions options, Long minimumOrderAmount) {
        Long total = query.select(p.id.count()).from(p)
                .join(c).on(c.id.eq(p.categoryId))
                .join(m).on(m.id.eq(p.sellerId))
                .where(where(keyword, categoryId, options).and(orderable(minimumOrderAmount))).fetchOne();
        return total == null ? 0 : total;
    }

    public List<ProductSummaryResponse> find(String keyword, Long categoryId, ProductSearchOptions options,
            Long minimumOrderAmount, int size, long offset) {
        return query.select(p.id, p.title, p.price, p.durationDays,
                        c.id, c.name, c.displayOrder, m.id, m.displayName)
                .from(p)
                .join(c).on(c.id.eq(p.categoryId))
                .join(m).on(m.id.eq(p.sellerId))
                .where(where(keyword, categoryId, options).and(orderable(minimumOrderAmount)))
                .orderBy(switch (options.sort()) {
                    case NEWEST -> p.createdAt.desc();
                    case PRICE_ASC -> p.price.asc();
                    case PRICE_DESC -> p.price.desc();
                }, p.id.desc())
                .limit(size).offset(offset).fetch().stream()
                .map(row -> new ProductSummaryResponse(
                        row.get(p.id).toString(), row.get(p.title), row.get(p.price), row.get(p.durationDays),
                        new CategoryResponse(row.get(c.id).toString(), row.get(c.name), row.get(c.displayOrder)),
                        new ProductSummaryResponse.Seller(row.get(m.id).toString(), row.get(m.displayName))))
                .toList();
    }

    public Optional<ProductDetailResponse> findPublicDetail(long id, Long minimumOrderAmount) {
        var row = query.select(p.id, p.title, p.price, p.durationDays,
                        c.id, c.name, c.displayOrder, m.id, m.displayName,
                        p.description, p.scopeDescription, p.preparationDescription,
                        p.version, p.saleStatus, p.createdAt, p.updatedAt)
                .from(p).join(c).on(c.id.eq(p.categoryId))
                .join(m).on(m.id.eq(p.sellerId))
                .where(where(null, null, ProductSearchOptions.DEFAULT).and(p.id.eq(id))).fetchOne();
        if (row == null) return Optional.empty();
        return Optional.of(new ProductDetailResponse(
                row.get(p.id).toString(), row.get(p.title), row.get(p.price), row.get(p.durationDays),
                new CategoryResponse(row.get(c.id).toString(), row.get(c.name), row.get(c.displayOrder)),
                new ProductSummaryResponse.Seller(row.get(m.id).toString(), row.get(m.displayName)),
                row.get(p.description), row.get(p.scopeDescription), row.get(p.preparationDescription),
                row.get(p.version), row.get(p.saleStatus),
                row.get(p.createdAt).atOffset(ZoneOffset.UTC), row.get(p.updatedAt).atOffset(ZoneOffset.UTC),
                minimumOrderAmount));
    }

    /** 공개 목록은 현재 신규 주문 정책으로 주문할 수 없는 가격의 상품을 뺀다. 상세는 안내 문구로 남긴다. */
    private static BooleanBuilder orderable(Long minimumOrderAmount) {
        var predicate = new BooleanBuilder();
        if (minimumOrderAmount != null) predicate.and(p.price.goe(minimumOrderAmount));
        return predicate;
    }

    private static BooleanBuilder where(String keyword, Long categoryId, ProductSearchOptions options) {
        var predicate = new BooleanBuilder(p.saleStatus.eq("ON_SALE").and(p.hidden.isFalse()));
        if (keyword != null && !keyword.isEmpty()) {
            String pattern = "%" + keyword.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            predicate.and(p.title.like(pattern, '!').or(p.description.like(pattern, '!')));
        }
        if (categoryId != null) predicate.and(p.categoryId.eq(categoryId));
        if (options.sellerId() != null) predicate.and(p.sellerId.eq(options.sellerId()));
        if (options.minPrice() != null) predicate.and(p.price.goe(options.minPrice().longValue()));
        if (options.maxPrice() != null) predicate.and(p.price.loe(options.maxPrice().longValue()));
        if (options.maxDurationDays() != null) predicate.and(p.durationDays.loe(options.maxDurationDays()));
        return predicate;
    }
}
