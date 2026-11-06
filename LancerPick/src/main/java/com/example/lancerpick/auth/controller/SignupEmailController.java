package com.example.lancerpick.auth.controller;

import com.example.lancerpick.auth.dto.SignupEmailRequest;
import com.example.lancerpick.auth.dto.SignupEmailVerificationRequest;
import com.example.lancerpick.auth.dto.SignupEmailVerificationResponse;
import com.example.lancerpick.auth.service.SignupEmailService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 가입 이메일 인증(API 2절). 로그인하지 않아도 부를 수 있고 CSRF는 적용된다.
 * 메일 링크를 여는 GET은 프런트 화면만 보여준다. 인증은 아래 POST로만 한다
 * (메일 보안 검사기가 링크를 자동으로 열어도 인증되지 않는다).
 * 최종 가입은 기존 POST /api/members에 emailVerificationProof를 담아 보낸다.
 */
@RestController
@Profile("!demo")
@RequestMapping("/api/auth")
public class SignupEmailController {

    /** 가입 여부·요청 제한·발송 결과와 상관없이 같은 본문. 202를 "메일 도착"으로 표현하지 않는다. */
    record Accepted(String message) {
    }

    private static final Accepted ACCEPTED = new Accepted("입력한 주소로 가입 인증 메일을 보냅니다. 메일함을 확인해 주세요.");

    private final SignupEmailService signupEmailService;

    public SignupEmailController(SignupEmailService signupEmailService) {
        this.signupEmailService = signupEmailService;
    }

    /** POST /api/auth/signup-email-requests — 항상 같은 202. 이메일 형식 오류만 400. */
    @PostMapping("/signup-email-requests")
    public ResponseEntity<Accepted> requestVerification(@Valid @RequestBody SignupEmailRequest body) {
        signupEmailService.requestVerification(body.email());
        return ResponseEntity.accepted().cacheControl(CacheControl.noStore()).body(ACCEPTED);
    }

    /** POST /api/auth/signup-email-verifications — 200 인증 결과와 가입 증명. 토큰 오류는 400 SIGNUP_EMAIL_TOKEN_INVALID. */
    @PostMapping("/signup-email-verifications")
    public ResponseEntity<SignupEmailVerificationResponse> verify(@Valid @RequestBody SignupEmailVerificationRequest body) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(signupEmailService.verify(body.token()));
    }
}
