package com.example.lancerpick.auth.controller;

import com.example.lancerpick.auth.dto.LoginRequest;
import com.example.lancerpick.auth.service.AuthService;
import com.example.lancerpick.member.dto.MemberResponse;
import com.example.lancerpick.notification.service.PasswordChangeRecommendations;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("!demo")   // demo 프로필에서는 인증 기능을 만들지 않는다
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final PasswordChangeRecommendations passwordRecommendations;

    public AuthController(AuthService authService, PasswordChangeRecommendations passwordRecommendations) {
        this.authService = authService;
        this.passwordRecommendations = passwordRecommendations;
    }

    /** POST /api/auth/login — 성공 시 200 Member, 실패 시 401 INVALID_CREDENTIALS */
    @PostMapping("/login")
    public ResponseEntity<MemberResponse> login(@Valid @RequestBody LoginRequest loginRequest,
            HttpServletRequest request, HttpServletResponse response) {
        MemberResponse member = authService.login(loginRequest, request, response);
        // 로그인 트랜잭션·세션 저장이 끝난 뒤 별도 트랜잭션으로 비밀번호 변경 권고(U6). 실패해도 로그인은 성공으로 둔다.
        passwordRecommendations.issueAfterLogin(Long.parseLong(member.id()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(member);
    }

    /** POST /api/auth/logout — 로그인한 회원만. 성공 시 204 */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response,
            Authentication authentication) {
        authService.logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }
}
