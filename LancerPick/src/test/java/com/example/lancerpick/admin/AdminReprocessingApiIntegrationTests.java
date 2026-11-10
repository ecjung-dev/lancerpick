package com.example.lancerpick.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.example.lancerpick.admin.dto.AdminActionReceipt;
import com.example.lancerpick.admin.dto.AdminContractPdfResponse;
import com.example.lancerpick.admin.entity.AdminAction;
import com.example.lancerpick.admin.entity.AdminActionLog;
import com.example.lancerpick.admin.entity.AdminActionStatus;
import com.example.lancerpick.admin.repository.AdminActionLogRepository;
import com.example.lancerpick.admin.service.AdminContractPdfQueryService;
import com.example.lancerpick.admin.service.AdminReprocessingService;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.member.entity.Member;
import com.example.lancerpick.member.repository.MemberRepository;
import com.example.lancerpick.support.IntegrationTest;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * K6 재처리 실행·감사 이력 HTTP 계약.
 *
 * <p>실행 API는 실제 Security 필터(ADMIN·CSRF)와 입력 검사·응답 코드(새 요청 202, 같은 키 200)·대상 해석만 본다. 감사 선행 커밋·처리권·
 * 외부 조회·Batch·PDF와 결과 코드는 {@link AdminReprocessingService} 쪽(AdminReprocessingIntegrationTests, MySQL)이 검사하므로 여기서는
 * 그 서비스를 대역으로 둔다. 감사 이력 목록은 실제 MySQL의 admin_action_logs와 현재 DB 역할 재확인으로 검사한다.
 */
@IntegrationTest
@AutoConfigureMockMvc
class AdminReprocessingApiIntegrationTests {
    @Autowired MemberRepository members;
    @Autowired AdminActionLogRepository logs;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc mvc;
    @MockitoBean AdminReprocessingService reprocessing;
    @MockitoBean AdminContractPdfQueryService pdfs;

    static final String KEY = "6f1c2b9e-4d3a-4f6b-8c1d-2e3f4a5b6c7d";
    long admin, user, topupTarget, jobTarget;
    final List<Long> memberIds = new ArrayList<>();

    record Principal(Long memberId) implements AuthenticatedMember {}

    static UsernamePasswordAuthenticationToken auth(long memberId, String role) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new Principal(memberId), null, AuthorityUtils.createAuthorityList("ROLE_" + role));
    }

    @BeforeEach
    void fixture() {
        var at = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5).truncatedTo(ChronoUnit.MICROS);
        admin = member(at);
        user = member(at);
        jdbc.update("UPDATE members SET role='ADMIN' WHERE id=?", admin);
        // 다른 테스트 기록과 섞이지 않도록 큰 대상 번호를 쓴다(target_id에는 FK가 없다).
        topupTarget = ThreadLocalRandom.current().nextLong(8_000_000_000_000_000L, 9_000_000_000_000_000L);
        jobTarget = topupTarget + 1;
    }

    long member(LocalDateTime at) {
        long id = members.saveAndFlush(Member.create(UUID.randomUUID() + "@admin-reprocess.test", "fixture", "표시 이름", at))
                .getId();
        memberIds.add(id);
        return id;
    }

    @AfterEach
    void cleanup() {
        for (long id : memberIds) jdbc.update("DELETE FROM admin_action_logs WHERE admin_id=?", id);
        for (long id : memberIds) jdbc.update("DELETE FROM members WHERE id=?", id);
    }

    static AdminActionReceipt receipt(AdminAction action, long targetId, AdminActionStatus status, String code) {
        var at = OffsetDateTime.parse("2026-11-10T00:00:00Z");
        return new AdminActionReceipt("10", "1", action, action.targetType(), Long.toString(targetId), null, "확인 요청",
                KEY, status, code, at, at.plusMinutes(10), status == AdminActionStatus.REQUESTED ? null : at);
    }

    static String body(String reason, String key) {
        return "{\"reason\":\"" + reason + "\",\"requestKey\":\"" + key + "\"}";
    }

    @Test
    void newRequestIs202AndSameKeyReplayIs200WithReceiptOnly() throws Exception {
        when(reprocessing.recheckTopup(admin, 7L, "  확인 요청 ", UUID.fromString(KEY)))
                .thenReturn(new AdminReprocessingService.Result(
                        receipt(AdminAction.TOPUP_RECHECK, 7, AdminActionStatus.SUCCEEDED, "TOPUP_CREDITED"), false))
                .thenReturn(new AdminReprocessingService.Result(
                        receipt(AdminAction.TOPUP_RECHECK, 7, AdminActionStatus.SUCCEEDED, "TOPUP_CREDITED"), true));
        // 사유는 서버(감사 서비스)가 정리하므로 Controller는 받은 그대로 넘긴다.
        for (var expected : List.of(HttpStatus.ACCEPTED, HttpStatus.OK)) {
            mvc.perform(post("/api/admin/finance/topups/7/recheck").with(csrf()).with(authentication(auth(admin, "ADMIN")))
                            .contentType("application/json").content(body("  확인 요청 ", KEY)))
                    .andExpect(status().is(expected.value()))
                    .andExpect(header().string("Cache-Control", "no-store"))
                    .andExpect(jsonPath("$.action").value("TOPUP_RECHECK"))
                    .andExpect(jsonPath("$.targetId").value("7"))
                    .andExpect(jsonPath("$.status").value("SUCCEEDED"))
                    .andExpect(jsonPath("$.resultCode").value("TOPUP_CREDITED"));
        }
    }

    @Test
    void batchRestartPassesExecutionIdAndKeepsRejectionAsReceipt() throws Exception {
        when(reprocessing.restartTopupRecovery(admin, 31L, "재시작", UUID.fromString(KEY)))
                .thenReturn(new AdminReprocessingService.Result(
                        receipt(AdminAction.TOPUP_RECOVERY_RESTART, 31, AdminActionStatus.REJECTED, "BATCH_RESTART_REJECTED"),
                        false));
        // 접수 이후 업무 거절은 HTTP 오류가 아니라 receipt 상태로 알린다.
        mvc.perform(post("/api/admin/finance/recovery-jobs/31/restart").with(csrf()).with(authentication(auth(admin, "ADMIN")))
                        .contentType("application/json").content(body("재시작", KEY.toUpperCase())))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.resultCode").value("BATCH_RESTART_REJECTED"));
    }

    @Test
    void pdfRegenerationResolvesContractFromOrderAndVersion() throws Exception {
        var at = OffsetDateTime.parse("2026-11-10T00:00:00Z");
        var candidate = new AdminContractPdfResponse.GenerationCandidate(
                true, AdminContractPdfResponse.GenerationKind.RETRY_FAILED, null);
        when(pdfs.inspect(admin, 55L, 2))
                .thenReturn(new AdminContractPdfResponse("55", "901", 2, "FAILED", true, false, at, at, at, candidate));
        when(pdfs.inspect(admin, 55L, null))
                .thenReturn(new AdminContractPdfResponse("55", "902", 3, "FAILED", true, false, at, at, at, candidate));
        when(reprocessing.regenerateContractPdf(eq(admin), anyLong(), eq("재생성"), eq(UUID.fromString(KEY))))
                .thenReturn(new AdminReprocessingService.Result(
                        receipt(AdminAction.CONTRACT_PDF_REGENERATE, 901, AdminActionStatus.FAILED, "PDF_GENERATION_FAILED"),
                        false));
        mvc.perform(post("/api/admin/orders/55/contract-pdf/regenerate").with(csrf()).with(authentication(auth(admin, "ADMIN")))
                        .contentType("application/json")
                        .content("{\"reason\":\"재생성\",\"requestKey\":\"" + KEY + "\",\"version\":2}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.resultCode").value("PDF_GENERATION_FAILED"));
        verify(reprocessing).regenerateContractPdf(admin, 901L, "재생성", UUID.fromString(KEY));
        // 버전을 생략하면 현재 계약.
        mvc.perform(post("/api/admin/orders/55/contract-pdf/regenerate").with(csrf()).with(authentication(auth(admin, "ADMIN")))
                        .contentType("application/json").content(body("재생성", KEY)))
                .andExpect(status().isAccepted());
        verify(reprocessing).regenerateContractPdf(admin, 902L, "재생성", UUID.fromString(KEY));
    }

    @Test
    void missingContractStopsBeforeAnyAuditRequest() throws Exception {
        when(pdfs.inspect(admin, 56L, null)).thenThrow(
                new BusinessException(HttpStatus.NOT_FOUND, "CONTRACT_NOT_FOUND", "계약을 찾을 수 없습니다."));
        mvc.perform(post("/api/admin/orders/56/contract-pdf/regenerate").with(csrf()).with(authentication(auth(admin, "ADMIN")))
                        .contentType("application/json").content(body("재생성", KEY)))
                .andExpect(status().isNotFound());
        verify(reprocessing, never()).regenerateContractPdf(anyLong(), anyLong(), anyString(), any());
    }

    @Test
    void securityAndMalformedInputNeverReachTheService() throws Exception {
        String path = "/api/admin/finance/topups/7/recheck";
        mvc.perform(post(path).with(csrf()).contentType("application/json").content(body("사유", KEY)))
                .andExpect(status().isUnauthorized());
        mvc.perform(post(path).with(csrf()).with(authentication(auth(user, "USER")))
                        .contentType("application/json").content(body("사유", KEY)))
                .andExpect(status().isForbidden());
        // CSRF 토큰이 없으면 거절한다.
        mvc.perform(post(path).with(authentication(auth(admin, "ADMIN"))).contentType("application/json").content(body("사유", KEY)))
                .andExpect(status().isForbidden());
        for (String bad : List.of(
                body("사유", "not-a-uuid"),
                "{\"reason\":\"사유\"}",
                "{\"reason\":\"사유\",\"requestKey\":123}",
                "{\"reason\":\"사유\",\"requestKey\":\"" + KEY + "\",\"version\":1}")) {
            mvc.perform(post(path).with(csrf()).with(authentication(auth(admin, "ADMIN"))).contentType("application/json").content(bad))
                    .andExpect(status().isBadRequest());
        }
        for (String bad : List.of(
                "{\"reason\":\"사유\",\"requestKey\":\"" + KEY + "\",\"version\":0}",
                "{\"reason\":\"사유\",\"requestKey\":\"" + KEY + "\",\"version\":\"2\"}",
                "{\"reason\":\"사유\",\"requestKey\":\"" + KEY + "\",\"version\":3000000000}")) {
            mvc.perform(post("/api/admin/orders/55/contract-pdf/regenerate").with(csrf()).with(authentication(auth(admin, "ADMIN")))
                            .contentType("application/json").content(bad))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(post("/api/admin/finance/topups/0/recheck").with(csrf()).with(authentication(auth(admin, "ADMIN")))
                        .contentType("application/json").content(body("사유", KEY)))
                .andExpect(status().isBadRequest());
        verify(reprocessing, never()).recheckTopup(anyLong(), anyLong(), anyString(), any());
        verify(pdfs, never()).inspect(anyLong(), anyLong(), any());
    }

    long log(AdminAction action, long targetId, LocalDateTime at, boolean done) {
        var entry = AdminActionLog.request(admin, action, targetId, null, "운영 확인 사유", UUID.randomUUID().toString(), at,
                at.plusMinutes(10));
        if (done) entry.complete(AdminActionStatus.UNKNOWN, "TOPUP_RESULT_UNCONFIRMED", at.plusSeconds(1));
        return logs.saveAndFlush(entry).getId();
    }

    @Test
    void actionLogsFilterByCommandAndTargetNewestFirst() throws Exception {
        var at = LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1).truncatedTo(ChronoUnit.MICROS);
        long older = log(AdminAction.TOPUP_RECHECK, topupTarget, at, true);
        long newer = log(AdminAction.TOPUP_RECHECK, topupTarget, at.plusSeconds(2), false);
        long otherCommand = log(AdminAction.TOPUP_RECOVERY_RESTART, topupTarget, at.plusSeconds(3), false);
        long otherTarget = log(AdminAction.TOPUP_RECHECK, jobTarget, at.plusSeconds(4), false);

        String body = mvc.perform(get("/api/admin/action-logs").param("action", "TOPUP_RECHECK")
                        .param("targetId", Long.toString(topupTarget)).with(authentication(auth(admin, "ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].id").value(Long.toString(newer)))
                .andExpect(jsonPath("$.items[0].status").value("REQUESTED"))
                .andExpect(jsonPath("$.items[0].resultCode").doesNotExist())
                .andExpect(jsonPath("$.items[1].id").value(Long.toString(older)))
                .andExpect(jsonPath("$.items[1].status").value("UNKNOWN"))
                .andExpect(jsonPath("$.items[1].reason").value("운영 확인 사유"))
                .andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain("\"id\":\"" + otherCommand + "\"", "\"id\":\"" + otherTarget + "\"");

        // 조건 없이 보면 다른 기록도 함께 최신순으로 나온다(다른 테스트 기록이 있을 수 있어 순서만 본다).
        String all = mvc.perform(get("/api/admin/action-logs").param("size", "100").with(authentication(auth(admin, "ADMIN"))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(all.indexOf("\"id\":\"" + otherTarget + "\"")).isNotNegative()
                .isLessThan(all.indexOf("\"id\":\"" + older + "\""));
    }

    @Test
    void actionLogsNeedCurrentAdminAndDocumentedValues() throws Exception {
        mvc.perform(get("/api/admin/action-logs")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/action-logs").with(authentication(auth(user, "USER")))).andExpect(status().isForbidden());
        // 세션은 ADMIN이지만 DB에서 강등된 경우.
        mvc.perform(get("/api/admin/action-logs").with(authentication(auth(user, "ADMIN"))))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        for (String[] bad : List.of(
                new String[] {"action", "topup_recheck"}, new String[] {"action", "DELETE"},
                new String[] {"targetId", "5"}, new String[] {"orderId", "0"}, new String[] {"size", "101"},
                new String[] {"page", "-1"})) {
            mvc.perform(get("/api/admin/action-logs").param(bad[0], bad[1]).with(authentication(auth(admin, "ADMIN"))))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
    }
}
