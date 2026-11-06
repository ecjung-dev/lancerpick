# 로그인·로그아웃·내 정보 API

## 프로필 저장·자격 증명 경합 보완 — 2026-11-06

`codex/fix-profile-payout-races` 로컬 변경: 표시 이름 수정도 비밀번호 변경·재설정과 같은 회원 행 배타 잠금을 사용한다. 과거 회원 상태를 읽은 프로필 저장이 나중에 커밋되며 비밀번호 해시·`auth_version`을 되돌리던 경합을 막는다. API·응답·DB 구조는 유지한다.

`ProfileCredentialRaceIntegrationTests`는 비밀번호 변경과 재설정 각각에 대해 실제 독립 트랜잭션의 순서를 제어한다. 수정 전 두 경우 모두 인증 버전이 0으로 돌아가 실패했고, 수정 후에는 새 비밀번호·인증 버전 1·새 표시 이름과 재설정 토큰 소비를 보존한다. SQL 조회 지점은 스파이와 실제 JPA 조회·행 잠금으로 제어하며 브라우저 조작 검증은 아니다. 최종 선택 검사 결과는 [현재 현황](current-status.md)을 따른다.

> 최신 수동 확인(2026-11-04): 사용자가 실제 화면의 일반 거래 완료·정산, 취소·환불·잔액 반환과 기본 충전 실패 흐름 확인을 완료했다. 아래 날짜별 미확인·후속 표기는 당시 기록이며, 현재 남은 확인과 보류 사항은 [사용자 수동 검증 현황](manual-trade-verification.md)을 따른다.

> 문서 읽기 기준(2026-11-04): 아래 날짜·브랜치별 구현 및 검증 설명은 당시 기록이다. 현재 반영 여부·남은 작업은 [현황표](current-status.md), 실행 설정은 [루트 README](../README.md), API 계약은 [API 명세](api-spec.md)를 따른다. 과거의 미구현·병합 전 표현과 검사 수를 현재 상태로 확대하지 않는다.

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

## 내 정보 화면 (2026-11-04, `feature/profile-display-name`)

MVP 3절의 "표시 이름 수정"을 화면으로 연결했다. 서버 `PATCH /api/me`(API 2절)는 그대로이며 백엔드 변경은 없다.

| 파일 | 역할 |
| --- | --- |
| `features/auth/api.ts` | `updateMe({ displayName })`: 본문에 표시 이름만 보낸다. 회원 ID는 서버가 세션에서 꺼낸다 |
| `features/auth/validation.ts` | `validateDisplayName`: 공백 정리 후 1~30자(코드 포인트). 가입 검사도 같은 함수를 쓴다 |
| `features/auth/profile.ts` | `saveDisplayName`: 세션 재확인 → PATCH 1회 → 응답 회원 ID 확인 → 진행 중인 `['auth', 'me']` 조회 취소 후 새 정보 반영 → 다른 조회는 오래된 것으로 표시 |
| `features/auth/pages/MyProfilePage.tsx` | `/mypage/profile`. 이메일·현재 표시 이름 표시, 표시 이름 입력·글자 수·저장. 비로그인은 `/login?next=%2Fmypage%2Fprofile` 안내 |
| `features/wallet/pages/WalletPage.tsx` | 마이페이지 메뉴의 "내 정보 준비 중"을 `/mypage/profile` 링크로 교체(한 줄) |

실패·동시 요청·늦은 응답:

- 같은 브라우저의 다른 탭에서 다른 계정으로 로그인하면 세션 쿠키가 바뀌어, 그대로 PATCH를 보내면 그 계정의 이름이 바뀐다. 그래서 저장 직전에 `GET /api/me`로 같은 계정인지 확인하고, 다르면 보내지 않고 `resetAccountState`로 이전 계정 캐시를 정리한 뒤 안내한다. 확인과 저장 사이의 짧은 틈은 응답의 회원 ID로 한 번 더 확인한다(이 경우 저장은 이미 다른 계정에 반영됐을 수 있다).
- 저장 전에 출발한 늦은 `/api/me` 응답이 헤더의 이름을 옛 이름으로 되돌리지 않도록, 새 정보를 넣기 전에 진행 중인 조회를 취소한다.
- 401은 이전 계정 캐시를 정리하고 로그인 안내로 바꾼다. 통신 오류·5xx는 저장됐을 수 있으므로 실패로 단정하지 않고 내 정보를 다시 조회하며 입력값은 남긴다. 400·CSRF 403은 서버 메시지를 보여주고 다시 보내지 않는다.
- 처리 중에는 입력을 막고 빠른 두 번 클릭은 ref로 한 번 더 막는다. `retry: false`. 지금 이름과 같으면 요청하지 않는다. 화면을 떠난 뒤 도착한 응답은 화면 상태를 바꾸지 않는다.
- 두 탭에서 동시에 이름을 바꾸면 서버에 버전 검사가 없으므로 나중에 저장한 값이 남는다(API 2절 계약 그대로). 화면은 저장 후 서버 응답 값을 표시한다.

검증: `tests/my-profile.test.mjs` 9건(PATCH 본문·CSRF·재시도 없음, 표시 이름 규칙과 가입 규칙 일치, 저장 후 헤더 캐시 갱신과 늦은 /me 무시, 다른 계정·세션 만료 시 PATCH 미전송과 캐시 정리, 다른 회원 응답 처리, 400·403·500 1회 전파, 비로그인 안내·next, 이메일·이름 표시와 이스케이프, 세션 확인 실패 시 재시도, 마이페이지 메뉴 링크). 실행 결과와 브라우저 확인 여부는 PR에 기록한다.

## 좁은 화면 확인 — 2026-11-05

에이전트가 Claude 데스크톱 앱의 브라우저 창을 조작해 320×740 viewport로 확인했다(2026-11-05, main `74328c1`, 로컬 개발 DB·개발 서버). 판정은 문서 `clientWidth`와 `scrollWidth`(둘 다 320)와 화면 밖으로 나간 요소 수(0)로 했다. 실기기 모바일·다른 브라우저·터치·가상 키보드 검증으로 확대하지 않는다. 로그인은 공개 시연 계정(`demo.b@example.com`)을 사용했다.

- `/login`, `/signup`: 입력칸·안내·버튼이 넘치지 않는다.
- `/login?next=%2Fmypage%2Fprofile`로 로그인 후 `/mypage/profile`로 돌아온다. 이메일·현재 표시 이름·입력칸·글자 수·저장 버튼이 넘치지 않는다.
- 표시 이름을 공백 없는 영문 30자로 저장해도 헤더가 넘치지 않는다(이름 한 줄, 로그아웃 버튼은 다음 줄). 확인 후 원래 이름(`테스트 회원 B`)으로 되돌렸다.
- `/mypage` 메뉴의 "내 정보" 링크가 좁은 화면에서도 표시된다.
- 발견된 결함이 없어 코드 변경은 없다. 2026-11-04 상품 담당의 헤더 줄바꿈 수정(`product-review-browser-check.md`) 이후 상태다.

## 비밀번호 변경·세션 폐기 — 2026-11-05 (`feature/password-change`)

반영 상태: main `b08e031`(#84)에 병합됐다. 아래 검증 결과는 병합 전 작업 브랜치 기록이다.

고도화 4인 설계 4절 3·4번(로그인 상태 비밀번호 변경, 기존 세션 무효화)과 3인 설계 6.2·9절을 구현했다. DB 변경은 없다. V5에서 추가된 `members.auth_version`(기본 0)을 사용한다. 새 라이브러리도 없다.

### 계약

| 경로 | 입력·권한 | 결과 |
| --- | --- | --- |
| POST /api/me/password | 회원 + CSRF; currentPassword, newPassword | 204 `no-store`. 지금 세션을 끝내고 다른 세션도 모두 폐기한다. 자동 로그인하지 않는다 |

| 상황 | 응답 |
| --- | --- |
| newPassword가 가입 규칙 위반·누락, currentPassword 누락 | 400 VALIDATION_FAILED, fieldErrors의 field=`newPassword`·`currentPassword` |
| 현재 비밀번호 불일치 | 400 CURRENT_PASSWORD_INCORRECT. 401이 아닌 이유: 401은 화면이 "로그인 끝남"으로 처리한다 |
| 새 비밀번호 = 현재 비밀번호 | 400 PASSWORD_UNCHANGED |
| 비로그인, 이미 폐기된 세션, 동시 변경에서 늦은 요청 | 401 AUTHENTICATION_REQUIRED |
| CSRF 누락 | 403 CSRF_INVALID |

두 비밀번호는 정리(trim)하지 않는다. currentPassword는 로그인과 같이 형식을 검사하지 않는다. newPassword는 가입과 같은 정규식이다(`member/dto/PasswordRules.PATTERN`, 가입 DTO도 같은 상수를 쓰도록 바꿨다). 72바이트를 넘는 currentPassword는 BCrypt 비교 없이 불일치로 처리한다(500 방지). 지갑·적립금은 건드리지 않는다.

### 서버 처리 순서

1. `MeController`가 세션 principal에서 회원 ID와 로그인 당시 `authVersion`을 꺼낸다.
2. `MemberService.changePassword`가 회원 행을 `SELECT … FOR UPDATE`로 잠근다(`MemberRepository.findByIdForUpdate`).
3. 세션 버전과 DB 버전이 다르면 401. 같은 회원의 변경 두 건이 동시에 오면 잠금에서 한 줄로 서고, 늦은 쪽은 앞 요청이 올린 버전을 보고 거절된다(덮어쓰지 않음).
4. 현재 비밀번호 확인 → 같은 비밀번호 거절 → 새 해시 저장 + `auth_version + 1` + `updated_at` 갱신(`Member.changePassword`). 한 트랜잭션이다.
5. 커밋 뒤 `AuthService.logout`(로그아웃과 같은 `SecurityContextLogoutHandler`)으로 지금 세션을 무효화한다.

### 세션 폐기 계약 (채팅 소켓 담당 참고)

- 로그인 principal은 `LoginMember(memberId, role, authVersion)`이다. `authVersion`은 비밀번호 해시와 같은 행 조회에서 읽은 값이다. `CurrentMemberId.require()` 사용법은 그대로다.
- `SessionAuthVersionFilter`(Security 체인, `CsrfFilter` 다음)가 매 요청 `AuthVersionLookup.currentAuthVersion(memberId)`로 DB 값을 읽어 비교한다. 다르거나 회원이 없으면 세션을 무효화하고 인증 정보를 지운 뒤 비로그인으로 계속 진행한다. 보호 API는 401, 공개 조회는 비로그인 결과다. 캐시하지 않으므로 요청마다 기본키 조회 1회가 추가된다.
- 로그인과 비밀번호 변경이 겹쳐 변경이 먼저 커밋되면, 그 로그인 세션은 옛 버전을 갖고 있어 다음 요청에서 폐기된다. 옛 비밀번호로 새 버전 세션이 발급되지 않는다.
- 소켓 연결·SEND 검사에서도 같은 `AuthVersionLookup`으로 `memberId + authVersion`을 다시 확인하면 된다. 서버가 소켓에 보내는 폐기 신호(이벤트)는 이번 범위가 아니다(소켓 구현 시 함께 정한다).
- `authVersion`이 null인 principal(DB 없이 만드는 테스트용 2인자 생성자)은 확인하지 않는다. 실제 로그인은 항상 값을 넣는다. demo 프로필과 `@WebMvcTest`에는 조회 Bean이 없어 필터를 넣지 않는다.

한계: 이미 필터를 통과해 처리 중인 요청은 되돌리지 않는다. 비밀번호 변경 요청 횟수 제한(현재 비밀번호 대입 방지)은 아직 없다(요청 제한 작업에서 함께 처리). 변경 완료 안내 메일은 메일 기반 작업 후 추가한다. 세션이 메모리에 있으므로 서버 재시작 때 모두 로그아웃되는 기존 동작은 같다.

### 화면

| 파일 | 역할 |
| --- | --- |
| `features/auth/api.ts` | `changePassword`: POST `/me/password`, 원문 그대로 1회 |
| `features/auth/validation.ts` | `validatePasswordChange`: 현재 비밀번호 필수, 새 비밀번호 가입 규칙·현재와 다름, 확인 일치. 정리하지 않음 |
| `features/auth/password.ts` | `submitPasswordChange`: 같은 계정인지 `/api/me`로 확인 → POST 1회 → 성공하면 `resetAccountState(null)`(CSRF·개인 캐시 정리) |
| `features/auth/pages/PasswordChangeForm.tsx` | `/mypage/profile` 아래 비밀번호 변경 폼. `autocomplete` current-password·new-password, 숨긴 username(이메일) |
| `features/auth/pages/LoginPage.tsx` | 이동 state `passwordChanged: 'done' \| 'unclear'` 안내 |

- 성공: 로그인 화면으로 이동해 "새 비밀번호로 로그인" 안내.
- 400: 코드별로 입력칸 안내(현재 비밀번호 틀림이면 그 칸을 비움). CSRF 403: 다시 시도 안내. 자동 재전송 없음.
- 401: 캐시를 정리하고 로그인 안내 화면으로 바뀐다.
- 통신 오류·5xx: `recheckSession`. 같은 계정 그대로면 바뀌지 않은 것이다(바뀌었다면 이 세션도 끊긴다). 로그인이 끝났으면 바뀌었을 수 있으므로 로그인 화면에서 새 비밀번호를 먼저 시도하도록 안내한다.

### 검증

백엔드 `PasswordChangeIntegrationTests`(실제 Security 필터·MySQL): 로그인 principal의 버전 0, 변경 성공 시 두 세션 모두 폐기·옛 비밀번호 401·새 비밀번호 로그인·`auth_version`=1·해시 변경·지갑 잔액·원장 불변, 현재 비밀번호 틀림(공백 포함·72바이트 초과 포함) 400·세션 유지, 같은 비밀번호 400, 새 비밀번호 규칙 위반 6종 400, 누락 400, 익명 401·CSRF 403, DB 버전이 바뀐 세션 폐기(보호 401·공개 200·변경 401), 삭제된 회원 세션 폐기, 동시 변경 2건 중 1건만 성공. 프런트 `tests/password-change.test.mjs`. 실행 결과(2026-11-05 로컬): 관련 백엔드 테스트·`clean build` 통과, 프런트 test:api 138건 통과·typecheck·lint·build 통과.

브라우저 확인(2026-11-05, 에이전트가 Claude 데스크톱 앱 브라우저로 로컬 개발 서버 확인, 새 테스트 계정 `pwcheck-1105a@example.com` 사용): 빈 값 제출 시 세 칸 안내, 틀린 현재 비밀번호 → POST 1회 400·현재 비밀번호 칸 비움·안내, 정상 변경 → `/login` 이동과 완료 안내·헤더 비로그인 표시·`/api/me` 401, 옛 비밀번호 로그인 401 INVALID_CREDENTIALS·새 비밀번호 로그인 200. 320×740에서 `/mypage/profile`의 문서 너비 320·넘친 요소 0. 두 기기 세션 폐기는 같은 브라우저로 재현하지 않았고 백엔드 통합 테스트로 검증했다. 실기기 모바일은 확인하지 않았다.

## 비밀번호 재설정·요청 제한 — 2026-11-05 (`feature/password-recovery`)

4인 설계 §4 2번(비밀번호 재설정)과 3인 설계 6.1·7·8절을 구현했다. DB·라이브러리 변경은 없다(V5 `account_challenges`·`auth_request_limits` 사용, 새 엔티티 2개). **실제 메일 발송은 C3(메일 인프라, 정은찬·황준범) 연결 후**이며(C3 발송기는 [메일 인프라](mail-infrastructure.md)에서 추가, #90으로 main 반영돼 `MAIL_ENABLED=true`로 켬), 이 작업은 4인 설계 §4의 김창재 범위(메일 내용·토큰·회원 API·요청 제한)만 한다.

### 계약

| 경로 | 입력·권한 | 결과 |
| --- | --- | --- |
| POST /api/auth/password-reset-requests | 비로그인 가능 + CSRF; email | 202 `{message}` `no-store`. 가입 여부·제한 초과·발송 실패와 무관하게 같은 응답. 이메일 형식 오류만 400 VALIDATION_FAILED |
| POST /api/auth/password-resets | 비로그인 가능 + CSRF; token, newPassword | 204 `no-store`. 토큰 위조·만료·사용됨·발급 후 비밀번호 변경은 모두 400 PASSWORD_RESET_TOKEN_INVALID. 새 비밀번호 규칙 위반은 400 VALIDATION_FAILED(field=newPassword, 토큰은 그대로 사용 가능) |

메일 링크: `lancerpick.frontend.base-url`(기본 `http://localhost:5173`, 환경변수 `FRONTEND_BASE_URL`) + `/password-reset#token=…`. 요청 Host·returnUrl을 쓰지 않는다. 링크 GET은 화면만 보여주고, 토큰 사용은 POST에서만 한다.

### 서버 처리 순서

요청(`PasswordResetService.requestReset`):
1. 이메일 정규화(가입과 같은 `member/dto/EmailRules`. 가입 코드도 이 클래스로 옮겼다).
2. 제한 키 = HMAC-SHA256(서버 키, `PASSWORD_RESET` + 이메일). 제한 행이 없으면 별도 짧은 트랜잭션에서 만들고 커밋한다(HQL `on conflict do nothing`, 지갑 최초 생성과 같은 방식). 같은 새 이메일의 첫 요청이 동시에 오면 MySQL이 같은 키 insert끼리 교착 상태(deadlock)로 한쪽을 롤백할 수 있어, 이 행 만들기만 교착 시 최대 3번 시도한다(결과가 같은 작업이고, 다시 시도할 때는 행이 이미 있다).
3. 업무 트랜잭션: 제한 행 잠금(간격 행 → 1시간 행) → 허용되면 회원 조회 → 회원이 있으면 SecureRandom 32바이트 토큰의 SHA-256 해시, 발급 당시 auth_version, 15분 만료, `delivery_status=PENDING` 저장.
4. 커밋 뒤 트랜잭션 밖에서 `AccountMailDispatcher`가 메일을 발송기에 넘긴다. 실패해도 예외를 던지지 않고 `delivery_status`·안전한 로그(challengeId·코드)만 남긴다.

새 요청이 와도 기존 유효 링크는 지우지 않는다(남이 반복 요청해 복구를 방해하지 못하게). 대신 요청 수를 제한한다.

실행(`resetPassword`, 한 트랜잭션): 토큰 해시로 회원 ID 조회(잠금 없음) → **회원 `FOR UPDATE` → 토큰 `FOR UPDATE`** → 잠근 뒤 목적·사용 여부·만료(경계 시각은 만료)·발급 당시 auth_version 재확인 → `Member.changePassword` 재사용(해시 변경 + auth_version + 1) → `consumed_at` 기록. 같은 토큰 동시 제출은 회원 잠금에서 한 줄로 서고 늦은 쪽은 400이다. auth_version이 오르므로 기존 세션은 `SessionAuthVersionFilter`가 폐기하고 다른 재설정 링크도 버전 불일치로 쓸 수 없다. 지갑·적립금은 건드리지 않는다.

### 요청 제한

`auth_request_limits`에 이메일(HMAC)마다 두 행을 쓴다. 가입하지 않은 이메일에도 같은 제한을 적용한다.

| scope | window_start | 의미 |
| --- | --- | --- |
| PASSWORD_RESET_EMAIL_INTERVAL | 고정값 2000-01-01 | 이메일당 한 행. `expires_at` = 마지막 허용 시각 + 60초. 이 시각 전이면 막는다(구간 경계와 무관하게 최소 60초) |
| PASSWORD_RESET_EMAIL_HOUR | 지금을 시 단위로 자른 값 | 그 1시간 구간에 허용한 수. 5 이상이면 막는다 |

막히면 발급하지 않고 같은 202를 돌려준다(3인 설계 6.1 2번). 같은 이메일의 동시 요청은 간격 행 잠금에서 한 줄로 선다. 제한 키는 `lancerpick.auth.request-limit-key`(환경변수 `AUTH_REQUEST_LIMIT_KEY`)로 각자 설정하며 저장소에 넣지 않는다. 비어 있으면 서버 시작 시 임의 키를 만든다(재시작하면 이전 제한 행과 이어지지 않는 개발용 한계).

### 메일 발송 경계 (C3 연결 계약)

- `auth/mail/AccountMailSender.enqueue(AccountMail)`: C3가 Bean으로 구현한다. 요청 스레드를 막지 않고 크기가 정해진 비동기 큐에 넣은 뒤 바로 돌아온다(넣었으면 true, 가득 찼으면 false). SMTP를 요청 스레드에서 하면 가입된 이메일만 느려져 가입 여부가 드러난다.
- 발송이 끝나면 `AccountMailDeliveryRecorder.markSent(challengeId)` / `markFailed(challengeId, 안전한 코드)`를 호출한다. PENDING인 행만 바뀐다.
- 구현: C3 `auth/mail/SmtpAccountMailSender`(`feature/mail-infrastructure`, [메일 인프라](mail-infrastructure.md)). `lancerpick.mail.enabled=true`(환경변수 `MAIL_ENABLED`)일 때만 등록된다.
- 구현 Bean이 없으면(기본 꺼짐) 발송하지 않고 `FAILED`·`MAIL_NOT_CONFIGURED`로 기록한다. 큐 가득 참은 `MAIL_QUEUE_FULL`, enqueue 예외는 `MAIL_ENQUEUE_FAILED`다. 앱은 메일 서버 없이도 지금처럼 켜진다.
- `AccountMail.body`에는 토큰이 있다. 로그·DB·거래 아웃박스에 평문으로 남기지 않는다. `toString`은 수신자·본문을 가린다.

### 화면

| 파일 | 역할 |
| --- | --- |
| `features/auth/api.ts` | `requestPasswordReset`, `resetPassword`(공통 `apiRequest`, 1회 전송) |
| `features/auth/passwordReset.ts` | `readResetToken`(fragment에서 43자 토큰만), `submitPasswordReset`(POST 1회 → `recheckSession`), `resetFailureKind`(code 기준 분류) |
| `features/auth/validation.ts` | `validatePasswordResetRequest`(가입과 같은 이메일 규칙), `validatePasswordReset`(가입 비밀번호 규칙·확인 일치, 정리 안 함) |
| `pages/PasswordResetRequestPage.tsx` | `/password-reset/request`. 같은 안내 문구, 보낸 뒤 60초 버튼 잠금(불명확한 결과도 잠금) |
| `pages/PasswordResetPage.tsx` | `/password-reset#token=…`. 토큰을 화면 상태에만 두고 주소창에서 지움. 잘못된 링크는 새 요청 안내 |
| `pages/LoginPage.tsx` | "비밀번호를 잊으셨나요?" 링크, `passwordReset: 'done' \| 'unclear'` 안내 |

- 처리 중 입력 잠금 + ref로 두 번 클릭 방지, `retry: false`(토큰은 일회용).
- 성공: `recheckSession`으로 이 브라우저의 로그인 상태를 다시 확인한다. 같은 계정 세션은 서버가 폐기해 로그아웃·개인 캐시 정리, 다른 계정이 로그인돼 있으면 그대로 둔다. 이후 로그인 화면에서 완료 안내.
- 400 토큰 오류: 링크 사용 불가 화면. 400 입력 오류: 새 비밀번호 칸 안내. CSRF 403: 다시 시도 안내.
- 통신 오류·5xx: 바뀌었을 수 있으므로 같은 토큰을 다시 보내지 않고 로그인 화면에서 새 비밀번호를 먼저 시도하도록 안내한다.
- 202 본문: 공통 `apiRequest`가 빈 202 본문을 응답 형식 오류로 처리하므로 공통 코드를 바꾸지 않고 서버가 고정 `{message}`를 보낸다.

### 한계

- 실제 메일 발송은 C3 발송기(`MAIL_ENABLED=true` + Mailpit)로 가능해졌다. 확인 범위는 [메일 인프라](mail-infrastructure.md#검증)를 따른다. 재설정 흐름 자체는 자동 테스트의 가짜 발송기로 끝까지 검증했고, 2026-11-06 브라우저 + Mailpit으로도 확인했다(아래 [브라우저 확인](#브라우저--mailpit-확인--2026-11-06)).
- 1시간 제한은 고정 구간이라 두 구간에 걸치면 짧은 시간에 최대 10회까지 허용될 수 있다(60초 간격은 항상 보장).
- IP·전체 발송량 제한, 재설정 POST 실패 횟수 제한(토큰이 256비트라 추측은 현실적이지 않음), 로그인 상태 비밀번호 변경의 시도 제한은 없다.
- 만료된 `account_challenges`·`auth_request_limits` 정리 작업은 없다(공통 스케줄러·Batch 담당과 함께 정한다). `account_challenges.email`에는 스키마상 정규화 이메일이 저장된다.
- 비밀번호 변경·재설정 완료 안내 메일은 아직 없다(C3 발송기를 사용해 K1에서 추가).
- 가입 여부에 따른 응답 시간 차이는 회원 조회·토큰 저장 1건 정도이며 실제 측정은 하지 않았다.

### 검증

백엔드 `PasswordResetIntegrationTests`(실제 Security 필터·MySQL, 가짜 발송기): 전체 흐름(기존 세션 폐기·옛 비밀번호 401·새 비밀번호 로그인·auth_version 1·지갑 잔액·원장 불변), 링크 주소·DB에는 해시만·15분·발송 결과 기록(SENT 뒤 늦은 실패가 덮어쓰지 않음), 가입 안 된 이메일의 같은 응답·미발급·같은 제한, 이메일 정규화·형식 400, 60초 내 재요청 미발급과 이후 재발급, 1시간 5회, 같은 이메일 동시 4건 중 1건만 발급, 일회용, 만료, 다른 링크 사용 후 무효, 비밀번호 변경 후 링크 무효, 잘못된 토큰 5종 같은 400, 새 비밀번호 규칙 위반 시 토큰 유지, 같은 토큰 동시 2건 중 1건만 성공, 큐 가득 참·예외 시 같은 202와 FAILED 기록, CSRF 403. `AccountMailDispatcherTests`(DB 없음): 발송기 없음 → MAIL_NOT_CONFIGURED, 기록 실패도 예외 없음. 프런트 `tests/password-reset.test.mjs` 12건. 실행 결과는 PR에 기록한다(#89: 병합 전 브랜치에서 백엔드 관련 71건·`clean build` 759건, 프런트 167건 통과, CI 성공).

### 브라우저 + Mailpit 확인 — 2026-11-06

C3 전달 사항 6번 요청에 따라 실제 화면에서 처음부터 끝까지 확인했다. 환경: main `0f67606`, 로컬 개발 DB, 백엔드 `bootRun`(이번 실행만 환경변수 `MAIL_ENABLED=true`, 요청 제한 키는 미설정이라 임의 키), 프런트 `npm run dev`, Mailpit v1.33.0. 이 PC에는 Docker가 없어 Docker 대신 Mailpit 공식 Windows 실행 파일(GitHub 릴리스, SHA-256 일치 확인)을 `--smtp 127.0.0.1:1025 --listen 127.0.0.1:8025`로 실행했다. 에이전트가 Claude 데스크톱 앱 내장 브라우저로 조작했고, 새 확인 계정 `pwreset-1106a@example.com`을 화면에서 가입해 사용했다.

| 단계 | 결과 |
| --- | --- |
| 로그인 화면 | "비밀번호를 잊으셨나요?" 링크 → `/password-reset/request` |
| 재설정 요청(로그인 세션을 둔 상태) | POST 1회 202, 같은 안내 문구, 버튼 "59초 후 다시 요청할 수 있어요"로 잠김 |
| Mailpit 수신 | 1통. 보낸 사람 `LancerPick <no-reply@lancerpick.local>`, 제목 `[랜서픽] 비밀번호 재설정 안내`, `text/plain; charset=UTF-8`, 15분 안내, 링크 `http://localhost:5173/password-reset#token=`+43자, 본문에 비밀번호 없음 |
| 메일 링크 열기 | "새 비밀번호 설정" 화면. 주소창이 `/password-reset`로 바뀌어 토큰이 사라지고, 화면 HTML·localStorage·sessionStorage에 토큰 없음. 여는 것만으로는 POST 없음 |
| 확인 칸 불일치 제출 | "새 비밀번호가 서로 달라요." 안내, POST 0회 |
| 정상 제출 | POST `/api/auth/password-resets` 1회 204 → `/login`으로 이동, "비밀번호를 다시 설정했어요…" 안내, 헤더 비로그인 표시, 기존 로그인 세션 `/api/me` 401(폐기) |
| 로그인 | 옛 비밀번호 401 `INVALID_CREDENTIALS`, 새 비밀번호 200 |
| 같은 링크 재사용 | 새 비밀번호 입력 후 제출 → "링크를 쓸 수 없어요" 화면(재설정 메일 다시 받기·로그인 링크). 비밀번호는 바뀌지 않음(재사용 시 입력한 값 로그인 401, 앞의 새 비밀번호 200) |
| 서버 로그 | `account mail` 경고·ERROR 0건. 재사용 거부는 `password reset rejected reason=ALREADY_CONSUMED challengeId=1`만 남고 토큰·이메일 없음 |
| 320×740 | 요청·새 비밀번호·링크 사용 불가·로그인 화면 모두 문서 너비 320, 넘친 요소 0 |

확인하지 않은 것: `account_challenges.delivery_status`의 `SENT`는 DB를 직접 조회하지 않았다(발송 실패·기록 실패 로그가 없는 것으로만 확인). 외부 SMTP·실제 받은편지함·실기기 모바일·다른 브라우저는 범위 밖이다. 60초·1시간 제한과 만료는 자동 테스트 결과를 따른다.

## 이메일 인증 가입 — 2026-11-06 (`feature/email-verification`)

4인 설계 §4 1번과 3인 설계 §5.1·7·8절을 구현했다. DB·라이브러리 변경은 없다(V5 `account_challenges`의 `SIGNUP_EMAIL`, `members.email_verified_at` 사용). 메일은 C3 발송기(#90)를 그대로 쓴다. 기존 회원의 이메일 인증 전환(§5.2, `EXISTING_EMAIL`)과 재설정 성공 시 인증 시각 기록 여부는 다음 작업으로 나눴다.

### 흐름과 계약

```text
/signup 이메일 입력 → POST /api/auth/signup-email-requests (항상 같은 202)
→ 메일 링크 /signup/verify#token=… (열기만 해서는 아무것도 바뀌지 않음)
→ "인증하기" → POST /api/auth/signup-email-verifications → 가입 증명(proof)
→ 비밀번호·표시 이름 입력 → POST /api/members(+emailVerificationProof)
→ 증명 소비 + 회원(email_verified_at) + 지갑 + 100만원, 한 트랜잭션 → /login(자동 로그인 없음)
```

| 경로 | 입력·권한 | 결과 |
| --- | --- | --- |
| POST /api/auth/signup-email-requests | 비로그인 가능 + CSRF; email | 202 `{message}` `no-store`. 이미 가입된 이메일·제한 초과·발송 실패와 무관하게 같은 응답. 형식 오류만 400 VALIDATION_FAILED |
| POST /api/auth/signup-email-verifications | 비로그인 가능 + CSRF; token | 200 `{email, proof, proofExpiresAt, alreadyRegistered}` `no-store`. 위조·만료·이미 인증·목적 다름은 400 SIGNUP_EMAIL_TOKEN_INVALID |
| POST /api/members | 비로그인 + CSRF; email, password, displayName, emailVerificationProof | 201 Member. 증명 없음 400 VALIDATION_FAILED, 증명 오류 400 EMAIL_VERIFICATION_INVALID, 이미 가입 409 EMAIL_ALREADY_EXISTS |

- **이미 가입된 이메일**: 요청 단계에서는 같은 202로 인증 메일도 똑같이 보낸다. 메일을 받은 본인이 인증한 뒤에만 `alreadyRegistered=true`로 알려 주고, 화면은 로그인·비밀번호 재설정으로 안내한다. 가입 API도 409로 한 번 더 막는다(3인 설계 §5.2 마지막 문단).
- **우회 경로 없음**: 증명 없는 구형 가입 요청은 400이다(3인 설계 §5.1). 메일이 꺼진 기본 상태(`MAIL_ENABLED=false`)에서는 화면에서 새 계정을 만들 수 없다. 로컬에서 새 계정이 필요하면 Mailpit을 켠다([메일 인프라](mail-infrastructure.md#로컬-mailpit-확인), Docker가 없으면 Mailpit 공식 실행 파일 사용). 기존 계정과 시드 SQL 계정은 그대로 로그인된다.

### 서버 처리

요청(`SignupEmailService.requestVerification`): 이메일 정규화(`EmailRules`) → 제한 행 준비(별도 커밋, 교착 시 재시도) → 업무 트랜잭션: 제한 확인(scope `SIGNUP_EMAIL_INTERVAL`·`SIGNUP_EMAIL_HOUR`, 60초 간격·1시간 5회, 재설정과 따로 센다) → `SIGNUP_EMAIL` 행 저장(링크 토큰 SHA-256 해시, 15분, `member_id`·`credential_version` NULL) → 커밋 뒤 `AccountMailDispatcher`로 메일(`SignupEmailMailContent`). 비밀번호는 받지도 저장하지도 않고, 회원·지갑·적립금도 만들지 않는다.

인증(`verify`, 한 트랜잭션): 링크 토큰 해시로 행 `FOR UPDATE` → 목적·미인증·미사용·만료 재확인 → **같은 행의 `token_hash`를 새 증명의 해시로 바꾸고 `verified_at` 기록, `expires_at` = 인증 시각 + 30분**(입력할 시간 확보, 2026-11-06 결정). 링크 토큰은 그 순간 무효다. 같은 링크 동시 인증은 잠금에서 한 줄로 서고 늦은 쪽은 400이다.

가입(`MemberService.signup`, 한 트랜잭션): 이메일·표시 이름 정규화 → 증명 해시로 행 `FOR UPDATE` → 목적·인증됨·미사용·미만료·이메일 일치 재확인 → 중복 이메일 409 → `Member.createVerified`(email_verified_at = 가입 시각) 저장 → 증명 `consumed_at` → 지갑·최초 지급. 어느 단계든 실패하면 증명 사용까지 롤백돼 같은 증명으로 다시 시도할 수 있다. 같은 증명 동시 가입은 한 건만 성공(나머지 400), 같은 이메일·다른 증명 동시 가입은 이메일 UNIQUE로 한 건만 성공(나머지 409, 그쪽 증명 사용은 롤백). 잠금 순서: 증명 행 → 새 회원 행 → 지갑.

목적은 엄격히 나뉜다: 가입 증명은 비밀번호 재설정 토큰으로, 재설정 토큰은 가입 인증·증명으로 쓸 수 없다(각 확인 메서드가 `purpose`를 먼저 본다).

공통 코드 정리: 토큰 유틸 이름을 `OneTimeTokens`로 바꿨고(재설정·가입 공용), 제한 행 생성의 교착 재시도를 `AuthRequestLimiter.prepare` 안으로 옮겼으며, 메일 링크 주소 검사를 `FrontendLinks`로 분리했다(재설정·가입 메일 공용).

### 화면

| 파일 | 역할 |
| --- | --- |
| `pages/SignupPage.tsx` | `/signup`. 이메일만 입력(비밀번호 칸 없음), 같은 안내, 60초 재요청 잠금 |
| `pages/SignupVerifyPage.tsx` | `/signup/verify#token=…`. "인증하기" → 가입 정보 입력(이메일은 읽기 전용) → 가입. 이미 가입이면 로그인·재설정 안내, 링크 오류·인증 만료는 인증 메일 다시 받기 안내 |
| `components/EmailLinkRequestForm.tsx` | 이메일 링크 메일 요청 공통 폼(가입 인증·재설정 요청이 함께 사용) |
| `linkToken.ts`, `useLinkToken.ts` | `#token=` 읽기(43자만)와 주소창에서 지우기(가입 인증·재설정 화면 공용) |
| `signupEmail.ts` | `verifyFailureKind`, `signupFailureKind`(code 기준 분류) |
| `api.ts` | `requestSignupEmail`, `verifySignupEmail`, `signup`(증명 포함) |

자동 재전송 없음, 두 번 클릭 방지, 증명·토큰은 메모리에만 둔다. 인증 결과가 불명확(통신 오류·5xx)하면 같은 링크를 다시 보내지 않고 새 인증 메일을 안내하고, 가입 결과가 불명확하면 다시 보내지 않고 로그인부터 시도하도록 안내한다.

### 테스트 회원 만들기 (팀 공통 영향)

`POST /api/members`를 부르는 기존 테스트는 증명이 필요하다. 테스트마다 메일 → 인증을 거치지 않도록 공통 도우미 `support/TestMembers`가 인증된 증명 행을 테스트 DB에 넣고 원문을 돌려준다(`signupJson`·`signupBody`·`signupRequest`). 이번 작업에서 기존 테스트 10개 파일의 가입 호출을 이 도우미로 바꿨다(각 1~2줄, main #111의 `ProfileCredentialRaceIntegrationTests` 포함. 이 파일은 `ResetTokens` → `OneTimeTokens` 이름 변경도 반영). 가짜 메일 발송기는 `support/FakeAccountMailSender`로 옮겨 재설정·가입 테스트가 같은 Spring 컨텍스트를 쓴다.

### 한계

- 기존 회원 이메일 인증 전환(§5.2)과 재설정 성공 시 `email_verified_at` 기록은 하지 않았다(다음 작업). 기존 회원의 `email_verified_at`은 NULL 그대로다.
- 메일이 꺼진 기본 상태에서는 화면 가입이 불가하다(위 "우회 경로 없음").
- 사용하지 않은 가입 인증 행·증명의 정리 작업은 없다. 테스트 도우미가 넣은 행도 각 테스트가 지우지 않으면 테스트 DB에 남는다(회원 FK가 없어 다른 테스트에 영향 없음).
- 아이디 안내는 메일 계약을 정은찬과 먼저 정한 뒤 진행한다(작업표 C3 전달 4번).

### 검증

백엔드 `SignupEmailIntegrationTests`(실제 Security 필터·MySQL, 가짜 발송기): 전체 흐름(인증 전 회원 없음·링크 1회·증명 1회·email_verified_at·지갑 100만원·로그인), DB에는 해시만·15분·증명 30분, 이미 가입된 이메일의 같은 202·인증 후 alreadyRegistered·409, 만료된 링크·증명, 인증 전 토큰·다른 이메일·목적 혼용(재설정 ↔ 가입) 거부, 잘못된 토큰·증명 같은 오류·구형 요청 400, 60초 재요청 미발급·정규화 이메일 같은 제한, 같은 링크 동시 인증 1건, 같은 증명 동시 가입 1건, 같은 이메일 다른 증명 동시 가입 1건(진 쪽 증명 롤백), CSRF 403. `MemberServiceTests`(DB 없음) 증명 형식·다른 이메일 거부, `MemberSignupRollbackIntegrationTests` 지갑 실패 시 회원·증명 사용 롤백. 프런트 `tests/signup-email.test.mjs`. 실행 결과는 PR에 기록한다.

브라우저 + Mailpit 확인(2026-11-06, 작업 브랜치 `88a2d6c`, 로컬 개발 DB, 백엔드 `bootRun`에 이번 실행만 `MAIL_ENABLED=true`, 프런트 `npm run dev`, Mailpit v1.33.0 Windows 실행 파일, 에이전트가 Claude 데스크톱 앱 내장 브라우저로 조작, 새 확인 계정 `signup-1106a@example.com`):

- `/signup`에서 이메일만 입력 → 같은 안내와 60초 재요청 대기 표시 → Mailpit에 메일 1통(보낸 사람 `LancerPick <no-reply@lancerpick.local>`, 한글 제목 `[랜서픽] 회원가입 이메일 인증 안내`, 링크 `http://localhost:5173/signup/verify#token=…` 43자).
- 링크를 열면 주소창의 `#token=`이 바로 지워짐 → "인증하기" → 가입 정보 화면(이메일 칸은 인증한 주소, 읽기 전용) → 가입 201 → `/login`에 완료 안내 → 로그인 200, `/api/me` 표시 이름 일치, `/api/me/wallet` 잔액 1,000,000(테스트).
- 로그인 상태로 인증 링크를 열면 "먼저 로그아웃" 안내. 로그아웃 뒤 같은 링크 재사용 → "링크를 쓸 수 없어요"와 "인증 메일 다시 받기".
- 이미 가입된 같은 이메일로 다시 요청 → 화면 안내는 처음과 같고 메일도 1통 더 도착 → 그 링크로 인증한 뒤에만 "이미 가입된 이메일이에요"(로그인·비밀번호 재설정 안내).
- 서버 로그: 일부러 만든 실패 2건(잘못 입력한 로그인, 재사용 링크)의 경고만 있고 이메일·토큰 없음.

확인하지 않은 것: `account_challenges`의 `delivery_status`·`verified_at`·`consumed_at`은 DB를 직접 조회하지 않았다(자동 테스트 결과를 따른다). 15분·30분 만료, 동시 요청, 1시간 5회 제한은 자동 테스트로만 확인했다. 실기기 모바일·다른 브라우저·외부 SMTP는 범위 밖이다.
