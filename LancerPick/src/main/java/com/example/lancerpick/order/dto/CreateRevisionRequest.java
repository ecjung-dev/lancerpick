package com.example.lancerpick.order.dto;

import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateRevisionRequest(
        @NotBlank @JsonDeserialize(using = OrderRequestJson.StringValue.class) String deliveryId,
        @NotBlank @JsonDeserialize(using = OrderRequestJson.StringValue.class) String reason) {}
