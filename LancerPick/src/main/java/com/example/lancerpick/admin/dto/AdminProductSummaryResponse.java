package com.example.lancerpick.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;

/** API 9절 AdminProductSummary. 상세 설명·이메일·지갑·운영 이력은 목록에 넣지 않는다. */
public record AdminProductSummaryResponse(
        String id,
        String title,
        long priceAmount,
        int durationDays,
        Category category,
        Seller seller,
        String saleStatus,
        // boolean 이름이 is로 시작하면 JSON에서 "hidden"으로 바뀔 수 있어 이름을 고정한다.
        @JsonProperty("isHidden") boolean isHidden,
        long version,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public record Category(String id, String name) {}

    public record Seller(String id, String displayName) {}
}
