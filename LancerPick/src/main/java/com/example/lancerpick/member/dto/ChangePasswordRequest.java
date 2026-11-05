package com.example.lancerpick.member.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * POST /api/me/password 요청. 두 값 모두 원문 그대로 받는다(정규화 없음).
 * - currentPassword: 형식을 검사하지 않는다(로그인과 같은 기준). 일치 여부는 서비스가 저장된 해시로 확인한다.
 * - newPassword: 가입과 같은 비밀번호 정책(PasswordRules). 위반은 400 VALIDATION_FAILED, field=newPassword.
 * toString에 비밀번호가 찍히지 않도록 record 기본 toString을 덮어쓴다.
 */
public record ChangePasswordRequest(
        @NotNull
        String currentPassword,

        @NotNull
        @Pattern(regexp = PasswordRules.PATTERN)
        String newPassword
) {
    @Override
    public String toString() {
        return "ChangePasswordRequest[currentPassword=***, newPassword=***]";
    }
}
