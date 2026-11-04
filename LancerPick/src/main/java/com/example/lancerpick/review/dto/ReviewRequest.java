package com.example.lancerpick.review.dto;

import com.example.lancerpick.common.exception.BusinessException;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

public record ReviewRequest(Integer rating, String content) {
    public static ReviewRequest parse(JsonNode body) {
        if (body == null || !body.isObject()) throw invalid();
        for (var field : body.properties()) {
            if (!field.getKey().equals("rating") && !field.getKey().equals("content")) throw invalid();
        }
        var rating = body.get("rating");
        if (rating == null || !rating.isIntegralNumber() || !rating.canConvertToInt()) throw invalid();
        var content = body.get("content");
        if (content != null && !content.isNull() && !content.isString()) throw invalid();
        return new ReviewRequest(rating.intValue(), content == null || content.isNull() ? null : content.asString());
    }
    private static BusinessException invalid() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "별점은 정수, 후기 글은 문자열로 입력해 주세요.");
    }
}
