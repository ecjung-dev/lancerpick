package com.example.lancerpick.admin.service;

import com.example.lancerpick.admin.dto.AdminProductDetailResponse;
import com.example.lancerpick.admin.dto.AdminProductSummaryResponse;
import com.example.lancerpick.admin.dto.ModerationLogResponse;
import com.example.lancerpick.admin.dto.UpdateProductVisibilityRequest;
import com.example.lancerpick.admin.repository.AdminProductQueries;
import com.example.lancerpick.admin.repository.AdminProductQueries.ProductFilter;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.text.TextNormalizer;
import com.example.lancerpick.common.web.PageResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** API 9절 관리자 상품 운영. 판매 상태·거래·지갑은 변경하지 않는다. */
@Service
@Profile("!demo")
public class AdminProductService {
    private static final int KEYWORD_MAX = 100;
    private static final int REASON_MAX = 1000;

    private final AdminProductQueries queries;
    private final Clock clock;

    public AdminProductService(AdminProductQueries queries, Clock clock) {
        this.queries = queries;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminProductSummaryResponse> list(String keyword, String isHidden, String saleStatus,
            int page, int size) {
        var filter = new ProductFilter(normalizeKeyword(keyword), parseHidden(isHidden), parseSaleStatus(saleStatus));
        long total = queries.count(filter);
        // page는 int라도 page*size가 int 범위를 넘을 수 있어 long으로 계산한다.
        List<AdminProductSummaryResponse> items = queries.find(filter, size, (long) page * size);
        return new PageResponse<>(items, page, size, total, totalPages(total, size));
    }

    @Transactional(readOnly = true)
    public AdminProductDetailResponse detail(String id) {
        return queries.findDetail(parseId(id)).orElseThrow(AdminProductService::productNotFound);
    }

    @Transactional(readOnly = true)
    public PageResponse<ModerationLogResponse> logs(String id, int page, int size) {
        long productId = parseId(id);
        if (!queries.exists(productId)) throw productNotFound();
        long total = queries.countLogs(productId);
        return new PageResponse<>(queries.findLogs(productId, size, (long) page * size), page, size, total,
                totalPages(total, size));
    }

    /**
     * 숨김·복구. 순서가 중요하다.
     * 1) 입력 검증 → 2) 실행자가 지금도 관리자인지 DB 확인 → 3) 상품 잠금
     * 4) 이미 같은 상태면 버전과 관계없이 현재 상세 반환(버전·이력 변경 없음)
     * 5) 버전 불일치면 409 → 6) 상태·버전·이력을 같은 트랜잭션에서 저장
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AdminProductDetailResponse changeVisibility(long adminId, String id, UpdateProductVisibilityRequest request) {
        String reason = normalizeReason(request.reason());
        long productId = parseId(id);
        boolean hidden = request.hidden();

        String role = queries.findMemberRole(adminId).orElseThrow(() ->
                new BusinessException(HttpStatus.UNAUTHORIZED, "AUTHENTICATION_REQUIRED", "로그인이 필요합니다."));
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(HttpStatus.FORBIDDEN, "FORBIDDEN", "접근 권한이 없습니다.");
        }

        var current = queries.lockVisibility(productId).orElseThrow(AdminProductService::productNotFound);
        if (current.hidden() == hidden) {
            return detail(productId);
        }
        if (current.version() != request.expectedVersion()) {
            throw versionConflict();
        }

        LocalDateTime at = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        if (queries.updateVisibility(productId, hidden, current.version(), at) != 1) {
            throw versionConflict();
        }
        queries.insertModerationLog(productId, adminId, hidden ? "HIDE" : "RESTORE", reason, at);
        return detail(productId);
    }

    private AdminProductDetailResponse detail(long productId) {
        return queries.findDetail(productId).orElseThrow(AdminProductService::productNotFound);
    }

    // ---------- 입력 검증 ----------

    private static String normalizeKeyword(String keyword) {
        if (keyword == null) return null;
        String normalized = TextNormalizer.normalize(keyword);
        if (normalized.isEmpty()) return null; // 정리 후 빈 값은 검색 제한 없음
        if (normalized.codePointCount(0, normalized.length()) > KEYWORD_MAX) {
            throw invalid("검색어는 최대 100자입니다.");
        }
        return normalized;
    }

    private static Boolean parseHidden(String value) {
        if (value == null) return null;
        // "TRUE"·"1"·빈 값을 허용하지 않는다.
        return switch (value) {
            case "true" -> Boolean.TRUE;
            case "false" -> Boolean.FALSE;
            default -> throw invalid("isHidden은 true 또는 false만 허용합니다.");
        };
    }

    private static String parseSaleStatus(String value) {
        if (value == null) return null;
        if (value.equals("ON_SALE") || value.equals("STOPPED")) return value;
        throw invalid("saleStatus는 ON_SALE 또는 STOPPED만 허용합니다.");
    }

    private static String normalizeReason(String reason) {
        String normalized = TextNormalizer.normalize(reason);
        int length = normalized.codePointCount(0, normalized.length());
        if (length < 1 || length > REASON_MAX) {
            throw invalid("사유는 공백 정리 후 1~1,000자여야 합니다.");
        }
        return normalized;
    }

    private static long parseId(String id) {
        if (id == null || !id.matches("[0-9]+")) throw invalid("상품 ID는 양의 정수 범위여야 합니다.");
        try {
            long value = Long.parseLong(id);
            if (value <= 0) throw invalid("상품 ID는 양의 정수 범위여야 합니다.");
            return value;
        } catch (NumberFormatException e) {
            throw invalid("상품 ID는 양의 정수 범위여야 합니다.");
        }
    }

    private static int totalPages(long total, int size) {
        return (int) ((total + size - 1) / size);
    }

    private static BusinessException invalid(String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", message);
    }

    private static BusinessException productNotFound() {
        return new BusinessException(HttpStatus.NOT_FOUND, "RESOURCE_NOT_FOUND", "상품을 찾을 수 없습니다.");
    }

    private static BusinessException versionConflict() {
        return new BusinessException(HttpStatus.CONFLICT, "PRODUCT_VERSION_CONFLICT",
                "상품 정보가 변경되었습니다. 최신 상태를 다시 확인해 주세요.");
    }
}
