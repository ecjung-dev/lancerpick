package com.example.lancerpick.common.security;

import java.io.Serializable;

/**
 * 인증 담당자가 검증 후 구성하는 principal의 최소 연결 계약.
 * 구현 객체는 SecurityContext의 Authentication.principal에 넣는다.
 * 요청 본문·헤더에서 이 객체를 직접 생성해 인증을 대체하면 안 된다.
 */
public interface AuthenticatedMember extends Serializable {
    Long memberId();
}
