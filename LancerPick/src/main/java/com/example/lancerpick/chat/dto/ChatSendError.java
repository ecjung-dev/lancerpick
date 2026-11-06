package com.example.lancerpick.chat.dto;

import com.example.lancerpick.common.exception.ApiError;
import java.util.List;
import java.util.Objects;

/**
 * 연결의 개인 오류 큐로만 보내는 응답. 메시지 원문·세션·토큰은 포함하지 않는다.
 * REST 오류({@link ApiError})와 같은 code·message·fieldErrors·traceId를 싣고, HTTP 응답 상태가 없으므로 같은 의미의
 * status를 함께 보낸다. orderId·clientMessageId는 어느 전송의 실패인지 클라이언트가 찾는 데 쓴다.
 */
public record ChatSendError(
    String orderId,
    String clientMessageId,
    int status,
    String code,
    String message,
    List<ApiError.FieldError> fieldErrors,
    String traceId) {

  public ChatSendError {
    if (status < 400 || status > 599) throw new IllegalArgumentException("Invalid error status");
    Objects.requireNonNull(code, "code");
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(traceId, "traceId");
    fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
  }

  public ChatSendError(
      String orderId, String clientMessageId, int status, String code, String message, String traceId) {
    this(orderId, clientMessageId, status, code, message, List.of(), traceId);
  }
}
