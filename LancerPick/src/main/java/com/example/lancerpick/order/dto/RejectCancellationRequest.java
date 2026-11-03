package com.example.lancerpick.order.dto;

import tools.jackson.databind.annotation.JsonDeserialize;

public record RejectCancellationRequest(
        @JsonDeserialize(using = OrderRequestJson.StringValue.class) String responseReason) {}
