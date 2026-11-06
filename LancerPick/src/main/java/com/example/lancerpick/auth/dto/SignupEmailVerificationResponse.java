package com.example.lancerpick.auth.dto;

import java.time.OffsetDateTime;

/**
 * POST /api/auth/signup-email-verifications 응답.
 * - email: 인증된(정규화된) 이메일. 가입 화면은 이 값을 바꿀 수 없게 보여 준다.
 * - proof: 가입 증명 원문(일회용, 43자). 화면 메모리에만 두고 POST /api/members의 emailVerificationProof로 보낸다.
 * - proofExpiresAt: 증명 만료 시각(UTC, ISO 8601).
 * - alreadyRegistered: 이미 가입된 이메일인지. 메일을 받은 본인이 인증한 뒤에만 알려 준다.
 * toString에 증명이 찍히지 않게 덮어쓴다.
 */
public record SignupEmailVerificationResponse(String email, String proof, OffsetDateTime proofExpiresAt,
        boolean alreadyRegistered) {
    @Override
    public String toString() {
        return "SignupEmailVerificationResponse[email=***, proof=***, proofExpiresAt=" + proofExpiresAt
                + ", alreadyRegistered=" + alreadyRegistered + "]";
    }
}
