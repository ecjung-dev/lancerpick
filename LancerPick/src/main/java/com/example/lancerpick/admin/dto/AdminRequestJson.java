package com.example.lancerpick.admin.dto;

import tools.jackson.core.JsonParser;
import tools.jackson.core.JsonToken;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * 관리자 입력 전용 JSON 변환 규칙.
 * API 10절: 숫자·boolean 필드는 정확한 JSON 타입으로만 받는다. "true"·"0" 같은 문자열을 자동 변환하지 않는다.
 * (JSON null은 이 변환기를 거치지 않고 null이 되므로 @NotNull이 거절한다.)
 */
public final class AdminRequestJson {
    private AdminRequestJson() {}

    public static final class StringValue extends ValueDeserializer<String> {
        @Override
        public String deserialize(JsonParser parser, DeserializationContext context) {
            if (!parser.hasToken(JsonToken.VALUE_STRING)) {
                throw MismatchedInputException.from(parser, String.class, "문자열이 필요합니다.");
            }
            return parser.getString();
        }
    }

    public static final class BooleanValue extends ValueDeserializer<Boolean> {
        @Override
        public Boolean deserialize(JsonParser parser, DeserializationContext context) {
            if (parser.hasToken(JsonToken.VALUE_TRUE)) return Boolean.TRUE;
            if (parser.hasToken(JsonToken.VALUE_FALSE)) return Boolean.FALSE;
            throw MismatchedInputException.from(parser, Boolean.class, "true 또는 false가 필요합니다.");
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
