package com.example.lancerpick.auth.controller;

import com.example.lancerpick.auth.dto.AccountGuideAcceptedResponse;
import com.example.lancerpick.auth.dto.PasswordResetEmailRequest;
import com.example.lancerpick.auth.service.AccountGuideService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 아이디 안내(K1). 로그인하지 않아도 부를 수 있고 CSRF는 적용된다. */
@RestController
@Profile("!demo")
@RequestMapping("/api/auth")
public class AccountGuideController {
    private final AccountGuideService accountGuideService;

    public AccountGuideController(AccountGuideService accountGuideService) {
        this.accountGuideService = accountGuideService;
    }

    /**
     * POST /api/auth/account-guide-requests — 가입 여부·요청 제한·메일 발송 결과와 상관없이 같은 202와 같은 본문.
     * 본문은 재설정 요청과 같은 {email} 형식이라 같은 DTO를 쓴다.
     */
    @PostMapping("/account-guide-requests")
    public ResponseEntity<AccountGuideAcceptedResponse> requestGuide(@Valid @RequestBody PasswordResetEmailRequest body) {
        accountGuideService.requestGuide(body.email());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(AccountGuideAcceptedResponse.INSTANCE);
    }
}
