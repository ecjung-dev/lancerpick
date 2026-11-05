package com.example.lancerpick.chat.realtime;

import com.example.lancerpick.common.security.AuthenticatedMember;
import java.security.Principal;

/** 개인 목적지의 이름은 회원/HTTP 세션 ID 대신 연결마다 발급한 불투명 식별자다. */
public record ChatSocketIdentity(Long memberId, long authVersion, String name)
    implements AuthenticatedMember, Principal {
  @Override
  public String getName() {
    return name;
  }
}
