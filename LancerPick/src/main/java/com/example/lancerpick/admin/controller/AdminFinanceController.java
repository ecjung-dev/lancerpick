package com.example.lancerpick.admin.controller;

import com.example.lancerpick.admin.dto.AdminContractPdfResponse;
import com.example.lancerpick.admin.dto.AdminFinanceResponses;
import com.example.lancerpick.admin.repository.AdminFinanceQueries.PaymentFilter;
import com.example.lancerpick.admin.repository.AdminFinanceQueries.TopupFilter;
import com.example.lancerpick.admin.service.AdminContractPdfQueryService;
import com.example.lancerpick.admin.service.AdminFinanceQueryService;
import com.example.lancerpick.common.policy.TradePolicyVersion;
import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.wallet.entity.WalletTopupStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 관리자 금융·계약 PDF 운영 조회(K3·K6 조회). 모두 읽기 전용 GET이며 no-store다. /api/admin/** 는 SecurityConfig에서 ROLE_ADMIN만
 * 통과하고, 각 서비스(황준범 #117·#125)가 현재 DB 역할·필터·대상을 다시 검사하며 결제 키·처리권·파일 경로 같은 민감정보를 빼고 돌려준다.
 *
 * <p>응답의 후보 판단(eligible·reason)은 조회 순간의 조건일 뿐 실행 권한이 아니다. 재확인·재시작·재생성 실행 API는 관리자 감사 이력
 * 설계 뒤에 별도로 만든다.
 */
@RestController
@Profile("!demo")
public class AdminFinanceController {
    private final AdminFinanceQueryService finance;
    private final AdminContractPdfQueryService pdfs;
    private final CurrentMemberId currentMemberId;

    public AdminFinanceController(AdminFinanceQueryService finance, AdminContractPdfQueryService pdfs,
            CurrentMemberId currentMemberId) {
        this.finance = finance;
        this.pdfs = pdfs;
        this.currentMemberId = currentMemberId;
    }

    /** status는 READY·PROCESSING·UNKNOWN·COMPLETED·FAILED만(대소문자 그대로). 다른 값은 400. */
    @GetMapping("/api/admin/finance/topups")
    public ResponseEntity<PageResponse<AdminFinanceResponses.Topup>> topups(Authentication authentication,
            @RequestParam(required = false) @Min(1) Long memberId,
            @RequestParam(required = false) WalletTopupStatus status,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok(finance.topups(admin(authentication), new TopupFilter(memberId, status), page, size));
    }

    @GetMapping("/api/admin/finance/topups/{id}")
    public ResponseEntity<AdminFinanceResponses.Topup> topup(Authentication authentication,
            @PathVariable("id") @Min(1) long id) {
        return ok(finance.topup(admin(authentication), id));
    }

    /** status는 PAID·REFUNDED(서비스가 검사), policyVersion은 LEGACY_V1·CONTRACT_V2·CONTRACT_V3. */
    @GetMapping("/api/admin/finance/payments")
    public ResponseEntity<PageResponse<AdminFinanceResponses.PaymentRecord>> payments(Authentication authentication,
            @RequestParam(required = false) @Min(1) Long orderId,
            @RequestParam(required = false) @Min(1) Long buyerId,
            @RequestParam(required = false) @Min(1) Long sellerId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) TradePolicyVersion policyVersion,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var filter = new PaymentFilter(orderId, buyerId, sellerId, status, policyVersion);
        return ok(finance.payments(admin(authentication), filter, page, size));
    }

    @GetMapping("/api/admin/finance/orders/{orderId}")
    public ResponseEntity<AdminFinanceResponses.OrderFinance> orderFinance(Authentication authentication,
            @PathVariable("orderId") @Min(1) long orderId) {
        return ok(finance.orderFinance(admin(authentication), orderId));
    }

    @GetMapping("/api/admin/finance/recovery-jobs")
    public ResponseEntity<PageResponse<AdminFinanceResponses.RecoveryJob>> recoveryJobs(Authentication authentication,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ok(finance.recoveryJobs(admin(authentication), page, size));
    }

    @GetMapping("/api/admin/finance/recovery-jobs/{executionId}")
    public ResponseEntity<AdminFinanceResponses.RecoveryJob> recoveryJob(Authentication authentication,
            @PathVariable("executionId") @Min(1) long executionId) {
        return ok(finance.recoveryJob(admin(authentication), executionId));
    }

    /** version을 생략하면 현재 계약. 지정 버전 검사(양수·존재)는 서비스가 한다. */
    @GetMapping("/api/admin/orders/{orderId}/contract-pdf")
    public ResponseEntity<AdminContractPdfResponse> contractPdf(Authentication authentication,
            @PathVariable("orderId") @Min(1) long orderId,
            @RequestParam(required = false) Integer version) {
        return ok(pdfs.inspect(admin(authentication), orderId, version));
    }

    private long admin(Authentication authentication) {
        return currentMemberId.require(authentication);
    }

    private static <T> ResponseEntity<T> ok(T body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }
}
