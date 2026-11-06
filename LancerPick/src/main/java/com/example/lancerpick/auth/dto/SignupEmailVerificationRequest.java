package com.example.lancerpick.auth.dto;

import jakarta.validation.constraints.NotNull;

/** POST /api/auth/signup-email-verifications 요청. token은 메일 링크 #token= 의 값. 형식은 서비스가 확인한다. */
public record SignupEmailVerificationRequest(
        @NotNull
        String token
) {
    @Override
    public String toString() {
        return "SignupEmailVerificationRequest[token=***]";
    }
}
