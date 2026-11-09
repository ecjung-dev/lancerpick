package com.example.lancerpick.admin.controller;

import com.example.lancerpick.admin.dto.AdminInquiryDecisionRequest;
import com.example.lancerpick.admin.dto.AdminInquiryResponses.Detail;
import com.example.lancerpick.admin.dto.AdminInquiryResponses.Summary;
import com.example.lancerpick.admin.service.AdminInquiryService;
import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.order.dto.AdminCancellationReviewResponse;
import com.example.lancerpick.order.service.AdminCancellationReviewService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 운영 문의(K4). /api/admin/** 는 SecurityConfig에서 ROLE_ADMIN만 통과하고, 서비스가 현재 DB 역할을 다시 확인한다. 승인/거절은
 * 주문 영역 서비스(AdminCancellationReviewService)를 그대로 호출하며 권한·사유·상태 판단은 그 서비스가 한다.
 */
@RestController
@Profile("!demo")
@RequestMapping("/api/admin/inquiries")
public class AdminInquiryController {
    private final AdminInquiryService inquiries;
    private final AdminCancellationReviewService reviews;
    private final CurrentMemberId currentMemberId;

    public AdminInquiryController(AdminInquiryService inquiries, AdminCancellationReviewService reviews,
            CurrentMemberId currentMemberId) {
        this.inquiries = inquiries;
        this.reviews = reviews;
        this.currentMemberId = currentMemberId;
    }

    @GetMapping
    public ResponseEntity<PageResponse<Summary>> list(Authentication authentication,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(inquiries.list(currentMemberId.require(authentication), status, type, page, size));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Detail> detail(Authentication authentication, @PathVariable("id") @Min(1) long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(inquiries.detail(currentMemberId.require(authentication), id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<AdminCancellationReviewResponse> approve(Authentication authentication,
            @PathVariable("id") @Min(1) long id, @RequestBody AdminInquiryDecisionRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reviews.approve(currentMemberId.require(authentication), id, request.reason()));
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<AdminCancellationReviewResponse> reject(Authentication authentication,
            @PathVariable("id") @Min(1) long id, @RequestBody AdminInquiryDecisionRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reviews.reject(currentMemberId.require(authentication), id, request.reason()));
    }

    /** 승인·거절할 수 없게 된 문의 종료. 본문 없음. */
    @PostMapping("/{id}/close")
    public ResponseEntity<Detail> close(Authentication authentication, @PathVariable("id") @Min(1) long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(inquiries.close(currentMemberId.require(authentication), id));
    }
}
