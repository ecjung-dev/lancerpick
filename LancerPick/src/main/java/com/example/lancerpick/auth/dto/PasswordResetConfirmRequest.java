package com.example.lancerpick.auth.dto;

import com.example.lancerpick.member.dto.PasswordRules;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * POST /api/auth/password-resets 요청. 두 값 모두 원문 그대로 받는다(정규화 없음).
 * - token: 메일 링크의 일회용 토큰. 형식·유효성은 서비스가 확인한다(틀리면 PASSWORD_RESET_TOKEN_INVALID).
 * - newPassword: 가입과 같은 비밀번호 정책(PasswordRules). 위반은 400 VALIDATION_FAILED, field=newPassword.
 * toString에 토큰·비밀번호가 찍히지 않도록 record 기본 toString을 덮어쓴다.
 */
public record PasswordResetConfirmRequest(
        @NotNull
        String token,

        @NotNull
        @Pattern(regexp = PasswordRules.PATTERN)
        String newPassword
) {
    @Override
    public String toString() {
        return "PasswordResetConfirmRequest[token=***, newPassword=***]";
    }
}
