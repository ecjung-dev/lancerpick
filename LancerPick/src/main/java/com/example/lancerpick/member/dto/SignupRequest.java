package com.example.lancerpick.member.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * POST /api/members 요청.
 * email·displayName은 서비스에서 정규화(공백 제거·소문자화)한 뒤 길이·형식을 검사한다.
 * password는 원문 그대로 검사한다: 영문+숫자 필수, ASCII(공백 제외) 8~64자 (PasswordRules).
 */
public record SignupRequest(
        @NotNull
        String email,

        @NotNull
        @Pattern(regexp = PasswordRules.PATTERN)
        String password,

        @NotNull
        String displayName
) {}
