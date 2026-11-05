package com.example.lancerpick.common.security;

import java.util.Optional;

/**
 * 세션 폐기 확인용 조회 계약. 회원의 현재 members.auth_version을 돌려준다(회원이 없으면 empty).
 * 공통 보안 필터가 회원 패키지에 직접 의존하지 않도록 인터페이스로 분리했다.
 * 구현: auth/service/MemberAuthVersionLookup (DB를 쓰므로 demo 프로필에는 없다 → 필터도 등록하지 않는다).
 */
public interface AuthVersionLookup {
    Optional<Long> currentAuthVersion(long memberId);
}
