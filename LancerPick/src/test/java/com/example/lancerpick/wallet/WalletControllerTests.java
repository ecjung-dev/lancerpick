package com.example.lancerpick.wallet;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.example.lancerpick.common.exception.ApiExceptionHandler;
import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.security.AuthenticatedMember;
import com.example.lancerpick.common.security.CurrentMemberId;
import com.example.lancerpick.common.security.SecurityConfig;
import com.example.lancerpick.common.security.SecurityErrorWriter;
import com.example.lancerpick.common.web.PageResponse;
import com.example.lancerpick.common.web.TraceIdFilter;
import com.example.lancerpick.wallet.controller.WalletController;
import com.example.lancerpick.wallet.dto.WalletBalanceResponse;
import com.example.lancerpick.wallet.dto.WalletEntryResponse;
import com.example.lancerpick.wallet.entity.WalletEntryType;
import com.example.lancerpick.wallet.service.WalletQueryService;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** 실제 로그인·DB와 분리한 HTTP 경계 테스트. 테스트 인증을 일반 실행에 넣지 않는다. */
@WebMvcTest(controllers = WalletController.class,
        properties = "spring.config.location=optional:classpath:/default-catalog-test.properties")
@ActiveProfiles("test")
@Import({SecurityConfig.class, SecurityErrorWriter.class, TraceIdFilter.class,
        ApiExceptionHandler.class, CurrentMemberId.class})
class WalletControllerTests {
    @Autowired MockMvc mvc;
    @MockitoBean WalletQueryService wallets;

    private static UsernamePasswordAuthenticationToken member(Long id) {
        return UsernamePasswordAuthenticationToken.authenticated(new TestMember(id), null,
                AuthorityUtils.createAuthorityList("ROLE_USER"));
    }

    record TestMember(Long memberId) implements AuthenticatedMember {}

    @ParameterizedTest
    @ValueSource(strings = {"/api/me/wallet", "/api/me/wallet/entries"})
    void anonymousCannotReadWallet(String path) throws Exception {
        mvc.perform(get(path)).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"))
                .andExpect(jsonPath("$.traceId").isNotEmpty())
                .andExpect(header().doesNotExist("Location"));
        verifyNoInteractions(wallets);
    }

    @Test
    void balanceUsesAuthenticatedIdDespiteClientSuppliedIds() throws Exception {
        when(wallets.getBalance(37L)).thenReturn(WalletBalanceResponse.of(870000));
        mvc.perform(get("/api/me/wallet").with(authentication(member(37L)))
                        .param("memberId", "999").header("X-Member-Id", "999"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.balance").value(870000))
                .andExpect(jsonPath("$.currency").value("KRW"))
                .andExpect(jsonPath("$.isTest").value(true));
        verify(wallets).getBalance(37L);
        verifyNoMoreInteractions(wallets);
    }

    @Test
    void entriesUseDefaultsAndKeepStringIdsAndUtc() throws Exception {
        var entry = new WalletEntryResponse("9007199254740993", WalletEntryType.PAYMENT,
                -50000, 870000, "9007199254740994", OffsetDateTime.parse("2026-11-02T03:00:00Z"));
        when(wallets.getEntries(37L, 0, 20)).thenReturn(new PageResponse<>(List.of(entry), 0, 20, 1, 1));
        mvc.perform(get("/api/me/wallet/entries").with(authentication(member(37L))).param("memberId", "999"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.items[0].id").value("9007199254740993"))
                .andExpect(jsonPath("$.items[0].orderId").value("9007199254740994"))
                .andExpect(jsonPath("$.items[0].createdAt").value("2026-11-02T03:00:00Z"))
                .andExpect(jsonPath("$.items[0].memberId").doesNotExist())
                .andExpect(jsonPath("$.page").value(0)).andExpect(jsonPath("$.size").value(20));
        verify(wallets).getEntries(37L, 0, 20);
        verifyNoMoreInteractions(wallets);
    }

    @Test
    void secondMemberAndExplicitPaginationUseTheirOwnId() throws Exception {
        when(wallets.getEntries(38L, 2, 5)).thenReturn(new PageResponse<>(List.of(), 2, 5, 0, 0));
        mvc.perform(get("/api/me/wallet/entries").with(authentication(member(38L)))
                        .param("page", "2").param("size", "5"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        verify(wallets).getEntries(38L, 2, 5);
        verifyNoMoreInteractions(wallets);
    }

    @ParameterizedTest
    @CsvSource({"-1,20", "0,0", "0,101", "wrong,20", "2147483648,20"})
    void invalidPaginationIsRejectedBeforeService(String page, String size) throws Exception {
        mvc.perform(get("/api/me/wallet/entries").with(authentication(member(37L)))
                        .param("page", page).param("size", size))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        verifyNoInteractions(wallets);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0, -1})
    void invalidPrincipalMemberIdIsRejected(Long id) throws Exception {
        mvc.perform(get("/api/me/wallet").with(authentication(member(id))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("AUTHENTICATION_REQUIRED"));
        verifyNoInteractions(wallets);
    }

    @Test
    void numericUsernameIsNotAcceptedAsMemberId() throws Exception {
        var unsupported = UsernamePasswordAuthenticationToken.authenticated("37", null,
                AuthorityUtils.createAuthorityList("ROLE_USER"));
        mvc.perform(get("/api/me/wallet").with(authentication(unsupported)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(wallets);
    }

    @Test
    void missingWalletIsNotConvertedToZeroBalance() throws Exception {
        when(wallets.getBalance(37L)).thenThrow(new BusinessException(HttpStatus.NOT_FOUND,
                "RESOURCE_NOT_FOUND", "지갑을 찾을 수 없습니다."));
        mvc.perform(get("/api/me/wallet").with(authentication(member(37L))))
                .andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.balance").doesNotExist());
    }
}
