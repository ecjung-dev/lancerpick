package com.example.lancerpick.admin.controller;

import com.example.lancerpick.admin.dto.AdminOutboxResponses.OutboxIssue;
import com.example.lancerpick.admin.service.AdminOutboxService;
import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.common.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 알림 처리 실패 조회(K6). /api/admin/** 는 SecurityConfig에서 ROLE_ADMIN만 통과한다. 조회 전용. */
@RestController
@Profile("!demo")
public class AdminOutboxController {
    private final AdminOutboxService outbox;
    private final CurrentMemberId currentMemberId;

    public AdminOutboxController(AdminOutboxService outbox, CurrentMemberId currentMemberId) {
        this.outbox = outbox;
        this.currentMemberId = currentMemberId;
    }

    @GetMapping("/api/admin/outbox-events")
    public ResponseEntity<PageResponse<OutboxIssue>> issues(Authentication authentication,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long orderId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(outbox.issues(currentMemberId.require(authentication), status, orderId, page, size));
    }
}
