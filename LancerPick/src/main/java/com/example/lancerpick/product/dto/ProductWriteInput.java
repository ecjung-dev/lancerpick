package com.example.lancerpick.product.dto;

import com.example.lancerpick.common.exception.BusinessException;
import com.example.lancerpick.common.text.TextNormalizer;
import com.example.lancerpick.product.entity.Product;
import java.util.Set;
import org.springframework.http.HttpStatus;
import tools.jackson.databind.JsonNode;

/** JSON 원본 타입을 확인해 문자열 숫자 강제 변환·null·미정의 필드를 거절한다. */
public record ProductWriteInput(long categoryId, String title, String description,
        String scopeDescription, String preparationDescription, long priceAmount, int durationDays) {
    private static final Set<String> FIELDS = Set.of("categoryId", "title", "description", "scopeDescription", "preparationDescription", "priceAmount", "durationDays");

    public static ProductWriteInput parse(JsonNode body, Product existing) {
        if (body == null || !body.isObject()) throw invalid();
        for (var field : body.properties()) {
            if ((!FIELDS.contains(field.getKey()) && !(existing != null && field.getKey().equals("expectedVersion"))) || field.getValue().isNull()) throw invalid();
        }
        if (existing == null && body.size() != FIELDS.size()) throw invalid();
        if (existing != null && (body.size() < 2 || !body.has("expectedVersion"))) throw invalid();
        return new ProductWriteInput(
                body.has("categoryId") ? id(textNode(body.get("categoryId"))) : existing.getCategoryId(),
                text(body, "title", 100, existing == null ? null : existing.getTitle()),
                text(body, "description", 10000, existing == null ? null : existing.getDescription()),
                text(body, "scopeDescription", 3000, existing == null ? null : existing.getScopeDescription()),
                text(body, "preparationDescription", 3000, existing == null ? null : existing.getPreparationDescription()),
                number(body, "priceAmount", 1, 1000000, existing == null ? 0 : existing.getPrice()),
                (int) number(body, "durationDays", 1, 30, existing == null ? 0 : existing.getDurationDays()));
    }

    public static long version(JsonNode body) {
        if (body == null || !body.isObject() || !body.has("expectedVersion")) throw invalid();
        return number(body, "expectedVersion", 0, Long.MAX_VALUE, -1);
    }

    public static long id(String raw) {
        if (raw == null || !raw.matches("[0-9]{1,19}")) throw invalid();
        try { long id = Long.parseLong(raw); if (id <= 0) throw invalid(); return id; }
        catch (NumberFormatException exception) { throw invalid(); }
    }

    private static String textNode(JsonNode value) {
        if (value == null || !value.isString()) throw invalid();
        return value.asString();
    }

    private static String text(JsonNode body, String field, int max, String fallback) {
        if (!body.has(field)) { if (fallback == null) throw invalid(); return fallback; }
        String value = TextNormalizer.normalize(textNode(body.get(field)));
        int length = value.codePointCount(0, value.length());
        if (length < 1 || length > max) throw invalid();
        return value;
    }

    private static long number(JsonNode body, String field, long min, long max, long fallback) {
        if (!body.has(field)) return fallback;
        JsonNode value = body.get(field);
        if (!value.isIntegralNumber() || !value.canConvertToLong()) throw invalid();
        long number = value.asLong();
        if (number < min || number > max) throw invalid();
        return number;
    }

    public static BusinessException invalid() {
        return new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "상품 입력값과 JSON 형식을 확인해 주세요.");
    }
}
