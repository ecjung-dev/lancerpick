package com.example.lancerpick.chat.dto;

/** 연결의 개인 오류 큐로만 보내는 응답. 메시지 원문·세션·토큰은 포함하지 않는다. */
public record ChatSendError(String orderId, String clientMessageId, String code, String message) {}
