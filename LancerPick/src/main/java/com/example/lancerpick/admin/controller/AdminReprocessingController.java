package com.example.lancerpick.admin.controller;

import com.example.lancerpick.admin.dto.AdminActionReceipt;
import com.example.lancerpick.admin.dto.AdminReprocessingRequest;
import com.example.lancerpick.admin.entity.AdminAction;
import com.example.lancerpick.admin.repository.AdminActionLogQueries.Filter;
import com.example.lancerpick.admin.service.AdminActionLogService;
import com.example.lancerpick.admin.service.AdminContractPdfQueryService;
import com.example.lancerpick.admin.service.AdminReprocessingService;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.common.web.PageResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.UUID;
import java.util.regex.Pattern;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * K6 관리자 재처리 실행·감사 이력 API. /api/admin/** 는 SecurityConfig에서 ROLE_ADMIN·CSRF를 요구한다.
 *
 * <p>실행은 황준범의 {@link AdminReprocessingService} 하나만 호출한다. 그 서비스가 감사 요청 선행 커밋 → 업무 처리권·잠금 재검사 →
 * 트랜잭션 밖 외부 조회/Batch 재시작/PDF 생성 → 결과 별도 커밋을 맡는다. 이 Controller는 입력 형식만 검사하고 결과를 고르지 않는다.
 *
 * <p>응답: 새 요청은 202, 같은 관리자·같은 요청 키 재전송은 재실행 없이 기존 기록을 200으로 돌려준다. 접수 이후 업무 결과(거절·실패·불명확)는
 * HTTP 오류가 아니라 receipt의 status·resultCode로 알린다. 대상의 최신 상태는 기존 관리자 GET으로 다시 조회한다.
 */
@RestController
@Profile("!demo")
public class AdminReprocessingController {
    private static final Pattern UUID_TEXT =
            Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final AdminReprocessingService reprocessing;
    private final AdminContractPdfQueryService pdfs;
    private final AdminActionLogService logs;
    private final CurrentMemberId currentMemberId;

    public AdminReprocessingController(AdminReprocessingService reprocessing, AdminContractPdfQueryService pdfs,
            AdminActionLogService logs, CurrentMemberId currentMemberId) {
        this.reprocessing = reprocessing;
        this.pdfs = pdfs;
        this.logs = logs;
        this.currentMemberId = currentMemberId;
    }

    /** 미확정(PROCESSING·UNKNOWN) 충전 단건 재확인. 결제 키·금액·회원 ID는 받지 않는다. */
    @PostMapping("/api/admin/finance/topups/{id}/recheck")
    public ResponseEntity<AdminActionReceipt> recheckTopup(Authentication authentication,
            @PathVariable("id") @Min(1) long id, @RequestBody AdminReprocessingRequest request) {
        long admin = admin(authentication);
        UUID key = requestKey(request);
        rejectVersion(request);
        return respond(reprocessing.recheckTopup(admin, id, request.reason(), key));
    }

    /** 실패·중지된 충전 복구 Batch 실행 재시작. 수락은 Job 완료를 뜻하지 않는다. */
    @PostMapping("/api/admin/finance/recovery-jobs/{executionId}/restart")
    public ResponseEntity<AdminActionReceipt> restartRecoveryJob(Authentication authentication,
            @PathVariable("executionId") @Min(1) long executionId, @RequestBody AdminReprocessingRequest request) {
        long admin = admin(authentication);
        UUID key = requestKey(request);
        rejectVersion(request);
        return respond(reprocessing.restartTopupRecovery(admin, executionId, request.reason(), key));
    }

    /**
     * 계약 PDF 생성 재시도. 주문·버전(생략하면 현재 계약)을 기존 PDF 운영 조회로 계약 ID로 바꾼 뒤 실행한다.
     * 그 사이 계약이 바뀌면 실행 단계의 재검사가 거절하므로 이 조회 결과를 실행 허가로 쓰지 않는다.
     */
    @PostMapping("/api/admin/orders/{orderId}/contract-pdf/regenerate")
    public ResponseEntity<AdminActionReceipt> regenerateContractPdf(Authentication authentication,
            @PathVariable("orderId") @Min(1) long orderId, @RequestBody AdminReprocessingRequest request) {
        long admin = admin(authentication);
        UUID key = requestKey(request);
        Integer version = null;
        if (request.version() != null) {
            if (request.version() < 1 || request.version() > Integer.MAX_VALUE) throw invalid();
            version = request.version().intValue();
        }
        long contractId = Long.parseLong(pdfs.inspect(admin, orderId, version).contractId());
        return respond(reprocessing.regenerateContractPdf(admin, contractId, request.reason(), key));
    }

    /** 감사 이력 목록(최신순). targetId는 action과 함께만 쓴다. */
    @GetMapping("/api/admin/action-logs")
    public ResponseEntity<PageResponse<AdminActionReceipt>> actionLogs(Authentication authentication,
            @RequestParam(required = false) AdminAction action,
            @RequestParam(required = false) @Min(1) Long targetId,
            @RequestParam(required = false) @Min(1) Long orderId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var body = logs.list(admin(authentication), new Filter(action, targetId, orderId), page, size);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(body);
    }

    private long admin(Authentication authentication) {
        return currentMemberId.require(authentication);
    }

    private static UUID requestKey(AdminReprocessingRequest request) {
        if (request.requestKey() == null || !UUID_TEXT.matcher(request.requestKey()).matches()) throw invalid();
        return UUID.fromString(request.requestKey());
    }

    /** 충전·Batch에는 버전 개념이 없다. 잘못 보낸 값을 조용히 무시하지 않는다. */
    private static void rejectVersion(AdminReprocessingRequest request) {
        if (request.version() != null) throw invalid();
    }

    private static ResponseEntity<AdminActionReceipt> respond(AdminReprocessingService.Result result) {
        return ResponseEntity.status(result.replay() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .cacheControl(CacheControl.noStore())
                .body(result.receipt());
    }

    private static BusinessException invalid() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "재처리 요청 키·사유·버전을 확인해 주세요.");
    }
}
