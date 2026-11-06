package com.example.lancerpick.chat.event;

import com.example.lancerpick.chat.dto.ChatMessageResponse;

/** 메모리 이벤트다. 대화 본문을 영속 아웃박스 payload에 복제하지 않는다. */
public record ChatMessageStored(ChatMessageResponse message, long buyerId, long sellerId) {}
