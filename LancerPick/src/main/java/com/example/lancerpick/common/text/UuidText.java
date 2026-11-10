package com.example.lancerpick.common.text;

import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 요청 키·클라이언트 메시지 ID 같은 UUID 문자열 입력 규칙. 하이픈 포함 8-4-4-4-12 16진수만 받는다.
 * {@link UUID#fromString}만으로는 자리수가 짧은 값도 받아들이므로 형식을 먼저 검사한다. 오류 응답은 호출자가 정한다.
 */
public final class UuidText {
  private static final Pattern CANONICAL =
      Pattern.compile("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

  private UuidText() {}

  /** 형식에 맞으면 UUID, null이거나 형식이 다르면 빈 값. 저장·비교용 문자열은 {@code toString()}(소문자)으로 얻는다. */
  public static Optional<UUID> parse(String raw) {
    if (raw == null || !CANONICAL.matcher(raw).matches()) {
      return Optional.empty();
    }
    return Optional.of(UUID.fromString(raw));
  }
}
