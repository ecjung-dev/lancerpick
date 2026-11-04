package com.example.lancerpick.product.repository;

import com.example.lancerpick.member.entity.QMember;
import com.example.lancerpick.product.dto.CategoryResponse;
import com.example.lancerpick.product.dto.OwnedProductSummaryResponse;
import com.example.lancerpick.product.dto.ProductSummaryResponse;
import com.example.lancerpick.product.entity.QCategory;
import com.example.lancerpick.product.entity.QProduct;
import com.querydsl.jpa.impl.JPAQueryFactory;
import jakarta.persistence.EntityManager;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;

@Repository
@Profile("!demo")
public class MyProductQueries {
    private final JPAQueryFactory query;
    private static final QProduct p = QProduct.product;
    private static final QCategory c = QCategory.category;
    private static final QMember m = QMember.member;

    public MyProductQueries(EntityManager em) { query = new JPAQueryFactory(em); }

    public long count(long memberId) {
        Long total = query.select(p.id.count()).from(p).where(p.sellerId.eq(memberId)).fetchOne();
        return total == null ? 0 : total;
    }

    public List<OwnedProductSummaryResponse> find(long memberId, int size, long offset) {
        return query.select(p.id, p.title, p.price, p.durationDays,
                        c.id, c.name, c.displayOrder, m.id, m.displayName,
                        p.saleStatus, p.hidden, p.version, p.createdAt, p.updatedAt)
                .from(p).join(c).on(c.id.eq(p.categoryId)).join(m).on(m.id.eq(p.sellerId))
                .where(p.sellerId.eq(memberId))
                .orderBy(p.createdAt.desc(), p.id.desc()).limit(size).offset(offset).fetch().stream()
                .map(row -> new OwnedProductSummaryResponse(
                        row.get(p.id).toString(), row.get(p.title), row.get(p.price), row.get(p.durationDays),
                        new CategoryResponse(row.get(c.id).toString(), row.get(c.name), row.get(c.displayOrder)),
                        new ProductSummaryResponse.Seller(row.get(m.id).toString(), row.get(m.displayName)),
                        row.get(p.saleStatus), row.get(p.hidden), row.get(p.version),
                        row.get(p.createdAt).atOffset(ZoneOffset.UTC), row.get(p.updatedAt).atOffset(ZoneOffset.UTC)))
                .toList();
    }
}
