package com.example.lancerpick.auth.controller;

import com.example.lancerpick.auth.dto.ExistingEmailVerificationRequest;
import com.example.lancerpick.auth.dto.PasswordResetAcceptedResponse;
import com.example.lancerpick.auth.service.ExistingEmailVerificationService;
import com.example.lancerpick.common.security.CurrentMemberId;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기존 회원 이메일 인증(K1). /api/me 아래라 로그인이 필요하고 CSRF가 적용된다. 회원 ID는 세션에서만 꺼낸다.
 * 메일 링크를 여는 GET은 프런트 화면만 보여 준다. 토큰 사용은 아래 POST로만 한다(메일 검사기가 링크를 열어도 인증되지 않음).
 */
@RestController
@Profile("!demo")
@RequestMapping("/api/me")
public class ExistingEmailVerificationController {
    static final PasswordResetAcceptedResponse ACCEPTED = new PasswordResetAcceptedResponse(
            "인증 메일을 보냈습니다. 15분 안에 메일의 링크를 열어 주세요.");

    private final ExistingEmailVerificationService service;
    private final CurrentMemberId currentMemberId;

    public ExistingEmailVerificationController(ExistingEmailVerificationService service, CurrentMemberId currentMemberId) {
        this.service = service;
        this.currentMemberId = currentMemberId;
    }

    /** POST /api/me/email-verification-requests — 202. 이미 인증된 회원은 409 EMAIL_ALREADY_VERIFIED. 본문 없음. */
    @PostMapping("/email-verification-requests")
    public ResponseEntity<PasswordResetAcceptedResponse> request(Authentication authentication) {
        service.requestVerification(currentMemberId.require(authentication));
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(ACCEPTED);
    }

    /** POST /api/me/email-verifications — 성공하면 204. 세션·비밀번호는 그대로다. */
    @PostMapping("/email-verifications")
    public ResponseEntity<Void> verify(Authentication authentication,
            @Valid @RequestBody ExistingEmailVerificationRequest body) {
        service.verify(currentMemberId.require(authentication), body.token());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
