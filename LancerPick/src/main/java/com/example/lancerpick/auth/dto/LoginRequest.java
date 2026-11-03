package com.example.lancerpick.auth.dto;

import jakarta.validation.constraints.NotNull;

/**
 * POST /api/auth/login 요청.
 * 로그인에서는 비밀번호 형식 규칙을 검사하지 않는다(API 10절). 값이 없을 때만 400.
 */
public record LoginRequest(
        @NotNull
        String email,

        @NotNull
        String password
) {}
