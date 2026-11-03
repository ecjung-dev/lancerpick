package com.example.lancerpick.order.dto;

import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateCancellationRequest(
        @NotBlank @JsonDeserialize(using = OrderRequestJson.StringValue.class) String reason,
        @NotBlank @JsonDeserialize(using = OrderRequestJson.StringValue.class) String requestKey) {}
