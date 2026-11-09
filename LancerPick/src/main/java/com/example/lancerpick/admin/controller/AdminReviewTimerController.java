package com.example.lancerpick.admin.controller;

import com.example.lancerpick.admin.dto.AdminReviewTimerResponses.ActiveTimer;
import com.example.lancerpick.admin.dto.AdminReviewTimerResponses.StopRequest;
import com.example.lancerpick.admin.service.AdminReviewTimerService;
import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.order.dto.ReviewTimerResponse;
import com.example.lancerpick.order.service.ReviewTimerService;
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
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 타이머 해제(K5). /api/admin/** 는 SecurityConfig에서 ROLE_ADMIN만 통과한다. 해제는 ReviewTimerService.adminStop을
 * 그대로 호출하며 권한(현재 DB 역할)·사유·V3 여부·활성 타이머·만료 우선 판단은 그 서비스가 한다.
 */
@RestController
@Profile("!demo")
public class AdminReviewTimerController {
    private final AdminReviewTimerService timers;
    private final ReviewTimerService reviewTimers;
    private final CurrentMemberId currentMemberId;

    public AdminReviewTimerController(AdminReviewTimerService timers, ReviewTimerService reviewTimers,
            CurrentMemberId currentMemberId) {
        this.timers = timers;
        this.reviewTimers = reviewTimers;
        this.currentMemberId = currentMemberId;
    }

    @GetMapping("/api/admin/review-timers")
    public ResponseEntity<PageResponse<ActiveTimer>> active(Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(timers.active(currentMemberId.require(authentication), page, size));
    }

    @PostMapping("/api/admin/orders/{orderId}/review-timer/stop")
    public ResponseEntity<ReviewTimerResponse> stop(Authentication authentication,
            @PathVariable("orderId") @Min(1) long orderId, @RequestBody StopRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(reviewTimers.adminStop(currentMemberId.require(authentication), orderId, request.reason()));
    }
}
