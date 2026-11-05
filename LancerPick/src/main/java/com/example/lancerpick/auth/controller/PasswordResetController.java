package com.example.lancerpick.auth.controller;

import com.example.lancerpick.auth.dto.PasswordResetAcceptedResponse;
import com.example.lancerpick.auth.dto.PasswordResetConfirmRequest;
import com.example.lancerpick.auth.dto.PasswordResetEmailRequest;
import com.example.lancerpick.auth.service.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 로그아웃 상태 비밀번호 재설정(API 2절). 로그인하지 않아도 부를 수 있고 CSRF는 적용된다.
 * 메일 링크를 여는 GET은 프런트 화면만 보여준다. 토큰 사용·비밀번호 변경은 아래 POST로만 한다
 * (메일 보안 검사기가 링크를 자동으로 열어도 토큰이 쓰이지 않는다).
 */
@RestController
@Profile("!demo")
@RequestMapping("/api/auth")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /**
     * POST /api/auth/password-reset-requests — 가입 여부·요청 제한·메일 발송 결과와 상관없이 같은 202와 같은 본문.
     * 202는 "요청을 받았다"는 뜻이지 메일이 도착했다는 뜻이 아니다.
     */
    @PostMapping("/password-reset-requests")
    public ResponseEntity<PasswordResetAcceptedResponse> requestReset(@Valid @RequestBody PasswordResetEmailRequest body) {
        passwordResetService.requestReset(body.email());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(PasswordResetAcceptedResponse.INSTANCE);
    }

    /**
     * POST /api/auth/password-resets — 성공하면 204. 자동 로그인하지 않는다(새 비밀번호로 다시 로그인).
     * 이 브라우저에 로그인 세션이 있었다면 auth_version이 바뀌어 다음 요청에서 폐기된다.
     */
    @PostMapping("/password-resets")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetConfirmRequest body) {
        passwordResetService.resetPassword(body.token(), body.newPassword());
        return ResponseEntity.noContent().cacheControl(CacheControl.noStore()).build();
    }
}
