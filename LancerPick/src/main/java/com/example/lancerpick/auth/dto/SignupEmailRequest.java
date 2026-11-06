package com.example.lancerpick.auth.dto;

import jakarta.validation.constraints.NotNull;

/** POST /api/auth/signup-email-requests 요청. 이메일은 서비스가 가입과 같은 규칙(EmailRules)으로 정규화·검사한다. */
public record SignupEmailRequest(
        @NotNull
        String email
) {
    @Override
    public String toString() {
        return "SignupEmailRequest[email=***]";
    }
}
