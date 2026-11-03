package com.example.lancerpick.common.security;

/**
 * 로그인 성공 후 SecurityContext의 Authentication.principal에 넣는 인증 회원 정보.
 * AuthenticatedMember 계약을 구현하므로 CurrentMemberId.require(authentication)로 회원 ID를 꺼낼 수 있다.
 * 세션에 저장되므로 비밀번호·해시 같은 민감 정보는 담지 않는다.
 *
 * @param memberId members.id
 * @param role     members.role 값(USER, ADMIN). 권한은 "ROLE_" + role 로 부여한다.
 */
public record LoginMember(Long memberId, String role) implements AuthenticatedMember {
}
