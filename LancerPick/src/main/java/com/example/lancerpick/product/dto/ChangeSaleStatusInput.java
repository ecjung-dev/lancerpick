package com.example.lancerpick.product.dto;

import tools.jackson.databind.JsonNode;

/** 판매 상태 전용 요청. 상품 내용·숨김 여부는 이 API로 변경하지 않는다. */
public record ChangeSaleStatusInput(String saleStatus, long expectedVersion) {
    public static ChangeSaleStatusInput from(JsonNode body) {
        if (body == null || !body.isObject() || body.size() != 2
                || !body.has("saleStatus") || !body.has("expectedVersion")
                || !body.get("saleStatus").isString()) {
            throw ProductWriteInput.invalid();
        }
        String status = body.get("saleStatus").asString();
        if (!"ON_SALE".equals(status) && !"STOPPED".equals(status)) {
            throw ProductWriteInput.invalid();
        }
        return new ChangeSaleStatusInput(status, ProductWriteInput.version(body));
    }
}
