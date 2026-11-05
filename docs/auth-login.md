# 로그인·로그아웃·내 정보 API

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
