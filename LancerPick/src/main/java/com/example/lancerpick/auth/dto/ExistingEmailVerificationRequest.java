package com.example.lancerpick.auth.dto;

import jakarta.validation.constraints.NotNull;

/** POST /api/me/email-verifications 요청. 토큰 형식은 서비스가 OneTimeTokens로 검사한다(틀리면 같은 400). */
public record ExistingEmailVerificationRequest(
        @NotNull
        String token
) {
    @Override
    public String toString() {
        return "ExistingEmailVerificationRequest[token=***]";
    }
}
