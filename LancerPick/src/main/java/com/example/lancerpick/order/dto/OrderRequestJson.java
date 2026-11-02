package com.example.lancerpick.order.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.exc.MismatchedInputException;

/** 주문 입력에만 적용한다. 숫자 ID·문자열 버전·소수 버전의 묵시적 변환을 막는다. */
public final class OrderRequestJson {
    private OrderRequestJson() {}

    public static final class StringValue extends ValueDeserializer<String> {
        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                throw MismatchedInputException.from(parser, String.class, "문자열이 필요합니다.");
            }
            return parser.getString();
        }
    }

    public static final class VersionValue extends ValueDeserializer<Long> {
        @Override
        public Long deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_NUMBER_INT)) {
                throw MismatchedInputException.from(parser, Long.class, "정수 버전이 필요합니다.");
            }
            return parser.getLongValue();
        }
    }
}
