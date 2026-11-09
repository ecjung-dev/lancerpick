package com.example.lancerpick.admin.controller;

import com.example.lancerpick.admin.dto.AdminDashboardResponse;
import com.example.lancerpick.admin.service.AdminDashboardService;
import com.example.lancerpick.common.security.CurrentMemberId;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 관리자 운영 현황(K3). /api/admin/** 는 SecurityConfig에서 ROLE_ADMIN만 통과한다. 조회 전용. */
@RestController
@Profile("!demo")
public class AdminDashboardController {
    private final AdminDashboardService dashboard;
    private final CurrentMemberId currentMemberId;

    public AdminDashboardController(AdminDashboardService dashboard, CurrentMemberId currentMemberId) {
        this.dashboard = dashboard;
        this.currentMemberId = currentMemberId;
    }

    @GetMapping("/api/admin/dashboard")
    public ResponseEntity<AdminDashboardResponse> summary(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(dashboard.summary(currentMemberId.require(authentication)));
    }
}
