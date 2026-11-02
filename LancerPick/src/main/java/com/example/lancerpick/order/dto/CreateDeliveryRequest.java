package com.example.lancerpick.order.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.annotation.JsonDeserialize;

public record CreateDeliveryRequest(
        @NotBlank @JsonDeserialize(using = OrderRequestJson.StringValue.class) String description,
        @NotBlank @JsonDeserialize(using = OrderRequestJson.StringValue.class) String resultUrl,
        @NotNull @Min(1) @Max(2) @JsonDeserialize(using = OrderRequestJson.SequenceValue.class) Integer expectedSequenceNo) {}
