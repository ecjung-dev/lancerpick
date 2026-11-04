package com.example.lancerpick.product.service;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.text.TextNormalizer;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.product.dto.ProductSummaryResponse;
import com.example.lancerpick.product.dto.ProductDetailResponse;
import com.example.lancerpick.product.repository.ProductSearchQueries;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Profile("!demo")
public class ProductSearchService {

    private final ProductSearchQueries queries;

    public ProductSearchService(ProductSearchQueries queries) {
        this.queries = queries;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> search(
            String keyword, String categoryId, int page, int size) {

        validatePage(page, size);

        String normalizedKeyword = normalizeKeyword(keyword);
        Long parsedCategoryId = parseCategoryId(categoryId);

        long total = queries.count(normalizedKeyword, parsedCategoryId);
        long offset = (long) page * size;

        var items = offset >= total ? List.<ProductSummaryResponse>of() : queries.find(
                normalizedKeyword, parsedCategoryId, size, offset
        );

        long pages = total / size + (total % size == 0 ? 0 : 1);
        int totalPages = (int) Math.min(pages, Integer.MAX_VALUE);

        return new PageResponse<>(
                items, page, size, total, totalPages
        );
    }

    @Transactional(readOnly = true)
    public ProductDetailResponse getPublicDetail(String productId) {
        if (productId == null || !productId.matches("[0-9]{1,19}")) {
            throw invalid("상품 번호는 양의 정수여야 합니다.");
        }
        long id;
        try {
            id = Long.parseLong(productId);
        } catch (NumberFormatException exception) {
            throw invalid("상품 번호가 허용 범위를 벗어났습니다.");
        }
        if (id <= 0) throw invalid("상품 번호는 양의 정수여야 합니다.");
        return queries.findPublicDetail(id).orElseThrow(() -> new BusinessException(
                HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "상품을 찾을 수 없습니다."));
    }

    private static void validatePage(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw invalid("페이지는 0 이상, 조회 개수는 1~100이어야 합니다.");
        }
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }

        String normalized = TextNormalizer.normalize(keyword);

        if (normalized.isEmpty()) {
            return null;
        }

        if (normalized.codePointCount(0, normalized.length()) > 100) {
            throw invalid("검색어는 최대 100자입니다.");
        }

        return normalized;
    }

    private static Long parseCategoryId(String categoryId) {
        if (categoryId == null) {
            return null;
        }

        if (!categoryId.matches("[0-9]{1,19}")) {
            throw invalid("카테고리 번호는 양의 정수여야 합니다.");
        }

        try {
            long id = Long.parseLong(categoryId);

            if (id <= 0) {
                throw invalid("카테고리 번호는 양의 정수여야 합니다.");
            }

            return id;
        } catch (NumberFormatException exception) {
            throw invalid("카테고리 번호가 허용 범위를 벗어났습니다.");
        }
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(
                HttpStatus.BAD_REQUEST,
                "VALIDATION_FAILED",
                message
        );
    }
}
