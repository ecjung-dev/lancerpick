package com.example.lancerpick.common.security;

/**
 * 로그인 성공 후 SecurityContext의 Authentication.principal에 넣는 인증 회원 정보.
 * AuthenticatedMember 계약을 구현하므로 CurrentMemberId.require(authentication)로 회원 ID를 꺼낼 수 있다.
 * 세션에 저장되므로 비밀번호·해시 같은 민감 정보는 담지 않는다.
 *
 * @param memberId    members.id
 * @param role        members.role 값(USER, ADMIN). 권한은 "ROLE_" + role 로 부여한다.
 * @param authVersion 로그인 당시 members.auth_version. 비밀번호를 바꾸면 DB 값이 1 올라가고,
 *                    SessionAuthVersionFilter가 값이 다른 세션을 폐기한다. null이면 버전 확인을 하지 않는다
 *                    (DB 없이 만드는 테스트용 principal). 실제 로그인은 항상 값을 넣는다.
 */
public record LoginMember(Long memberId, String role, Long authVersion) implements AuthenticatedMember {

    /** 버전 확인이 필요 없는 테스트·기존 호출용. 실제 로그인(AuthService)은 3개 인자 생성자를 쓴다. */
    public LoginMember(Long memberId, String role) {
        this(memberId, role, null);
    }
}
