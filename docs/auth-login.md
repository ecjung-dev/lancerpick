# 로그인·로그아웃·내 정보 API

현재 통합 상태 2026-11-03: `LoginMember` → `AuthenticatedMember` → `CurrentMemberId.require()` 계약으로 지갑·거래 API를 연결했다. 실제 가입/로그인 세션의 [지갑 검사](wallet-session-integration.md)와 [거래 검사](session-trade-integration.md)를 통과했다. 2026-11-04: 로그인·가입 폼과 전역 인증 상태(아래 '프런트 연결')를 구현했다. 세션 저장 뒤 판매자 프로필 조회가 실패하면 응답과 세션이 어긋나던 검토 항목은 조회를 세션 변경 앞으로 옮겨 해결했다.

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
3. 응답에 넣을 `hasSellerProfile`을 먼저 조회한다. 세션을 바꾼 뒤 조회가 실패하면 화면은 실패인데 서버는 로그인된 상태로 남기 때문이다.
4. 기존 세션이 있으면 `changeSessionId()`로 세션 ID를 바꾸고, 없으면 새로 만든다. 세션 고정 공격을 막기 위해서다.
5. principal은 `LoginMember(memberId, role)`이다. `AuthenticatedMember`를 구현하므로 각 기능은 `CurrentMemberId.require()`로 회원 ID를 얻는다. 권한은 `ROLE_USER` 또는 `ROLE_ADMIN`이고, credentials는 저장하지 않는다.
6. SecurityContext를 `HttpSessionSecurityContextRepository`로 세션에 저장한다.
7. CSRF 토큰을 교체한다. 프런트는 로그인·로그아웃 후 `GET /api/auth/csrf`를 다시 호출해야 한다.

로그아웃은 `SecurityContextLogoutHandler`로 세션과 인증 정보를 지운다. PATCH /me는 displayName을 정리한 뒤 1~30자를 허용하고, 값이 실제로 바뀐 경우에만 updated_at을 UTC 마이크로초로 갱신한다. hasSellerProfile은 `SellerProfile` 엔티티 개수를 JPQL로 센다(쓰기 없음).

## 검증 범위

AuthLoginIntegrationTests 11건과 AuthLoginResponseFailureIntegrationTests 1건은 실제 Security 필터, HTTP, Service, MySQL을 연결해 검사한다. 대상은 로그인 성공 후 세션으로 /me 조회, principal의 memberId와 ROLE_ADMIN, 기존 세션 ID 변경, 이메일 정리, 두 실패 응답의 동일성, 비밀번호 공백을 정리하지 않음, 빈 요청 400, CSRF 403, PATCH 성공과 실패, 로그아웃 후 401, 익명 401, 응답 조회가 실패하면 세션에 로그인 정보가 남지 않음(2026-11-04 추가)이다. 실패 검사는 `@MockitoSpyBean`이 별도 테스트 컨텍스트를 만들기 때문에 `@DirtiesContext`로 분리해, 끝나면 컨텍스트와 DB 연결을 닫는다(CI의 Too many connections 방지). 브라우저에서도 로그인 200 → /me 200 → PATCH 200 → 로그아웃 204 → /me 401 흐름을 수동으로 확인했다. 준범님이 로그인 세션으로 지갑 조회 API가 동작하는 것도 확인했다.

로그인 시도 횟수 제한, 기억하기(remember-me), 배포 환경의 쿠키 Secure·SameSite 설정은 이번 범위가 아니다.

## 프런트 연결 (2026-11-04)

| 파일 | 역할 |
| --- | --- |
| `features/auth/api.ts` | `getCurrentMember`(401이면 null), `login`, `logout`, `signup`. 공통 `apiRequest`를 사용해 CSRF·오류 형식을 통일한다 |
| `features/auth/session.ts` | `resetAccountState`: 로그인·로그아웃 직후 `clearCsrfToken()` → 진행 중인 `['auth', 'me']` 조회 취소(늦은 응답이 새 상태를 덮어쓰지 않게) → `clearWalletCache()` → `['auth']` 외 조회 취소·삭제 → 새 로그인 상태 반영. `safeNextPath`: 같은 사이트 경로만 허용 |
| `features/auth/queries.ts` | `useCurrentMember` (`['auth', 'me']`, 항상 서버에 다시 확인) |
| `features/auth/validation.ts` | 서버와 같은 이메일·비밀번호·표시 이름 규칙. 로그인은 빈 값만 검사하고 비밀번호는 절대 정리하지 않는다 |
| `features/auth/pages/LoginPage.tsx`·`SignupPage.tsx` | 로그인 성공 시 `?next=` 경로로 이동, 가입 성공 시 자동 로그인 없이 `/login`으로 이동(이메일 채움). 409 중복 이메일·400 fieldErrors를 입력칸별로 표시 |
| `features/auth/components/MemberNav.tsx` | 헤더 회원 메뉴. 로그인 시 표시 이름·로그아웃, 아니면 로그인·회원가입. 로그아웃 401은 이미 끝난 세션으로 보고 똑같이 정리한다 |

요청 버튼은 처리 중에 비활성화하고, 로그인·가입·로그아웃은 자동으로 다시 보내지 않는다(`retry: false`). 통신 오류·5xx처럼 서버 처리 여부가 불명확하면 실패로 단정하지 않는다. 로그인·로그아웃은 `recheckSession()`으로 CSRF 토큰을 버리고 `/api/me`를 다시 확인하며, 가입은 로그인부터 시도하도록 안내한다(개발 규칙 4절). 화면 문구가 아니라 오류 `code`로 분기한다. 프런트 검증은 `tests/auth.test.mjs` 10건(불명확한 실패 분류·재확인 시 명령 재전송 없음, `?next=`의 탭·줄바꿈·역슬래시 차단, 로그인·로그아웃 전에 보낸 늦은 /me 응답이 새 상태를 덮어쓰지 않음, 401→null, CSRF 1회 전송·재시도 없음, 로그인 후 새 CSRF·이전 계정 캐시 삭제, 서버와 같은 입력 규칙, 오픈 리다이렉트 차단)이다.
