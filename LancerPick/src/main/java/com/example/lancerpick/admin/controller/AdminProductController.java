package com.example.lancerpick.admin.controller;

import com.example.lancerpick.admin.dto.AdminProductDetailResponse;
import com.example.lancerpick.admin.dto.AdminProductSummaryResponse;
import com.example.lancerpick.admin.dto.ModerationLogResponse;
import com.example.lancerpick.admin.dto.UpdateProductVisibilityRequest;
import com.example.lancerpick.admin.service.AdminProductService;
import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.common.web.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * API 9절 관리자 상품 운영. /api/admin/** 는 SecurityConfig에서 ROLE_ADMIN만 통과한다.
 * isHidden·saleStatus는 String으로 받아 Service에서 정확한 값만 허용한다(빈 값·다른 문자열 400).
 */
@RestController
@Profile("!demo")
@RequestMapping("/api/admin/products")
public class AdminProductController {
    private final AdminProductService products;
    private final CurrentMemberId currentMemberId;

    public AdminProductController(AdminProductService products, CurrentMemberId currentMemberId) {
        this.products = products;
        this.currentMemberId = currentMemberId;
    }

    @GetMapping
    public ResponseEntity<PageResponse<AdminProductSummaryResponse>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String isHidden,
            @RequestParam(required = false) String saleStatus,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(products.list(keyword, isHidden, saleStatus, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AdminProductDetailResponse> detail(@PathVariable String id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(products.detail(id));
    }

    @GetMapping("/{id}/moderation-logs")
    public ResponseEntity<PageResponse<ModerationLogResponse>> logs(@PathVariable String id,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(products.logs(id, page, size));
    }

    @PatchMapping("/{id}/visibility")
    public ResponseEntity<AdminProductDetailResponse> changeVisibility(Authentication authentication,
            @PathVariable String id, @Valid @RequestBody UpdateProductVisibilityRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(products.changeVisibility(currentMemberId.require(authentication), id, request));
    }
}
