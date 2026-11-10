package com.example.lancerpick.product.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.product.dto.ProductSummaryResponse;
import com.example.lancerpick.product.entity.Product;
import com.example.lancerpick.product.entity.ProductFavorite;
import com.example.lancerpick.product.repository.ProductFavoriteQueries;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Clock;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!demo")
public class ProductFavoriteService {
    private final EntityManager entityManager;
    private final ProductFavoriteQueries queries;
    private final Clock clock;

    public ProductFavoriteService(EntityManager entityManager, ProductFavoriteQueries queries, Clock clock) {
        this.entityManager = entityManager;
        this.queries = queries;
        this.clock = clock;
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResponse<ProductSummaryResponse> list(long memberId, int page, int size) {
        if (!PageResponse.isValidRequest(page, size)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "페이지는 0 이상, 조회 개수는 1~100이어야 합니다.");
        }
        long count = queries.count(memberId);
        long offset = (long) page * size;
        var items = offset < count ? queries.find(memberId, size, offset) : List.<ProductSummaryResponse>of();
        return new PageResponse<>(items, page, size, count,
                (int) Math.min(Integer.MAX_VALUE, count / size + (count % size == 0 ? 0 : 1)));
    }

    @Transactional(readOnly = true)
    public boolean contains(long memberId, long productId) {
        return entityManager.find(ProductFavorite.class, new ProductFavorite.Key(memberId, productId)) != null;
    }

    @Transactional
    public void add(long memberId, long productId) {
        lockMember(memberId);
        var product = entityManager.find(Product.class, productId, LockModeType.PESSIMISTIC_READ);
        if (product == null || product.isHidden() || !"ON_SALE".equals(product.getSaleStatus())) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "상품을 찾을 수 없습니다.");
        }
        var key = new ProductFavorite.Key(memberId, productId);
        if (entityManager.find(ProductFavorite.class, key) == null) {
            var now = UtcTimes.nowMicros(clock);
            entityManager.persist(new ProductFavorite(key, now));
        }
    }

    @Transactional
    public void remove(long memberId, long productId) {
        lockMember(memberId);
        var favorite = entityManager.find(ProductFavorite.class, new ProductFavorite.Key(memberId, productId));
        if (favorite != null) entityManager.remove(favorite);
    }

    private void lockMember(long memberId) {
        // Serialize a member's writes before checking whether the relation already exists.
        if (entityManager.find(Member.class, memberId, LockModeType.PESSIMISTIC_WRITE) == null) {
            throw new BusinessException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다.");
        }
    }
}
