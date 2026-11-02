package com.example.lancerpick.order.dto;

import jakarta.validation.constraints.NotBlank;
import tools.jackson.databind.annotation.JsonDeserialize;

public record RejectOrderRequest(
        @NotBlank @JsonDeserialize(using = OrderRequestJson.StringValue.class) String reason) {}
