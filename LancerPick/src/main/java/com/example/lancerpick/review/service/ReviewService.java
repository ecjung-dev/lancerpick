package com.example.lancerpick.review.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.text.TextNormalizer;
import com.example.lancerpick.common.time.UtcTimes;
import com.example.lancerpick.order.entity.OrderStatus;
import com.example.lancerpick.order.repository.OrderRepository;
import com.example.lancerpick.review.dto.*;
import com.example.lancerpick.review.entity.Review;
import com.example.lancerpick.review.repository.ReviewQueries;
import com.example.lancerpick.review.repository.ReviewRepository;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!demo")
public class ReviewService {
    private final OrderRepository orders;
    private final ReviewRepository reviews;
    private final ReviewQueries queries;
    private final Clock clock;

    public ReviewService(OrderRepository orders, ReviewRepository reviews, ReviewQueries queries, Clock clock) {
        this.orders = orders;
        this.reviews = reviews;
        this.queries = queries;
        this.clock = clock;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ReviewResponse write(long memberId, String id, ReviewRequest request) {
        long orderId = parseId(id);
        if (request.rating() == null || request.rating() < 1 || request.rating() > 5) throw invalid();
        String content = request.content() == null ? null : TextNormalizer.normalize(request.content());
        if (content != null && (content.isEmpty() || content.codePointCount(0, content.length()) > 2000)) {
            throw invalid();
        }
        // 기존 주문 명령과 같은 회원 공유 잠금 → 주문 배타 잠금 순서. 동시 후기 쓰기를 직렬화한다.
        var actor = orders.findActorForShare(memberId).orElseThrow(() ->
                error(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."));
        var order = orders.findForDecision(orderId, memberId).orElseThrow(() ->
                error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "주문을 찾을 수 없습니다."));
        if (!order.getBuyerId().equals(memberId)) {
            throw error(HttpStatus.FORBIDDEN, "FORBIDDEN", "구매자만 후기를 작성할 수 있습니다.");
        }
        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw error(HttpStatus.CONFLICT, "ORDER_STATE_CONFLICT", "구매 확정된 주문에만 후기를 작성할 수 있습니다.");
        }
        if (reviews.existsByOrderId(orderId)) {
            throw error(HttpStatus.CONFLICT, "REVIEW_ALREADY_EXISTS", "이미 작성한 후기가 있습니다.");
        }
        var now = UtcTimes.nowMicros(clock);
        var review = reviews.saveAndFlush(Review.create(orderId, request.rating(), content, now));
        return new ReviewResponse(review.getId().toString(), idOf(orderId), actor.getDisplayName(),
                review.getRating(), review.getContent(), now.atOffset(ZoneOffset.UTC));
    }

    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ProductReviewsResponse list(String id, int page, int size, String ratingParam) {
        long productId = parseId(id);
        if (page < 0 || size < 1 || size > 100 || (ratingParam != null && !ratingParam.matches("[1-5]"))) {
            throw invalid();
        }
        Integer rating = ratingParam == null ? null : Integer.valueOf(ratingParam);
        if (!queries.isPublicProduct(productId)) {
            throw error(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "상품을 찾을 수 없습니다.");
        }
        var counts = queries.countByRating(productId);
        long reviewCount = counts.values().stream().mapToLong(Long::longValue).sum();
        double ratingSum = counts.entrySet().stream()
                .mapToDouble(entry -> entry.getKey() * (double) entry.getValue()).sum();
        var summary = new ProductReviewsResponse.Summary(reviewCount == 0 ? null : ratingSum / reviewCount, reviewCount);
        long total = rating == null ? reviewCount : counts.getOrDefault(rating, 0L);
        long offset = (long) page * size;
        var items = offset >= total ? List.<PublicReviewResponse>of() : queries.find(productId, rating, size, offset);
        return new ProductReviewsResponse(items, page, size, total,
                (int) Math.min(Integer.MAX_VALUE, total / size + (total % size == 0 ? 0 : 1)), summary);
    }

    private static String idOf(long id) { return Long.toString(id); }
    private static long parseId(String value) {
        try {
            if (value == null || !value.matches("[0-9]{1,19}")) throw invalid();
            long id = Long.parseLong(value);
            if (id <= 0) throw invalid();
            return id;
        } catch (NumberFormatException e) { throw invalid(); }
    }
    private static BusinessException invalid() {
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "입력값을 확인해 주세요.");
    }
    private static BusinessException error(HttpStatus status, String code, String message) {
        return new BusinessException(status, code, message);
    }
}
