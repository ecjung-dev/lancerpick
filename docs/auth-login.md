# 로그인·로그아웃·내 정보 API

작성일: 2026-11-03. 담당: 김창재. PR #31(병합). 프런트 로그인·회원가입 화면과 인증 캐시 연결은 별도다.

## 계약

| 경로 | 입력·권한 | 결과 |
| --- | --- | --- |
| POST /api/auth/login | 비로그인 + CSRF; email, password | 200 Member, 세션 발급 |
| POST /api/auth/logout | 회원 + CSRF; 본문 없음 | 204, 세션 무효화 |
| GET /api/me | 회원 | 200 Member |
| PATCH /api/me | 회원 + CSRF; displayName | 200 Member |

Member: id(문자열), email, displayName, role, hasSellerProfile. 모든 성공 응답은 `Cache-Control: no-store`다. SecurityConfig는 변경하지 않았다. 로그인·가입 경로는 기존 설정에서 이미 공개되어 있고, 나머지 경로는 인증이 필요하다.

## 로그인 처리 순서

1. 이메일은 가입과 같은 방식으로 정리한다. TextNormalizer로 공백을 정리하고 Locale.ROOT 기준 소문자로 바꾼 뒤 회원을 조회한다. 비밀번호는 정리하지 않고 입력 그대로 비교한다.
2. 회원이 없으면 더미 해시와 한 번 비교한다. 응답 시간 차이로 가입 여부가 드러나는 것을 줄이기 위해서다. 이메일이 없을 때와 비밀번호가 틀렸을 때 모두 같은 `401 INVALID_CREDENTIALS`와 같은 메시지를 반환한다. 로그인에서는 비밀번호 형식을 검사하지 않는다(API 10절).
3. 기존 세션이 있으면 `changeSessionId()`로 세션 ID를 바꾸고, 없으면 새로 만든다. 세션 고정 공격을 막기 위해서다.
4. principal은 `LoginMember(memberId, role)`이다. `AuthenticatedMember`를 구현하므로 각 기능은 `CurrentMemberId.require()`로 회원 ID를 얻는다. 권한은 `ROLE_USER` 또는 `ROLE_ADMIN`이고, credentials는 저장하지 않는다.
5. SecurityContext를 `HttpSessionSecurityContextRepository`로 세션에 저장한다.
6. CSRF 토큰을 교체한다. 프런트는 로그인·로그아웃 후 `GET /api/auth/csrf`를 다시 호출해야 한다.

로그아웃은 `SecurityContextLogoutHandler`로 세션과 인증 정보를 지운다. PATCH /me는 displayName을 정리한 뒤 1~30자를 허용하고, 값이 실제로 바뀐 경우에만 updated_at을 UTC 마이크로초로 갱신한다. hasSellerProfile은 `seller_profiles`의 행 수만 조회한다(쓰기 없음). 판매자 프로필 Repository가 생기면 그쪽으로 옮긴다.

## 검증 범위

AuthLoginIntegrationTests 11건은 실제 Security 필터, HTTP, Service, MySQL을 연결해 검사한다. 대상은 로그인 성공 후 세션으로 /me 조회, principal의 memberId와 ROLE_ADMIN, 기존 세션 ID 변경, 이메일 정리, 두 실패 응답의 동일성, 비밀번호 공백을 정리하지 않음, 빈 요청 400, CSRF 403, PATCH 성공과 실패, 로그아웃 후 401, 익명 401이다. 브라우저에서도 로그인 200 → /me 200 → PATCH 200 → 로그아웃 204 → /me 401 흐름을 수동으로 확인했다. 준범님이 로그인 세션으로 지갑 조회 API가 동작하는 것도 확인했다.

로그인 시도 횟수 제한, 기억하기(remember-me), 배포 환경의 쿠키 Secure·SameSite 설정은 이번 범위가 아니다.
