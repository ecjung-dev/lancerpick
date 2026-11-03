package com.example.lancerpick.member.dto;

import jakarta.validation.constraints.NotNull;

/** PATCH /api/me 요청. 표시 이름만 바꿀 수 있다. 정규화·길이 검사는 서비스에서 한다. */
public record UpdateMeRequest(
        @NotNull
        String displayName
) {}
