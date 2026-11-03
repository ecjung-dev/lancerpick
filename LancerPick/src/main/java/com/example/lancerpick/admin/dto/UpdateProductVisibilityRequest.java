package com.example.lancerpick.admin.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import tools.jackson.databind.annotation.JsonDeserialize;

/** PATCH /api/admin/products/{id}/visibility 요청. reason 길이·공백 정리는 Service에서 검사한다. */
public record UpdateProductVisibilityRequest(
        @NotNull
        @JsonDeserialize(using = AdminRequestJson.BooleanValue.class)
        Boolean hidden,

        @NotNull
        @JsonDeserialize(using = AdminRequestJson.StringValue.class)
        String reason,

        @NotNull
        @PositiveOrZero
        @JsonDeserialize(using = AdminRequestJson.VersionValue.class)
        Long expectedVersion
) {}
