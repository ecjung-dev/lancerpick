package com.example.lancerpick.member.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * POST /api/members 요청.
 * email·displayName은 서비스에서 정규화(공백 제거·소문자화)한 뒤 길이·형식을 검사한다.
 * password는 원문 그대로 검사한다: 영문+숫자 필수, ASCII(공백 제외) 8~64자 (PasswordRules).
 * emailVerificationProof: 이메일 인증(POST /api/auth/signup-email-verifications)에서 받은 가입 증명. 필수다(2026-11-06부터).
 *   인증 없이 가입하는 경로는 남기지 않는다(3인 설계 §5.1). 서버가 이메일 일치·만료·사용 여부를 다시 확인한다.
 * toString에 비밀번호·증명이 찍히지 않도록 record 기본 toString을 덮어쓴다.
 */
public record SignupRequest(
        @NotNull
        String email,

        @NotNull
        @Pattern(regexp = PasswordRules.PATTERN)
        String password,

        @NotNull
        String displayName,

        @NotNull
        String emailVerificationProof
) {
    @Override
    public String toString() {
        return "SignupRequest[email=***, password=***, displayName=" + displayName + ", emailVerificationProof=***]";
    }
}
