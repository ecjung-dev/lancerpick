# 도메인별 기능 명세

### 상품 등록·서비스 조회 조건 화면 개편 (2026-11-11 로컬 후속)

상품 등록/수정 공통 폼을 기본 정보·서비스 안내·가격 및 작업 기간의 세 영역으로 나눈다. 상품명은 한 줄 입력, 설명·작업 범위·준비사항은 예시·안내·서버와 같은 정규화 기준 글자 수를 제공한다. 작성 중 요약에 카테고리·상품명·가격·기간을 표시한다. 기존 검증·저장 요청·버전 충돌 비교/입력 유지·계정 재확인·불명확 결과 잠금을 유지한다.

서비스 목록의 검색어·최저/최고가·최대 작업 기간·정렬을 하나의 필터 영역으로 모은다. 단위와 허용 범위를 안내하고 적용·초기화를 구분한다. 적용은 카테고리를 보존하고 첫 페이지로 돌아가며 URL로 검색어/조건을 복원한다. 초기화는 URL 조건과 아직 적용하지 않은 입력·오류를 함께 비운다. 홈의 공통 필터에도 같은 스타일을 사용하고 좁은 화면에서는 입력과 버튼을 세로로 배치한다. API·서버 검증·상품 카드의 상세 진입 방식은 유지한다.

### 구매·판매 내역 화면 개편 (2026-11-11 로컬 후속)

구매·판매 목록의 검색어·주문일·빠른 기간·상태·정렬을 하나의 필터 영역에 배치한다. 기간은 선택 상태를 표시하는 작은 버튼, 조회는 기본 버튼, 초기화는 보조 버튼으로 구분한다. 각 거래는 상태·주문번호, 상품명·상대방, 주문 금액, 주문일·현재 납기·상세 진입으로 나눈 카드로 표시한다. 좁은 화면에서는 금액을 별도 행으로 배치한다. 목록 전용 CSS를 사용하여 주문 상세·처리 폼에는 영향을 주지 않는다. 기존 API·조회 조건 URL·페이지 초기화·상세 링크의 조건 보존을 유지한다.

### 메시지·알림 사이드바 (2026-11-11 로컬 후속)

헤더 메시지·알림은 오른쪽 전체 높이 사이드바를 연다. 목록에서 주문 대화를 선택하며 좁은 화면에서는 선택 대화만 표시한다. 계정별 임시 입력은 방 전환·목록 복귀·닫기 후에도 유지하고 계정 캐시 초기화 때 제거한다. 미확정 발송 UUID와 재전송 흐름을 유지한다. 새로고침은 접근성 이름·툴팁이 있는 아이콘이다. Enter는 전송, Alt+Enter는 줄바꿈이며 IME 조합·keyCode 229는 전송하지 않는다.

알림 배지는 기존 서버 미확인 수를 사용한다. 채팅 서버에는 읽음 커서·미독 수가 없어 메시지 점은 현재 페이지 세션 시작 이후 감지한 상대방의 새 메시지만 표시한다. 로그인 중 최신 대화 20개를 30초 간격으로 조회하고, 보이는 대화창에서 성공적으로 조회한 메시지를 확인하면 표시를 해제한다. 과거 미독 수·전체 대화 미독 수·다른 기기 읽음 동기화는 제공하지 않는다. 페이지 새로고침·계정 캐시 초기화 시 기준을 다시 잡고 기기 시각에 영향을 받는다. 로컬 구현이며 원격 반영·실서버 검증과 구분한다.

[목차](README.md) · [API·인증 설계](architecture.md) · [진행 상태](project-progress.md) · [변경·검증 이력](change-history.md)

<a id="policy-versions"></a>
## 정책 버전과 적용 우선순위

기능 규칙·예외·명령 계약을 도메인별로 통합했다. 날짜별 구현/검증 결과는 [변경 이력](change-history.md), 미완료·예정 작업은 [진행 현황](project-progress.md)에서 관리한다. 현재 규칙을 주제별로 합치고 날짜별 미구현/후속 표기는 이력으로 분리했다. 완료 여부·남은 일은 [현재 상태](project-progress.md#current-status)를 따른다.

| 구분 | 보존 정책 |
| --- | --- |
| LEGACY_V1(MVP) | 상품 1원~100만원·소요일 1~30일, 주문당 상품/수량 1개. 가입 테스트 적립금 100만원 1회. 구매 적립금과 판매 수익은 분리·전환하지 않음. 구매자 무응답은 검수 대기 유지 |
| CONTRACT_V2 | 과거 선금/잔금·부분 반환/보상 설계 및 기존 계약 호환. 이전 서명/PDF·주문을 소급 변경하지 않음. 신규 권장 정책으로 적용하지 않음 |
| CONTRACT_V3 | 총액 10,000~1,000,000원, 양측 동일 계약/PDF 확인·서명 후 전액 결제·주문별 보관. 확정 시 10% 원 미만 버림 수수료·나머지를 판매자 구매용 적립금으로 지급 |
| V3 취소·검수 | 결제 후 합의 승인 또는 운영 문의 관리자 승인 시 전액 반환·수익/수수료 0. 납품 후 무응답 48시간 → 판매자 시작 7일 타이머 → 자동 확정, 사전 안내·응답/제재/관리자 해제 예외 적용 |
| 공통 유지 | 주문 스냅샷 불변, 자기 상품 주문 금지, 원래 범위 수정 1회, 소요일은 24시간 단위, 금액·권한·재전송/경합은 서버 검증 |
| 회원 제한 예외 | 일반 제한은 새 활동만 차단·기존 거래 허용. 보안 잠금은 자동 취소/환불하지 않으며 구매자 자동 확정 타이머 시작 금지·중단·자동 재개 없음 |

서명·조건 확인을 법적 효력이 보장된 전자서명으로 표현하지 않는다. 실제 현금 이체·출금·PG 에스크로·외부 파일 원본 보존을 제공한다고 표현하지 않는다. V3는 #152로 기본 활성화됐으며 기존 V1/V2 주문은 생성 당시 정책을 유지한다.

## 주제별 목차

- [회원·인증·제재](#domain-members) — [회원가입·로그인·세션](#members-session) · [내 정보·닉네임·비밀번호 기준일](#members-profile) · [비밀번호 변경·재설정·이메일 인증](#members-recovery) · [일반 제한·보안 잠금·관리자 회원](#members-sanctions)
- [상품·판매자·후기](#domain-products) — [판매자 프로필·공개 소개](#products-seller) · [카테고리·검색·상세](#products-catalog) · [상품 등록·수정·판매 상태·운영](#products-manage) · [후기·관심 상품](#products-reviews)
- [주문·계약·납품·취소](#domain-orders) — [신청·스냅샷·수락](#orders-create) · [구매·판매 내역·상세](#orders-query) · [계약·서명·PDF](#orders-contract) · [납품·수정·재납품](#orders-delivery) · [취소 요청·운영 문의](#orders-cancel)
- [지갑·결제·환불·정산](#domain-finance) — [지갑·초기 지급·원장](#finance-wallet) · [Toss 테스트 충전·재확인](#finance-topup) · [정책별 결제·보관·환불](#finance-funds) · [구매 확정·판매 수익](#finance-settlement) · [충전 복구 Batch·관리자 조회](#finance-batch)
- [채팅·알림·자동 확정](#domain-communication) — [저장·실시간 채팅·미확정 전송](#communication-chat) · [아웃박스·알림·읽음](#communication-notifications) · [기한 통지·검수·자동 확정](#communication-timer)
- [화면과 공통 정책](#domain-screens) — [화면 흐름·마이페이지·조회 조건](#screens-navigation) · [MVP·V2·V3 정책 경계](#screens-versions)

<a id="domain-members"></a>
## 회원·인증·제재

<a id="members-session"></a>
### 회원가입·로그인·세션

가입은 이메일 인증·회원 저장·지갑 생성·최초 테스트 적립금 100만원 지급을 원자적으로 연결한다. 로그인은 이메일 정규화와 원문 비밀번호 비교, 세션 ID/CSRF 교체를 수행한다. 로그아웃·비밀번호 변경·보안 잠금은 세션/인증 버전과 계정별 캐시에 영향을 준다. 현재 principal은 LoginMember(memberId, role, authVersion)이며 2인자 생성자는 테스트·기존 호출 호환용이다.

#### API·입력·오류

##### 계약

| 경로 | 입력·권한 | 결과 |
| --- | --- | --- |
| POST /api/auth/login | 비로그인 + CSRF; email, password | 200 Member, 세션 발급 |
| POST /api/auth/logout | 회원 + CSRF; 본문 없음 | 204, 세션 무효화 |
| GET /api/me | 회원 | 200 Member |
| PATCH /api/me | 회원 + CSRF; displayName | 200 Member |

#### 처리 순서·동시성·재전송

##### 로그인 처리 순서

로그아웃은 `SecurityContextLogoutHandler`로 세션과 인증 정보를 지운다. PATCH /me는 displayName을 정리한 뒤 1~30자를 허용하고, 값이 실제로 바뀐 경우에만 updated_at을 UTC 마이크로초로 갱신한다. hasSellerProfile은 `SellerProfile` 엔티티 개수를 JPQL로 센다(쓰기 없음).

#### 화면·캐시·계정 경계

##### 프런트 연결

<a id="b-216-0"></a>
| 파일 | 역할 |
| --- | --- |
| `features/auth/api.ts` | `getCurrentMember`(401이면 null), `login`, `logout`, `signup`. 공통 `apiRequest`를 사용해 CSRF·오류 형식을 통일한다 |
| `features/auth/session.ts` | `resetAccountState`: 로그인·로그아웃 직후 `clearCsrfToken()` → 진행 중인 `['auth', 'me']` 조회 취소(늦은 응답이 새 상태를 덮어쓰지 않게) → `clearWalletCache()` → `['auth']` 외 조회 취소·삭제 → 새 로그인 상태 반영. `safeNextPath`: 같은 사이트 경로만 허용 |
| `features/auth/queries.ts` | `useCurrentMember` (`['auth', 'me']`, 항상 서버에 다시 확인) |
| `features/auth/validation.ts` | 서버와 같은 이메일·비밀번호·표시 이름 규칙. 로그인은 빈 값만 검사하고 비밀번호는 절대 정리하지 않는다 |
| `features/auth/pages/LoginPage.tsx`·`SignupPage.tsx` | 로그인 성공 시 `?next=` 경로로 이동, 가입 성공 시 자동 로그인 없이 `/login`으로 이동(이메일 채움). 409 중복 이메일·400 fieldErrors를 입력칸별로 표시 |
| `features/auth/components/MemberNav.tsx` | 헤더 회원 메뉴. 로그인 시 표시 이름·로그아웃, 아니면 로그인·회원가입. 로그아웃 401은 이미 끝난 세션으로 보고 똑같이 정리한다 |

요청 버튼은 처리 중에 비활성화하고, 로그인·가입·로그아웃은 자동으로 다시 보내지 않는다(`retry: false`). 통신 오류·5xx처럼 서버 처리 여부가 불명확하면 실패로 단정하지 않는다. 로그인·로그아웃은 `recheckSession()`으로 CSRF 토큰을 버리고 `/api/me`를 다시 확인하며, 가입은 로그인부터 시도하도록 안내한다(개발 규칙 4절). 화면 문구가 아니라 오류 `code`로 분기한다. 프런트 검증은 `tests/auth.test.mjs` 10건(불명확한 실패 분류·재확인 시 명령 재전송 없음, `?next=`의 탭·줄바꿈·역슬래시 차단, 로그인·로그아웃 전에 보낸 늦은 /me 응답이 새 상태를 덮어쓰지 않음, 401→null, CSRF 1회 전송·재시도 없음, 로그인 후 새 CSRF·이전 계정 캐시 삭제, 서버와 같은 입력 규칙, 오픈 리다이렉트 차단)이다.

<a id="members-profile"></a>
### 내 정보·닉네임·비밀번호 기준일

내 정보 응답은 id(문자열), email, displayName, role, hasSellerProfile, displayNameChangeAvailableAt, emailVerified(2026-11-10 추가, 인증 시각은 응답하지 않음)다. 닉네임 첫 변경은 허용하며 변경 후 한국 달력 6개월 제한을 적용한다. null 가능일은 첫 변경 가능을 뜻한다. 비밀번호 권고 기준일부터 6개월마다 로그인한 회원에게 변경 권고 알림을 보낸다([권고 알림](#password-recommendation)).

#### 실행·환경

##### 프로필 저장·자격 증명 경합 보완

<a id="b-212-1"></a>
`ProfileCredentialRaceIntegrationTests`는 비밀번호 변경과 재설정 각각에 대해 실제 독립 트랜잭션의 순서를 제어한다. 수정 전 두 경우 모두 인증 버전이 0으로 돌아가 실패했고, 수정 후에는 새 비밀번호·인증 버전 1·새 표시 이름과 재설정 토큰 소비를 보존한다. SQL 조회 지점은 스파이와 실제 JPA 조회·행 잠금으로 제어하며 브라우저 조작 검증은 아니다. 최종 선택 검사 결과는 [현재 현황](project-progress.md#current-status)을 따른다.

#### 화면·캐시·계정 경계

##### 내 정보 화면

<a id="b-217-0"></a>
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

#### 업무 규칙·연결

##### V11 데이터와 호환

기존 마이그레이션은 수정하지 않고 `V11__add_member_change_policy_dates.sql`로 members에 세 컬럼을 추가한다. 테이블 추가는 없다.

| 컬럼 | 기존 회원 | 신규 가입 / 후속 갱신 |
| --- | --- | --- |
| display_name_changed_at | NULL: 첫 변경 허용 | 실제 닉네임 변경 성공 시각 |
| password_changed_at | NULL: 과거 시각 알 수 없음 | 가입·비밀번호 변경/재설정 성공 시각 |
| password_recommendation_base_at | V11 도입 UTC 시각 | 가입·비밀번호 변경/재설정 성공 시각 |

#### API·입력·오류

##### 회원 API와 화면

내 정보 화면은 다음 가능일을 한국 시간으로 보여 주고 제한 중 닉네임 입력·저장만 막는다. 비밀번호 폼은 계속 사용할 수 있다. 열린 화면에서도 기한 도달 시 버튼을 다시 활성화한다. 클라이언트 시계는 안내용이고 실제 요청은 서버가 최종 검사한다. 409를 받으면 본인 정보를 재조회한다.

##### 정은찬·김창재 연결 계약

`PasswordRecommendationQuery.find(long memberId)`는 잠금 없이 `Optional<Basis>`를 반환한다. 존재하지 않는 회원은 empty, 양수가 아닌 ID는 내부 호출 오류다. JPA 스칼라 projection만 조회하며 인증정보·이메일은 읽지 않는다.

| Basis | 의미 |
| --- | --- |
| memberId | 대상 회원 |
| passwordChangedAt | 실제 마지막 기록 시각. 기존 회원은 null 가능 |
| basisAt | 중복 원천에 사용할 권고 기준 시각. null 아님 |
| firstRecommendedAt | basisAt의 한국 달력 6개월 후, UTC |

이 조회 서비스는 재확인·알림 저장을 원자적으로 묶어 주지 않는다. 발송 쪽(정은찬)이 알림 저장 트랜잭션에서 회원 잠금 뒤 기준을 다시 읽는다. 발송 규칙은 아래와 같다.

<a id="password-recommendation"></a>
##### 비밀번호 변경 권고 발송(U6)

- 스케줄링·스캔 쿼리·인덱스 없음: **로그인한 회원에게만** 권고한다. 권고일 뿐이라 로그인하지 않는 회원에게 알림이 쌓이지 않아도 되고, 다음 로그인 때 현재 회차가 생긴다.
- 반복 회차: 기준일부터 한국 달력 6개월 × n이 지날 때마다 1건(`MemberChangePolicy.elapsedSixMonthPeriods`). n번째 경계는 앞 경계에 6개월을 더하지 않고 기준에서 계산한다(8/31 기준 → 2/28, 8/31, 2/28 …). 오래 로그인하지 않았다가 들어오면 지난 회차를 채우지 않고 현재 회차 1건만 만든다.
- 최신 기준 재확인: 알림 저장 트랜잭션에서 비밀번호 변경·재설정과 같은 회원 배타 잠금(`findByIdForUpdate`) 뒤 기준일을 다시 읽는다. 비밀번호를 바꾸면 기준일이 바뀌어 새로 센다.
- 중복 원천: (회원, 기준일, 회차)의 이름 기반 이벤트 ID + 아웃박스 `uk_outbox_event`.
- 로그인 경계: 로그인 트랜잭션(읽기 전용·세션 저장)에 넣지 않는다. 로그인 성공 뒤 별도 짧은 트랜잭션이며 실패해도 로그인은 성공이다. 발행·전달 세부는 [아웃박스·알림](#communication-notifications)의 회원 알림 절을 따른다.

김창재의 기존 변경/재설정 경로는 `Member.changePassword`를 재사용하므로 기록을 한곳에서 갱신한다. 세션·요청 제한·메일·토큰 소비와 기존 롤백 경계를 바꾸지 않았다. 기존 회원 초기 기준에 관한 미정 문구보다 위 사용자 결정을 우선한다.

<a id="members-recovery"></a>
### 비밀번호 변경·재설정·이메일 인증

로그인 상태 변경·비로그인 재설정·이메일 인증 가입을 구분한다. 자격 증명 변경은 인증 버전을 증가시켜 기존 HTTP/소켓 인증을 폐기한다. 복구 요청은 가입 여부를 드러내지 않는 응답·요청 제한·토큰 수명/일회성을 적용한다. 메일은 커밋 뒤 메모리 큐로 보내며 SMTP 기본 비활성을 유지한다. 아래 로컬 후속은 오래된 PENDING의 결과 상태만 정리하고 자동 재발송은 하지 않는다.

<a id="mail-delivery-reconciliation"></a>
#### 오래된 메일 대기 결과 정리 — 2026-11-10, main 반영(#166)

사용자 합의 범위는 ‘결과 미확인 상태 정리 + 기존 사용자 재요청’이다. `feature/mail-delivery-reconciliation`(황준범, main `a7236ef` 기준)에서 발급 시각이 기본 30분 이상 지난 PENDING을 UNKNOWN / MAIL_RESULT_UNCONFIRMED로 바꾼다. 미발송·메일 유실을 확정하는 상태가 아니다. 메모리 큐의 본문을 복원하거나 SMTP를 재호출하지 않으며, 수신자·토큰 원문·메일 본문을 새로 보관하지 않는다.

- 기존 SENT/FAILED는 확정 결과로 유지한다. 늦게 도착한 실제 발송 결과는 PENDING/UNKNOWN에서 SENT 또는 FAILED로 확정할 수 있고, 확정 결과를 다시 덮어쓰지 않는다.
- 사용·인증·만료된 요청도 발송 결과 메타데이터는 정리할 수 있지만 토큰 해시·만료/사용/인증 시각·인증 버전을 바꾸지 않는다. 만료 링크를 살리거나 사용한 증명을 다시 쓰게 하지 않는다. 이 작업의 ‘복구’는 발송 결과 장기 미확인 기록 정리이며 메일 재전송/토큰 복원이 아니다.
- 기본 1분마다 전용 단일 스레드가 가장 오래된 대기 기록 최대 50건을 짧은 READ_COMMITTED 트랜잭션에서 정리한다. 여러 실행이 같은 후보를 읽거나 실제 결과와 경합해도 조건부 UPDATE로 현재 PENDING만 변경한다. 실제 결과 기록이 최종 상태로 남는다.
- 회원가입 인증·비밀번호 재설정·기존 회원 이메일 인증의 account_challenges 기록이 대상이다. DB 행이 없는 아이디 안내(untracked)는 제외한다. 사용자는 기존 화면에서 요청 간격/횟수 제한 안에 새 메일을 요청하며 기존 유효 링크 유지 정책도 바꾸지 않는다.
- V12는 기존 CHECK에 UNKNOWN을 추가하고 (delivery_status,issued_at,id) 인덱스를 추가한다. 테이블·컬럼·공개 API·화면·의존성 추가는 없다. 이전 마이그레이션은 수정하지 않는다.

운영 설정은 [메일 인프라](architecture.md#mail), 검사 결과·한계는 [검증 이력](change-history.md#mail-delivery-reconciliation)을 따른다. main 반영·원격 CI는 별도다.

#### 업무 규칙·연결

##### 비밀번호 변경·세션 폐기

<a id="b-219-1"></a>
고도화 4인 설계 4절 3·4번(로그인 상태 비밀번호 변경, 기존 세션 무효화)과 3인 설계 6.2·9절을 구현했다. DB 변경은 없다. V5에서 추가된 `members.auth_version`(기본 0)을 사용한다. 새 라이브러리도 없다.

##### 이메일 인증 가입

<a id="b-234-0"></a>
4인 설계 §4 1번과 3인 설계 §5.1·7·8절을 구현했다. DB·라이브러리 변경은 없다(V5 `account_challenges`의 `SIGNUP_EMAIL`, `members.email_verified_at` 사용). 메일은 C3 발송기(#90)를 그대로 쓴다. 기존 회원의 이메일 인증 전환(§5.2, `EXISTING_EMAIL`)과 재설정 성공 시 인증 시각 기록 여부는 다음 작업으로 나눴다.

##### 이메일 인증 가입

<a id="b-236-0"></a>
요청(`SignupEmailService.requestVerification`): 이메일 정규화(`EmailRules`) → 제한 행 준비(별도 커밋, 교착 시 재시도) → 업무 트랜잭션: 제한 확인(scope `SIGNUP_EMAIL_INTERVAL`·`SIGNUP_EMAIL_HOUR`, 60초 간격·1시간 5회, 재설정과 따로 센다) → `SIGNUP_EMAIL` 행 저장(링크 토큰 SHA-256 해시, 15분, `member_id`·`credential_version` NULL) → 커밋 뒤 `AccountMailDispatcher`로 메일(`SignupEmailMailContent`). 비밀번호는 받지도 저장하지도 않고, 회원·지갑·적립금도 만들지 않는다.

인증(`verify`, 한 트랜잭션): 링크 토큰 해시로 행 `FOR UPDATE` → 목적·미인증·미사용·만료 재확인 → **같은 행의 `token_hash`를 새 증명의 해시로 바꾸고 `verified_at` 기록, `expires_at` = 인증 시각 + 30분**(입력할 시간 확보, 2026-11-06 결정). 링크 토큰은 그 순간 무효다. 같은 링크 동시 인증은 잠금에서 한 줄로 서고 늦은 쪽은 400이다.

가입(`MemberService.signup`, 한 트랜잭션): 이메일·표시 이름 정규화 → 증명 해시로 행 `FOR UPDATE` → 목적·인증됨·미사용·미만료·이메일 일치 재확인 → 중복 이메일 409 → `Member.createVerified`(email_verified_at = 가입 시각) 저장 → 증명 `consumed_at` → 지갑·최초 지급. 어느 단계든 실패하면 증명 사용까지 롤백돼 같은 증명으로 다시 시도할 수 있다. 같은 증명 동시 가입은 한 건만 성공(나머지 400), 같은 이메일·다른 증명 동시 가입은 이메일 UNIQUE로 한 건만 성공(나머지 409, 그쪽 증명 사용은 롤백). 잠금 순서: 증명 행 → 새 회원 행 → 지갑.

목적은 엄격히 나뉜다: 가입 증명은 비밀번호 재설정 토큰으로, 재설정 토큰은 가입 인증·증명으로 쓸 수 없다(각 확인 메서드가 `purpose`를 먼저 본다).

공통 코드 정리: 토큰 유틸 이름을 `OneTimeTokens`로 바꿨고(재설정·가입 공용), 제한 행 생성의 교착 재시도를 `AuthRequestLimiter.prepare` 안으로 옮겼으며, 메일 링크 주소 검사를 `FrontendLinks`로 분리했다(재설정·가입 메일 공용).

#### API·입력·오류

##### 비밀번호 변경·세션 폐기

<a id="b-220-0"></a>
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

##### 비밀번호 재설정·요청 제한

<a id="b-226-0"></a>
| 경로 | 입력·권한 | 결과 |
| --- | --- | --- |
| POST /api/auth/password-reset-requests | 비로그인 가능 + CSRF; email | 202 `{message}` `no-store`. 가입 여부·제한 초과·발송 실패와 무관하게 같은 응답. 이메일 형식 오류만 400 VALIDATION_FAILED |
| POST /api/auth/password-resets | 비로그인 가능 + CSRF; token, newPassword | 204 `no-store`. 토큰 위조·만료·사용됨·발급 후 비밀번호 변경은 모두 400 PASSWORD_RESET_TOKEN_INVALID. 새 비밀번호 규칙 위반은 400 VALIDATION_FAILED(field=newPassword, 토큰은 그대로 사용 가능) |

메일 링크: `lancerpick.frontend.base-url`(기본 `http://localhost:5173`, 환경변수 `FRONTEND_BASE_URL`) + `/password-reset#token=…`. 요청 Host·returnUrl을 쓰지 않는다. 링크 GET은 화면만 보여주고, 토큰 사용은 POST에서만 한다.

##### 아이디 안내

<a id="account-guide"></a>
랜서픽은 별도 아이디 없이 가입한 이메일로 로그인한다. 아이디 안내는 **입력한 주소가 가입한 이메일이면 그 주소로 "이 주소가 아이디"라는 안내를 보내는 것**까지다. 잊어버린 이메일을 찾아 주지 않는다(표시 이름으로 이메일 찾기·마스킹 목록 없음).

| 경로 | 입력·권한 | 결과 |
| --- | --- | --- |
| POST /api/auth/account-guide-requests | 비로그인 가능 + CSRF; email | 202 `{message}` `no-store`. 가입 여부·제한 초과·발송 실패와 무관하게 같은 응답. 이메일 형식 오류만 400 VALIDATION_FAILED |

- 요청 제한: 이메일별 60초 간격·1시간 5회. 비밀번호 재설정 제한과 따로 센다.
- 메일: 제목 "[랜서픽] 아이디 안내", 본문에 로그인·비밀번호 재설정 화면 링크(서버 설정 주소 + `/login`, `/password-reset/request`). 토큰 없음, 비밀번호·표시 이름 등 다른 회원 정보 없음. `AccountMail.untracked`라 `account_challenges` 행과 발송 결과 기록이 없다([메일 경계](architecture.md#mail)).
- 화면: `/account-guide`. 비밀번호 재설정 요청과 같은 공통 폼(같은 안내·1회 전송·60초 다시 요청 잠금). 로그인 화면의 "아이디(이메일)가 기억나지 않나요?" 링크로 들어간다.

##### 비밀번호 재설정·요청 제한

<a id="b-229-0"></a>
- `auth/mail/AccountMailSender.enqueue(AccountMail)`: C3가 Bean으로 구현한다. 요청 스레드를 막지 않고 크기가 정해진 비동기 큐에 넣은 뒤 바로 돌아온다(넣었으면 true, 가득 찼으면 false). SMTP를 요청 스레드에서 하면 가입된 이메일만 느려져 가입 여부가 드러난다.
- 발송이 끝나면 `AccountMailDeliveryRecorder.markSent(challengeId)` / `markFailed(challengeId, 안전한 코드)`를 호출한다. PENDING인 행만 바뀐다.
- 구현: C3 `auth/mail/SmtpAccountMailSender`(`feature/mail-infrastructure`, [메일 인프라](architecture.md#mail)). `lancerpick.mail.enabled=true`(환경변수 `MAIL_ENABLED`)일 때만 등록된다.
- 구현 Bean이 없으면(기본 꺼짐) 발송하지 않고 `FAILED`·`MAIL_NOT_CONFIGURED`로 기록한다. 큐 가득 참은 `MAIL_QUEUE_FULL`, enqueue 예외는 `MAIL_ENQUEUE_FAILED`다. 앱은 메일 서버 없이도 지금처럼 켜진다.
- `AccountMail.body`에는 토큰이 있다. 로그·DB·거래 아웃박스에 평문으로 남기지 않는다. `toString`은 수신자·본문을 가린다.

##### 이메일 인증 가입

<a id="b-235-0"></a>
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
- **우회 경로 없음**: 증명 없는 구형 가입 요청은 400이다(3인 설계 §5.1). 메일이 꺼진 기본 상태(`MAIL_ENABLED=false`)에서는 화면에서 새 계정을 만들 수 없다. 로컬에서 새 계정이 필요하면 Mailpit을 켠다([메일 인프라](architecture.md#b-590-0), Docker가 없으면 Mailpit 공식 실행 파일 사용). 기존 계정과 시드 SQL 계정은 그대로 로그인된다.

#### 화면·캐시·계정 경계

##### 비밀번호 변경·세션 폐기

<a id="b-223-0"></a>
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

##### 비밀번호 재설정·요청 제한

<a id="b-230-0"></a>
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

##### 이메일 인증 가입

<a id="b-237-0"></a>
| 파일 | 역할 |
| --- | --- |
| `pages/SignupPage.tsx` | `/signup`. 이메일만 입력(비밀번호 칸 없음), 같은 안내, 60초 재요청 잠금 |
| `pages/SignupVerifyPage.tsx` | `/signup/verify#token=…`. "인증하기" → 가입 정보 입력(이메일은 읽기 전용) → 가입. 이미 가입이면 로그인·재설정 안내, 링크 오류·인증 만료는 인증 메일 다시 받기 안내 |
| `components/EmailLinkRequestForm.tsx` | 이메일 링크 메일 요청 공통 폼(가입 인증·재설정 요청이 함께 사용) |
| `linkToken.ts`, `useLinkToken.ts` | `#token=` 읽기(43자만)와 주소창에서 지우기(가입 인증·재설정 화면 공용) |
| `signupEmail.ts` | `verifyFailureKind`, `signupFailureKind`(code 기준 분류) |
| `api.ts` | `requestSignupEmail`, `verifySignupEmail`, `signup`(증명 포함) |

자동 재전송 없음, 두 번 클릭 방지, 증명·토큰은 메모리에만 둔다. 인증 결과가 불명확(통신 오류·5xx)하면 같은 링크를 다시 보내지 않고 새 인증 메일을 안내하고, 가입 결과가 불명확하면 다시 보내지 않고 로그인부터 시도하도록 안내한다.

#### 실행·환경

##### 비밀번호 재설정·요청 제한

<a id="b-225-0"></a>
4인 설계 §4 2번(비밀번호 재설정)과 3인 설계 6.1·7·8절을 구현했다. DB·라이브러리 변경은 없다(V5 `account_challenges`·`auth_request_limits` 사용, 새 엔티티 2개). **실제 메일 발송은 C3(메일 인프라, 정은찬·황준범) 연결 후**이며(C3 발송기는 [메일 인프라](architecture.md#mail)에서 추가, #90으로 main 반영돼 `MAIL_ENABLED=true`로 켬), 이 작업은 4인 설계 §4의 김창재 범위(메일 내용·토큰·회원 API·요청 제한)만 한다.

##### 비밀번호 재설정·요청 제한

<a id="b-228-0"></a>
`auth_request_limits`에 이메일(HMAC)마다 두 행을 쓴다. 가입하지 않은 이메일에도 같은 제한을 적용한다.

| scope | window_start | 의미 |
| --- | --- | --- |
| PASSWORD_RESET_EMAIL_INTERVAL | 고정값 2000-01-01 | 이메일당 한 행. `expires_at` = 마지막 허용 시각 + 60초. 이 시각 전이면 막는다(구간 경계와 무관하게 최소 60초) |
| PASSWORD_RESET_EMAIL_HOUR | 지금을 시 단위로 자른 값 | 그 1시간 구간에 허용한 수. 5 이상이면 막는다 |

막히면 발급하지 않고 같은 202를 돌려준다(3인 설계 6.1 2번). 같은 이메일의 동시 요청은 간격 행 잠금에서 한 줄로 선다. 제한 키는 `lancerpick.auth.request-limit-key`(환경변수 `AUTH_REQUEST_LIMIT_KEY`)로 각자 설정하며 저장소에 넣지 않는다. 비어 있으면 서버 시작 시 임의 키를 만든다(재시작하면 이전 제한 행과 이어지지 않는 개발용 한계).

#### 처리 순서·동시성·재전송

##### 비밀번호 재설정·요청 제한

<a id="b-227-1"></a>
새 요청이 와도 기존 유효 링크는 지우지 않는다(남이 반복 요청해 복구를 방해하지 못하게). 대신 요청 수를 제한한다.

#### 범위·후속 조건

##### 이메일 인증 가입

<a id="b-239-0"></a>
- 기존 회원 이메일 인증 전환(§5.2)과 재설정 성공 시 `email_verified_at` 기록은 하지 않았다(다음 작업). 기존 회원의 `email_verified_at`은 NULL 그대로다. 후속: 기존 회원 전환은 2026-11-10 아래 [기존 회원 이메일 인증](#existing-email-verification)으로 구현했다. 재설정 성공 시 기록은 여전히 하지 않는다.

##### 기존 회원 이메일 인증

<a id="existing-email-verification"></a>
이메일 인증 없이 가입한 기존 회원이 로그인한 뒤 자기 주소를 인증한다(3인 설계 §5.2). 검증 증거 없이 `email_verified_at`을 일괄로 채우지 않으며, 인증하지 않아도 로그인·기존 거래는 그대로다. 다른 계정과 합치거나 회원·지갑을 새로 만들지 않는다.

| 경로 | 입력·권한 | 결과 |
| --- | --- | --- |
| POST /api/me/email-verification-requests | 회원 + CSRF; 본문 없음 | 202 `{message}` `no-store`. 로그인한 회원 본인 주소로 15분 일회용 링크 메일. 이미 인증됨 409 EMAIL_ALREADY_VERIFIED. 요청 제한 초과도 같은 202(메일 없음) |
| POST /api/me/email-verifications | 회원 + CSRF; token | 204 `no-store`. 토큰 위조·만료·사용됨·다른 계정의 토큰·발급 후 비밀번호 변경(auth_version 불일치)·주소 불일치는 모두 400 EMAIL_VERIFICATION_INVALID. 세션·비밀번호·인증 버전은 그대로 |

- 저장: `account_challenges` 목적 `EXISTING_EMAIL`(member_id·발급 당시 auth_version 필수, V5 CHECK 그대로), 토큰은 해시만 저장. 새 DDL 없음.
- 처리: 요청은 제한 행 준비(별도 커밋) → 짧은 트랜잭션에서 제한 확인·회원 잠금·재확인(그사이 인증됐으면 발급 안 함)·토큰 저장 → 커밋 뒤 발송. 확인은 한 트랜잭션에서 로그인 회원 잠금 → 토큰 행 잠금(재설정과 같은 순서) → 재검사 → 인증 시각 기록·토큰 사용. 같은 링크 동시 사용은 한 번만 성공한다. 거절된 토큰은 소비하지 않는다.
- 요청 제한: 이메일별 60초·1시간 5회(`EXISTING_EMAIL_INTERVAL`·`EXISTING_EMAIL_HOUR`), 가입 인증·재설정·아이디 안내와 별도.
- 메일: 제목 "[랜서픽] 이메일 인증 안내", 링크 `{frontend}/mypage/email-verification#token=…`, 비밀번호 없음.
- 화면: `/mypage/profile`의 "이메일 인증" 칸(미인증이면 "인증 메일 받기", 60초 다시 요청 잠금, 이미 인증 409면 내 정보 재조회). 링크 화면 `/mypage/email-verification`은 토큰을 주소에서 지워 메모리에만 두고, "이메일 인증하기"를 눌러야 POST 1회. 로그아웃 상태면 로그인 후 링크를 다시 열도록 안내한다(토큰을 저장소에 남기지 않음). 두 명령 모두 계정 전환 보호(`captureAccountScope`)를 쓴다.
- 메일이 꺼진 기본 상태에서는 화면 가입이 불가하다(위 "우회 경로 없음").
- 사용하지 않은 가입 인증 행·증명의 정리 작업은 없다. 테스트 도우미가 넣은 행도 각 테스트가 지우지 않으면 테스트 DB에 남는다(회원 FK가 없어 다른 테스트에 영향 없음).
- 아이디 안내 메일 계약은 정했다(2026-11-06): challenge 행 없이 `AccountMail.untracked`로 보내며 발송 결과는 DB에 기록하지 않는다(작업표 C3 전달 4번). 화면·API는 2026-11-10 위 [아이디 안내](#account-guide)로 구현했다([기록](change-history.md#account-guide-20261110)).

<a id="members-sanctions"></a>
### 일반 제한·보안 잠금·관리자 회원

일반 제한은 로그인과 기존 거래를 허용하고 신규 주문·상품 등록·판매자 수락을 차단한다. 보안 잠금은 새 로그인/기존 인증과 구매자 자동 확정 타이머를 차단·중단하고, 잠긴 구매자의 수동 구매 확정(V1·V3)을 403 `MEMBER_LOCKED`로 막는다. 자동 취소/환불/정산 회수는 하지 않는다. 보안 잠금 판매자의 거래 진전 명령(계약 초안 작성·서명, 납품·재납품, 취소 요청 거절, 자동 구매 확정 타이머 시작)도 403 `MEMBER_LOCKED`로 막는다. 판매자 수락은 기존 U7 검사로 403 `MEMBER_RESTRICTED`다. 구매자 지갑으로만 전액 반환하는 취소·환불(판매자 승인·관리자 승인·V1 직접 취소)과 결제 전 거절·취소는 판매자·구매자 어느 쪽 잠금이어도 허용한다. 탈취자가 판매자의 대가를 포기시킬 수 있는 잔여 위험은 구매자 돈이 묶이지 않는 쪽을 택해 수용한다. 이미 시작된 타이머를 판매자 잠금으로 중단하지는 않는다. 관리자 회원 목록·검색·상세·적용/해제 API와 `/admin/members` 화면은 main에 반영됐다(#157). 관리자·본인 보호, 같은 요청 키·만료·교체·해제 경합을 서버에서 검증한다. [기존 구현 근거](change-history.md#local-admin-member-branch).

#### 업무 규칙·연결

##### 범위와 정책

##### 제공 범위

| 항목 | 이번 구현 | 후속 연결 |
| --- | --- | --- |
| B1 제재 조회 | `MemberSanctionQuery.restriction(long memberId)` | 주문·타이머 U7/U8 재사용, 황준범이 상품 등록 차단 연결(박소현 리뷰) |
| B2 제재 명령 | `MemberSanctionCommandService.apply` / `revoke`, 관리자 DB 권한·회원 잠금·이력·중복 방지 | 황준범: 회원 관리 API·화면 연결 완료, main 반영(#157) |
| 보안 잠금 인증 | 로그인 거절, 인증 버전 증가, 기존 HTTP/소켓 인증 재검사 경로 연결 | 실제 브라우저·소켓 폐기 확인은 별도 |
| 타이머 중단 전달 | 커밋 후 호출, 실패/연결 전 미처리 기록, 내부 재시도 | 정은찬: `SecurityLockTimerStopper` 구현체·타이머 재검사 |
| 만료 | 서버 UTC 시각으로 기간 종료 시 제한 없음으로 조회 | 별도 만료 Batch·행 삭제 불필요 |

#### API·입력·오류

##### API 계약

관리자 회원 API의 공통 prefix는 `/api/admin/members`다. 로그인한 ADMIN에 더해 현재 DB 역할을 검사하며 변경은 CSRF를 요구한다. 응답은 no-store다.

| 메서드·하위 경로 | 입력 | 성공 |
| --- | --- | --- |
| GET 목록 | q·restriction·page=0·size=20 | 200 PageResponse |
| GET `/{memberId}` | 회원 ID | 200 member·emailVerifiedAt·serverTime·canManage·activeSanctions |
| GET `/{memberId}/sanctions` | page·size | 200 전체 이력 PageResponse |
| POST `/{memberId}/sanctions` | kind·reason·endsAt·requestKey | 신규/같은 요청 모두 200 `{memberId,sanctionId}` |
| POST `/{memberId}/sanctions/{sanctionId}/revoke` | reason | 204 |

q는 100 코드 포인트 이하이며 숫자만이면 회원 ID 정확 일치, 나머지는 이메일·표시 이름의 문자 그대로 부분 일치다. restriction은 생략 또는 NONE/GENERAL_RESTRICTION/SECURITY_LOCK이며 두 제재 공존 시 대표 상태로 필터링한다. page≥0, size 1~100, 최신 가입·ID 내림차순이다. ID는 JSON 문자열이다. 적용 종료 시각은 오프셋 ISO 필수·UTC 변환 후 미래 시각, requestKey는 표준 UUID다. 사유는 정규화 후 1~1000 코드 포인트, 숫자/불리언의 문자열 강제 변환은 차단한다.

목록은 id/email/displayName/role/restriction/createdAt, 제재 이력은 id/kind/state/reason/appliedBy/startsAt/endsAt/revokedAt/revokedBy/revokeReason/timerStopDeliveredAt이다. 비밀번호·해시·인증 버전·요청 키는 조회하지 않는다. 해제 재시도는 최초 사유·시각을 보존하며 이전 제재 ID 해제가 교체된 새 제재를 해제하지 않는다. 본인/관리자 대상 403, 없는 회원·타 회원 제재 404, 잘못된 입력 400, 다른 조건의 같은 키 409다. 일반 권한·CSRF·상세 오류 코드는 아래 공통 계약과 기존 이력에 따른다.

##### B1: 조회 계약

```java
var restriction = memberSanctionQuery.restriction(memberId);
restriction.kind();                // NONE / GENERAL_RESTRICTION / SECURITY_LOCK
restriction.blocksNewActivity();   // 일반 제한 또는 보안 잠금
restriction.securityLocked();
restriction.adminId();             // 현재 제재 적용 관리자. NONE이면 null
restriction.latestSecurityLock();  // 가장 최근 보안 잠금 기록. 없으면 null
```

클래스는 `member/service/MemberSanctionQuery`다. 조회는 회원/주문 잠금을 추가하지 않는다. 호출자가 회원 존재·권한·잠금을 보장한다. 없는 양수 ID의 조회는 NONE이며 유효하지 않은 ID는 내부 호출 오류다. 변경 명령에서는 READ_COMMITTED 트랜잭션의 기존 회원 잠금 뒤 조회하고, 조회 결과만으로 별도 트랜잭션의 명령을 허용하지 않는다. 기존 외부 트랜잭션에 참여할 때 조회 서비스의 isolation 선언이 외부 격리 수준을 바꾸지는 않으므로 REPEATABLE_READ의 오래된 스냅샷을 명령 판단에 사용하지 않는다. 일반 이용 제한은 로그인·기존 거래를 막지 않는다.

현재 유효 기간은 `startsAt <= 서버 시각 < endsAt`, 철회 기록은 제외한다. 둘 다 유효하면 SECURITY_LOCK 우선이다. 같은 종류의 새 제재는 기존 활성 기록을 철회하고 교체하며 다른 종류는 보존한다. 보안 잠금 해제 후 일반 제한이 남아 있으면 일반 제한이 적용된다.

##### B2: 변경·트랜잭션 계약

```java
long sanctionId = memberSanctionCommandService.apply(
    adminId, memberId, SanctionKind.SECURITY_LOCK,
    reason, untilUtc, requestKey); // UUID, 재시도 시 동일 키·동일 인자
memberSanctionCommandService.revoke(adminId, memberId, sanctionId, reason);
```

- 관리자 ID는 Controller의 `CurrentMemberId.require(authentication)`에서 받는다. 요청 본문의 관리자 ID를 신뢰하지 않는다. 서비스도 DB ADMIN 권한을 확인한다. 자기 자신·관리자 계정 대상 제재는 거절한다.
- 사유는 정규화 후 1~1,000자, 종료 시각은 잠금 뒤 서버 시각보다 미래다. 모든 시각은 UTC LocalDateTime, 마이크로초 정밀도다.
- 호출자는 외부 트랜잭션 없이 호출한다(`NEVER`). 서비스의 짧은 READ_COMMITTED 트랜잭션에서 회원 ID 오름차순으로 관리자 공유 잠금·대상 회원 배타 잠금을 얻는다. 주문/지갑 잠금은 이 트랜잭션에서 얻지 않는다.
- 제재 이력 저장과 보안 잠금의 인증 버전 증가는 함께 성공·롤백한다. 버전은 만료·철회 때 감소시키지 않는다. 기존 세션은 복구되지 않고 새 로그인이 필요하다.
- `(member_id, request_key)` UNIQUE와 대상 회원 잠금으로 반복·동시 요청을 방어한다. 같은 키·동일 인자는 최초 ID를 반환하고 인증 버전·이벤트를 반복 생성하지 않는다. 다른 인자는 409 `SANCTION_REQUEST_CONFLICT`다.
- 보안 잠금 로그인은 올바른 비밀번호 검증 후 403 `MEMBER_LOCKED`. 기존 HTTP 세션은 기존 인증 버전 검사에서 401, 소켓은 기존 인증 재검사 경로를 사용한다. 이미 진행 중인 명령은 기존 회원 잠금 순서로 직렬화하며 완료 거래를 소급 취소하지 않는다. 인증 필터를 통과한 뒤 잠금이 커밋된 구매 확정 요청은 회원 공유 잠금 아래 재검사(`OrderCommandLocks.requireNotSecurityLocked`)로 403 `MEMBER_LOCKED`가 된다. 확정이 먼저 회원 잠금을 얻으면 제재 적용이 기다리고 확정은 유지된다.

#### 화면·캐시·계정 경계

##### 화면과 요청 복구

관리자 메뉴의 회원 관리에서 검색·상세·현재 제재·전체 이력·적용/해제를 제공한다. 한국 시간 입력을 UTC로 변환하며 실제 판단은 서버 시각을 따른다. 30초/포커스 재조회, 401/403 후 캐시 표시 차단, 처리 영향 확인 체크, 전송 중 반복 클릭 차단을 적용한다. 불명확한 결과는 계정별 메모리에 전체 원래 요청과 UUID를 보존해 경로 이동 후에도 같은 키로 재확인한다. 로그아웃/계정 변경 시 정리하며 브라우저 종료/새로고침을 넘는 영속 보관은 없다. 보안 잠금 타이머 전달 미완료는 경고하며 자동 재시도 로컬 후속은 아래 계약을 따른다.

#### 보안 잠금 전달 자동 재시도 — 2026-11-10, main 반영(#163)

`feature/security-lock-delivery-retry`(황준범, main `db92468` 기준). 커밋 직후 전달 실패나 프로세스 중단으로 `member_sanctions.timer_stop_delivered_at`이 NULL인 SECURITY_LOCK 기록을 전용 단일 스레드 스케줄러가 재전달한다. 신규 제재를 만들거나 인증 버전을 다시 올리지 않는다. 기존 `SecurityLockDelivery.retryPending(int limit)` 내부 호출 계약은 유지한다. API·화면·DDL·의존성 추가는 없다.

| 설정 | 환경변수 | 기본값 |
| --- | --- | --- |
| lancerpick.member.security-lock-retry.enabled | SECURITY_LOCK_RETRY_ENABLED | true. demo는 생성 안 함 |
| lancerpick.member.security-lock-retry.delay-ms | SECURITY_LOCK_RETRY_DELAY_MS | 30000. 최초 지연·실행 종료 후 다음 지연, 양수 밀리초 |
| lancerpick.member.security-lock-retry.batch-size | SECURITY_LOCK_RETRY_BATCH_SIZE | 50. 허용 1~100, 잘못된 값은 기동 실패 |

- 한 번에 최대 batch-size개 ID를 오름차순 조회한다. 실패해도 마지막 조회 ID 다음 페이지로 진행해 앞쪽 반복 실패가 뒤의 대기 건을 막지 않는다. 페이지가 비면 커서를 0으로 되돌려 다음 실행부터 다시 순회한다. 개별 건의 재시도 간격은 전체 순회량·실행 시간에 따라 달라지며 30초 이내 처리를 보장하지 않는다.
- 커서는 프로세스 메모리의 조회 진행 위치일 뿐이다. 미전달 근거는 DB에 남고 재시작하면 0부터 다시 스캔한다. 만료·해제·같은 종류 교체 후에도 과거 잠금의 미전달 기록은 제외하지 않는다.
- 핸들러 호출에 외부 트랜잭션을 걸지 않는다. 기존 U8의 주문별 트랜잭션·회원→주문 잠금·잠금 적용 시각까지 시작된 타이머 판정을 재사용한다. 성공 후 전달 시각만 조건부 갱신해 다른 제재 필드를 덮어쓰지 않고, 중복 완료는 최초 시각을 보존한다. 타이머 중단 성공 뒤 완료 표식만 유실돼도 재전달로 이력·알림·지급을 중복 생성하지 않는다.
- 한 인스턴스의 실행 중 재진입은 건너뛴다. 여러 인스턴스/커밋 직후 전달과의 중복 호출은 허용하며 기존 타이머 잠금·멱등성과 조건부 완료 기록으로 처리한다. 분산 작업 선점·전역 exactly-once를 제공한다고 표현하지 않는다.
- 개별 오류는 사유 없이 제재 ID·예외 종류만 로그에 남기고 다음 건을 진행한다. DB 자원/연결·트랜잭션 시작 장애는 배치를 중단하고 커서를 유지해 다음 주기에 다시 조회한다. 일반 스케줄러와 스레드를 공유하지 않는다. 자동 최대 재시도 횟수·메일/외부 결제 호출·관리자 재시도 API는 추가하지 않는다.
- 공통 test 프로필과 DB 없는 공개 메인 테스트는 자동 실행을 끈다. 전용 스케줄러 테스트만 명시적으로 활성화한다. 개인 설정을 추가하지 않아도 기본값으로 동작하며 서버 재시작이 필요하다. 기존 만료 시점의 보안 잠금 이력 재검사는 그대로 유지한다.

main 반영·검증 근거는 [진행 현황](project-progress.md#remaining)과 [변경 이력](change-history.md#security-lock-delivery-retry)을 따른다.

#### 데이터·제약

##### V10 DB

기존 V1~V9는 수정하지 않고 `V10__add_member_sanctions.sql`로 업무 테이블 1개를 추가한다. 누적 업무 32개 + Batch 시스템 9개 = 41개이며 Flyway 이력 테이블은 별도다. 개인 DB는 정상 서버 기동/Flyway 실행 때 적용되며 자동 DDL 생성으로 대체하지 않는다.

| 컬럼 | 의미 |
| --- | --- |
| id / member_id | 제재 ID / 대상 회원 FK |
| kind / reason / applied_by | 종류·사유·적용 관리자 FK |
| request_key | 대상별 중복 방지 UUID |
| starts_at / ends_at | 적용·종료 UTC 시각 |
| revoked_at / revoked_by / revoke_reason | 수동 해제 또는 같은 종류 교체 이력 |
| timer_stop_delivered_at | 타이머 중단 전달 완료 시각. 일반 제한은 NULL |

기간·종류·해제 필드 묶음·전달 시각 CHECK, 대상/관리자 FK와 요청 UNIQUE를 적용한다. 현재 상태 조회 인덱스와 미처리 전달 조회 인덱스를 제공한다. 만료 이력을 삭제하거나 이전 Flyway를 다시 쓰지 않는다.

<a id="domain-products"></a>
## 상품·판매자·후기

<a id="products-seller"></a>
### 판매자 프로필·공개 소개

판매자 소개·전문 분야·포트폴리오 HTTPS 링크를 관리한다. 판매자 프로필 작성 후 상품을 등록한다. 공개 소개·판매 상품은 공개 노출 정책을 적용하며 내 프로필 쓰기와 공개 조회를 구분한다.

#### 업무 규칙·연결

##### 구현 범위

API 명세 3·10절을 따라 기존 seller_profiles Entity를 확장하고 JPA Repository·요청/응답 DTO·입력 검사·Service·Controller를 연결했다. 공유 회원 PK를 프로필 PK로 사용하며 새 테이블·Flyway 변경·프로필 삭제 API는 추가하지 않는다.

| API | 권한·결과 |
| --- | --- |
| GET /api/sellers/{id} | 공개 조회. 잘못된 ID는 400, 미등록 프로필은 404 |
| GET /api/me/seller-profile | 인증된 본인. 미등록 404 |
| PUT /api/me/seller-profile | 인증·CSRF 필요. 최초 생성 201, 수정 200 |

응답은 id(문자열), displayName, introduction, specialty, portfolioUrl이다. 표시 이름은 회원에서 조회하며 이메일·비밀번호·잔액을 공개하지 않는다. 회원 번호는 요청 본문·쿼리·헤더가 아닌 공통 CurrentMemberId의 인증 principal에서 얻는다. 실제 로그인 기능은 기존 인증 담당자의 구현을 사용하며 테스트 인증을 운영 코드에 넣지 않는다.

##### S2 판매자 공개 소개·판매 상품 조회

<a id="b-895-0"></a>
상품 상세의 판매자 이름 링크에서 `/sellers/:id`로 이동한다. 기존 공개 프로필 API로 표시 이름·전문 분야·소개·선택 포트폴리오를 조회한다. 포트폴리오 링크는 사용자 정보가 없는 HTTPS URL만 새 탭에 연다.

프로필을 조회한 뒤 기존 공개 상품 목록 API에 `sellerId`를 전달해 해당 판매자의 공개·판매 중 상품을 최신순으로 8개씩 조회한다. 상품이 없는 판매자와 소개가 없는 판매자를 구분한다. 페이지는 URL에 보관하고, 범위를 넘긴 경우 첫 페이지 복귀를 제공한다. 상품 상세에서 돌아오면 판매자 상품 페이지를 복원한다.

#### API·입력·오류

##### 입력·저장 규칙

- introduction 필수 1~2000, specialty 필수 1~100 코드 포인트. 공통 TextNormalizer로 CRLF/CR을 LF로 통일하고 Unicode 앞뒤 공백을 제거한다.
- portfolioUrl은 선택, 최대 2048 코드 포인트. HTTPS·host 존재·userinfo 부재를 URI 파서로 확인한다. 서버에서 외부 주소에 접속하지 않는다.
- PUT은 전체 교체다. 소개·전문 분야를 매번 제출하고, 링크 생략·null·공백은 기존 링크를 NULL로 지운다.
- 조회는 읽기 전용 트랜잭션, 쓰기는 READ_COMMITTED 단일 트랜잭션이다. 회원 행을 먼저 PESSIMISTIC_WRITE로 잠가 같은 회원의 최초 생성·수정 경쟁을 직렬화한다.
- 신규 공유 PK Entity는 EntityManager.persist로 등록하고 기존 관리 Entity는 도메인 메서드로 수정한다. 일반 setter나 직접 JDBC 운영 코드는 추가하지 않는다.
- 공통 Clock.instant를 UTC LocalDateTime으로 변환하고 마이크로초로 절삭한다. DATETIME은 LOCAL_DATE_TIME JDBC 매핑으로 저장한다. 수정 시 created_at은 보존한다.
- demo에서는 Controller·Service를 등록하지 않는다. DB 없는 기본 HTTP 테스트에 SellerProfileService 모의 빈만 추가하고 기존 테스트 설정을 유지했다.

#### 화면·캐시·계정 경계

##### 프런트 후속 구현

- `/api/me`로 서버 세션 확인 후 계정별 프로필을 조회한다. 미인증이면 폼을 숨기고 로그인 안내를 제공한다.
- 본인 조회의 404 RESOURCE_NOT_FOUND만 미등록으로 처리한다. 서버·네트워크 오류를 신규 등록으로 숨기지 않는다.
- 소개·전문 분야·선택 링크, 코드 포인트 글자 수·필드 오류·저장 중 비활성화·성공 안내를 제공한다. PUT에는 세 필드를 모두 보내고 빈 링크는 null로 제출한다.
- 공통 apiRequest로 쿠키·CSRF·오류를 처리하며 변경 요청을 자동 재시도하지 않는다. 저장 전 서버 계정을 다시 확인하고 다른 계정이면 제출을 중단한다.
- 계정별 조회 키, gcTime=0, 취소·제거 정리, 세션 만료 시 폼 숨김을 적용한다. 세션 재확인 중에는 이전 계정 폼을 숨기고, 같은 계정 확인 후에는 입력을 유지한다.
- 저장 후 서버 응답으로 폼을 갱신한다. 사용자가 외부 포트폴리오 링크를 입력해도 앱이 해당 주소를 자동 호출하지 않는다.

브라우저 확인: 비로그인 안내, 미등록 폼, 필수값·HTTP 링크 오류, 최초 등록·서버 정규화 반영, 수정·링크 삭제·새로고침 유지, 다른 계정으로 재진입 시 이전 데이터 미노출, 로그아웃 후 폼 숨김. 프로필 등록 화면을 캡처했다. 이것은 임시 도구를 통한 실제 세션 검사이며 운영 로그인/회원가입 화면·전역 인증 메뉴 전체 완료를 뜻하지 않는다.

실제 운영 로그인 화면에서의 진입·로그아웃 흐름 통합은 인증/React 공통 담당자와 후속 확인한다. MySQL 8.4 최신 CI·팀원 리뷰·PR 병합도 별도다.

<a id="products-catalog"></a>
### 카테고리·검색·상세

공개 카테고리·상품 검색·필터/정렬/페이지·상세를 제공한다. 판매 중지·관리자 숨김·판매자 제한 관련 노출과 신규 주문 허용 여부는 서버 정책을 따른다. 기존 주문의 조건은 현재 상품 변경과 분리한다.

#### 업무 규칙·연결

##### 구현 범위

`GET /api/categories`가 기존 V1의 `categories`를 조회한다. 로그인 없이 접근할 수 있으며 응답은 `id`(문자열), `name`, `displayOrder`의 배열이다. `display_order ASC, id ASC`로 정렬하며 데이터가 없으면 `200 []`를 반환한다. 서버 오류는 공통 오류 응답을 사용하고 빈 목록으로 숨기지 않는다.

- Entity·Repository·DTO·읽기 전용 Service·Controller를 product 패키지에 둔다.
- Entity를 직접 반환하지 않고 서비스 트랜잭션 안에서 DTO로 변환한다.
- 실제 DB를 사용하지 않는 demo 프로필에서는 이 Controller·Service를 등록하지 않는다. 기존 `/api/demo/categories`는 별도 예시다.
- DB 없는 공통 HTTP 검사에서는 CategoryService만 mock으로 대체하고 기존 지갑·충전·주문 서비스 mock을 보존한다.
- 기존 V1/V2, Security 정책, 실제 카테고리 초기 데이터는 변경하지 않는다. 카테고리 등록·편집 API도 추가하지 않는다.

API 명세 1·3·9·10절에 따라 `GET /api/products`를 구현했다. 로그인 없이 조회하며 `keyword`, `categoryId`, `page`, `size`를 받는다. React 메인 검색도 실제 상품·카테고리 API로 연결했다. 상품 등록·상세·주문 진입 기능과 구분한다.

- 서버 QueryDSL 조건에서 판매 중(`ON_SALE`)이고 관리자에게 숨겨지지 않은 상품만 조회한다.
- keyword는 공통 TextNormalizer로 정규화하고 최대 100 코드 포인트를 허용한다. 생략·정규화 후 빈 값은 제한 없음이다.
- 제목 또는 설명의 포함 검색이며, 카테고리 조건과는 AND로 결합한다. `%`, `_`, `!`, 역슬래시 등 사용자 입력은 바인딩하며 LIKE 와일드카드로 실행하지 않는다.
- categoryId는 생략 가능하지만 제출하면 양의 BIGINT 10진 문자열이어야 한다. 형식·범위 오류는 400 VALIDATION_FAILED, 존재하지 않는 정상 ID는 빈 결과다.
- page 기본 0, size 기본 20·허용 범위 1~100. 오프셋은 long으로 계산한다.
- created_at DESC, id DESC 정렬. 범위 밖 페이지는 빈 items와 같은 필터의 전체 건수·페이지 수를 반환한다.
- 공통 PageResponse를 사용하고 상품·카테고리·판매자 ID는 문자열로 반환한다. 판매자 이메일·비밀번호·내부 숨김 상태는 응답하지 않는다.

##### 구현

- GET /api/products/{id}: 로그인 없이 공개·판매 중 상품만 조회한다. 기존 목록과 같은 ON_SALE·is_hidden=false 조건을 QueryDSL 조인에 적용한다.
- 잘못된 양수 BIGINT ID는 400 VALIDATION_FAILED, 미존재·숨김·판매 중지 상품은 동일한 404 RESOURCE_NOT_FOUND다.
- ProductSummary에 description, scopeDescription, preparationDescription, version, saleStatus, createdAt, updatedAt을 추가한 DTO를 반환한다. ID는 문자열, 시각은 UTC offset을 명시한다. 판매자의 이메일·비밀번호나 isHidden은 공개하지 않는다. 성공 응답은 no-store다.
- React /products/:id 경로와 목록 카드 이동을 연결한다. 설명·작업 범위·준비사항, 가격·작업 기간·판매자·카테고리·한국 시각을 표시한다. 검색 결과에서 진입하면 목록 복귀 링크에 검색 조건·페이지를 유지한다.
- 로딩·404·서버 오류를 구분한다. 상세 캐시를 장기 보존하지 않고 진입·포커스에서 서버 상태를 재확인한다. 설명을 HTML로 해석하지 않고 React 텍스트로 표시한다.
- DB·Flyway·Security·의존성 변경 없음. 운영 데이터 접근은 기존 JPA Entity와 QueryDSL만 사용한다.

##### S1 상품 검색 조건·정렬 확장

<a id="b-821-0"></a>
공개 상품 목록에 최저가·최고가·최대 작업 기간과 최신순·낮은 가격순·높은 가격순을 추가한다. 입력 범위와 오류는 [API 명세](architecture.md#api)를 따른다. 집계와 목록에는 동일한 필터를 적용하며 가격이나 등록 시각이 같은 상품은 ID 내림차순으로 조회한다. 판매 중이고 숨김이 아닌 상품만 포함한다.

메인 검색 결과에서 조건을 적용하면 기존 검색어·카테고리와 함께 URL에 저장하고 첫 페이지로 돌아간다. 필터와 정렬은 페이지 이동 및 상품 상세에서 목록으로 돌아올 때 유지한다.

재현 예시는 `/?minPrice=10000&maxPrice=50000&maxDurationDays=3&sort=PRICE_ASC`다.

##### 구조와 연결

ProductSearchController → 읽기 전용 ProductSearchService → ProductSearchQueries(QueryDSL/JPA) → 기존 Product·Category·Member 조인이다. 최신 main의 [JPA·QueryDSL 통일 기준](architecture.md#persistence)에 맞춰 운영 코드의 직접 JDBC를 제거했다. 목록에 필요한 열만 선택하고 관리자 전용 응답이나 쓰기 경로를 공개 API에 재사용하지 않는다. CategoryResponse와 공유 Product Entity는 재사용하며 테이블·마이그레이션을 추가하지 않는다. 범위 밖 페이지는 Service에서 집계 후 빈 목록을 반환해 JPA offset의 int 변환 오류를 방지한다.

Service와 Queries는 demo에서 제외한다. 실제 DB가 없는 공통 HTTP 테스트에만 검색 Service·Queries mock을 추가했다. Security 공개 경로는 기존 `/api/products` 설정을 사용하고 CSRF·보호 API 정책을 변경하지 않는다.

<a id="products-manage"></a>
### 상품 등록·수정·판매 상태·운영

상품 버전과 수정/판매 상태를 관리한다. 내 상품 목록은 중지·숨김을 포함한다. 상품 변경·판매 중지·관리자 숨김은 주문 당시 스냅샷을 바꾸지 않으며 기존 거래 처리를 막지 않는다. 관리자의 숨김/복구는 판매 상태와 독립적이고 사유·운영 이력을 남긴다.

#### API·입력·오류

##### 계약

| 경로 | 입력 | 결과 |
| --- | --- | --- |
| GET /api/admin/products | keyword, isHidden, saleStatus, page, size | AdminProductSummary 목록 |
| GET /api/admin/products/{id} | 없음 | 관리자 ProductDetail(isHidden 포함) |
| GET /api/admin/products/{id}/moderation-logs | page, size | 운영 이력, 최신순 |
| PATCH /api/admin/products/{id}/visibility | hidden, reason, expectedVersion + CSRF | 관리자 ProductDetail |

`/api/admin/**`는 기존 SecurityConfig에서 ROLE_ADMIN만 허용한다. 익명은 401, 일반 회원은 403이다. 모든 성공 응답은 `Cache-Control: no-store`다. 숨김 변경은 판매 상태, 거래, 지갑을 바꾸지 않는다.

##### 입력 기준

- keyword: 공백을 정리한 뒤 빈 값이면 조건을 걸지 않는다. 최대 100 코드 포인트다. 제목 또는 설명에서 부분 문자열로 검색한다. `%`, `_`, 이스케이프 문자(`!`)는 `ESCAPE '!'`로 글자 그대로 검색하고, 값은 항상 바인딩한다.
- isHidden은 `true` 또는 `false`, saleStatus는 `ON_SALE` 또는 `STOPPED`만 허용한다. 빈 값, 대문자 변형, 다른 문자열은 400 VALIDATION_FAILED다. 필터는 AND로 결합한다.
- 정렬은 created_at 내림차순, 같은 시각이면 id 내림차순이다. 범위를 벗어난 page는 items=[]이고, 집계는 같은 필터의 전체 결과를 기준으로 한다.
- PATCH 본문: hidden은 JSON boolean, expectedVersion은 0 이상의 JSON 정수, reason은 JSON 문자열이다. reason은 정리 후 1~1000 코드 포인트를 허용한다. `"true"`, `"0"`, 1, 0.5, null, 필드 누락은 400이다.
- 상품 ID는 양의 BIGINT 범위의 10진 문자열만 허용하고, 그 외는 400이다. 없는 상품은 404 RESOURCE_NOT_FOUND다.

#### 처리 순서·동시성·재전송

##### 숨김·복구 처리 순서

AdminProductService는 READ_COMMITTED 트랜잭션에서 다음 순서로 처리한다.

`AdminProductQueries`는 QueryDSL로 `Product`·`Category`·`Member`를 조인해 목록과 상세를 만든다. 잠금은 `LockModeType.PESSIMISTIC_WRITE`(MySQL `FOR UPDATE`)이고, keyword는 `like(pattern, '!')`로 이스케이프 문자를 지정한다. 범위를 벗어난 page는 목록 조회 없이 빈 배열을 돌려준다. 상품 수정과 판매 상태 변경도 같은 상품 행 잠금과 version 검사를 사용해야 관리자 숨김과 동시에 실행돼도 안전하다(API 11절 시나리오). 새 테이블과 Flyway 변경은 없다.

#### 화면·캐시·계정 경계

##### 관리자 화면

<a id="b-62-0"></a>
| 경로 | 내용 |
| --- | --- |
| `/admin/products` | 검색어·노출(전체/공개/숨김)·판매 상태(전체/판매 중/판매 중지) 필터와 페이지를 URL에 보관한다. 허용하지 않는 값은 "전체"로 읽어 400 요청을 보내지 않는다. 검색어는 정리 후 100자를 넘으면 요청하지 않고 안내한다. 숨김과 판매 중지를 서로 다른 표시로 구분한다 |
| `/admin/products/:id` | 상세·숨기기/복구하기(사유 필수)·운영 이력(최신순, 페이지)을 보여준다. 목록에서 들어오면 같은 필터의 목록으로 돌아가며, 돌아갈 주소는 관리자 목록 경로만 허용한다 |

- 파일: `features/admin/`의 `types`·`api`·`validation`·`access`·`queries`와 `pages/`. 공통 `apiRequest`·`AsyncState`·`usePageSearchParam`·`normalizeText`·`formatDateTime`을 재사용한다.
- 권한: 헤더의 "관리자" 링크는 `GET /api/me`의 role이 ADMIN일 때만 보이지만 편의 표시일 뿐이다. 서버 응답 401이면 로그인 안내(`?next=`로 복귀), 403 `FORBIDDEN`이면 "접근 권한이 없어요"를 보여주고 상품 내용은 표시하지 않는다. CSRF 403은 권한 문제로 보지 않는다.
- 캐시: `['admin', ...]` 아래에 두고 매번 최신으로 조회한다(staleTime·gcTime 0). 로그인·로그아웃 때 `resetAccountState()`가 `['auth']` 외 캐시를 지우므로 다른 계정에 남지 않는다.
- 숨김·복구 요청: 마지막으로 본 `version`을 `expectedVersion`으로 보낸다. 처리 중 버튼을 막고 자동 재시도하지 않는다. 성공하면 상세를 응답으로 바꾸고 목록·이력을 다시 조회한다. 409 `PRODUCT_VERSION_CONFLICT`는 입력한 사유를 남긴 채 최신 상품을 다시 불러와 안내한다. 통신 오류·5xx는 서버 처리 여부가 불명확하므로 실패로 단정하지 않고 상품·이력을 다시 조회한다. 화면을 떠난 뒤 도착한 응답은 화면 상태를 바꾸지 않는다.
- 자체 검수 보완(2026-11-04): 다른 관리자가 먼저 같은 상태로 바꾼 경우 서버는 이력 없이 현재 상태를 200으로 돌려주므로, 성공 안내는 "내가 바꿨다"가 아니라 현재 상태(숨김/공개)로 표시하고 운영 이력을 다시 불러온다. 상세 재조회만 실패하면 마지막 상품과 입력 중인 사유를 유지한 채 "다시 불러오기"를 보여준다. 빠른 두 번 클릭은 ref로 한 번 더 막고, 목록의 선택 상자를 바꿀 때 입력해 둔 검색어도 함께 적용한다.
- 검증: `tests/admin.test.mjs`(API 요청 형식·잘못된 ID 차단·PATCH 본문과 재시도 없음, URL 필터·사유 규칙, 401/403 구분, 목록 URL 복원·숨김/판매 중지 구분, 접근 거부 시 데이터 미표시, 100자 초과 미요청, 상세 숨김/복구 버튼·텍스트 이스케이프·이력, 잘못된/없는 ID, 재조회 실패 시 화면 유지, 돌아갈 주소 제한, 헤더 관리자 링크). 실행 결과와 브라우저 확인 여부는 PR에 기록한다.
- 로컬 관리자 계정(2026-11-04 팀 확인): 회원가입은 항상 USER이므로, 로컬 개발 DB에서 테스트용으로 가입한 계정 1개만 `UPDATE members SET role = 'ADMIN', updated_at = UTC_TIMESTAMP(6) WHERE email = '...'`로 바꾼다. 테스트 DB(`lancerpick_test`)에는 하지 않는다. role은 로그인 시 세션에 담기므로 바꾼 뒤 다시 로그인한다.
- 브라우저 확인(로컬): 관리자 계정의 헤더 링크·목록 조회(빈 목록)·이전/다음 비활성, 일반 계정의 링크 미표시와 `/admin/products` 직접 접근 시 접근 권한 없음 안내를 확인했다.
- 실제 데이터 브라우저 확인(2026-11-04, main `16bb8c5`, 로컬 개발 DB, 사용자 수동 확인): [수동 테스트 데이터 SQL](sql/seed-manual-trade-demo.sql)로 상품 4개를 만든 뒤 관리자 계정으로 확인했다.
  - 목록: 4개 모두 공개·판매 중·버전 0으로 표시.
  - 숨기기(`[테스트 B] 상세페이지 디자인`, 사유 "테스트 숨김"): 배지 숨김, 판매 상태는 판매 중 유지, 버전 0→1, 운영 이력 1줄(숨김·사유·관리자 ID), 버튼이 복구하기로 바뀜. 비로그인 공개 목록이 3개로 줄어 해당 상품만 빠짐.
  - 복구(사유 "테스트 복구"): 운영 이력 2줄(최신순 복구→숨김), 버튼이 숨기기로 바뀜. 공개 목록이 다시 4개.
- 추가 확인(2026-11-04, main `edd1762`, 같은 상품, 사용자 수동 확인): 숨김 상태에서 비로그인으로 `/products/3`에 직접 접근하면 "현재 조회할 수 없는 상품이에요"로 막힌다. 판매자(`demo.b`) 내 상품 목록에서 같은 상품이 "관리자 숨김"으로 보이고 공개 상세 링크가 없다. 판매 중지 시 "판매 중지·관리자 숨김"이 함께 표시되고 판매 상태 화면에 "관리자 숨김은 판매 재개로 해제되지 않아요" 안내가 나온다. 숨김 상태에서 판매 재개 후에도 "판매 중·관리자 숨김"이 유지되고 공개 상세는 계속 막힌다. 판매 상태 변경으로 버전이 바뀐 뒤에도 관리자 복구가 정상 처리됐다(운영 이력 숨김·복구 각 2건).
- 남은 확인: 두 관리자가 같은 버전으로 동시에 처리하는 409 충돌 안내의 화면 재현. 다른 창에서 돌아오면 상세를 다시 조회하므로 손으로 재현하기 어렵고, 409 처리는 `tests/admin.test.mjs`·백엔드 통합 테스트 범위로 둔다. 숨김 후 기존 주문 유지는 주문 담당의 [교차 기능 회귀 검사](change-history.md#b-726-0)와 아래 정은찬 사용자 확인을 따른다. 자동 테스트·백엔드 통합 테스트 결과와 사용자 수동 확인은 합산하지 않는다.

#### 업무 규칙·연결

##### 구현

- GET /api/me/products?page=0&size=20. 인증 principal로만 소유자를 결정한다. 판매 중·중지·관리자 숨김을 모두 포함하되 타인 상품은 제외한다.
- QueryDSL 필요한 열만 조인 조회. 생성 시각·ID 내림차순. 동일 읽기 전용 REPEATABLE_READ 트랜잭션에서 전체 건수·목록 조회. page0이상·size1~100 검사, long offset 사용, 범위 밖은 전체 건수와 빈 items 반환.
- 본인 전용 OwnedProductSummary에 상태·숨김·버전·UTC 시각 포함. 공개 목록 DTO는 유지한다. 상세 설명·이메일 등 개인정보를 목록에 노출하지 않는다. Cache-Control:no-store.
- /mypage/products 화면·마이페이지 메뉴. 계정별 Query 키와 세션 확인, 401/오류/빈 목록/페이지 초과 구분. page를 URL에 보존한다.
- 목록→등록·수정·판매 상태 관리. 내부 이동의 fromMyProducts 상태로 목록 페이지를 보존하고 외부 복귀 주소는 허용하지 않는다. 공개·판매 중 항목에만 공개 상세 링크 표시.
- 기존 상품 수정/판매 상태 성공 시 products 하위 목록도 재조회한다. 계정 재확인 중 이전 목록 숨김, 로그인/로그아웃 공통 캐시 제거 정책 유지.
- DB·Flyway·Security·의존성 변경 없음. 엔티티 저장 API나 판매 상태 API 계약은 바꾸지 않는다.

- POST /api/products: 판매자 프로필 보유 회원이 7개 필수 필드로 등록, 201. 서버가 sellerId·ON_SALE·isHidden=false·version=0 지정.
- PATCH /api/products/{id}: 소유자만 부분 수정. 생략=유지, 명시 null·미정의 필드·잘못된 JSON 타입·수정 필드 없는 요청은 400. expectedVersion 필수. 타인은 404. 버전 불일치는 409 PRODUCT_VERSION_CONFLICT.
- GET /api/me/products/{id}: 수정 화면의 본인 상세 초기값. 숨김·중지 상품도 소유자만 조회하고 isHidden 포함.
- 원본 JSON 타입을 확인해 문자열 숫자·소수·범위 초과를 차단한다. 공통 정규화와 코드 포인트 길이, 존재하는 카테고리·가격·기간 검사.
- JPA 상품 행 PESSIMISTIC_WRITE로 기존 관리자 숨김/주문 생성의 상품 잠금과 직렬화. 같은 값이라도 버전 검사 후 실제 변경이 없으면 version·updatedAt 유지. 변경 시 version 1 증가, createdAt 보존·UTC 마이크로초 저장.
- 주문·스냅샷은 수정하지 않는다. V1/V2·스키마·Security·의존성 변경 없음.
- /mypage/products/new, /mypage/products/:id/edit 화면과 마이페이지 상품 등록 메뉴. 기존 공통 API/CSRF와 TanStack Query 사용.
- 수정은 실제 바뀐 필드만 PATCH. 충돌은 사용자 입력을 유지하고 최신 전체 값과 비교한 뒤 명시적으로 최신 값 사용/내 입력 재검토를 선택한다. 자동 재저장·덮어쓰기 없음.
- 저장 중 중복 클릭 차단·계정 재확인·화면 이탈 후 늦은 요청/이동 차단. 등록 결과가 불확실하면 자동 재시도하지 않고 폼을 잠근다. 현재 등록 API에는 멱등 키 계약이 없어 페이지를 다시 열어 새 등록하면 중복 가능성이 있으므로 결과 확인이 필요하다.

- PATCH /api/products/{id}/sale-status: 본인 상품만 ON_SALE/STOPPED 변경. saleStatus와 expectedVersion만 받는다. 문자열 상태·정수 버전 타입, null·미정의 필드·범위 검사를 수행한다.
- 인증 principal·CSRF 필수, 타인/없는 상품은404, 버전 불일치409 PRODUCT_VERSION_CONFLICT. 같은 상태 요청도 버전이 일치해야 하며 실제 변경이 없으면 version·updatedAt 유지.
- 기존 상품 PESSIMISTIC_WRITE와 READ_COMMITTED 트랜잭션 사용. 실제 변경 때만 version 증가·UTC 마이크로초 updatedAt 변경. 상품 내용·createdAt·isHidden은 변경하지 않는다.
- 판매 재개로 관리자 숨김을 해제하지 않는다. 판매 중지/숨김은 신규 주문을 차단하지만 기존 주문·스냅샷·멱등 재확인은 보존한다.
- /mypage/products/:id/sale-status 화면을 상품 수정 화면에서 연결. 현재 판매 상태와 공개 상태를 구분하고 중지/재개 선택 후 적용·취소를 제공한다.
- 상태 변경 성공 후 공개 목록·상세와 해당 소유자 수정 캐시를 갱신한다. 계정별 조회·세션 재확인·401/계정 변경 시 폼 숨김, 처리 중 중복 클릭 방지.
- 버전 충돌·네트워크 오류 등 변경 실패는 자동 재전송하지 않는다. 사용자가 현재 상태를 재조회한 뒤 다시 선택한다. 최신 상태 확인만으로 변경 요청을 전송하지 않는다.
- 운영 인증 API 재사용, queries.ts 조회 훅·기능 HTTP 함수·페이지 책임 구분. DB·Flyway·Security·의존성 변경 없음. 내 상품 목록은 다음 업무다.

<a id="products-reviews"></a>
### 후기·관심 상품

완료 주문의 구매자가 주문당 후기 1개를 작성한다. 별점 1~5 필수·글 선택이며 공개 목록·요약·별점 필터를 제공한다. 관심 상품은 회원·상품 복합 키와 본인 목록으로 관리한다. 후기 수정/삭제·판매자 답글은 기존 MVP 제외 범위다.

#### 업무 규칙·연결

##### 구현과 정책

- `POST /api/orders/{id}/review`: 세션 회원의 구매 확정(COMPLETED) 주문에 한 번만 작성, 성공 201. 별점은 JSON 정수 1~5, 글은 생략/null 가능. 문자열 숫자·소수·불리언·추가 필드는 400. 글은 공통 Unicode 가장자리 공백 제거·개행 정규화 후 1~2000 코드 포인트, 입력한 공백 글은 400이다.
- 기존 주문 명령과 동일하게 회원 공유 잠금 → 참여 주문 배타 잠금 순서로 처리한다. READ_COMMITTED에서 주문 잠금 후 후기 존재 여부를 검사하여 동시 요청을 직렬화하며 DB의 기존 주문 UNIQUE도 유지한다. 중복은 409 REVIEW_ALREADY_EXISTS, 미완료는 409 ORDER_STATE_CONFLICT, 판매자는 403, 타인/없는 주문은 404다. 세션·CSRF 설정 변경 없음.
- 후기 작성으로 주문 상태·이력·지갑·정산을 변경하지 않는다. 상품의 현재 숨김/판매 상태와 관계없이 기존 완료 주문의 구매자는 작성할 수 있다. 수정·삭제·판매자 답글은 MVP 범위 밖이다.
- `GET /api/products/{id}/reviews`: 공개·판매 중 상품만 비로그인 조회, 나머지는 404. page 기본0, size 기본20/허용1~100. 작성 시각·ID 내림차순, 범위 밖은 전체 건수와 빈 목록. 한 REPEATABLE_READ 읽기 트랜잭션에서 공개 여부·개수·목록을 읽는다.
- 공개 응답은 id,authorDisplayName,rating,content,createdAt만 반환한다. 주문·작성자 ID와 이메일을 노출하지 않는다. 작성 응답과 참여자 주문 상세에는 기존 계약대로 orderId가 있다. ID는 문자열, 저장 시각 UTC·응답 ISO UTC·화면 KST. 작성자명은 현재 회원 표시 이름을 사용한다.
- QueryDSL 조인/필요 열 조회, JPA 저장. 기존 V1/V2·의존성·공개 상품 DTO 변경 없음.

#### 화면·캐시·계정 경계

##### 화면

- 주문 상세: WRITE_REVIEW 허용 행동이 있는 구매자에게 별점·선택 글 입력 폼 표시. 글 공백만 입력하면 선택 글을 null로 전송한다. 등록 중 중복 클릭 차단, 자동 POST 재전송 없음. 성공/오류 후 주문 상세와 공개 후기 쿼리를 재조회한다. 실패/불명확 결과에서는 입력을 잠그고 명시적인 주문 상태 재확인 후에만 다시 작성하도록 한다.
- 등록 후 기존 주문 상세의 구매 후기 기록을 표시하며 폼을 제거한다. 상품 상세에 공개 후기·작성자·별점·작성 시각·페이지 버튼을 표시한다. 목록의 page는 URL에 보존하며 로딩·실패·빈 목록·범위 초과를 구분한다. React 텍스트 출력으로 사용자 글을 HTML로 실행하지 않는다.

<a id="domain-orders"></a>
## 주문·계약·납품·취소

<a id="orders-create"></a>
### 신청·스냅샷·수락

본인 상품 주문은 금지한다. 서버가 현재 상품 버전·판매 조건을 재검증하고 orders/order_snapshots·최초 이력을 함께 생성한다. 요청 키 재전송은 기존 결과를 확인한다. 판매자 수락은 단일 /accept 경로이며 새 거래 제한을 검사한다. 기본 생성 정책은 CONTRACT_V3(#152)이며 V2는 비활성이다. 기존 주문은 저장된 정책으로 처리한다.

#### 업무 규칙·연결

##### 구현 범위

현재 main에 실제 회원·상품 Entity가 없어 해당 영역을 중복 구현하지 않았다. 주문 전용 `OrderCreationQueries`가 기존 members·products를 조회한다. 테스트 회원·상품은 SQL fixture이며 실제 가입·상품 등록 기능 완료를 의미하지 않는다.

##### UTC 저장 보완

기존 Order·OrderHistory의 LocalDateTime/TIMESTAMP 매핑은 서울 JVM에서 DB 저장값이 9시간 어긋나도 JPA 재조회만으로 정상처럼 보이는 문제를 실제 재현했다. 주문·이력의 DATETIME 필드에 `@JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)`을 사용해 JDBC LocalDateTime으로 직접 전달하도록 보완했다. 서비스는 Clock의 시간대와 관계없이 Instant를 UTC로 변환한다. 기존 공통 JDBC UTC 설정이나 지갑 Entity는 변경하지 않았다.

##### 구현

- 상품 상세 → /products/:id/order → 요구사항 작성 → 기존 POST /api/orders → 주문 상세 이동.
- /api/me·상품을 재조회한다. 비로그인·자기 상품의 작성 폼을 차단하고 제출 직전 계정을 다시 검사한다. 최종 권한·상품 상태·가격·버전은 서버가 판단한다.
- 기존 createOrder와 CSRF 클라이언트 사용. 상품 ID·조회 버전·정규화한 요구사항·UUID만 전송하며 가격·구매자·판매자 ID를 본문에 보내지 않는다.
- 요구사항 1~5000 코드 포인트 검사. 전송 중 버튼과 ref로 중복 클릭 방지, 자동 재시도 없음.
- 계정·상품별 sessionStorage에 전송 직전 본문·키 저장. 저장 불가·손상 시 신규 전송 차단. 성공 후 제거한다. 불확실한 결과는 입력을 잠그고 같은 요청 재확인·주문 목록 확인을 제공한다. 새로고침 후에도 같은 본문·키를 복원한다.
- 400/401/403/404/409는 제출 중단·상품/주문 목록 재확인. 버전 충돌을 자동 덮어쓰지 않는다.
- 요구사항은 현재 탭에 남을 수 있으나 인증 토큰·비밀번호를 저장하지 않는다. 운영 로그인/로그아웃 공통 저장값 정리, 여러 탭/새 창과 탭 종료 후 결과 확인은 후속이다.

##### 최종 검토

사용자 요청으로 원격을 다시 확인하고 `git pull origin main` 실행: Already up to date. 현재 HEAD와 origin/main은 모두 42d56d8로 추가 반영·충돌이 없었다. 미커밋 주문 진입 변경은 보존했다. 코드가 달라지지 않아 앞선 64건 검사 후 테스트는 반복하지 않았다.

검토 후 보완: 서버가 400/404/409로 거절한 요청을 사용자 동작으로 정리하고 상품을 다시 조회하는 경로를 추가했다. 네트워크·5xx 불확실 결과에는 이 정리를 제공하지 않는다. /me 확인 도중 화면을 떠나면 POST를 시작하지 않고, 전송 후 화면이 바뀌면 뒤늦은 이동을 막는다. 요청 본문은 4개 계약 필드만 구성해 복원 데이터의 추가 필드를 전달하지 않는다.

#### 처리 순서·동시성·재전송

##### 처리 순서와 재전송

성공했던 요청은 상품 수정·판매 중지·숨김이나 주문 종료 이후에도 기존 입력으로 재전송할 수 있다. 기존 키 확인은 현재 상품 조건 검사와 현재 거래 규칙 생성보다 먼저 수행한다. UUID는 소문자 하이픈 표준형, 요구사항은 공통 Unicode 공백·줄바꿈 정규화 결과로 비교한다. 새 키는 별도 주문을 생성한다. DB 오류 전부를 멱등 성공으로 바꾸거나 실패한 트랜잭션 안에서 재시도하지 않는다.

##### 트랜잭션과 잠금 연결 기준

신규 주문의 잠금 순서는 **참여 회원 ID 오름차순 → 요청 키 조회 → 상품**이다. 구매자만 잠그면 A가 B의 상품을, B가 A의 상품을 구매할 때 orders.seller_id 외래 키의 공유 잠금과 상대 회원의 배타 잠금이 교차해 데드락이 발생한다. 격리 MySQL 두 연결에서 ERROR 1213을 재현했고 두 참여 회원을 같은 순서로 잠그도록 보완했다. 하나의 ORDER BY 쿼리가 아니라 작은 ID 행부터 개별 잠금 조회한다.

같은 구매자뿐 아니라 같은 판매자의 주문도 회원 행에서 직렬화된다. 안전성을 위한 보수적 선택이며 처리량은 측정하지 않았다. 서로 다른 참여 회원 조합은 별도로 진행할 수 있지만 상품·요청 키 등 추가 경합은 가능하다. 교차 구매 회귀 테스트는 실제 서비스 두 호출을 판매자 사전 조회 지점에서 맞춰 5회 실행한다. 모든 경로에서 데드락이 불가능하다는 보장은 아니며 다른 서비스가 역순 회원 잠금을 도입하면 재검토해야 한다.

서비스는 상위 READ_COMMITTED 트랜잭션에는 참여할 수 있으며 상위 롤백 시 세 기록도 함께 롤백한다. 격리 수준이 다르거나 지정되지 않은 기존 상위 트랜잭션에는 쓰기 전에 IllegalTransactionStateException으로 거절한다. REQUIRES_NEW로 상위 업무와 분리 커밋하지 않는다. Controller가 업무 시작 서비스로 호출하는 것이 기본 연결 방식이다. 상위 트랜잭션이 이미 회원·상품·주문 잠금을 보유했다면 전체 획득 순서를 먼저 검토해야 하며 격리 수준 일치만으로 데드락 안전성이 보장되지는 않는다.

##### 트랜잭션·잠금

OrderDecisionService의 READ_COMMITTED 트랜잭션에서 실행자 회원 FOR SHARE → 참여자 조건의 주문 FOR UPDATE → 판매자·상태 확인 → 상태/시각·이력 flush → 상세 응답 조립을 수행한다. 상태·이력·응답 조립 중 예외가 발생하면 전체 변경을 롤백한다. 이력의 actor FK가 참조하는 회원 공유 잠금을 주문보다 먼저 확보해, 회원→주문 순서의 생성 재전송과 잠금 순서가 엇갈리지 않도록 했다. 생성 서비스의 회원 ID 오름차순 잠금, 향후 주문→지갑 순서는 변경하지 않았다.

#### API·입력·오류

##### HTTP 생성 API 후속 구현

<a id="b-696-0"></a>
`POST /api/orders`는 `CurrentMemberId`가 검증한 `AuthenticatedMember.memberId()`만 구매자로 사용한다. 요청의 buyerId·가격·상태·조건 원문은 신뢰하지 않는다. 기존 세션/CSRF 보안을 재사용하며 로그인 구현 자체를 추가하지 않았다. `demo`에서는 Controller·DB 조회 Bean을 만들지 않는다.

요청 예시(상품 ID·버전은 실제 조회 결과로 교체):

```json
{"productId":"15","expectedProductVersion":0,"requirements":"파란색으로 제작해 주세요.","requestKey":"c00af24a-391c-48d8-9754-d5591ce7df22"}
```

주문 입력에만 전용 JSON 역직렬화를 적용한다. productId·requirements·requestKey는 문자열, expectedProductVersion은 Long 범위의 JSON 정수만 받는다. 문자열/소수/boolean 버전·숫자 ID를 묵시적으로 변환하지 않는다. 필수값/음수/공백은 Bean Validation, ID·UUID·정규화 후 요구사항 길이는 기존 DTO 검증을 따른다. 공통 오류 응답의 code·message·fieldErrors·traceId를 재사용한다.

`OrderCreationApiService`는 READ_COMMITTED 생성 트랜잭션에서 생성 서비스와 상세 조회를 함께 실행한다. 신규 생성/재전송 결과에 따라 201/200, Cache-Control: no-store를 반환한다. DTO 조립 실패는 새 주문·스냅샷·이력까지 롤백한다. 직렬화 이후 네트워크 실패까지 롤백한다는 의미는 아니며, 응답 유실 시 같은 키·입력으로 재전송한다.

`OrderDetailQueries`는 주문 참여자 조건을 확인하고 저장한 스냅샷·현재 상태·현재 참여자 표시 이름·이력·납품·수정·취소 요청·주문 결제·후기를 조립한다. 상품의 현재 설명으로 스냅샷을 재작성하지 않는다. 모든 ID는 문자열, DATETIME은 JDBC LocalDateTime 원문에서 UTC OffsetDateTime으로 변환한다. 미발생 단일 객체/시각은 null, 없는 하위 목록은 빈 배열이다. 서비스의 `OrderAllowedActions` 정책으로 현재 허용 행동을 붙이지만 수락·결제 등 해당 명령 API의 구현 완료를 뜻하지 않는다. 최초 구매자 응답은 REQUESTED·CREATED 이력 1건·allowedActions=[CANCEL]이다.

주문 상태가 바뀐 뒤 같은 생성 요청을 재전송해도 현재 주문 상세를 반환한다. 후속 상태의 읽기 검사는 SQL fixture로 수행하며 후속 업무 서비스를 실행한 검증은 아니다. 원격 CI·실제 로그인·브라우저 검증은 별도다. 최종 테스트 수와 실행 결과는 [작업표](project-progress.md#remaining)를 따른다.

##### 계약

| 경로 | 입력 | 결과 |
| --- | --- | --- |
| POST /api/orders/{id}/accept | 본문 없음 | REQUESTED → AWAITING_PAYMENT |
| POST /api/orders/{id}/reject | reason 문자열 필수 | REQUESTED → REJECTED |

두 API 모두 인증 principal 회원 ID와 CSRF를 사용하고 판매자만 실행할 수 있다. 주문의 구매자는 403 FORBIDDEN, 비참여자와 없는 주문은 동일 404 RESOURCE_NOT_FOUND다. ID 형식·범위 오류는 400이다. 관리자 역할만으로 판매자 권한을 우회하지 않는다.

거절 사유는 일반 문자열 정책으로 앞뒤 Unicode 공백 제거·줄바꿈 통일 후 1~1000 코드 포인트를 허용한다. 숫자를 문자열로 자동 변환하지 않는다. 입력 오류는 400 VALIDATION_FAILED다. 수락은 accepted_at·updated_at, 거절은 updated_at만 갱신한다. 거절을 직접 취소로 기록하지 않으며 accepted_at·cancelled_at·paid_at·납기는 새로 설정하지 않는다.

성공은 200 OrderDetail과 Cache-Control: no-store다. ACCEPTED/REJECTED 이력의 actor는 인증 판매자, fromStatus는 REQUESTED, toStatus는 실제 전환 결과다. 수락 사유는 null, 거절 사유는 정규화한 원문이다. 시각은 서버 Clock의 Instant를 UTC·마이크로초로 변환해 사용한다.

이미 수락·거절했거나 다른 상태이면 동일 명령 재전송도 409 ORDER_STATE_CONFLICT다. 생성 요청의 멱등 200과 구분한다. 재시도 때 시각·이력을 덮어쓰지 않는다. 상품이 숨김·판매 중지되었어도 기존 주문의 결정은 가능하다.

##### S5 신규 계약 최소 금액 안내

<a id="b-710-0"></a>
공개 상품 상세의 `minimumOrderAmount`는 서버가 신규 주문에 적용하는 계약 최소 금액이다. 계약 주문(V2 호환 또는 V3)에서는 10,000원, 기존 주문 정책(LEGACY_V1)에서는 `null`을 반환한다.

#### 화면·캐시·계정 경계

##### 프런트 요청 함수 준비

<a id="b-697-0"></a>
`features/orders/create.ts`는 양의 BIGINT 문자열 상품 ID, 안전한 정수 범위의 비음수 버전, 정규화한 요구사항 1~5000 코드 포인트와 UUID 키를 검증한 뒤 기존 공통 세션·CSRF 클라이언트로 POST한다. 동일 키·입력의 명시적 재전송은 가능하지만 네트워크 실패 후 자동 재전송하지 않는다. 현재 공개 상품 요약에는 버전이 없고 거래 규칙 원문 게시 계약도 없으므로 실제 상품 목록에서 주문 제출을 열지 않았다. 상품 상세의 버전·거래 규칙 표시가 준비되면 작성 화면과 연결한다. API 대역 테스트 2개는 201/200, 동일 본문 재요청·불명확 결과 무자동 재시도·입력 경계를 검사한다. 브라우저·DB 통합 검증과 구분한다.

##### 판매자 화면 연결

`/orders/{id}`에서 서버 allowedActions에 ACCEPT 또는 REJECT가 있을 때만 해당 입력과 버튼을 표시한다. 수락은 본문 없이, 거절은 사유만 전송하며 회원 ID·가격을 보내지 않는다. 사유 정규화와 1~1000 코드 포인트 검증은 서버 계약을 따른다. 빈 사유는 화면에서도 차단한다.

실행 중에는 다른 주문 행동도 잠그고 POST를 자동 재전송하지 않는다. 성공·실패 후 기존 주문/지갑 캐시를 무효화하고, 오류 후에는 현재 상태를 다시 조회해야 재실행할 수 있다. 서버의 권한·상태 검사는 그대로 유지한다. 실제 인증 화면·브라우저 조작 검증과 납품·수정·취소 요청 UI는 후속 작업이다.

<a id="orders-query"></a>

### 구매·판매 내역·상세

본인 구매/판매 목록과 당사자 상세·상태·당시 조건·기한/납품 이력·결제/후기·allowedActions를 제공한다. U3 검색·한국 날짜 기간·정렬/페이지 조건은 URL과 서버 검증을 따른다. 판매자 작업 지연과 구매자 검수 대기를 구분하고 행위 가능 여부를 서버에서 재검증한다.

#### 거래 상세 탭 화면 — 로컬 구현 후속

2026-11-11, 황준범, `feature/order-detail-layout`. 선택한 탭 전환 시안에 따라 거래 진행·계약/조건·거래 기록으로 구분하고 현재 상태/단계·납기 안내, 오른쪽 거래 요약 카드를 배치했다. 모바일은 한 열로 표시한다. 상태 변경은 기존 서버 `allowedActions`와 요청/재조회 흐름을 그대로 사용한다.

- 탭은 hidden으로 전환하고 폼을 유지한다. 동의·납품 입력·결과 불명확한 취소 요청 UUID를 탭 변경으로 초기화하지 않는다. 방향키·Home/End로 탭을 선택할 수 있다.
- 계약 작성/서명은 계약·조건 탭으로 안내한다. 납품·수정·확정은 거래 진행, 결제 기록/주문 이력은 거래 기록에 있다. 대기 중 취소 요청은 상단 안내와 펼친 취소·문의 영역에 표시한다.
- 판매 수익 링크는 현재 회원 조회가 성공한 판매자에게만 표시한다. 완료 주문은 `q=주문ID`로 수익 내역을 검색하고, 진행 중은 전체 수익 내역으로 연결한다. 화면 표시 범위이며 API 권한을 대신하지 않는다.
- V3 예상 수익은 결제 응답에 보관된 수수료율이 있을 때만 판매자에게 표시한다. 확정 수수료·지급·반환액은 대금 API를 사용하며 예상과 구분한다. LEGACY_V1은 V3 보관금으로 표시하지 않는다.
- 이번 범위는 거래 상세 가독성·버튼·수익 링크다. 구매/판매 목록·상품 등록·알림/메시지 사이드바·채팅 Enter 규칙은 후속으로 유지한다.

#### 업무 규칙·연결

##### 구현 범위

정은찬 담당 GET /api/orders와 GET /api/orders/{id}를 구현했다. CurrentMemberId의 인증 principal 계약을 사용하며 실제 로그인 필터·React 주문 화면·후속 주문 상태 변경은 포함하지 않는다. 읽기 요청은 CSRF 토큰 없이 사용할 수 있고, 기존 Security의 인증 요구는 유지한다. 응답은 Cache-Control: no-store다. demo 프로필에서는 DB 기반 주문 Controller·서비스를 만들지 않는다.

##### 목록

```text
GET /api/orders?side=BUYER&page=0&size=20
GET /api/orders?side=SELLER&status=REQUESTED
```

##### 목록 검색·기간·정렬 조건 (U3)

<a id="b-735-0"></a>
[화면 개편 결정 §16.1](change-history.md#b-993-0)의 U3 계약을 구현했다(정은찬, main 반영 #130). DB·Flyway 변경은 없고 기존 `ix_orders_buyer_created`·`ix_orders_seller_created` 인덱스 범위에서 조회한다.

```text
GET /api/orders?side=BUYER&q=로고&from=2026-10-01&to=2026-10-31&sort=oldest
GET /api/orders?side=SELLER&q=123
```

- `q`: 앞뒤 공백을 지우고 비면 조건 없음. 100자(코드 포인트) 초과는 400. ASCII 숫자만이면 주문번호 정확 일치만 본다(숫자로 된 상품명은 이 경우 찾지 않음, 0·long 범위 초과는 결과 없음). 그 밖에는 `order_snapshots.product_title_snapshot` 또는 상대방(BUYER 조회는 판매자, SELLER 조회는 구매자)의 현재 `display_name` 부분 일치이며 `%`·`_`는 `!` 이스케이프로 글자 그대로 찾는다. 대소문자 구분은 DB 콜레이션을 따른다.
- `from`/`to`: 네 자리 연도 `yyyy-MM-dd`만 받고 없는 날짜는 400. 서버가 [from 00:00 KST, to+1일 00:00 KST)를 UTC로 바꿔 `orders.created_at`과 비교한다(종료일 당일 포함). 둘 중 하나만 줄 수 있고 from>to는 400. 기간 상한은 없다.
- `sort`: `latest`(기본, created_at·id 내림차순)·`oldest`(오름차순). 그 외 값은 400.
- 건수 조회도 목록과 같은 스냅샷·상대방 조인과 조건을 쓴다.

화면(`/orders`): 검색어·시작일·종료일 입력과 조회·초기화, 최근 1개월/3개월/전체 기간 버튼(한국 날짜 오늘 기준, 없는 날짜는 그 달 말일), 정렬 선택, 결과 건수, 항목의 주문번호를 추가했다. 조건은 URL(`q`·`from`·`to`·`sort`)에 두고 기본값은 생략하며, 조건을 바꾸면 첫 페이지로 간다. 주소에 잘못된 값이 있으면 그 조건만 빼고 조회한다(from>to면 둘 다). 상세로 갈 때와 상세의 "거래 내역" 복귀 링크는 기존처럼 현재 쿼리를 그대로 넘긴다. 목록 queryKey는 `['wallet', 'orders', side, status, page, q, from, to, sort]`이며 `['wallet']` 무효화·계정 전환 정리는 그대로 적용된다.

##### 상세

```text
GET /api/orders/15
```

id는 양의 BIGINT 범위 10진 문자열이다. 형식·범위 오류는 400, 없는 주문과 비참여자의 주문은 모두 404 RESOURCE_NOT_FOUND다. 구매자·판매자만 같은 상세 데이터에 접근하며 allowedActions는 실행자 역할과 현재 상태에 따라 달라진다. 관리자 권한만으로 참여자 제한을 우회하지 않는다.

#### 화면·캐시·계정 경계

##### 주문 목록 화면 후속

<a id="b-738-0"></a>
`/orders`는 구매/판매 구분·상태 필터·페이지를 URL에 보존한다. 전체 선택과 잘못된 상태 값은 status 없이 조회하며, 구분·상태 변경 시 첫 페이지로 돌아간다. 조회 실패 중에도 필터를 다시 고를 수 있다. 한국어 상태·주문 시각·현재 납기를 표시하고 결제 전처럼 납기가 없으면 생략한다. 최신 main의 직접 URL·페이지 복원 동작을 유지한 채 프런트 대역 테스트와 빌드를 확인했다. 실제 브라우저 거래 검증은 별도다.

주문 이력에서도 같은 상태 이름을 사용하고 취소 요청의 대기·승인·거절·철회를 한국어로 표시한다. 서버 상태 값과 허용 행동 판단은 바꾸지 않는다.

#### 범위·후속 조건

##### 주문 상세 지연 표시 후속

<a id="b-740-0"></a>
IN_PROGRESS와 REVISION_IN_PROGRESS에서만 현재 적용 기한을 현재 시각과 비교해 작업 지연을 표시한다. 확인 대기·종료 상태에는 현재 지연 경고를 표시하지 않는다. 각 납품은 1회차 원래 기한, 2회차 수정 기한과 제출 시각을 마이크로초 단위로 비교한다. 같은 시각은 지각이 아니며, 1마이크로초라도 늦으면 지각이다. 현재 시각은 브라우저의 밀리초 정밀도를 사용한다. 기한이 없는 비정상 응답에는 지각 단정을 표시하지 않는다. 별도 DB 필드나 API 계산값은 추가하지 않았다. 화면 새로고침 시 다시 계산하며 실시간 타이머는 없다. 이전 34개 검사는 마이크로초 보완 전 기록이며, 실제 새 상세 표시의 브라우저 조작은 후속이다.

주문 상세의 보관 조건 화면은 주문 시점의 상품 설명·작업 범위·준비 사항·거래 규칙과 요구사항을 구분해 표시한다. 현재 상품이 수정되어도 상세 응답의 스냅샷만 사용한다.

##### 결제·후기 기록 표시 후속

<a id="b-741-0"></a>
주문 상세의 선택 객체 payment가 있으면 현재 결제 상태·금액·결제 시각과 환불 시각을 표시한다. review가 있으면 작성자 표시 이름·별점·작성 시각·선택 본문을 보여준다. 두 값이 null이면 해당 영역을 만들지 않는다. 조회 응답만 사용하며 결제나 후기의 새 쓰기 기능을 의미하지 않는다.

<a id="orders-contract"></a>
### 계약·서명·PDF

canonicalContent는 정책별로 고정한 원문이다. V2의 선금/잔금과 V3의 전액 보관 조건을 구분하며 현재 상품을 다시 읽어 기존 계약을 바꾸지 않는다. PDF 표시가 어려운 문자가 있어도 계약 원문을 조회로 보존하고 외부 HTML/URL 내용을 실행·수집하지 않는다.

계약 주문의 수락·표준 본문/특약 초안·PDF 생성·양측 같은 문서 확인/동의/서명을 연결한다. V2 기존 계약과 V3 전액 보관 본문을 구분하며 이미 서명한 파일을 소급 변경하지 않는다. 짧은 DB 트랜잭션·생성 시도 키·비공개 파일·해시 일치·당사자 다운로드로 복구와 무결성을 관리한다.

#### 업무 규칙·연결

##### 제공 범위

`AdminContractPdfQueryService.inspect(long adminId, long orderId, Integer version)` → `AdminContractPdfResponse`.

- `version=null`이면 현재 계약, 지정하면 해당 버전을 조회한다. 현재 DB의 관리자 역할을 먼저 확인한다.
- 주문/계약 ID는 문자열, 시각은 UTC다. 버전·상태·현재 버전 여부·생성/변경 시각·조회 시각·파일 메타데이터 유무·생성 후보 판단을 반환한다.
- 파일 메타데이터 유무는 LOCAL 백엔드·키·양수 크기/상한·32바이트 해시의 DB 필드 조합만 검사한다. 실제 파일 존재·바이트·해시 일치를 확인했다는 뜻이 아니다.
- 계약 본문·특약·원문 해시·파일 경로·파일 해시·생성 처리권 키·회원 정보를 반환하지 않는다.
- JPA 읽기 전용 REPEATABLE_READ 트랜잭션이다. 파일 I/O·외부 요청·생성·삭제·금융 변경·처리권 획득·감사 이력 저장은 없다. 새 DDL·REST API·화면은 없다.

##### 생성 후보 판단

후보는 조회 시점 정보이며 **관리자 실행 권한이나 예약이 아니다**. 기존 당사자 PDF 생성 규칙과 동일한 주문 상태·기한을 기준으로 하며 `contracts.generation-timeout`(기본 2분, 0초 초과~30분 이하)을 사용한다.

| 조건 | 결과 |
| --- | --- |
| 이전/교체된 버전 | `NOT_CURRENT_VERSION` |
| 수락 전·결제 후·종료 주문 등 생성 불가 상태 | `ORDER_NOT_WRITABLE` |
| 대기 중 취소 요청 | `CANCELLATION_PENDING` |
| 계약 변경 시각이 서버 시각 이후 | `CLOCK_BEFORE_UPDATE` |
| DRAFT | `INITIAL_GENERATION` 후보 |
| FAILED | `RETRY_FAILED` 후보 |
| GENERATING, 변경 시각+기한 전 | `GENERATION_ACTIVE` |
| GENERATING, 변경 시각+기한 도달/초과 | `RETRY_EXPIRED` 후보 |
| READY·SIGNED | `DOCUMENT_READY`·`DOCUMENT_SIGNED` (재생성 후보 아님) |
| 지원하지 않는 상태 | `UNSUPPORTED_STATE` |

여러 조건이 겹치면 표의 위쪽 차단 조건을 먼저 적용한다. 결과의 `eligible=true`일 때만 kind가 후보 종류이며 reason은 null이다. false이면 kind는 NONE이고 reason이 있다. 이 판단에는 파일 유실 복구나 서명 문서 재생성 정책을 추가하지 않았다.

##### 3차: V2 수락·초안·서명 흐름 연결

<a id="b-260-1"></a>
| API (기본 `/api/orders/{orderId}/contract`) | 요청 | 결과 |
| --- | --- | --- |
| ~~POST `/accept`~~ | (삭제, #92) | 일반 주문과 같은 POST `/api/orders/{id}/accept` 하나로 수락한다. 계약 주문은 REQUESTED → AWAITING_CONTRACT, 응답은 주문 상세. 수락 후 재요청은 409 |
| POST `/draft` | `expectedVersion`(최초 0), `specialTerms`(5000 코드 포인트 이하) | 판매자만 초안 저장/교체. 동일 내용 재전송은 기존 버전 반환 |
| POST `/sign` | `version`, `contentHash`, `fileHash`, `agreed:true` | 실제 파일 무결성 확인 후 주문 잠금 아래 현재 버전/해시 재확인·서명 저장. 양측 완료 시 SIGNED·AWAITING_PAYMENT |

표준 본문은 입력받지 않는다. 특약 저장 후 별도 ‘PDF 생성’ 버튼으로 고정 파일을 만든다. 화면은 현재 버전 PDF를 다운로드·해시 확인하고 동의해야 서명 버튼이 활성화된다. 이전 버전의 동의는 새 버전에 재사용하지 않는다. 초안 교체로 이전 서명/파일을 삭제하지 않는다. 파일 확인은 트랜잭션 밖, 서명·주문 상태·이력·아웃박스는 같은 트랜잭션이다. 이벤트 저장 실패 시 초안 교체/서명/상태 변경이 롤백된다.

##### 구현 범위와 연결 전제

- 서버가 검증한 계약 스냅샷으로 한글 PDF를 만든다. Apache PDFBox 3.0.8과 라이선스를 동봉한 나눔고딕을 사용한다.
- 판매자가 생성하고 구매자·판매자만 조회·다운로드한다. 현재 계약뿐 아니라 `version`으로 이전 계약도 확인한다. 취소·종료 후에도 당사자의 보관 문서는 조회 가능하다.
- 생성 실패는 FAILED로 기록하고 재시도 시 새 시도 키를 발급한다. 생성 중 프로세스가 종료되면 기본 2분 후 다음 생성 요청에서 다시 시도할 수 있다.
- 실제 파일 SHA-256·크기를 내려받기 전에 검사한다. 파일 변조·유실은 503으로 차단하며 READY/SIGNED 파일을 자동 재생성하거나 기존 서명 해시를 바꾸지 않는다.
- Flyway V1~V6는 수정하지 않았다. 기존 `generation_key`와 `updated_at`으로 시도 식별·재시도 기한을 판단한다.

기존 내부 `OrderContractService.prepare/sign`의 주문 상태 연결은 주문 담당자와 이어서 진행한다. PDF 생성 검사는 수락·미결제·AWAITING_CONTRACT 또는 내부 기반의 AWAITING_PAYMENT·대기 취소 요청 없음이 전제다. 계약 상태가 READY/SIGNED이고 주문이 생성 가능한 상태라면 생성 재요청은 기존 결과를 반환한다.

##### 구현

- V4__add_order_contracts.sql: order_contracts·contract_signatures 두 테이블. 주문당 현재 계약 한 개·버전 중복·역할별 서명 중복·서명 파일 필수값을 DB에서 제한한다.
- contract/entity·repository·service·dto: 계약과 서명 JPA 매핑, 판매자의 특약 초안 생성·교체, 양측 해시 확인 후 서명, 양측 완료 시 SIGNED.
- 표준 본문은 contracts/standard-v1.txt의 고정 양식이다. 상품의 현재 정보 대신 주문 스냅샷·가격·소요일과 양측 표시 이름을 문서에 고정한다. 특약은 5,000자 이내다.
- 서비스 잠금은 회원 공유 잠금 → 주문 쓰기 잠금이다. READ_COMMITTED에서 현재 버전을 확인하고 이전 계약을 SUPERSEDED로 전환·flush한 뒤 새 버전을 추가한다.
- 이전 서명은 보존하지만 새 버전에 재사용하지 않는다. SIGNED 내용은 교체할 수 없다. 동일 초안 재요청과 동일 당사자의 서명 재전송은 추가 행을 만들지 않는다.
- 문서 JSON은 현재 필드가 모두 스칼라인 고정 스키마이며 키 정렬 후 SHA-256을 계산한다. MySQL JSON 재조회 시 키 순서가 바뀔 수 있어 같은 정렬 규칙으로 복원·검증한다. 표준 본문의 줄바꿈도 LF로 정규화한다. PDF 해시는 별도로 보관한다.
- 기존 전액 결제 API는 CONTRACT_V2 주문을 CONTRACT_PAYMENT_NOT_READY로 차단한다. 계약 주문이 서명 없이 기존 API에서 주문 결제 금액만 차감되지 않도록 한 안전장치다.

##### 마이그레이션

#### API·입력·오류

##### 오류와 후속 연결

- 현재 회원 없음/로그인 ID 없음: 401 `AUTHENTICATION_REQUIRED`. 일반 회원: 403 `FORBIDDEN`.
- 관리자 권한 확인 후 잘못된 주문 ID·버전: 400 `VALIDATION_FAILED`.
- 없는 주문·계약·버전 또는 LEGACY_V1 주문: 404 `RESOURCE_NOT_FOUND`.
- 향후 관리자 Controller는 세션에서 얻은 관리자 ID로 호출하고 관리자 접근 통제·`Cache-Control: no-store`를 적용한다. 요청의 관리자 ID를 신뢰하지 않는다.
- 관리자 PDF 실행 내부 입구는 [AdminReprocessingService](#admin-reprocessing)로 구현했다(#167). 감사 요청에 연결된 별도 관리자 경로에서 권한·주문 잠금·현재 계약 버전·취소 요청·시각·처리권을 재검사한다. 기존 구매자/판매자 ID를 대신 넣어 당사자 API를 호출하지 않는다. 관리자 실행 API·화면은 김창재가 [연결](#admin-reprocessing-api)했다(#168).

##### API·화면 연결

<a id="b-40-0"></a>
`GET /api/admin/orders/{orderId}/contract-pdf?version=`(no-store)로 이 서비스를 그대로 호출하고, `/admin/finance/orders/:id` 화면의 "계약 PDF" 칸에 버전·상태·파일 정보 유무·생성 후보 판단을 보여 준다. 현재 버전의 생성 후보이면 사유를 적고 재생성을 요청할 수 있다(2026-11-10, [K6 실행 API·화면](#admin-reprocessing-api)). [API 9-3절](architecture.md#b-204-1).

##### 계약서 PDF 생성·복구·당사자 다운로드

황준범 담당 PDF 생성·비공개 보관·실패 재시도 API다. 기존 [계약 버전·서명 기반](features.md#orders-contract)에 연결하며 신규 거래 정책은 [4인 설계](change-history.md#b-94-0)를 따른다.

##### API

기존 로그인 세션과 공통 CSRF 보호를 사용한다. 회원번호·파일 경로·렌더링 결과·해시는 클라이언트 입력으로 받지 않는다.

| 메서드·경로 | 입력 | 성공 | 권한 |
| --- | --- | --- | --- |
| GET `/api/orders/{orderId}/contract` | 선택 쿼리 `version`(1 이상, 생략 시 현재) | 200 계약 JSON | 주문 당사자 |
| POST `/api/orders/{orderId}/contract/generate` | JSON `{"version":1}` + CSRF | 200 READY/기존 문서, 다른 요청이 생성 중이면 202 GENERATING | 판매자 |
| GET `/api/orders/{orderId}/contract/file` | 선택 쿼리 `version` | 200 PDF 첨부 파일 | 주문 당사자 |

JSON은 `id`(문자열), `version`, `status`, `specialTerms`, `canonicalContent`, `contentHash`, `fileHash`(생성 전 null), `signatures` 배열이다. 서명 항목은 `memberId`(문자열)·`role`·`signedAt`(UTC 오프셋 포함 ISO-8601)이다. 원문·파일의 해시와 실제 다운로드 파일을 대조할 수 있다. 저장소 키·생성 시도 키·서버 경로는 응답에 포함하지 않는다. 성공 응답은 `Cache-Control: no-store`, PDF는 `Content-Disposition: attachment`와 `nosniff`를 사용한다.

| HTTP·오류 코드 | 의미·처리 |
| --- | --- |
| 401 AUTHENTICATION_REQUIRED | 로그인 필요 |
| 403 FORBIDDEN / CSRF 공통 오류 | 구매자의 생성 요청 또는 유효 CSRF 없음 |
| 404 RESOURCE_NOT_FOUND | 타인/다른 정책 주문·없는 계약/버전. 타인에게 존재 여부를 공개하지 않음 |
| 400 VALIDATION_FAILED | 잘못된 주문번호·버전·JSON |
| 409 CONTRACT_CONFLICT | 버전 불일치·생성 불가 주문·준비되지 않은 파일 |
| 409 CONTRACT_GENERATION_STALE | 생성 도중 계약 교체·주문 종료·시도 키 변경. 최신 계약 재조회 |
| 503 CONTRACT_GENERATION_FAILED | 렌더링/저장 실패. 상태 조회 후 생성 재시도 |
| 503 CONTRACT_FILE_UNAVAILABLE | 파일 유실·크기/해시 불일치. 관리자 점검 필요 |

DB 오류·커밋 결과 불명확은 기존 공통 오류 처리로 반환한다. 상태를 재조회한 뒤 GENERATING이면 기한 이후 재시도한다. 오류 응답 형식은 기존 `code/message/fieldErrors/traceId`다.

#### 화면·캐시·계정 경계

##### 2차: 주문 상세 PDF 화면 연결

<a id="b-261-0"></a>
1차 백엔드는 `2558d2a`, 문서·main 통합 기록은 `5e32d92`로 커밋했다. main `63ac00d`를 병합한 뒤 같은 브랜치에서 PDF 화면을 추가했다. 아래 1차 범위의 ‘화면 제외’는 최초 작업 기록이며 2차 PDF 전용 화면과 구분한다.

- `features/contracts`: API·타입·조회 훅·원문 표시·ContractPanel을 분리한다. `/orders/:id`의 CONTRACT_V2 주문에서만 표시한다. LEGACY_V1 화면에는 추가 요청을 만들지 않는다.
- 당사자 계약 상태·이전 버전·원문/특약 확인, 판매자 생성/재시도, 양측 버전 지정 다운로드를 연결한다. 버전은 서버가 다시 검증한다. 초안/서명 명령·주문 상태 전환은 E4/E5 담당 범위를 유지한다.
- GENERATING은 5초마다 조회하며 오류가 나면 주기 조회를 멈춘다. 변경 요청은 자동 재전송하지 않고 불명확한 결과를 조회하도록 안내한다.
- 공통 `apiPdfRequest`는 같은 세션으로 바이너리를 요청하며 JSON 오류를 공통 ApiError로 처리한다. HTML 성공 응답을 다운로드하지 않는다. 파일은 고정 버전을 지정해 받고 브라우저 SHA-256과 해당 버전 메타데이터를 대조한다.
- 바이너리는 Query 캐시에 넣지 않는다. 개인 조회는 회원 ID를 포함한 `wallet/contract` 키로 관리해 기존 계정 캐시 폐기 정책을 따른다. 처리 전/다운로드 직전에 현재 세션을 재확인하며 화면 종료·계정 변경 시 진행 중 다운로드를 폐기한다.
- 기본 생성 기한은 서버 설정에서 바꿀 수 있다. 화면은 기본 2분을 안내하며 실제 재시도 허용 여부는 서버가 판단한다.

#### 처리 순서·동시성·재전송

##### 트랜잭션과 동시성

1. `ContractDocumentTransactions.claim`: 회원 공유 잠금 → 주문 쓰기 잠금, 당사자·정책·주문 상태·현재 버전·원문 해시 확인. DRAFT/FAILED 또는 기한 지난 GENERATING을 생성 중으로 반영하고 커밋한다. 유효한 다른 시도는 새로 만들지 않는다.
2. `ContractDocumentService`: 트랜잭션 밖에서 PDF 렌더링·비공개 파일 저장. `Propagation.NEVER`로 호출자의 긴 트랜잭션에 묶이는 것도 차단한다.
3. `complete`: 다시 같은 잠금 순서를 적용한다. 현재 계약 ID·시도 키·주문 상태를 확인한 뒤 파일 키·크기·SHA-256과 READY를 한 트랜잭션으로 반영한다.
4. 계약 교체·종료·기한 후 새 시도가 먼저 반영됐다면 늦은 결과는 현재 문서로 채택하지 않는다. 주문 잠금 아래 `existsByFileStorageKey`로 이전 버전까지 확인하고 참조되지 않는 해당 시도의 파일만 삭제한다. 이미 보관된 버전의 완료 작업이 재전달돼도 그 파일을 지우지 않는다.
5. DB 커밋 결과가 불명확하면 파일을 삭제하지 않는다. 이미 커밋된 READY 문서가 가리킬 수 있기 때문이다. 상태 조회·기한 후 재시도로 복구하며 참조되지 않는 파일이 남을 수 있다.

DB와 파일시스템을 하나의 원자적 트랜잭션으로 묶지는 않는다. 미참조 파일 자동 청소·주기 복구 Batch는 후속이다. 참조 여부/진행 중 시도/경과 시간을 확인하는 청소 정책 없이 파일을 일괄 삭제하지 않는다.

#### 실행·환경

##### 개인 실행 설정

추가 설정 없이 프로젝트 실행 디렉터리의 `.local-data/contracts`를 사용한다. `.local-data/`는 Git에서 제외한다. 외부 웹 정적 리소스 경로로 노출하지 않는다.

필요할 때 기존 개인 설정 파일에 다음 값을 추가할 수 있다. `${...}`나 키 파일 처리 방식은 기존 프로젝트 설정을 따른다.

```properties
contracts.storage-root=C:/project/.local-data/contracts
contracts.generation-timeout=PT2M
```

생성 기한은 0 초과·30분 이하 ISO-8601 Duration이다. 요청마다 별도 스레드/큐를 추가하지 않고 생성 요청이 동기로 실행된다. 서버 UUID 파일명만 허용하고 경로 탈출·심볼릭 링크·덮어쓰기를 차단한다. 최대 원문 250,000 UTF-16 코드 단위, PDF 150페이지·16MiB로 자원 사용을 제한한다.

현재 저장 어댑터는 단일 서버 로컬 디스크용이다. 배포 시 영속 볼륨·백업과 DB/파일 복원 절차가 필요하며 다중 인스턴스 공유 저장은 후속이다. 임의 공개 URL·외부 저장소·보관 기간 정책을 구현한 것으로 표현하지 않는다.

<a id="orders-delivery"></a>
### 납품·수정·재납품

납기는 결제 시각+주문 당시 소요일×24시간이다. 납품은 설명·HTTPS 결과물 링크·회차로 보존한다. 수정은 원래 범위에서 거래 전체 1회이며 요청 시각+원래 소요일을 재납품 기한으로 삼는다. 최초 납품의 지연 재전송을 수정 납품으로 바꾸지 않는다. 기존 거래·취소 대기·정책 버전·회차를 서버가 함께 검사한다.

#### 화면·캐시·계정 경계

##### 화면 연결

주문 상세에서 DELIVER가 허용된 판매자에게 설명·HTTPS 링크 입력을 표시한다. 현재 상태가 IN_PROGRESS면 1회차, REVISION_IN_PROGRESS면 2회차를 정수로 전송한다. 설명 1~5000자·URL 최대 2048자는 공통 Unicode 정규화 기준으로 검사하고 URI의 최종 유효성은 서버에서 검증한다. POST 자동 재전송은 없으며 실패 시 입력을 비우고 상태 재조회를 요구한다. 재조회 후 새 회차가 표시돼도 이전 내용을 자동 제출하지 않는다. 서버·스키마는 변경하지 않았다.

주문 상세에서 REQUEST_REVISION이 허용되고 최초 납품이 있는 경우 사유 입력을 표시한다. 최초 납품 ID를 문자열로 전송하고 공통 Unicode 정규화 기준 사유 1~1000자를 검사한다. 요청된 사유·시각·서버가 계산한 수정 기한을 상세에 표시한다. 기한을 클라이언트에서 계산하거나 전송하지 않는다. 기존 변경 중 잠금·CSRF·오류 후 재조회 흐름을 사용하며 POST는 자동 재전송하지 않는다.

#### API·입력·오류

##### HTTP 계약

```text
POST /api/orders/{id}/deliveries
```

```json
{"description":"요청한 결과물을 전달합니다.","resultUrl":"https://example.com/result","expectedSequenceNo":1}
```

- 인증 principal의 회원 ID만 사용하고 CSRF를 요구한다. 판매자만 제출할 수 있다. 주문 구매자는 403, 비참여자와 없는 주문은 동일 404다.
- description·resultUrl은 문자열, expectedSequenceNo는 JSON 정수 1 또는 2 필수다. 문자열/소수 회차나 숫자 설명을 조용히 변환하지 않는다.
- 설명은 Unicode 공백·줄바꿈 정규화 후 1~5000 코드 포인트, URL은 정규화 후 최대 2048 코드 포인트다.
- URL은 URI 파서로 HTTPS 스킴·호스트 존재·userinfo 부재를 확인한다. 형식/스킴 오류는 400 INVALID_URL이다. 서버는 링크에 접속하거나 파일을 가져오지 않는다.
- 최초 납품은 IN_PROGRESS에서 1회차, 재납품은 REVISION_IN_PROGRESS에서 2회차만 허용한다. 성공하면 AWAITING_CONFIRMATION으로 전환하고 200 OrderDetail·no-store를 반환한다.

##### 계약

```text
POST /api/orders/{id}/revision-request
```

```json
{"deliveryId":"15","reason":"요청 범위 안에서 색상을 수정해 주세요."}
```

#### 처리 순서·동시성·재전송

##### 재전송·상태·이력

실행자 회원 공유 잠금 → 참여자 주문 잠금 → 판매자 확인 → 기존 회차 확인 → 현재 상태/회차 확인 → 대기 취소 요청 없음 확인 → 재납품 근거 확인 → 저장 순서다. 기존 수락·거절의 회원→주문 잠금 순서를 재사용한다.

이미 제출한 회차는 내용이 같거나 현재 상태가 수정 진행으로 바뀌었어도 409 DELIVERY_ALREADY_SUBMITTED다. 미존재 회차라도 상태와 맞지 않으면 409 DELIVERY_SEQUENCE_CONFLICT, 대기 취소 요청이 있으면 409 CANCELLATION_PENDING이다. 클라이언트는 같은 회차의 재전송을 다음 회차로 바꾸지 않고 GET 상세로 실제 기록을 확인한다.

재납품은 같은 주문의 최초 1회차 납품을 참조한 수정 요청 1건이 있어야 한다. 상태만 REVISION_IN_PROGRESS인데 이 근거가 누락되거나 연결이 틀리면 500 DATA_INTEGRITY_ERROR다. 수정 요청 쓰기는 [구매자 수정 요청 API](features.md#orders-delivery)를 따른다.

Delivery는 기존 deliveries에 설명·URL·회차·UTC 마이크로초 제출 시각을 저장한다. DELIVERED 이력의 actor는 판매자, fromStatus는 IN_PROGRESS/REVISION_IN_PROGRESS, toStatus는 AWAITING_CONFIRMATION, reason은 null이다. 주문 updated_at을 갱신하고 최초/수정 납기·결제 시각·금액은 보존한다. 제출 시각과 기한을 사용한 화면의 지연 표시는 후속 구현이다.

READ_COMMITTED 트랜잭션 안에서 납품·주문 상태·이력·상세 응답을 함께 처리한다. 어떤 단계든 실패하면 전체 변경을 롤백하며 동일 회차를 정상 재시도할 수 있다. (order_id,sequence_no) UNIQUE를 유지하고 주문 잠금으로 동시 제출을 직렬화한다. 다른 서비스도 취소/수정 등 상태 변경 전에 같은 주문을 잠가야 한다.

#### 업무 규칙·연결

##### 결과물 링크 스킴 표시 보완

<a id="b-708-0"></a>
서버 Delivery.create는 HTTPS 스킴을 대소문자 구분 없이 허용하지만 주문 상세 화면은 소문자 접두어만 표시해, HTTPS:// 또는 hTtPs://로 저장된 링크가 숨겨졌다. 화면의 HTTPS 접두어 판정을 대소문자 구분 없이 수행하도록 수정하고 저장 URL 원문과 새 창 링크의 noopener noreferrer를 유지한다. 서버 URL 검증·납품 저장·회차·권한은 변경하지 않는다.

##### 기한·이력·저장

수정 요청 시각은 서버 Clock의 Instant를 UTC·마이크로초로 변환한다. 새 기한은 수정 요청 시각 + orders.duration_days_snapshot(1일=24시간)이다. 현재 상품의 소요일이나 클라이언트가 제출한 기한은 사용하지 않는다. 최초 orders.due_at·paid_at·금액은 보존한다. 수정 요청일과 결제일이 다르면 최초 납기와 수정 납기도 다를 수 있다.

기존 revision_requests 테이블에 요청 ID·주문/납품 ID·정규화 사유·requested_at·due_at을 매핑했다. 수정 요청·주문 상태/updated_at·REVISION_REQUESTED 이력·현재 상세 응답을 READ_COMMITTED 한 트랜잭션으로 처리한다. 이력 actor는 구매자, fromStatus는 AWAITING_CONFIRMATION, toStatus는 REVISION_IN_PROGRESS이며 사유는 같은 정규화 원문이다.

회원 공유 잠금 → 참여자 주문 FOR UPDATE → 구매자 확인 → 기존 수정 요청 없음 → 상태 → 납품 소속/회차 → 취소 대기 없음 → 저장 순서다. 상태 변경 서비스는 같은 주문 잠금을 사용해야 한다. (order_id)·(delivery_id) UNIQUE를 유지하며 동시에 두 요청이 오면 한 요청만 성공한다. 어떤 저장/응답 조립 단계에서든 실패하면 새 요청·상태·기한·이력 전체를 롤백한다.

수정 요청 후 판매자는 기존 납품 API로 expectedSequenceNo=2를 제출한다. 이후 상태는 AWAITING_CONFIRMATION, 구매자는 추가 수정 요청 없이 확인할 수 있다. 허용 행동에 CONFIRM이 있다는 것이 구매 확정 API의 구현 완료를 뜻하지는 않는다.

<a id="orders-cancel"></a>
### 취소 요청·운영 문의

결제 전 취소, LEGACY_V1의 직접 취소/전액 반환, 결제 후 구매자 요청·판매자 승인/거절·구매자 철회를 구분한다. V3 결제 후 반환은 보관금 전액과 종료 기록을 원자 처리한다. 운영 문의 CANCELLATION_REVIEW에 대한 관리자 승인/거절은 주문 영역이 트랜잭션을 소유한다. 일반 제재 자체가 거래를 자동 취소하지 않는다.

#### 업무 규칙·연결

##### 문의 엔티티 (`inquiry` 패키지)

김창재 선행 작업이던 문의 엔티티를 K4 진행을 위해 정은찬이 먼저 만들었다(2026-11-06 조장 결정). 문의 접수·목록·답변 API는 김창재 담당으로 남는다.

| 항목 | 내용 |
| --- | --- |
| 엔티티 | `inquiry/entity/SupportInquiry` ↔ `support_inquiries`(V5). 유형 `CANCELLATION_REVIEW`·`GENERAL`, 상태 `OPEN`·`IN_REVIEW`·`CLOSED` 문자열 상수 |
| 생성 | `SupportInquiry.cancellationReview(requesterId, orderId, cancellationRequestId, subject, at)`, `SupportInquiry.general(requesterId, orderId 또는 null, subject, at)`. 제목은 공백 정리 후 1~150자(벗어나면 `IllegalArgumentException`, 접수 서비스가 400으로 바꾼다) |
| 종료 | `close(adminId, at)`: 열린 문의만, 처리 시각이 생성·최근 변경 시각 이후. `assigned_admin_id`=종료한 관리자, `resolved_at`·`updated_at`=처리 시각. 되돌리는 메서드는 없다 |
| 저장소 | `inquiry/repository/SupportInquiryRepository`: `findTarget(id)`(잠금 전 유형·주문 ID만 읽는 projection), `findForUpdate(id)`(배타 잠금) |
| 접수 서비스가 검증할 것(김창재) | 신청자 = 주문 구매자, 연결한 취소 요청이 그 주문의 `PENDING` 요청, V3 결제 후 주문. 같은 취소 요청의 열린 문의 중복 여부(DB UNIQUE 없음) |

DB CHECK(`ck_inquiry_type`·`ck_inquiry_status`·`ck_inquiry_time`)와 같은 상태 조합을 엔티티가 먼저 지킨다. 복합 FK(`cancellation_request_id, order_id`)가 다른 주문의 취소 요청 연결을 막는다.

##### 승인·거절·철회

요청이 해당 주문에 속하는지 확인하고 같은 주문 잠금 안에서 해결한다. 같은 해결 동작 재전송은 200으로 현재 결과를 반환하며 해결 시각·응답 사유·이력을 덮어쓰지 않는다. 다른 해결 동작은 409 REQUEST_ALREADY_RESOLVED다.

승인은 기존 OrderCancellationService를 같은 트랜잭션에 참여시켜 주문 CANCELLED·원결제 REFUNDED·전액 지갑 복구·REFUND 원장·CANCELLATION_APPROVED/CANCELLED 이력을 함께 처리한다. 같은 환불 코드를 복제하지 않는다. 새 승인 요청에는 responseReason 입력이 없으므로 null로 보존한다. 판매자 직접 취소가 먼저 대기 요청을 승인했다면 승인 재전송은 이미 저장된 결과를 그대로 반환한다.

READ_COMMITTED 한 트랜잭션에서 요청 해결·주문·금액·이력·상세 응답을 처리한다. 회원 공유 잠금 → 주문 배타 잠금 → 환불 시 구매자 지갑 배타 잠금 순서를 지킨다. 요청 행은 기존 직접 취소 JDBC 갱신과 같은 원문을 조회해 JPA 캐시와의 불일치를 피한다. 단계 중 실패하면 전체를 롤백한다. 대기 요청 복수·요청자 불일치 등 모순은 자동 보정하지 않는다.

##### 주문 직접 취소·적립금 환불

POST /api/orders/{id}/cancel. 본문은 `{"reason":"취소 사유"}`. 인증 참여자·CSRF가 필요하며 200 OrderDetail·no-store를 반환한다. 사유는 공통 문자열 정규화 후 1~1000 코드 포인트다. 숫자 사유는 허용하지 않는다.

구매자는 결제 전 취소 가능하다. 판매자는 명세 5절에 따라 수락 후 미완료 주문을 직접 취소할 수 있다. REQUESTED에서 판매자는 기존 거절 API를 사용한다. 결제 후 구매자는 직접 취소할 수 없고 취소 요청을 사용해야 한다. 완료·거절 주문은 409, 비참여자·없는 주문은 동일 404다.

회원 공유 잠금 → 주문 배타 잠금 → 구매자 지갑 배타 잠금 순서를 사용한다. READ_COMMITTED 한 트랜잭션에서 주문 CANCELLED·취소 시각·원결제 REFUNDED·전액 지갑 복구·REFUND 원장·CANCELLED 이력을 반영한다. 원장 키는 REFUND:{paymentId}다. 구매자와 환불액은 주문/원결제에서 가져오며 클라이언트가 지정하지 않는다. UTC 마이크로초 시각을 공유하며, 결제 주문은 구매자 지갑 잠금을 얻은 뒤 그 시각을 정한다(잠금 대기 중 같은 구매자의 다른 거래가 지갑을 먼저 갱신해도 환불이 실패하지 않게 한다).

대기 취소 요청이 한 건이면 직접 취소와 함께 APPROVED로 해결하고 CANCELLATION_APPROVED 이력도 기록한다. 대기 요청이 여러 건인 모순 상태는 500 DATA_INTEGRITY_ERROR로 롤백한다. 요청 해결·원장·이력·응답 조립 중 실패도 전체 롤백한다.

정상 CANCELLED 재호출은 금액을 다시 지급하지 않고 현재 주문을 반환한다. 유료 취소는 원결제·환불 원장 일치를 확인한다. 정상 상태와 기록이 모순되면 자동 보정하지 않는다. 결제 전 취소에서는 결제·금액 원장을 만들지 않는다. 토스 충전 원결제 취소·현금 환불·부분 환불은 제공하지 않는다.

초기 직접 취소 구현 이후 [취소 요청 4종 API](features.md#orders-cancel)와 [구매 확정/정산](features.md#finance-settlement)이 연결되었다. 취소 요청 승인은 이 서비스를 같은 트랜잭션으로 호출하며, 실제 확정·직접 취소와의 경쟁은 후속 취소 요청 통합 테스트에서도 검증한다.

OrderCancellationIntegrationTests는 기존 결제 fixture와 시나리오를 재사용해 결제 회귀도 검사한다. 추가로 유료/무료 취소, 반복/동시 환불, 구매자 유료 직접 취소 차단, 타인 접근, 완료 후 환불 금지, 대기 요청 해결, 실제 이력 FK 오류 롤백과 결제/취소 경쟁을 검사한다. 실제 로그인은 테스트 principal로 대체한다. 검증 실행 결과는 완료 후 아래 기록한다.

#### API·입력·오류

##### 서비스 계약

<a id="b-30-0"></a>
`order/service/AdminCancellationReviewService`(트랜잭션 소유, `READ_COMMITTED`):

- `approve(long adminId, long inquiryId, String reason)` → `AdminCancellationReviewResponse`
- `reject(long adminId, long inquiryId, String reason)` → `AdminCancellationReviewResponse`
- 응답: `inquiryId`·`orderId`·`cancellationRequestId`(문자열), `decision`(`APPROVED`·`REJECTED`), `orderStatus`, `inquiryStatus`(`CLOSED`), `resolvedAt`(UTC). 관리자는 주문 당사자가 아니라 주문 상세를 돌려주지 않는다.
- 사유는 공백 정리 후 1~1000자이며 취소 요청의 `response_reason`과 주문 이력 사유로 남는다(양측 주문 상세의 "처리 사유"로 보인다).

| 상황 | 결과 |
| --- | --- |
| 사유 공백·1000자 초과, 문의 ID 0 이하 | 400 `VALIDATION_FAILED` |
| 실행 회원 없음 | 401 `AUTHENTICATION_REQUIRED` |
| 실행 회원이 ADMIN이 아님 | 403 `FORBIDDEN` |
| 문의 없음 | 404 `RESOURCE_NOT_FOUND` |
| 일반 문의 | 409 `INQUIRY_NOT_CANCELLATION_REVIEW` |
| 취소 요청이 이미 판매자 승인·거절/구매자 철회됨 | 409 `REQUEST_ALREADY_RESOLVED`(문의는 열린 채로 남는다. 종료는 문의 영역이 별도로 처리) |
| V3가 아닌 주문 | 409 `ORDER_POLICY_NOT_READY` |
| 결제 후 미완료 상태가 아님 | 409 `ORDER_STATE_CONFLICT` |
| 이미 종료된 문의 + 같은 관리자 결정 | 200 현재 결과(재전송) |
| 이미 종료된 문의 + 다른 결정 | 409 `INQUIRY_CLOSED` |
| 기록 불일치(문의 신청자·요청자 ≠ 구매자, 대기 요청 2건 이상, 활성 타이머 남음 등) | 500 `DATA_INTEGRITY_ERROR` |

컨트롤러(김창재)가 할 일: POST·CSRF·`Cache-Control: no-store`, `@CurrentMemberId`로 관리자 ID 전달, 문의 ID 경로 변수의 형식 검사. 권한·상태 판단은 서비스가 한다.

##### API·화면 연결

<a id="b-34-0"></a>
이 서비스와 문의 엔티티를 그대로 사용해 구매자 접수·관리자 처리 경로를 연결했다. 경로·오류는 [API 9-1절](architecture.md#b-202-1). DB·Flyway 변경 없음.

| 구분 | 백엔드 | 화면 |
| --- | --- | --- |
| 구매자 검토 요청 | `inquiry/service/InquiryService`·`InquiryController`. 회원 공유 잠금 → 주문 배타 잠금(`OrderCommandLocks`) 아래에서 구매자·대기 요청·V3 결제 후를 확인하고, 같은 요청의 열린 문의가 있으면 그 문의를 돌려준다(UNIQUE 없이 중복 방지) | 주문 상세 취소 요청 아래 `CancellationReviewRequest`(V3 주문에서 내 문의 상태 표시, 구매자 대기 요청이고 열린 문의가 없을 때만 입력 칸) |
| 내 문의 | `GET /api/me/inquiries`(`SupportInquiryQueries`, QueryDSL) | 위 컴포넌트가 주문별로 조회(`['wallet', 'order-inquiries', id]`, 주문 명령 뒤 함께 갱신) |
| 관리자 목록·상세 | `admin/service/AdminInquiryService`·`AdminInquiryQueries`. 현재 DB 역할 재확인, 상세에 주문 요약·취소 요청·allowedActions | `/admin/inquiries`(상태·유형 필터 URL 보존), `/admin/inquiries/:id`, 관리자 탭(`AdminNav`) |
| 승인/거절 | `AdminInquiryController`가 이 서비스(`approve`·`reject`)를 그대로 호출 | 처리 사유 1~1000자, 승인은 되돌릴 수 없음 확인 체크 후에만 버튼 활성 |
| 처리 불가 문의 종료 | `AdminInquiryService.close`: 관리자 공유 → 주문 배타 → 문의 배타 잠금(이 서비스와 같은 순서), 요청이 대기 중이면 409 `REQUEST_STILL_PENDING`, 닫힌 문의는 200 재전송 | 상세의 "문의 닫기"(allowedActions에 CLOSE일 때만) |

화면 명령은 처리 중 버튼 차단·ref 이중 클릭 차단·자동 재전송 없음이다. 409는 서버 안내를 보여 주고 최신 상태를 다시 불러오며, 통신 오류·5xx는 서버가 처리했을 수 있어 실패로 단정하지 않고 다시 조회한다.

결정(2026-11-09 사용자 확정): 판매자 무응답 대기 시간 조건 없음(정책에 없음, 관리자가 판단), 구매자 입력은 제목 1~150자만(문의 본문 칸 없음), 같은 요청 재접수는 기존 문의 200, 종료 사유 없이 닫기(DB 칸 없음, 메시지 기능에서 후속).

##### HTTP 계약

| 경로 | 실행자·입력 | 성공 |
| --- | --- | --- |
| POST /api/orders/{id}/cancellation-requests | 구매자; reason,requestKey(UUID) | 신규 201 / 기존 키 200 CancellationResult |
| POST /api/orders/{id}/cancellation-requests/{requestId}/approve | 판매자; 본문 없음 | 200 CancellationResult |
| POST /api/orders/{id}/cancellation-requests/{requestId}/reject | 판매자; responseReason 선택, 본문 생략 가능 | 200 CancellationResult |
| POST /api/orders/{id}/cancellation-requests/{requestId}/withdraw | 구매자; 본문 없음 | 200 CancellationResult |

모두 인증 principal·CSRF를 사용하고 no-store를 반환한다. 반대 역할의 참여자는 403, 비참여자·없는 주문·해당 주문에 속하지 않은 요청은 404다. ID는 양의 BIGINT 문자열, 문자열 입력은 숫자에서 자동 변환하지 않는다. reason은 공백/줄바꿈 정규화 후 1~1000 코드 포인트다. responseReason은 미입력/null이면 null, 입력 시 같은 길이 기준을 적용한다. UUID는 소문자 하이픈 표준형으로 저장한다.

CancellationResult는 cancellationRequest와 현재 order(OrderDetail)다. 요청 항목은 id,orderId,requestKey,reason,status,requestedAt,resolvedAt,resolvedBy,responseReason이며 ID는 문자열, 시각은 UTC다. 내부 requested_by·원장 키·회원 비밀번호를 노출하지 않는다.

##### 직접 취소 입력 검사 보완

<a id="b-744-0"></a>
주문 상세의 직접 취소 사유를 서버와 같은 Unicode 공백·줄바꿈 정규화 후 1~1000 코드 포인트로 검사한다. 글자 수·최대 길이를 표시하고 범위를 벗어나면 취소 버튼과 제출을 막는다. 프런트 cancelOrder도 동일 기준으로 검증·정규화하므로 잘못된 입력은 CSRF 조회·변경 요청 전에 거절한다. 서버의 권한·취소/환불·멱등 처리 정책은 변경하지 않는다.

#### 처리 순서·동시성·재전송

##### 잠금 순서·트랜잭션

관리자 회원 공유 잠금 → 주문 배타 잠금(`findForSystemUpdate`) → 문의 배타 잠금 → (승인 시) 구매자 지갑 잠금. 문의의 주문 ID는 바뀌지 않으므로 잠금 전에는 projection으로 유형·주문 ID만 읽고, 주문을 잠근 뒤 문의를 잠가 다시 확인한다(먼저 엔티티를 읽으면 잠금 조회가 캐시된 값을 돌려줄 수 있다).

판매자 승인(`/cancel`·취소 요청 승인)과 구매자 철회도 주문 잠금으로 직렬화되므로 둘 중 먼저 잠근 쪽만 반영된다. **취소 검토 문의의 상태를 바꾸는 다른 경로(문의 영역의 종료·담당 지정)는 주문 → 문의 순서를 지키거나 문의만 잠가야 한다**(문의 → 주문 순서는 교착 위험).

승인 금액 처리는 `FullFundingService.adminRefund`가 판매자 승인 환불과 같은 검증(결제·보관·원장 일치, 보관 HELD, 종료·정산 없음, 활성 타이머 없음)을 거쳐 구매자 지갑 환불·`REFUND` 원장·결제/보관 `REFUNDED`·종료 기록을 남긴다. 이력은 관리자 실행자의 `CANCELLATION_APPROVED`(알림 없음)·`CANCELLED`, 거절은 `CANCELLATION_REJECTED`다. 승인 처리 시각은 구매자 지갑 잠금 뒤에 정하고, 환불·요청 처리·종료 기록·주문 취소·이력·문의 종료에 같은 시각을 쓴다.

##### 생성·재전송

회원 공유 잠금 → 참여자 주문 FOR UPDATE → 구매자 확인 → 기존 키와 정규화 사유 비교 → 신규 키인 경우 유료 미완료 상태(IN_PROGRESS/AWAITING_CONFIRMATION/REVISION_IN_PROGRESS) → 대기 요청 없음 → 요청·이력 저장 순서다.

같은 구매자/주문/키·같은 사유는 이미 승인·거절·철회되거나 주문이 완료·취소된 뒤에도 기존 요청과 현재 주문을 200으로 반환한다. 같은 키·다른 사유는 409 IDEMPOTENCY_KEY_REUSED다. 새 키라도 대기 요청이 있으면 409 CANCELLATION_PENDING이다. 해결 후에는 현재 주문 상태가 허용할 때 새 키로 다시 요청할 수 있다.

제출은 PENDING 요청과 CANCELLATION_REQUESTED 이력을 만들며 주문 상태·돈·납기는 바꾸지 않는다. 구매자는 WITHDRAW_CANCELLATION, 판매자는 APPROVE_CANCELLATION/REJECT_CANCELLATION과 기존 직접 취소 행동을 받는다. 대기 중에는 기존 납품·수정·확정 서비스가 요청을 차단한다.

#### 화면·캐시·계정 경계

##### 알림·화면

- 승인: 기존 V3 규칙대로 `ORDER_FUNDS_REFUNDED`. 실행자가 당사자가 아니라 구매자·판매자 **양측**에 간다.
- 거절: 새 유형 `ORDER_CANCELLATION_ADMIN_REJECTED`(양측). 판매자 거절 문구("판매자가 거절했습니다")와 구분한다. 프런트 `notifications/messages.ts`에 문구 추가.
- 주문 이력에서 당사자가 아닌 회원 실행자를 "기록자" 대신 **"관리자"**로 표시한다(타이머 해제 이력에도 적용). `orders/history.ts`.
- 관리자 승인으로 취소된 주문에 판매자가 `/cancel`을 다시 보내면 판매자 승인 재전송으로 보지 않고 409 `ORDER_STATE_CONFLICT`다.

##### 주문 상세 화면

구매자 제출·철회, 판매자 승인·거절 입력과 취소 요청 내역을 연결했다. 서버 allowedActions와 단일 PENDING 요청으로 버튼을 결정한다. 승인에는 전액 적립금 환불 동의를 요구하고 거절 사유는 선택 입력이다. 거절·철회는 주문/결제를 유지한다는 안내를 표시한다.

모든 주문 행동이 같은 실행 잠금과 공통 CSRF·오류 후 재조회·캐시 무효화 흐름을 사용한다. 제출 키는 브라우저 crypto.randomUUID로 생성하며 결과 불명확 시 같은 화면에서는 키·정규화 사유를 유지하고 사유 편집을 잠근다. 400 입력 오류는 편집을 다시 허용한다. 요청 상태가 새로 조회되면 입력과 승인 동의를 초기화한다. POST 자동 재전송은 없고, 새로고침/화면 이탈을 넘는 키 복원은 지원하지 않는다. 실제 인증 브라우저 통합 검증은 후속이다.

<a id="domain-finance"></a>
## 지갑·결제·환불·정산

<a id="finance-wallet"></a>
### 지갑·초기 지급·원장

가입 시 지갑·최초 테스트 적립금 100만원을 1회 생성한다. 본인 잔액·원장만 조회하며 없는 지갑을 조회 중 임의 생성하지 않는다. 원장과 잔액을 같은 트랜잭션에서 갱신한다. MVP 판매 수익 분리와 V3 판매자 구매용 적립금 지급을 구분한다.

#### 업무 규칙·연결

##### 구현 범위

기존 V1의 `wallets`, `wallet_entries`에 Entity·Repository를 매핑하고 회원가입에서 호출할 내부 초기화 서비스를 구현했다. 테이블 추가·V1 수정·공개 API 추가는 하지 않았다.

- 회원당 지갑 한 개, 최초 테스트 적립금 1,000,000원 한 번 지급.
- 최초 내역은 `INITIAL_GRANT`, `payment_id=NULL`, `amount_delta=1000000`, `balance_after=1000000`.
- 고정 이벤트 키는 `INITIAL_GRANT:{memberId}`.
- 지갑·원장에는 같은 UTC 시각을 사용하고 마이크로초로 절삭한다.
- `Wallet.updatedAt`·`WalletEntry.createdAt`은 `LOCAL_DATE_TIME` JDBC 매핑으로 UTC 원문을 보존한다. 네이티브 지갑 INSERT에는 UTC로 변환한 `Instant`를 전달한다. `LocalDateTime` 파라미터를 그대로 바인딩하면 JVM 시간대에 따라 DB 원문이 이동할 수 있다.
- 기존 정상 지급 건을 재호출해도 현재 잔액·시각·기존 내역을 변경하지 않는다.

이 작업만으로 회원가입·로그인·잔액 조회 화면·주문 결제·환불·정산·외부 충전이 완성되는 것은 아니다. 실제 회원 Entity와 회원가입 서비스는 회원 담당자가 구현한다.

##### 구현 파일

기본 경로는 `LancerPick/src/main/java/com/example/lancerpick/`이다.

| 파일 | 책임 |
| --- | --- |
| wallet/entity/Wallet.java | 공유 PK `member_id`, 잔액, 갱신 시각 매핑 |
| wallet/entity/WalletEntry.java | 원장 매핑·초기 지급 기록 생성·기존 지급 근거 확인 |
| wallet/entity/WalletEntryType.java | 기존 V1의 INITIAL_GRANT·PAYMENT·REFUND 코드 |
| wallet/repository/WalletRepository.java | 회원 행 잠금·지갑 생성 시도·기존 지갑 잠금 조회 |
| wallet/repository/WalletEntryRepository.java | 이벤트 키로 기존 지급 근거 잠금 조회 |
| wallet/service/WalletInitializationService.java | 지급 정책·트랜잭션 참여·중복과 불완전 상태 검사 |

Member·Payment Entity를 중복 정의하지 않고 외래키 ID 컬럼을 매핑했다. 외래키 존재는 기존 MySQL 제약으로 확인한다. `WalletEntry`에는 수정·삭제용 도메인 메서드가 없고 공개 쓰기 API도 제공하지 않는다. Repository 자체를 DB 변경 권한 통제 장치로 표현하지는 않는다.

##### 현재 범위

[API 명세 8절](architecture.md#b-199-2)에 맞는 조회 DTO와 내부 `WalletQueryService`, 내역 조회 Repository를 준비했다. 초기 지급과 같은 지갑 기반 작업으로 현재 브랜치에서 이어서 작성했다.

| 보호 경로 | 내부 서비스 | 응답 |
| --- | --- | --- |
| GET /api/me/wallet | getBalance(authenticatedMemberId) | balance, currency=KRW, isTest=true |
| GET /api/me/wallet/entries?page=0&size=20 | getEntries(authenticatedMemberId,page,size) | PageResponse&lt;WalletEntryResponse&gt; |

조회 서비스는 readOnly 트랜잭션을 사용한다. 조회 중 지갑을 생성하거나 최초 적립금을 지급하지 않는다. 잔액 조회 결과는 표시용이며 결제 처리 시에는 지갑 잠금 후 다시 검증해야 한다.

##### 기능별 파일

기본 경로는 `LancerPick/src/main/java/com/example/lancerpick/`이다.

| 파일 | 책임 |
| --- | --- |
| wallet/dto/WalletBalanceResponse.java | 현재 테스트 잔액·통화·테스트 여부 응답 |
| wallet/dto/WalletEntryResponse.java | 내역 ID·유형·증감액·처리 후 잔액·주문 ID·UTC 시각 |
| wallet/repository/WalletEntryView.java | 필요한 컬럼만 받는 내부 조회 projection |
| wallet/repository/WalletEntryRepository.java | 회원별 내역 수·페이지 조회, payments LEFT JOIN |
| wallet/service/WalletQueryService.java | 입력 확인·지갑 확인·내역 DTO 변환·페이지 응답 |
| wallet/controller/WalletController.java | 인증된 본인 잔액·내역 GET, 페이지 입력 검증, Cache-Control: no-store |
| common/security/AuthenticatedMember.java | 인증 principal이 제공할 최소 회원 ID 인터페이스 |
| common/security/CurrentMemberId.java | 인증 상태·principal 타입·양수 ID 확인, 미충족 시 401 |

Entity와 내부 projection을 API로 직접 반환하지 않는다. `memberId`, `paymentId`, `eventKey`는 내역 응답에 포함하지 않는다.

##### 인증 담당자와 연결할 부분

```java
// 형태 설명용 예시. 실제 로그인 구현 클래스가 아님.
public record LoginMember(Long memberId) implements AuthenticatedMember {}
```

1. WalletController는 Spring Security가 제공한 Authentication을 CurrentMemberId에 전달한다.
2. 인증 여부·principal 인터페이스·양수 회원 ID가 확인되면 그 ID만 WalletQueryService에 전달한다.
3. principal이 없거나 계약에 맞지 않으면 401이다. username을 숫자로 파싱하거나 요청의 memberId/X-Member-Id를 신뢰하지 않는다.
4. Spring Security의 기존 보호 정책을 유지한다. 테스트 전용 인증 주입을 일반 실행에 추가하지 않았다.
5. 실제 로그인·로그아웃·세션 만료 및 본인 조회를 통합 검증하고, React 로그인 상태와 연결하는 작업은 남아 있다.

현재 HTTP 테스트는 테스트에서 구성한 인증 principal이 정확한 서비스 인자로 전달되는지, 다른 회원 ID를 요청에 넣어도 바뀌지 않는지 확인한다. DB 필터 분리는 별도 MySQL 테스트의 결과다. 실제 로그인 성공부터 DB 응답까지의 전체 흐름 검증을 완료한 것은 아니다. 가입 호출은 [최초 지급 계약](features.md#b-1031-0)을 따른다.

#### API·입력·오류

##### 회원가입에서 호출할 계약

<a id="b-1031-0"></a>
호출 대상은 다음 메서드다.

```java
walletInitializationService.initializeForNewMember(savedMemberId);
```

회원가입 서비스에서 **회원 저장 후 ID를 확정하고, 같은 트랜잭션 안에서 Spring이 주입한 서비스 빈을 호출**한다. 호출자의 회원 저장과 지갑·지급 내역은 함께 커밋·롤백한다.

| 항목 | 구현 계약 |
| --- | --- |
| 입력 | 서버가 저장한 회원 ID(long). 금액·이벤트 키는 클라이언트 입력으로 받지 않음 |
| 반환 | void. 최초 생성·정상 재호출 모두 정상 반환 |
| 트랜잭션 | `Propagation.MANDATORY`. 호출자의 트랜잭션이 없으면 처리 전 실패 |
| 회원 없음 | 404 `RESOURCE_NOT_FOUND` |
| ID가 0 이하 | 400 `VALIDATION_FAILED` |
| 기존 지갑의 최초 지급 근거 누락·불일치 | 500 `DATA_INTEGRITY_ERROR`. 자동 보정·재지급하지 않음 |
| 원장 INSERT 등 DB 처리 실패 | 예외 전파. 호출자가 숨기지 않고 전체 롤백 |

상위 회원가입의 중복 이메일·가입 요청 계약을 대신하는 기능은 아니다. 회원 담당자와 호출 위치·실패 전파를 함께 검토해야 한다. 현재 인증 principal 형식은 이 내부 서비스의 입력에 영향을 주지 않으며, 사용자 입력의 memberId를 그대로 신뢰하는 공개 엔드포인트는 만들지 않는다.

##### 저장 구조와 응답 규칙

기존 V1을 그대로 사용하며 컬럼·테이블·마이그레이션을 추가하지 않았다.

```text
wallet_entries.member_id → 조회 대상 지갑 필터
wallet_entries.payment_id → payments.id → payments.order_id
```

초기 지급은 payment_id가 NULL이므로 LEFT JOIN하고 응답의 orderId도 NULL이다. 결제·환불 내역의 주문 ID는 조인 결과로 가져오며 원장에 중복 저장하지 않는다. Payment Entity를 임의로 추가하지 않았다.

ID는 문자열로 변환한다. BIGINT 값이 JavaScript 안전 정수 범위를 넘더라도 주문 ID를 숫자로 변환해 전달하지 않는다. 금액은 기존 계약의 원 단위 정수다. DB의 UTC DATETIME은 네이티브 조회에서 JDBC UTC 기준 Instant로 받고 응답 경계에서 UTC OffsetDateTime으로 변환한다.

첫 검증에서는 네이티브 Timestamp를 LocalDateTime으로 투영할 때 JVM 기본 시간대가 섞여 9시간 이동하는 문제를 재현했다. Instant 투영으로 수정하고 UTC·Asia/Seoul·America/Los_Angeles 기본 시간대에서 같은 응답 시각을 확인한다. ORM Entity의 LocalDateTime 매핑과 네이티브 projection 변환을 동일하다고 가정하지 않는다.

정렬은 `created_at DESC, id DESC`로 고정한다. page는 0부터, size는 1~100이다. Controller는 생략 기본값 page=0, size=20을 적용하고 형식·범위를 검증한다. 내부 서비스도 두 값을 확인한다.

범위를 벗어난 페이지는 items=[]로 반환하고 전체 건수와 전체 페이지 수는 유지한다. 내역이 없으면 totalElements=0, totalPages=0이다. 조회 오프셋은 long으로 계산·전달해 큰 page에서 int 곱셈·변환 오류를 피한다.

같은 읽기 서비스 호출은 하나의 readOnly 트랜잭션에서 실행된다. 페이지별 별도 요청 사이에 데이터가 추가되면 목록이 달라질 수 있으며, 여러 페이지를 하나의 고정된 스냅샷으로 보장하는 기능은 아니다.

##### 오류와 없는 지갑

| 조건 | 내부 처리 |
| --- | --- |
| 회원 ID가 0 이하 | 400 VALIDATION_FAILED |
| page가 음수, size가 1~100 밖 | 400 VALIDATION_FAILED |
| 조회 대상 지갑 없음 | 404 RESOURCE_NOT_FOUND |
| 유효한 지갑의 내역 0건 | 정상 빈 목록, 자동 지급 없음 |

지갑이 없는 기존 회원을 보정하는 기능은 아니다. 조회 오류를 잡아 초기 지급 서비스를 호출하거나, 실패를 잔액 0원으로 바꾸지 않는다.

#### 처리 순서·동시성·재전송

##### 잠금·동시 요청 설계

```text
상위 회원가입 트랜잭션
 → 회원 PK 잠금·존재 확인
 → 공유 PK로 지갑 INSERT 시도
    ├─ 신규 1건: 최초 원장 INSERT
    └─ 기존 0건: 실제 지갑·최초 지급 내역을 잠금 조회해 확인
 → 상위 처리까지 성공하면 커밋
```

지갑 생성은 MySQL `INSERT IGNORE`의 반환 건수로 구분한다. ID는 존재·양수 여부를 확인하고, 금액은 서버 고정값, 시각은 UTC·마이크로초·MySQL 범위로 검증한다. 0건을 정상 지급으로 간주하지 않고 실제 지갑과 올바른 원장 존재를 확인한다. 그 밖의 반환값·불완전 상태는 실패한다. 기존 지갑의 잔액이나 updated_at을 덮어쓰는 upsert는 사용하지 않는다.

잠금 조회를 사용해 호출자가 앞서 일반 조회로 만든 REPEATABLE READ 스냅샷이 있더라도 다른 요청이 커밋한 지급 근거를 확인한다. PK·이벤트 UNIQUE 제약은 마지막 중복 방어선으로 유지한다.

##### 잠금·동시 요청 설계 / 테스트 중 발견한 교착 상태와 수정

없는 행에 잠금 조회를 먼저 하지 않고 지갑 INSERT를 먼저 시도하도록 바꿨다. 이후 같은 회원·서로 다른 회원의 동시 초기화 검사를 각각 5회 반복한다. 이는 이 경로의 검증이며 모든 거래·잠금 조합에서 데드락이 없다는 보장은 아니다.

#### 실행·환경

##### 테스트 실행과 결과

테스트 클래스는 `WalletInitializationIntegrationTests`다. 개발용 DB와 다른 `lancerpick_test` 또는 `lancerpick_ci`를 사용해야 한다. 설정은 [테스트 DB 안내](architecture.md#environment)를 따른다.

```powershell
cd C:\project\LancerPick
.\gradlew.bat test --tests "com.example.lancerpick.wallet.WalletInitializationIntegrationTests"
```

통합 테스트는 클래스 전체를 하나의 자동 롤백 트랜잭션으로 감싸지 않는다. 각 요청을 독립 트랜잭션으로 커밋하고 저장된 결과를 확인한다. 테스트용 회원은 SQL fixture로 만들며 실제 회원가입 구현 완료를 뜻하지 않는다.

| 검사 | 확인할 결과 |
| --- | --- |
| 최초 지급·JPA 매핑 | 회원·지갑·원장 각 1건, 100만 원, FK·타입·UTC 시각 일치 |
| 독립 호출 | 외부 트랜잭션 없으면 지급 전 거절 |
| 재호출 | 원장 ID·금액·시각 불변, 지급 1회 |
| 잔액 변경 후 재호출 | 낮아진 현재 잔액을 다시 100만 원으로 덮어쓰지 않음; 결제 동작 검증은 아님 |
| 같은 회원 동시 4요청 × 5회 | 지갑·원장 1개, 잔액·내역 합계 100만 원 |
| 서로 다른 회원 동시 4요청 × 5회 | 회원마다 지갑·원장 1개, 불필요한 초기화 교착 방지 |
| 기존 스냅샷 후 다른 요청의 지급 커밋 | 최신 지급 근거를 확인하고 추가 지급 없음 |
| 실제 원장 UNIQUE 실패 | 같은 트랜잭션의 새 회원·지갑·원장 모두 롤백 |
| 초기화 후 가입 후속 실패 | 새 회원·지갑·원장 모두 롤백 |
| 불완전 지갑·다른 회원의 원장 | DATA_INTEGRITY_ERROR, 자동 보정 없음 |
| 없는 회원·잘못된 ID | 신규 금액·내역 없음 |

DB 없는 메모리 예시(`demo`)에서는 초기화 서비스 빈을 만들지 않는다. DB를 제외하는 기본 프로필 HTTP 테스트에서는 이 서비스를 모의 빈으로 대체하고, 실제 서비스 동작은 위 MySQL 통합 테스트에서 확인한다.

#### 화면·캐시·계정 경계

##### 마이페이지 화면 준비

기존 AppLayout의 로고·검색·회원 메뉴·푸터와 흑백 스타일을 재사용한다. 회원 메뉴는 인증 담당자가 로그인 상태에 맞춰 연결할 예정이며 현재 로그인된 것처럼 바꾸지 않았다.

- `/mypage`, `/mypage/wallet`: 인증·API 연결 준비 안내와 미리보기 링크. 지갑 API를 호출하지 않는다.
- `/mypage/wallet/preview`: 별도 예시 데이터로 잔액·지급/결제/전액 환불 내역·페이지 이동을 확인한다. 실제 계정 정보가 아니라는 안내를 표시한다.
- `src/features/wallet/api.ts`: 공통 apiRequest를 통해 본인 잔액·내역을 요청한다. 회원 ID를 전송하지 않는다.
- `queries.ts`: AbortSignal 전달, 계정 캐시 구분, 페이지별 캐시를 담당한다.
- `cache.ts`: clearWalletCache(QueryClient)로 진행 중인 지갑 쿼리를 취소한 뒤 모든 계정의 지갑 캐시를 제거한다. 인증 담당자가 실제 로그아웃·계정 전환 흐름에서 호출해야 한다.
- `pages/WalletPage.tsx`: 준비 화면, 예시 미리보기, 실제 조회용 ConnectedWalletPage 및 공통 표시 컴포넌트.

실제 인증 상태가 준비되면 라우트의 WalletPage를 인증 보호 화면으로 바꾸고, 확인된 로그인 세션의 캐시 구분 값을 전달해 `<ConnectedWalletPage key={sessionKey} sessionKey={sessionKey} />`를 렌더링한다. key는 계정 변경 시 페이지 상태도 초기화한다. sessionKey는 실제 세션 쿠키·토큰이 아닌 캐시 구분 값이며 서버로 보내지 않는다. 미인증·세션 만료 시 해당 화면을 해제하고 로그인 안내로 전환한다. 어느 한 지갑 조회라도 401이면 화면은 잔액·내역을 숨기고 로그인 안내를 표시한다. 로그아웃·계정 전환 시 `await clearWalletCache(queryClient)`를 호출한다. 단순히 enabled=false로 바꾸는 것만으로 캐시가 삭제되지는 않는다.

<a id="finance-topup"></a>
### Toss 테스트 충전·재확인

Toss는 테스트 결제이며 실제 현금 출금이 아니다. 충전 신청→본인 조회→서버 승인/재확인→검증된 외부 DONE에 대한 1회 지급 순서를 따른다. 승인/조회 외부 호출은 짧은 DB 트랜잭션과 분리하고 불명확 결과를 실패로 단정하지 않는다. 일반 개발 외부 호출 기본값은 true, test는 false다.

#### API·입력·오류

##### 호출 계약

| 메서드·경로 | 입력·권한 | 응답 |
| --- | --- | --- |
| POST /api/me/wallet/topups | 인증+CSRF, JSON amount: 원 단위 정수 1~1000000 | 201, Location: 생성된 조회 경로, WalletTopupResponse |
| GET /api/me/wallet/topups/{id} | 인증, 양수 long ID | 200, WalletTopupResponse |

```json
{"amount":50000}
```

응답 필드: `id`(문자열), `tossOrderId`(서버 생성), `amount`, `currency=KRW`, `isTest=true`, `status`, `createdAt`(UTC ISO 8601).

생성 상태는 항상 READY다. memberId·status·tossOrderId·paymentKey·approvalRequestKey를 요청으로 받아 적용하지 않는다. 응답에도 회원 ID·paymentKey·내부 승인 멱등키·오류 원문은 넣지 않는다. 성공 응답은 Cache-Control: no-store다. 금액은 DTO에서 BigDecimal로 받아 소수 자릿수를 검증한 뒤 longValueExact로 변환하며 Long 역직렬화 과정의 소수 절삭을 피한다.

##### 오류

| 조건 | 결과 |
| --- | --- |
| 미인증 GET, 유효한 CSRF를 가진 미인증 POST | 401 AUTHENTICATION_REQUIRED |
| CSRF 누락·불일치 POST | 403 CSRF_INVALID, 미인증 여부보다 먼저 차단될 수 있음 |
| 누락·음수·0·상한 초과·소수·형식 오류 금액 | 400 VALIDATION_FAILED |
| 조회 ID 형식·범위 오류 | 400 VALIDATION_FAILED |
| 없는 지갑의 생성 요청, 타인/없는 충전 조회 | 404 RESOURCE_NOT_FOUND |

##### 공개 API 계약

| 호출 | 입력 | 정상 결과 |
| --- | --- | --- |
| POST /api/me/wallet/topups/{id}/confirm | 인증+CSRF; paymentKey,orderId,amount | 완료/종료 상태 200, PROCESSING/UNKNOWN은 202 |
| POST /api/me/wallet/topups/{id}/reconcile | 인증+CSRF; 본문 없음 | 저장된 paymentKey로 외부 조회 후 반영. 처리 중 202, 완료/종료 200 |
| GET /api/me/wallet/topups/{id} | 인증 | DB 조회만. 외부 승인·조회·지급 없음 |

```json
{"paymentKey":"SDK에서 받은 결제 식별자","orderId":"서버가 생성한 충전 주문번호","amount":50000}
```

요청 amount는 정수 1~100만원이며 DTO에서 소수 절삭을 차단한다. 식별자 길이/형식을 검증하고, DB에 저장된 본인 신청·주문번호·금액과 다시 비교한다. 최초 승인 시도에서 paymentKey를 신청과 묶고 이후 변경하지 않는다. 다른 신청에서 같은 paymentKey 사용은 UNIQUE로 차단하고 409로 응답한다.

응답은 기존 WalletTopupResponse다. no-store를 적용하고 내부 멱등키·paymentKey·provider 원문·시크릿은 반환하지 않는다. 200 자체를 충전 성공으로 판단하지 말고 status=COMPLETED를 확인해야 한다. FAILED도 상태 조회 결과로 200을 반환할 수 있다. 202는 성공/실패 미확정이며 새 충전을 자동 생성하거나 승인을 자동 재전송하지 않는다.

#### 처리 순서·동시성·재전송

##### 보안·트랜잭션 경계

- Controller는 기존 CurrentMemberId로 검증된 principal의 memberId를 얻는다. 실제 인증 구현을 추가하거나 고정 회원 ID를 사용하지 않는다.
- create는 하나의 짧은 DB 트랜잭션에서 입력·지갑 존재 확인 후 서버 주문번호와 승인 멱등키를 생성하고 READY 행을 저장한다.
- 조회는 readOnly 트랜잭션에서 `(id, memberId)` 조건을 사용한다. 타인 건과 없는 건은 동일한 404로 응답한다.
- 지갑이 없어도 자동 생성·가입 지급을 호출하지 않는다. 신청 저장은 지갑 잔액·원장·상품 주문을 변경하지 않는다.
- 외부 통신은 없다. 이후 승인 단계의 PROCESSING·토스 호출·지급 트랜잭션 분리는 [스키마 문서](database-design.md#b-1100-0)를 따른다.
- !demo Controller·Service다. DB 없는 demo에는 등록하지 않는다. 실제 로그인/로그아웃 연동과 세션 만료의 종단 간 검증은 남아 있다.

##### 트랜잭션 경계

```text
WalletTopupApprovalService (Propagation.NEVER)
  → prepare: 본인 충전 행 잠금·금액 검사·키 연결·PROCESSING, DB 커밋
  → 토스 승인 호출: DB 트랜잭션 밖
  → apply: 충전 행 → 지갑 잠금
      외부 결과 검증
      잔액 증가 + TOPUP 원장 + COMPLETED, 같은 DB 커밋
```

DB 메서드는 별도 Spring Bean인 TopupApprovalTransactions에서 호출하므로 self-invocation 문제를 피한다. 조율 서비스의 NEVER는 호출자 트랜잭션 안에서 외부 통신하는 실수를 차단한다. 실제 HTTP 호출은 상위 가입/주문 트랜잭션 안에서 실행하지 않는다.

충전·지갑 잠금 후 현재 잔액을 읽고 Math.addExact로 합산한다. TOPUP:{id} 이벤트 키·topup_id UNIQUE·같은 회원/금액 FK로 중복/잘못된 지급을 방어한다. 없는 원장의 FOR UPDATE는 사용하지 않아 다른 지갑끼리 원장 간격 잠금에 걸리지 않게 한다. 완료 재호출은 기존 원장의 근거까지 확인한 뒤 현재 결과를 반환한다.

지급 시각은 Instant를 native 쿼리에 전달해 UTC DATETIME을 저장한다. 충전 완료·지갑 갱신·원장 생성의 물리 UTC 시각을 테스트에서 비교한다. 기존 초기 지급용 LocalDateTime API는 이번에 변경하지 않는다.

#### 업무 규칙·연결

##### 범위

```properties
TOSS_TOPUP_ENABLED=true
TOSS_SECRET_KEY=CHANGE_ME
```

##### 불명확 결과·복구

- 외부 응답 유실·HTTP 오류는 UNKNOWN으로 남긴다. HTTP 오류만으로 결제 실패를 확정하지 않는다.
- 외부 성공 후 잔액/원장/완료 중 실패하면 지급 트랜잭션을 롤백하고 UNKNOWN/CREDIT_PENDING을 별도 저장한다.
- 재확인 POST는 승인 POST를 반복하지 않고 저장된 paymentKey로 외부 조회한다. 검증된 DONE을 같은 멱등 지급 경로로 반영한다.
- 같은 신청의 동시 confirm은 최초 한 요청만 외부 승인한다. 다른 요청은 PROCESSING/UNKNOWN 또는 이미 완료된 결과를 반환한다.
- 늦은 UNKNOWN 갱신은 COMPLETED/FAILED를 되돌리지 않는다. 이미 완료됐다면 반복 조회/반영으로 추가 지급하지 않는다.
- DB 자체 장애로 UNKNOWN 저장도 실패하면 5xx가 날 수 있다. 앞서 커밋한 PROCESSING·식별자는 복구 근거로 남으며, 장애 해소 후 재확인한다.

##### 검사

WalletTopupApprovalIntegrationTests는 실제 DB 커밋·잠금과 대역 Gateway를 사용한다. 외부 호출 시 트랜잭션이 없고 PROCESSING이 이미 커밋됐는지 확인한다. 같은 충전·같은 지갑·서로 다른 지갑의 동시 요청, provider 타임아웃 후 조회 복구, 내부 저장 중 실제 CHECK/FK 실패 후 전체 롤백·재확인, 결제 키 재사용·결과 불일치·미완료 상태를 검사한다.

HttpTossPaymentGatewayTests는 네트워크 대신 MockRestServiceServer로 승인 JSON·인증/멱등 헤더·paymentKey 조회·원문 오류 차단·live 키 거부를 확인한다. 실제 테스트키 결제와 인증 화면부터의 종단 간 검증은 아니다. 실행 결과는 검증 후 작업표에 기록한다.

##### 구현 범위

기존 흑백 AppLayout·마이페이지·공통 apiRequest를 재사용해 충전 금액 선택, 토스 주문서형 SDK 초기화, 결제 요청, 복귀 후 서버 승인·조회, 결과 재확인 화면을 구현했다. SDK는 `@tosspayments/tosspayments-sdk@2.10.0`으로 고정한다. 위젯 준비·정리와 결제 요청 흐름은 `features/wallet/useTopupCheckout.ts`, 화면은 `pages/WalletTopupPage.tsx`, 오른쪽 요약은 `components/TopupSummary.tsx`다.

##### 복귀·결과 처리

- success라는 경로 또는 HTTP 200만으로 완료를 표시하지 않는다. 서버 status=COMPLETED에서만 충전 완료다.
- 복귀 시 원래 세션 마커의 id·nonce·주문번호·금액과 URL 정보를 비교하고, 본인 조회 API 결과와도 다시 비교한다. 마커 없음·중복 파라미터·불일치 시 자동 승인하지 않는다. 최종 검증은 서버 승인 API가 담당한다.
- 승인 POST는 메모리 Promise로 1회만 실행한다. StrictMode·재렌더링·실패 때문에 자동 재전송하지 않는다. 항상 본인 GET 조회 후 호출하고, 승인 응답을 별도 계정 캐시로 재사용하지 않는다.
- PROCESSING/UNKNOWN은 확인 중으로 표시하고 사용자 클릭으로 reconcile을 호출한다. 페이지 새로고침 자체는 승인 POST를 반복하지 않는다. 자동 외부 재승인·새 충전 재시도·주기 재확인은 없다.
- paymentKey·nonce·제공자 오류 쿼리는 캡처 후 replace 탐색으로 주소에서 제거한다. no-referrer 메타도 적용했다. raw 제공자 오류 문구는 표시하지 않는다.
- 완료 시 지갑 쿼리를 무효화하고 세션 마커를 정리한다. 로그아웃·계정 변경 시 clearWalletCache가 지갑 쿼리를 취소/제거하고 복귀 마커·승인 메모리도 정리한다. 실제 인증 흐름에서 이 함수를 호출하는 연결은 남아 있다.
- 세션 만료·창 닫기·저장소 차단·잘못된 복귀는 자동 결제를 우회하지 않고 상태 조회/로그인 안내로 처리한다. GET도 인증이 필요하다. 전체 서버 상태가 항상 자동 복구된다는 의미는 아니다.

##### 11-03 사용자 테스트·초기 약관 상태 보완

<a id="b-1082-0"></a>
사용자가 가입·로그인 API로 브라우저 세션을 만든 뒤 토스 테스트 결제를 진행했다. 서버 충전 비활성화 오류를 확인하고 활성화 설정 후 성공했으며, 제공한 화면에서 가입 100만원·충전 5만원·잔액 105만원·내역 2건을 확인했다. 이는 사용자 실행 결과이며 에이전트의 브라우저 재현·DB 원문 검사 또는 새로고침 중복 지급 검사 완료를 뜻하지 않는다.

추가로 최초 진입 시 약관은 체크되어 있지만 결제 버튼이 비활성화되는 증상을 보고했다. 기존 React 상태는 false로 시작해 agreementStatusChange가 와야만 true가 되었다. V2 웹 SDK 공개 API에는 초기 약관 상태 조회 메서드가 없으므로 초기 상태를 미확인(null)으로 구분한다. 위젯 준비 완료 뒤 요청을 허용하고 실제 필수 약관 검증은 SDK requestPayment에 맡긴다. 변경 이벤트에서 명시적 미동의를 받으면 버튼과 요청을 막고, 재동의 시 화면 오류를 정리한다. 미확인을 동의 완료로 기록하거나 실제 약관 체크를 강제하지 않는다.

#### 화면·캐시·계정 경계

##### 경로와 확인

| 경로 | 동작 |
| --- | --- |
| /mypage/wallet/topup | 본인 지갑 접근 확인 후 금액·결제 수단 선택 |
| /mypage/wallet/topup/preview | API·SDK를 호출하지 않는 명시적 예시 화면 |
| /mypage/wallet/topup/{id}/success | 복귀 정보·서버 소유권/조건 확인 후 승인·상태 조회 |
| /mypage/wallet/topup/{id}/fail | 제공자 오류 문구를 노출하지 않고 서버 상태 확인 |
| /mypage/wallet/topup/{id}/status | DB 상태 조회, 필요한 경우 사용자 클릭으로 재확인 POST |

```powershell
cd C:\project\LancerPickFrontend
npm ci
npm run dev
```

미리보기: http://localhost:5173/mypage/wallet/topup/preview. 백엔드·DB·키가 없어도 화면을 볼 수 있다. 일반 경로는 본인 지갑 API가 필요하다. 정상 로그인·지갑이 없는 상황에서 고정 회원 ID나 예시 잔액으로 우회하지 않는다.

마이페이지의 실제 잔액 카드에는 충전 화면 링크, 예시 잔액 카드에는 충전 미리보기 링크를 둔다. 로그인 메뉴·표시 이름 등 전역 인증 UI는 인증 담당자가 연결한다.

#### 실행·환경

##### 실제 연동 전 설정

현재 프런트 개인 설정 파일은 `keys.env`다. 없는 팀원은 `.env.example`을 복사해 `keys.env`를 만들며 기존 개인 파일은 덮어쓰지 않는다.

```dotenv
VITE_TOSS_CLIENT_KEY=CHANGE_ME
```

위 값을 본인의 **test_gck** 클라이언트 키로 교체하고 Vite를 재시작한다. Vite 기본 로더는 keys.env를 자동으로 읽지 않으므로 vite.config.ts에서 Node 내장 parseEnv로 읽어 VITE_TOSS_CLIENT_KEY 하나만 브라우저에 연결한다. 적용 우선순위는 셸 변수 → keys.env → 표준 .env 계열이며 빈 셸 값은 SDK를 끈다. 파일 전체·다른 VITE 변수·서버 시크릿을 define이나 process.env에 일괄 주입하지 않는다.

SDK와 Vite 설정은 테스트 gck만 허용하며 시크릿·라이브·개별 연동 ck 키는 거부한다. 클라이언트 키는 브라우저에 포함되는 공개 설정이다. gsk는 프런트에 넣지 않는다. keys.env는 Git에서 제외하고 Vite 기본 민감 파일 차단을 유지하면서 해당 원본 파일도 HTTP 제공을 차단한다. 파일을 public 폴더에 두지 않는다.

백엔드는 기존 [키 설정 안내](architecture.md#environment)에 따라 같은 세트의 테스트 시크릿과 `TOSS_TOPUP_ENABLED=true`를 설정한다. 명시적으로 비활성화한 경우 승인 API가 503을 반환하며 READY를 유지한다. 일반 실행의 현재 기본값과 test 프로필은 위 현재 실행 설정을 따른다. 실제 Spring Security 인증 principal과 회원가입 지갑 생성도 연결돼 있어야 한다.

<a id="finance-funds"></a>
### 정책별 결제·보관·환불

LEGACY_V1은 기존 주문 결제/전액 환불 계약을 유지한다. V3는 지갑에서 주문 총액만 1회 차감해 order_funds에 전액 보관하고 확정 시 수수료 floor(총액×10%)·나머지 판매자 적립금을 지급한다. 취소 승인 시 전액 반환·수익/수수료 0이다. V2 분할·보상 설계는 호환/이력이며 V3에 적용하지 않는다.

#### 업무 규칙·연결

##### 구현 범위와 활성화 경계

CONTRACT_V3 주문은 양측 계약 서명 후 구매자 지갑 잔액에서 해당 주문 결제 금액만 차감하고 `order_funds`에 보관한다. 납품 검수 후 구매자가 확정하면 수수료 10%(원 미만 버림)를 제외한 금액을 판매자의 **구매용 지갑 잔액**에 실제 지급한다. 구매자 취소 요청을 판매자가 승인하면 보관금 전액을 구매자에게 반환한다. 정산·종료 기록과 판매대금/환불 원장도 함께 남긴다.

예: 지갑 잔액 1,000,000원으로 100,000원 주문을 결제하면 **지갑 잔액 900,000원·해당 주문 보관금 100,000원**이다. 나머지 잔액은 계속 사용할 수 있다. 구매 확정 시 보관금에서 판매자 90,000원 지급·수수료 10,000원을 기록한다. 취소 승인 시 보관금 100,000원을 구매자 지갑으로 돌려준다.

##### 결제 금액 보관·판매자 적립금 지급·자동 구매 확정 — CONTRACT_V3

확정일: 2026-11-05. 사용자 결정에 따라 추천한 결제 금액 보관 조합과 검수 무응답 자동 구매 확정을 채택한다. 신규 정책은 CONTRACT_V3이며 LEGACY_V1·CONTRACT_V2와 이미 서명된 PDF에는 소급하지 않는다. 정책 확정·DB/계약 기반·금액 이동 기능 완료를 구분한다.

##### 거래 흐름과 금액

주문 신청 → 판매자 수락 → 표준 계약·추가 특약 → 양측 서명 → 전액 적립금 결제·주문별 보관 → 작업·납품 → 구매 확정 또는 자동 구매 확정 → 판매자 구매용 적립금 지급·거래 완료.

| 항목 | 신규 정책 |
| --- | --- |
| 총액 | 10,000~1,000,000원. 계약 생성·확정·결제 시 서버 검증 |
| 결제 | 지갑 잔액에서 해당 주문 결제 금액만 한 번 차감. 선금/잔금 없음 |
| 대금 보관 | 주문별 order_funds. 관리자 개인 회원/지갑에 이체하지 않음 |
| 정상/자동 확정 | 수수료=floor(총액×10%), 판매자 지급=총액-수수료 |
| 판매대금 | settlements의 수익 기록에 더해 판매자 wallets에 실제 적립. 구매에 다시 사용할 수 있음 |
| 취소 | 결제 전 기존 취소, 결제 후 구매자 요청·판매자 승인 또는 운영 문의에 대한 관리자 승인. 보관금 전액 반환, 수익/수수료 0 |
| 소요일·납기 | 전액 결제 완료 시각+소요일×24시간 |
| 수정 | 원래 범위에서 1회. 재납품 기한은 수정 요청 시각+원래 소요일 |
| 제외 | 잔금 대기·잔금 미지급 종료·무응답 위약금/보상금·보상 수수료 1% |

10만원 결제 예: 구매자 -100,000 / 주문 보관 +100,000. 확정 시 보관 -100,000 / 판매자 +90,000 / 플랫폼 수수료 10,000 기록. 취소 시 보관 -100,000 / 구매자 +100,000. 플랫폼 수수료를 관리자 구매 지갑에 충전하지 않는다. 실제 현금 이체·출금·PG 에스크로를 의미하지 않는다. 토스 테스트 충전은 기존 별도 흐름을 유지한다.

지갑 잔액이 1,000,000원이면 100,000원 주문 결제 후 **지갑 잔액 900,000원·해당 주문 보관금 100,000원**이다. 나머지 900,000원은 계속 사용할 수 있다. 문서의 ‘결제 금액 보관’은 이 분리를 뜻한다.

관리자 승인 취소(K4) 경계(2026-11-06 조장 결정): 대상은 `CANCELLATION_REVIEW` 운영 문의에 연결된 대기 취소 요청이다. 주문 영역 서비스가 트랜잭션을 소유하고 관리자 공유 잠금 → 주문 → 구매자 지갑 순으로 잠근다. 같은 트랜잭션에서 취소 요청 APPROVED/REJECTED(`resolution_source=ADMIN`), 승인 시 보관금 전액 반환·closure `ADMIN_CANCELLATION`(`inquiry_id` 필수, V7 CHECK)·이력·아웃박스를 저장하고, 문의 종료는 문의 영역(김창재)이 제공하는 메서드를 이 트랜잭션 안에서 호출한다. 관리자 화면·API는 김창재가 이 서비스를 호출한다.

##### 주문 적립금 결제

저장소 후속 변경: [JPA·QueryDSL 전환](architecture.md#persistence)에 따라 Payment Entity·JpaRepository로 전환했다. 아래 최초 구현의 JDBC 설명은 변경 전 기록이며 현재 저장소 구조는 후속 문서를 따른다. API·잠금·금액 정책은 유지한다.

#### API·입력·오류

##### API와 화면

보호 API는 세션의 `CurrentMemberId.require()`로 회원을 확인한다. 변경 요청에는 CSRF가 필요하다. 클라이언트 회원 ID·금액을 결제 근거로 사용하지 않는다.

| API | 권한·처리 |
| --- | --- |
| `POST /api/orders/{id}/payment` | 구매자, 기존 `{"agreedToTerms":true}`. V3면 계약/서명·주문 금액 검증 후 주문 결제 금액만 차감·보관 |
| `POST /api/orders/{id}/confirm` | 구매자, 검수 대기·납품/재납품 존재·취소 대기 없음 확인 후 보관 해제·판매자 지급·정산·완료 |
| `POST /api/orders/{id}/cancellation-requests` | 구매자, 기존 사유·UUID 요청 키로 취소 요청. 대기 중 확정/납품/수정 차단 |
| `POST /api/orders/{id}/cancellation-requests/{requestId}/approve` | 판매자, 기존 승인 API로 전액 반환·요청 승인·주문 취소 |
| `POST /api/orders/{id}/cancel` | V3 결제 전 기존 취소. 결제 후 판매자는 구매자의 대기 요청이 있어야 반환 가능. 화면에는 승인 동선을 제공 |
| `GET /api/orders/{id}/funds` | 구매자/판매자만 V3 대금 조회. `Cache-Control: no-store`. 타인·없는 주문·다른 정책은 404 |

변경 API의 기존 주문 상세/취소 결과 응답 형식은 유지한다. V3에는 PAY·납품·수정·요청 승인·구매 확정 행동을 상태에 따라 노출하고, 결제 후 판매자의 단독 CANCEL 버튼은 숨긴다. 서버도 이를 다시 검증한다.

보관금 조회 응답은 아래 필드다. 금액은 정수 원, 시각은 UTC ISO 8601이다.

| 필드 | 의미 |
| --- | --- |
| `status` | `NOT_PAID`, `HELD`, `RELEASED`, `REFUNDED` |
| `amount`, `heldAmount`, `refundedAmount` | 주문 총액·현재 보관액·반환 누계 |
| `feeAmount`, `sellerPayout` | 종료 시 확정된 수수료·판매자 지급액. 미종료는 null, 전액 취소는 0 |
| `paidAt`, `resolvedAt` | 결제·보관 종료 시각. 해당 처리 전 null |

`/orders/:id`의 V3 상세에 대금 보관/지급/반환 패널을 연결한다. 프런트는 `orders/types.ts` → `api.ts` → `queries.ts` → `components/OrderFundsPanel.tsx`를 사용한다. 조회 키는 기존 계정 정리 계약의 `['wallet', 'order-funds', id]`, 변경 성공/실패 후 wallet 하위 조회를 갱신한다. 권한 재조회 실패 시 이전 대금 내용을 숨기며 변경 요청은 자동 재전송하지 않는다. 판매자 지갑 원장은 기존 SELLER_PAYOUT 표시를 재사용한다.

##### 오류와 후속 연결

| HTTP·코드 | 조건 |
| --- | --- |
| 401 `AUTHENTICATION_REQUIRED` | 로그인 필요 |
| 403 `FORBIDDEN` | 당사자 중 해당 명령 역할이 아님 |
| 404 `RESOURCE_NOT_FOUND` | 타인/없는 주문. 대금 조회는 다른 정책도 포함 |
| 409 `CONTRACT_NOT_SIGNED` | 활성 계약·양측 서명 필요 |
| 403 `MEMBER_LOCKED` | 보안 잠금 중인 구매자의 구매 확정(인증 필터를 통과한 진행 중 요청 포함) |
| 409 `CANCELLATION_PENDING` | 취소 요청 대기 중 구매 확정 |
| 409 `CANCELLATION_REQUEST_REQUIRED` | 구매자 요청 없이 결제 후 판매자 직접 취소 시도 |
| 409 `PAYMENT_ALREADY_REFUNDED` | 반환한 결제의 재결제 시도 |
| 409 `ORDER_STATE_CONFLICT` | 현재 주문/보관 상태에서 명령 불가 |
| 409 `AUTO_CONFIRM_DUE` | 만료 시각이 지난 자동 확정 타이머가 있는 주문의 구매 확정(2026-11-06 E7, 아래 참고) |
| 409 `INSUFFICIENT_BALANCE` | 지갑 잔액 부족 |
| 500 `DATA_INTEGRITY_ERROR` | 결제·보관금·원장·종료·정산 근거 불일치 |

관리자 승인 취소(K4, main 반영 #121)는 `FullFundingService.adminRefund`로 같은 반환 경로를 쓰고 종료 기록을 `ADMIN_CANCELLATION`(문의 ID 포함)으로 남긴다. 관리자 승인으로 취소된 주문에 판매자가 `/cancel`을 다시 보내면 409 `ORDER_STATE_CONFLICT`다. 상세는 [관리자 취소 검토](features.md#orders-cancel).

##### 검수 무응답과 타이머

1. 납품 후 48시간 동안 검수 응답이 없으면 판매자가 7일 타이머를 시작할 수 있다. 타이머를 시스템이 자동 시작하지 않는다.
2. 만료 시 AWAITING_CONFIRMATION·납품 존재·보관금 HELD·활성 현재 타이머·수정/취소 대기 없음이면 정상 구매 확정과 같은 수수료로 자동 완료한다.
3. 수정 요청은 구매자의 응답이다. 기존 타이머를 취소하고 재납품을 새 검수 기산점으로 삼는다.
4. 취소 요청 대기 중에는 자동 완료하지 않는다. 기존 타이머를 취소하고, 거절·철회 후 재개하면 해결 시각을 새 검수 기산점으로 삼는다. 다시 48시간 후 판매자가 새 7일 타이머를 시작한다. 만료된 예전 타이머를 즉시 재사용하지 않는다.
5. 관리자 문의 접수만으로 중단하지 않는다. 관리자가 사유를 기록해 활성 타이머를 해제할 수 있다. 해제는 이미 완료된 정산을 되돌리는 작업이 아니다. 해제된 기산점으로는 판매자가 타이머를 다시 시작할 수 없고(409 `TIMER_ADMIN_STOPPED`), 재납품이나 취소 요청 거절·철회로 새 기산점이 생긴 뒤 다시 48시간이 지나야 시작할 수 있다(2026-11-06 조장 결정).
6. 납기 초과·미납품은 자동 구매 확정 사유가 아니다. 타이머 시작·사전 안내·만료/완료 결과를 알림으로 전달하고 화면에 예정 시각을 표시한다.

자동 확정은 COMPLETED이며 TERMINATED/보상 종료가 아니다. 시스템 이력은 actor_type=SYSTEM·actor_id=NULL, 완료 결과 reason=AUTO_CONFIRMED로 구분한다. 일반 구매자 확정과 마찬가지로 구매자 후기를 허용한다.

같은 주문 잠금 아래 서버 현재 시각으로 판단한다. 활성 타이머의 만료 시각 이상이면 만료 처리를 우선하며, 수정/취소 등 응답이 먼저 커밋돼 타이머가 취소됐다면 예전 만료 작업은 아무 것도 지급하지 않는다. 관리자 해제와 자동 확정도 같은 잠금으로 직렬화한다.

##### 정책·계약 호환

| 버전 | 결제·수익 | 계약 |
| --- | --- | --- |
| LEGACY_V1 | 기존 주문 결제 금액만 차감·판매 수익 기록, 구매 지갑과 분리 | 기존 MVP 그대로 |
| CONTRACT_V2 | 기존 선금/잔금·보상 정책 이력. 기존 데이터/해시 보존 | standard-v1.txt 보존 |
| CONTRACT_V3 | 결제 금액 보관·완료 시 구매용 적립금 지급·자동 확정 | standard-v2-full-funding.txt |

##### API·캐시·실패 처리

features/orders/api.ts가 공통 apiRequest를 사용한다. 세션 쿠키·CSRF는 공통 처리를 따르고 요청에 금액·회원 ID를 넣지 않는다. ID는 양의 signed BIGINT 문자열로 검사하며 Number로 변환하지 않는다. 결제/취소/확정 POST는 자동 재전송하지 않는다.

화면에 실행 중 잠금을 두고, 실패 후에는 상태를 다시 조회한 다음 재시도할 수 있도록 한다. 성공/실패 모두 지갑·주문·정산 쿼리를 무효화해 현재 세션으로 다시 읽는다. 변경 응답을 계정 캐시에 직접 되살리지 않는다. 쿼리는 wallet 하위 키·gcTime=0을 사용하므로 기존 clearWalletCache에 참여한다. 실제 로그인/로그아웃/계정 전환에서 캐시 정리와 인증 화면 분기를 연결하는 것은 인증 담당자의 후속 작업이다.

401이면 로그인 안내, 조회 실패면 재조회, 빈 목록이면 빈 상태를 표시한다. 현재 로그인 백엔드 API는 구현되었으나 로그인 화면 연결은 후속이므로 일반 실행에서 인증 안내가 나올 수 있다. 개발용 고정 회원이나 인증 우회는 추가하지 않았다.

##### API와 연결

POST /api/orders/{id}/payment. 인증 구매자와 CSRF가 필요하다. 본문은 `{"agreedToTerms":true}`이며 문자열 "true"·숫자·false·누락은 거절한다. 금액·회원 ID·수수료는 요청에서 받지 않는다. 성공은 200 OrderDetail·Cache-Control: no-store다. 실제 로그인과 결제 화면 연결은 별도다.

OrderPaymentController는 인증 ID·입력을 확인하고 OrderPaymentService를 호출한다. 서비스의 READ_COMMITTED 트랜잭션 안에서 실행자 회원 FOR SHARE → 참여자 주문 FOR UPDATE → 구매자 확인 → 기존 결제 확인 → 구매자 지갑 FOR UPDATE 순서로 처리한다. 기존 주문 명령의 회원→주문 순서와 호환되며, 서로 다른 주문도 같은 지갑 잠금으로 잔액을 보호한다.

최초 결제는 AWAITING_PAYMENT에서만 가능하다. 저장된 orders.price_amount로 결제하며 payments(PAID,1000bps), 지갑 차감, PAYMENT 원장, 조건 확인/결제/납기 시각, IN_PROGRESS 상태, PAID 이력을 함께 반영한다. 조회 응답 조립까지 실패하면 전체 롤백한다. 원장 키는 PAYMENT:{paymentId}이고 결제 order_id·원장 event_key/(payment_id,type)의 기존 UNIQUE를 유지한다.

결제 전 동의는 이 요청에서 서버 시각으로 기록한다. 납기는 결제 시각 + 주문 소요일×24시간이다. UTC 마이크로초 시각을 공유하고 JDBC의 LocalDateTime 직접 바인딩·기존 JPA LOCAL_DATE_TIME 매핑을 사용한다. PaymentRepository는 기존 조회 전용 SQL 저장소들과 같은 JdbcTemplate 방식이며 서비스 트랜잭션에 참여한다. 새 테이블·마이그레이션은 추가하지 않는다.

##### 반복 요청·오류

- PAID이고 진행·검수·수정·완료 상태이면 원결제·원장 일치를 확인해 현재 주문을 반환한다. 추가 차감·이력·시각 변경은 없다.
- REFUNDED는 409 PAYMENT_ALREADY_REFUNDED다. 최초 결제의 잘못된 주문 상태는 409 ORDER_STATE_CONFLICT다.
- 잔액 부족은 409 INSUFFICIENT_BALANCE다. 지갑·결제·원장·주문 변경을 확정하지 않는다.
- 결제 후 상태인데 원결제가 없거나 금액/원장이 모순되면 500 DATA_INTEGRITY_ERROR다. 자동 복구·재차감하지 않는다.
- 판매자는 403, 비참여자·없는 주문은 동일 404다. 인증·CSRF·입력 오류는 공통 정책을 따른다.

상품 숨김/판매 중지는 기존 주문의 결제를 차단하는 조건으로 추가하지 않는다. 토스 충전과 별개인 구매 적립금 결제이며 외부 통신·토스 결제 취소를 수행하지 않는다. 구매 확정·취소·환불·판매 수익 정산은 후속 기능이다.

##### 내부 타입·외부 계약

| 엔티티 | 내부 타입 | 기존 DB 값·길이 |
| --- | --- | --- |
| Payment.status / status() / getStatus() | `PaymentStatus` | PAID, REFUNDED / VARCHAR(10) |
| OrderFunds.status / getStatus() | `OrderFundsStatus` | HELD, RELEASED, REFUNDED / VARCHAR(16) |

두 필드는 `@Enumerated(EnumType.STRING)` + `@JdbcTypeCode(SqlTypes.VARCHAR)`를 사용한다. DB의 기존 CHECK·NOT NULL·컬럼 길이·원문 값을 유지하고 ordinal이나 DB native ENUM으로 바꾸지 않는다. 알 수 없는 값을 정상 enum으로 보정하는 Converter/기본값은 없다. 새 enum 상수를 추가하려면 해당 DB CHECK·상태 정책을 별도 검토해야 한다.

- 팩터리·환불/보관 해제 전이는 enum 상수, 비교는 `==`/`!=`다. PaymentRepository.markRefunded, OrderFundsReader, 기존 주문 결제·취소·확정과 FullFundingService의 V3 보관·지급·반환·자동 확정에 적용했다.
- QueryDSL 생성 경로는 `EnumPath<PaymentStatus>` / `EnumPath<OrderFundsStatus>`다. 관리자 금융 조회의 상태 조건도 enum 비교로 구성한다.
- 관리자 조회 입력의 status는 기존 String 경계를 유지한다. 서비스에서 enum 이름을 엄격히 검사하고 저장소에서는 검증된 값을 enum으로 변환한다. 알 수 없는 값/다른 대소문자는 기존 400 `VALIDATION_FAILED`다.
- 주문 상세 결제, V3 대금 패널, 관리자 결제/보관금 DTO는 기존 String 필드를 유지하고 조립 시 `.name()`으로 변환한다. JSON·프런트 API·`NOT_PAID`(미결제 조회용 가상 상태) 계약은 그대로다. NOT_PAID를 DB enum에 추가하지 않는다.

금액·수수료·원장·멱등 키·환불/정산 횟수·잠금 순서·트랜잭션 경계·정책 버전·외부 토스 상태는 변경하지 않았다. 인증·채팅·회원 제재·알림 API에도 새 기능을 추가하지 않는다.

#### 처리 순서·동시성·재전송

##### 트랜잭션·잠금·중복 방지

기존 주문 서비스가 정책을 분기하고 `FullFundingService`가 같은 DB 트랜잭션에 참여한다. 격리는 READ_COMMITTED이며 **실행 회원 공유 잠금 → 주문 배타 잠금 → 영향을 받는 지갑 배타 잠금** 순서다. 각 경로에서 지갑 하나만 갱신한다. 보관금 조회는 REPEATABLE_READ 읽기 전용으로 한 스냅샷의 결제·보관·종료 정보를 읽는다. PDF·토스 등 외부 호출은 이 경계 안에 없다. 서명 계약·결제·보관금·원장·종료 기록 대사는 order 내부 `FullFundingRecords`가 같은 트랜잭션 안에서 맡고, 잠금 순서·상태 변경은 서비스에 있다.

| 명령 | 함께 커밋/롤백하는 데이터 |
| --- | --- |
| 결제 | 구매자 잔액 감소·FULL 결제·HELD 보관금·PAYMENT 원장·진행 중 상태/납기·PAID 이력/아웃박스·납기 통지 이력/아웃박스(E8, [V3 알림](features.md#communication-timer)) |
| 확정 | 보관금 RELEASED·수수료/정산·판매자 잔액/SELLER_PAYOUT 원장·COMPLETED 종료 기록·주문 완료·이력/아웃박스 |
| 승인 환불 | 구매자 잔액/REFUND 원장·결제 REFUNDED·보관금 REFUNDED·취소 요청 APPROVED·MUTUAL_CANCELLATION 종료 기록·주문 취소·이력/아웃박스 |

주문 잠금으로 같은 거래의 완료·취소를 직렬화하고, 지갑 잠금으로 서로 다른 주문이 같은 잔액을 중복 사용하지 못하게 한다. DB의 주문당 결제/보관/정산/종료 및 결제별 원장 고유 제약도 유지한다. 재요청은 기존 금액·원장·완료 기록을 확인하고 추가 지급하지 않는다. 반환된 결제의 재결제는 거절한다.

##### 필수 트랜잭션

- 결제: 현재 서명 계약·상태·총액·잔액 확인 → 구매자 차감·PAYMENT 원장·FULL 결제·HELD 보관금·진행/납기·이력/아웃박스 함께 커밋.
- 정상/자동 확정: 같은 주문 잠금 → HELD/미종료 확인 → 보관금 해제·정산·판매자 지갑 증가·SELLER_PAYOUT 원장·closure·완료·타이머 종료·이력/아웃박스 함께 커밋. 두 지갑이 필요한 경로는 회원 ID 오름차순으로 잠근다.
- 취소: 보관금 전액 반환·REFUND 원장·결제 환불·보관금 REFUNDED·closure·취소/타이머 종료·이력/아웃박스 함께 커밋.
- DB와 외부 통신을 묶지 않는다. PDF·PG·메일·소켓은 주문/지갑 잠금 밖에서 처리한다.

반복/경합 시 주문당 완료·취소 결과는 하나만 성공한다. 금액 불변식은 결제액=미해결 보관액+구매자 반환액+판매자 지급액+수수료다. 실패 시 판매자 지갑만 먼저 증가하거나 보관금만 먼저 해제되지 않아야 한다.

#### 데이터·제약

##### DB와 마이그레이션

#### 화면·캐시·계정 경계

##### 경로와 역할

- /orders: BUYER/SELLER 구분과 20개 단위 페이지 조회, 주문 상세 링크.
- /orders/{id}: 보관한 상품 범위·거래 규칙·요구사항·금액·소요일·납기·납품 설명/HTTPS 결과물 링크 조회.
- 서버 allowedActions에 PAY·CANCEL·CONFIRM이 있을 때만 해당 버튼을 표시한다. 버튼 노출이 서버 권한 검사를 대신하지 않는다.

결제는 거래 조건·금액 동의, 구매 확정은 결과물 확인과 확정 후 환불 불가 안내 동의가 있어야 실행한다. 직접 취소는 사유를 받는다. 완료·환불 등 재호출 결과는 응답의 현재 상태를 표시하고 임의로 결제 성공 화면으로 이동하지 않는다.

<a id="finance-settlement"></a>

### 판매 수익 검색·지급 구분 후속 — 2026-11-10, main 반영(#159)

`feature/settlement-query-details`(황준범, main `3353e57` 기준). GET `/api/me/settlements`와 `/summary`에 선택 검색어 `q`를 추가한다. 공백 정리 후 100 코드 포인트 이하, 숫자만이면 주문번호 정확 일치(0/범위 초과는 빈 결과), 나머지는 주문 당시 보관 상품명 부분 일치다. `%`, `_`, `!`는 문자 그대로 검색한다. 기간·정렬·페이지와 기존 금액 필드는 유지하며 목록·총건수·합계가 동일한 판매자/기간/검색 조건을 사용한다. 상품 원본 수정은 검색 결과의 보관 상품명을 바꾸지 않는다.

목록 응답에 `productTitle`(보관 정보가 없으면 null), `buyerId`, `buyerDisplayName`(현재 표시 이름), `policyVersion`, `payoutStatus`를 추가한다. JPA/QueryDSL 스칼라 및 정산 조회이며 구매자 이메일·인증 정보를 반환하지 않는다. 지급 상태는 다음과 같다.

| 값 | 의미 |
| --- | --- |
| CREDITED | 해당 주문의 PAID 결제·정책·결제액과 해당 판매자의 동일 수익 금액 SELLER_PAYOUT 원장이 일치함 |
| RECORD_ONLY | 위 지급 증거가 없는 LEGACY_V1 수익 기록. 적립금 지급 완료로 표시하지 않음 |
| UNCONFIRMED | 그 외 정책의 정산에 지급 증거가 없음. 화면에서 지급 기록 확인 필요로 표시 |

실제 지급 여부를 계약 버전만으로 단정하지 않으며 다른 회원의 원장·금액 불일치는 지급 근거로 쓰지 않는다. 원장 EXISTS로 판정해 목록/합계 행 수를 늘리지 않는다. 합계는 조회 조건의 정산 기록 합계이며 사용 가능 잔액이나 지급 완료 금액 합계가 아니다. 금액 지급/환불 명령·DB·Flyway는 바꾸지 않는다.

판매 수익 화면은 검색·기간·정렬을 URL에 보존하고 페이지·새로고침과 계정 캐시 경계를 유지한다. 입력 초기화와 검색 조건이 목록/합계의 조회 키에 함께 반영된다. 새 화면 표시는 API 추가 필드를 사용하며 실제 서버 재시작이 필요하다. 검증 범위는 [이력](change-history.md#settlement-query-details)을 따른다. main 반영(#159)이며 원격 CI 결과는 PR 기록을 따른다.

### 구매 확정·판매 수익

구매 확정·정산/지급·주문 완료·원장을 함께 처리하고 반복 요청으로 중복 생성하지 않는다. 본인 판매 수익 목록/조건 합계·한국 날짜 기간을 제공한다. 상품명/주문번호 검색·구매자/보관 상품 표시·실제 지급 증거 구분은 위 로컬 후속에 구현했으며 main 반영 여부는 진행 문서를 따른다.

#### 업무 규칙·연결

##### 구매 확정·판매 수익 정산

저장소 후속 변경: [JPA·QueryDSL 전환](architecture.md#persistence)에 따라 정산 생성은 Entity/JpaRepository, 수익 조회는 QueryDSL로 변경했다. API·수수료·동시성 정책은 유지한다.

##### 구매 확정

POST /api/orders/{id}/confirm. 인증 구매자·CSRF 필요, 본문 없음. 검수 대기 AWAITING_CONFIRMATION이며 대기 취소 요청이 없는 주문만 가능하다. 판매자는 403, 비참여자/없는 주문은 동일 404다. 수정 중·취소/거절 등은 409 ORDER_STATE_CONFLICT, 대기 취소 요청은 409 CANCELLATION_PENDING이다. 구매자가 보안 잠금 중이면 403 MEMBER_LOCKED다(인증 필터를 통과한 진행 중 요청 포함).

실행자 회원 FOR SHARE → 참여자 주문 FOR UPDATE 순서로 잠그고 원결제 PAID·주문 금액 일치·결제 시각 존재를 검사한다. READ_COMMITTED 트랜잭션에서 COMPLETED·완료/갱신 시각·정산 한 건·COMPLETED 이력·응답 조립까지 함께 처리한다. 원결제의 fee_rate_bps를 복사하고 원 단위 정수 나눗셈으로 수수료를 버림 계산한다. 999원·1000bps는 수수료 99원, 판매 수익 900원이다. 구매 지갑을 변경하지 않는다.

완료 재호출은 원결제와 기존 정산 금액/비율 일치를 확인해 현재 주문을 반환하며 이력·시각·정산을 재생성하지 않는다. 상태/결제/정산 모순은 DATA_INTEGRITY_ERROR로 처리한다. order_id UNIQUE를 유지하고 새 테이블이나 Flyway 변경은 없다. 직접 취소와 구매 확정이 같은 주문 잠금을 사용하므로 취소·환불과 완료·정산이 동시에 확정되지 않는다.

##### 본인 판매 수익 조회

- GET /api/me/settlements?page=0&size=20: 본인 판매 주문의 정산만 조회. 최대 100, created_at 내림차순·동일 시각 ID 내림차순. 공통 PageResponse이며 범위를 벗어난 페이지는 빈 items다.
- GET /api/me/settlements/summary: completedCount,grossAmount,feeAmount,sellerAmount. 없는 내역은 모두 0.

두 API는 인증 principal 회원 ID만 사용하며 200·no-store다. ID는 문자열, 금액은 정수, 시각은 UTC ISO 8601이다. SQL SUM은 정확한 정수로 변환하며 JSON 안전 정수 범위를 초과하면 조용히 반올림하지 않고 실패한다. REPEATABLE_READ 읽기 트랜잭션으로 목록·건수를 조회한다. 각 HTTP 요청 사이의 전체 스냅샷까지 공유하지는 않는다.

#### 화면·캐시·계정 경계

##### 화면

/mypage/settlements에서 실제 조회 API를 사용해 합계·내역·페이지 이동·로딩·재조회·401 로그인 안내를 표시한다. 마이페이지에 링크를 추가했고 기존 흑백 스타일·공통 apiRequest·TanStack Query·CSS Modules를 사용한다. wallet 하위 쿼리 키와 gcTime=0을 사용해 기존 계정 전환 캐시 정리에 참여한다. 실제 로그인에서 캐시 정리 함수를 호출하는 연결은 인증 담당자가 완료해야 한다.

판매 수익은 구매 적립금과 별도이며 출금 기능은 제공하지 않는다고 화면에 안내한다. 예시 수익을 실제 응답처럼 표시하지 않는다. 현재 로그인 기능이 없으면 정상 경로에서 로그인 안내가 나온다. 구매 확정 버튼을 포함한 주문 상세 화면은 주문 담당자 연결 범위이며 이번에는 판매 수익 화면을 추가했다.

##### 화면·코드

카드는 ‘조회 조건 합계’로 표시하고 구매 확정 거래 수·판매 총액·플랫폼 수수료·수수료 제외 수익으로 구분한다. 사용 가능한 적립금 잔액과 다르다는 안내와 기존/V3 지급 안내는 유지한다. 목록은 전체 조건 건수와 정산 기록일을 표시한다.

조건별 목록·합계 캐시 키를 분리하며 wallet 접두사를 유지해 기존 계정 전환 정리에 참여한다. 합계는 정렬·페이지와 무관하므로 기간만 키에 포함한다. 공통 한국 날짜·달력 프리셋은 `shared/lib/dateRange.ts`로 분리했고 기존 주문 목록 함수의 export는 호환되게 유지한다. 따라서 정은찬의 주문 목록 화면도 회귀 검사 대상이다.

#### API·입력·오류

##### 확정한 조회 계약

| API | 추가 조건 | 기존 조건 |
| --- | --- | --- |
| GET /api/me/settlements | from, to: 정산 기록일 / sort=latest 또는 oldest | page=0, size=20, 최대 100 |
| GET /api/me/settlements/summary | from, to: 목록과 동일한 정산 기록일 | completedCount/grossAmount/feeAmount/sellerAmount 응답 유지 |

- 조건은 모두 선택이다. 생략 시 기존 전체 기간·최신순 조회와 같으며 내부 기존 서비스 호출도 유지한다.
- 날짜는 1000~9999년 `yyyy-MM-dd` 한국 날짜다. `settlements.created_at`에 `[from 00:00 Asia/Seoul, to+1 00:00 Asia/Seoul)`를 UTC로 변환해 적용한다. 종료일 당일을 포함한다. 주문 신청일이나 현재 지갑 잔액 기준이 아니다. 거래 완료와 함께 생성하는 정산 기록 시각을 사용한다.
- 시작/종료 한쪽만 지정할 수 있고 기간 길이 제한은 없다. 형식 오류·존재하지 않는 날짜·역순 기간·알 수 없는 목록 sort·잘못된 페이지/크기는 400 `VALIDATION_FAILED`다. `from=`처럼 명시한 빈 날짜도 서버는 거절한다.
- 같은 시각은 정산 ID를 보조 정렬로 사용한다. 오래된순은 시각·ID 모두 오름차순, 최신순은 모두 내림차순이다. 범위를 벗어난 페이지는 빈 목록이다.
- 목록·건수·합계에 동일한 기간 및 `order.seller_id=인증 회원` 조건을 적용한다. 클라이언트 회원 ID는 사용하지 않으며 인증·no-store·ID 문자열·UTC 응답·정수 금액을 유지한다.
- 각 API는 기존 REPEATABLE_READ 읽기 트랜잭션이다. 목록과 건수는 같은 요청의 스냅샷이며, **목록·합계의 별도 HTTP 요청 사이까지 하나의 스냅샷으로 보장하지 않는다.** 새 정산이 그 사이 추가되면 새로 조회해 확인한다. 금액 합산의 BigDecimal·정확한 정수 변환은 유지한다.

<a id="finance-batch"></a>
### 충전 복구 Batch·관리자 조회

J5는 기존 PROCESSING/UNKNOWN 충전과 저장된 payment_key를 조회해 복구한다. 신규 승인·충전 생성·READY 자동 처리를 하지 않는다. 처리권/재시도·기존 지급 멱등·Spring Batch 영속 메타데이터를 사용한다. 관리자 금융/PDF 후보 조회는 실제 실행을 예약하지 않는다. K6 [감사 기반](#admin-action-audit)·[실행 내부 입구](#admin-reprocessing)(#167)와 [관리자 실행 API/화면](#admin-reprocessing-api)(#168)은 main 반영됐다.

#### 업무 규칙·연결

##### 창재님이 연결할 서비스

빈: `admin/service/AdminFinanceQueryService`(복구 Batch 실행 조회·재시작 가능 판단은 같은 트랜잭션 안에서 `AdminRecoveryJobQueries`가 맡는다). 모든 public 진입에서 전달받은 회원의 **현재 DB 역할**을 검사한다. 존재하지 않는 회원·잘못된 인증 ID는 401 `AUTHENTICATION_REQUIRED`, 일반 회원은 403 `FORBIDDEN`이다. 대상 조회 전에 권한을 확인한다. 인증 시점의 세션 역할만으로 조회를 허용하지 않는다.

| 메서드 | 반환 | 용도 |
| --- | --- | --- |
| `topups(adminId, TopupFilter, page, size)` | `PageResponse<AdminFinanceResponses.Topup>` | 회원 ID·충전 상태로 충전 목록 조회 |
| `topup(adminId, topupId)` | `Topup` | 충전 상태·시도 수·다음 시각·처리권 만료와 후보 조건 |
| `payments(adminId, PaymentFilter, page, size)` | `PageResponse<PaymentRecord>` | 주문·구매자·판매자·결제 상태·정책별 결제/환불 목록 |
| `orderFinance(adminId, orderId)` | `OrderFinance` | 주문 금액·결제/환불·보관금·정산·종료 분배를 함께 조회 |
| `recoveryJobs(adminId, page, size)` | `PageResponse<RecoveryJob>` | 충전 복구 JobInstance별 최신 실행과 집계 |
| `recoveryJob(adminId, executionId)` | `RecoveryJob` | 특정 실행의 집계·재시작 후보 판단 |

조건 타입은 `AdminFinanceQueries.TopupFilter(memberId, status)`와 `PaymentFilter(orderId, buyerId, sellerId, status, policyVersion)`다. 필터 자체가 null이면 전체 조건이다. 회원·주문 ID 필터는 양수만 허용하고, 결제 상태는 `PAID`/`REFUNDED`, 정책은 `TradePolicyVersion`, 충전 상태는 `WalletTopupStatus`를 사용한다. 문자열 상태를 임의로 대소문자 변환하지 않는다.

페이지는 0부터, 크기는 1~100이다. 충전은 생성 시각·ID 내림차순, 결제는 결제 시각·ID 내림차순, Job은 인스턴스 최신 순이다. Job 목록의 `totalElements`는 실행 수가 아닌 인스턴스 수이며, 이전 실행은 상세 메서드로 조회한다. 잘못된 조건은 400 `VALIDATION_FAILED`, 없는 상세 대상 또는 다른 종류 Job의 실행 ID는 404 `RESOURCE_NOT_FOUND`다.

##### 후보 판단의 의미 / Batch 재시작

고정 Job 이름 `walletTopupRecovery`만 조회한다. 같은 인스턴스의 **최신 실행**이 `FAILED` 또는 `STOPPED`이고, 복구 설정·토스 설정·허용 파라미터가 유효할 때만 후보가 된다. 실행 중은 `JOB_RUNNING`, 이전 실행은 `NEWER_EXECUTION_EXISTS`, 잘못된 파라미터는 `INVALID_JOB_PARAMETERS`, 나머지 상태는 `JOB_NOT_RESTARTABLE`이다.

`STARTED`·`UNKNOWN`·`ABANDONED`를 임의로 실패로 바꾸거나 강제 재시작하지 않는다. 프로세스 종료 확인과 이력 복구는 [Batch 운영 경계](features.md#b-1088-0)를 따른다. 기존 `TopupRecoveryJobService.start()` / `restart(executionId)`는 내부 조율용 그대로이며 관리자 API를 직접 붙인 상태가 아니다.

##### 처리 범위

사용자가 결제창을 닫았거나 승인 후 통신/지급 저장이 실패해 남은 `PROCESSING`·`UNKNOWN` 충전을 조회하고 복구한다. 서버가 이미 저장한 `payment_key`로 **결제 조회만** 호출한다. 승인 요청을 다시 보내거나 충전을 새로 만들지 않는다. `READY` 신청은 대상이 아니다.

기존 `TopupApprovalTransactions.apply`가 주문번호·키·금액·KRW·테스트 여부·승인 시각·제공자 상태를 다시 검증한다. 확인된 `DONE`에서만 충전 완료·잔액 증가·원장 기록이 함께 커밋된다. `ABORTED`·`EXPIRED`·`CANCELED`는 지급 없이 실패로 끝낸다. 불명확한 응답은 미확정으로 유지한다. 주문 환불·출금·실제 현금 반환은 이 작업의 범위가 아니다.

##### 공식 근거

- [Spring Boot Batch 자동 구성·시작 실행 제어](https://docs.spring.io/spring-boot/reference/io/spring-batch.html)
- [Tasklet Step의 트랜잭션](https://docs.spring.io/spring-batch/reference/step/tasklet.html)
- [JobRepository와 메타데이터](https://docs.spring.io/spring-batch/reference/job/configuring-repository.html)
- [JobOperator 재시작·복구 API](https://docs.spring.io/spring-batch/reference/api/org/springframework/batch/core/launch/JobOperator.html)

V9 스키마의 원본 경로는 `spring-batch-core-6.0.6.jar!/org/springframework/batch/core/schema-mysql.sql`이다.

#### API·입력·오류

##### 응답·개인정보·시간

결제 키·승인 요청 키·처리권 토큰·회원 이메일/비밀번호·외부 응답은 조회 응답에 포함하지 않는다. 마지막 충전 오류는 알려진 내부 코드만 반환하고, 알 수 없는 저장 값은 `RESULT_REQUIRES_REVIEW`로 바꾼다. Batch 파라미터·컨텍스트·예외/스택 원문은 DTO로 반환하지 않는다.

Batch `scanRequestedAt`은 허용된 `requestedAtMillis` 파라미터를 UTC로 변환한 **원래 스캔 요청 시각**이다. 재시작 실행의 실제 시작 시각으로 표시하면 안 된다. 프레임워크의 시간대 없는 `LocalDateTime` 메타데이터를 UTC라고 가정하지 않는다. 결과 집계는 `completed`, `failed`, `deferred`, `exhausted`, `stale`만 읽으며 `creditedCount`는 Step의 지급 완료 건수다. Job `COMPLETED`는 모든 충전의 지급 완료를 의미하지 않는다.

프로세스 종료 등으로 인스턴스만 있고 실행이 없으면 `status=NO_EXECUTION`, `executionId=null`, 재시작 불가로 반환한다. 조회 전체가 실패하거나 이 인스턴스를 정상 완료로 표시하지 않는다.

##### API·화면 연결

<a id="b-49-0"></a>
이 서비스와 `AdminContractPdfQueryService`를 그대로 호출하는 **조회 전용** 관리자 API·화면을 연결했다. 경로는 [API 9-3절](architecture.md#b-204-1). `admin/controller/AdminFinanceController`는 세션의 관리자 ID만 넘기고 필터 값을 그대로 전달한다(enum은 대소문자 그대로 변환, 틀리면 400). DB·Flyway 변경 없음.

##### 내부 실행·재시작 계약

<a id="b-1088-0"></a>
| 호출 | 동작 |
| --- | --- |
| `TopupRecoveryJobService.start()` | 새 서버 UUID와 시작 시각으로 `walletTopupRecovery` 실행 |
| `TopupRecoveryJobService.restart(executionId)` | 같은 Job의 실패/중지 실행을 Batch 규칙에 따라 체크포인트부터 재시작 |

Job 파라미터는 `run.id`(UUID)와 `requestedAtMillis`(0 이상, UTC 9999년 이내 epoch 밀리초) 두 개만 허용한다. 규칙은 `wallet/batch/TopupRecoveryJobParameters` 하나에 있고 Job 검증기(시작과 저장된 파라미터로 재시작할 때 모두 적용, 재시작 실패는 `JobRestartException`)와 관리자 재시작 판단(`INVALID_JOB_PARAMETERS`)이 같이 쓴다. Step 컨텍스트에는 `highestId`·`lastId`와 `completed`·`failed`·`deferred`·`exhausted`·`stale` 집계만 업무 값으로 저장한다. 프레임워크의 기본 체크포인트 항목도 함께 저장된다. 결제 키·처리권 토큰·외부 응답 원문은 저장하지 않는다. 업무 예외는 고정 오류 코드로 바꿔 Batch 실패 메타데이터에 제공자 원문이 들어가지 않도록 한다.

프로세스 강제 종료로 메타데이터가 `STARTED`에 남으면 바로 `restart`할 수 없다. 새 Job 실행은 만료된 처리권을 다시 확보해 업무 복구를 계속할 수 있다. 예전 실행 이력의 복구는 작업자 종료를 운영자가 확인한 후 Spring Batch `JobOperator.recover`로 실패 상태를 정리하는 별도 운영 절차다. 실행 중인 작업을 자동으로 종료된 것으로 간주하거나 강제 복구하는 API는 제공하지 않는다.

#### 실행·환경

##### K6 실제 실행을 연결할 때

<a id="b-48-1"></a>
상품 숨김/복구는 기존 `product_moderation_logs`를 유지한다. K6 공통 감사 기반은 [V13·내부 연결 계약](#admin-action-audit)을 따른다. 관리자 실행 API·화면은 김창재, DB·내부 감사/금융/PDF 기반은 황준범 담당이다. 금융 작업을 상품 로그에 끼워 넣거나 원장/Batch 컨텍스트를 관리자 감사 이력으로 사용하지 않는다.

1. Controller는 세션의 `CurrentMemberId.require(authentication)`에서 관리자 ID를 얻고 `/api/admin/**` 권한·변경 요청 CSRF·`Cache-Control: no-store`를 적용한다. 요청 본문/쿼리의 관리자 ID를 신뢰하지 않는다.
2. 변경 서비스는 DB 권한, 사유, 대상과 중복 요청을 재검사하고 **감사 요청 기록을 먼저 커밋**한다. 내부 저장 계약은 아래 `AdminActionAuditService`이며 실행 조율자는 `execute=false`인 재전송을 다시 실행하지 않는다.
3. 외부 조회/Batch 시작은 감사 기록 트랜잭션 밖에서 실행한다. `TopupRecoveryJobService`의 `NEVER`, 실제 충전 claim → 트랜잭션 밖 조회 → 지급 적용 경계를 깨지 않는다.
4. 결과 이력을 별도 커밋한다. 조회·실행 결과가 불명확하면 성공/실패를 단정하지 않는다. 잔액 직접 수정·실패 상태 강제 완료·시도 수 초기화로 복구하지 않는다.

관리자 단건 재확인은 인증된 구매자용 API에 타인의 ID를 넣어 호출하는 방식으로 연결하지 않는다. 아래 관리자 명령 경계가 기존 지급 트랜잭션과 처리권을 재사용한다. 조회 후보·실제 실행 서비스·공개 API/화면의 구현 상태는 구분한다.

<a id="admin-action-audit"></a>
##### K6 감사 기반 — main 반영(#167)

2026-11-10, 황준범. main `86ecba1` 기준 `feature/admin-action-audit`에서 V13과 JPA 내부 서비스를 추가했다. [기존 제안](change-history.md#b-19-0)의 요청 선행 커밋·멱등 키·결과 분리를 구현하며 실제 재처리 버튼이나 공개 API가 완성됐다는 의미는 아니다.

| 내부 호출 | 연결 규칙 |
| --- | --- |
| `AdminActionAuditService.begin(adminId, action, targetId, reason, UUID requestKey)` | 트랜잭션 밖에서 호출. 회원 공유 잠금·현재 DB ADMIN 검사 → 동일 키 재전송 검사 → 진행 중 대상 잠금 → 기존 운영 조회의 대상 존재/후보 검사 → REQUESTED 독립 커밋. `BeginResult(receipt, execute)` 반환 |
| `execute=false` | 같은 관리자·키·명령·대상·정규화 사유의 재전송. 기존 상태가 REQUESTED/UNKNOWN이어도 재실행하지 않고 이력을 반환한다. 다른 인자는 409 `ADMIN_ACTION_REQUEST_CONFLICT` |
| `execute=true` | **커밋된 요청을 받은 조율자만** 업무 명령을 트랜잭션 밖에서 실행한다. 업무 명령은 현재 ADMIN·대상·잠금·처리권·상태를 재검증해야 한다. 감사 예약만으로 실행 권한을 대신하지 않는다 |
| `complete(logId, Outcome)` | 신뢰한 서버 내부 조율자만 호출. REQUESTED에서 허용된 Outcome을 결과 코드/상태로 별도 커밋. 아래 업무별 코드와 기존 공통 SUCCEEDED / BUSINESS_REJECTED / EXECUTION_FAILED / RESULT_UNCONFIRMED를 지원한다. 예외 원문을 받지 않으며 이미 닫힌 결과는 덮어쓰지 않는다 |
| `inspect(adminId, logId)` | 현재 DB ADMIN 재확인 후 안전한 단건 이력 DTO 반환. 목록 API·검색·화면은 후속 |

- 대상 매핑은 `TOPUP_RECHECK → WALLET_TOPUP`, `TOPUP_RECOVERY_RESTART → BATCH_JOB_EXECUTION`, `CONTRACT_PDF_REGENERATE → CONTRACT`. PDF의 `targetId`는 **계약 ID**이고 주문 ID·버전은 해당 계약에서 조회한다. 다른 대상의 `orderId`는 NULL이다.
- 사유는 앞뒤 공백을 제거한 1~1,000자, 요청 키는 UUID다. 정규화된 사유까지 동일해야 같은 요청이다. API에서는 세션 관리자 ID를 전달하고 클라이언트가 실행 결과·관리자 ID를 지정하게 하지 않는다.
- 같은 대상·명령의 REQUESTED는 생성 열 UNIQUE로 관리자 간에도 한 건만 허용한다. 경쟁 삽입의 UNIQUE 위반은 롤백 후 새 트랜잭션에서 승자 기록을 다시 확인한다. 진행 중이면 409 `ADMIN_ACTION_IN_PROGRESS`, 후보가 아니면 기록 없이 409 `ADMIN_ACTION_NOT_ELIGIBLE`이다.
- `lancerpick.admin.action-audit.pending-timeout` 기본 PT10M, 허용 1~30분. **업무 처리권/실행 제한 시간이 아니라 감사 결과 미확정 표시 기준**이다. 정확히 만료된 시각부터 다음 새 요청이 기존 요청을 UNKNOWN/LEASE_EXPIRED로 닫고 새 후보를 검사한다. 후보 검사 실패 시 그 트랜잭션도 롤백한다. 자동 정리·재실행 스케줄러는 없다.
- 외부 응답 유실·DB 결과 유실은 UNKNOWN으로 보존한다. UNKNOWN 또는 기한 경과가 재실행 성공을 보장하지 않으며 지급/Batch/PDF의 고유 처리권과 멱등 검사가 별도로 필요하다. 과거 요청의 늦은 완료 결과가 UNKNOWN을 덮지 않는다.
- 감사 기록은 삭제 API를 제공하지 않으며 기존 상품·문의·회원 제재 이력은 중복 기록하지 않는다. `reason`은 관리자가 입력한 사유라 API·화면에서도 관리자만 볼 수 있게 해야 한다. 서버가 결제 키·외부 응답·경로·예외 원문을 사유/결과에 자동 첨부하지 않는다.
- 관리자용 충전 재확인·Batch 재시작·PDF 실행 조율자는 아래 구현(#167)을 따른다. 사유/UUID 입력·감사 목록/실행 API·화면은 [K6 실행 API·화면](#admin-reprocessing-api)(김창재)으로 연결했다. 알림 FAILED 재발송은 이번 공통 감사 대상 밖이다.

<a id="admin-reprocessing"></a>
##### K6 관리자 재처리 실행 내부 입구 — main 반영(#167)

황준범, `feature/admin-action-audit`, 감사 기반 커밋 `7b53482` 후속. 김창재의 #141 설계에 맞춘 내부 호출 계약이다. **Controller는 아래 서비스만 호출하며 begin/complete를 따로 중복 호출하지 않는다.** 호출자는 세션의 관리자 ID를 전달하고 변경 요청의 CSRF·ADMIN·no-store를 적용한다.

| AdminReprocessingService 메서드 | 인자/대상 |
| --- | --- |
| `recheckTopup(adminId, topupId, reason, UUID requestKey)` | 저장된 충전 PK. 결제 키·금액·구매자 ID를 입력받지 않는다 |
| `restartTopupRecovery(adminId, executionId, reason, UUID requestKey)` | walletTopupRecovery의 실패/중지 실행 PK. 파라미터를 추가/변경하거나 새 Job으로 바꾸지 않는다 |
| `regenerateContractPdf(adminId, contractId, reason, UUID requestKey)` | 특정 계약 버전의 PK. 주문 ID/버전을 쓰는 Controller는 기존 `AdminContractPdfQueryService.inspect(adminId,orderId,version).contractId()`로 해석한다. 현재 계약 버전은 실행 트랜잭션에서 다시 검사한다 |

공통 응답은 `Result(receipt, replay)`다. `replay=false`인 새 요청은 설계안대로 HTTP 202, `true`인 동일 키 재전송은 200에 대응할 수 있다. receipt는 감사 결과만 포함하며 결제 키·외부 응답·회원 이메일·PDF 원문/해시/경로·처리권 키는 반환하지 않는다. 대상 최신 상태는 기존 관리자 GET으로 조회한다. 요청 검증/최초 권한/후보 거절은 기존 400/401/403/404/409이고, **감사 접수 이후 업무 상태가 바뀌거나 결과가 불명확한 경우는 receipt의 상태/코드**로 판단한다.

| 결과 코드 | 감사 상태 | 해석 |
| --- | --- | --- |
| TOPUP_CREDITED | SUCCEEDED | 기존 지급 트랜잭션 커밋으로 적립금 반영 |
| TOPUP_RESOLVED_FAILED | SUCCEEDED | 조회 자체는 성공했고 제공자 결제가 취소/실패로 확정됨. **충전 성공 표시 금지** |
| TOPUP_STATE_CHANGED | REJECTED | 처리권 또는 대상 상태가 바뀌어 이번 요청에서 반영하지 않음 |
| TOPUP_RESULT_UNCONFIRMED | UNKNOWN | 외부 응답/검증/지급 커밋 결과 확인 필요 |
| BATCH_RESTART_ACCEPTED | SUCCEEDED | 재시작 호출 수락. **Job 완료나 충전 지급 성공을 뜻하지 않음**, 최신 실행 조회 필요 |
| BATCH_RESTART_REJECTED | REJECTED | 후보/권한/중복 실행/재시작 조건에 맞지 않아 거절 |
| BATCH_RESULT_UNCONFIRMED | UNKNOWN | 재시작 접수 또는 메타데이터 결과가 불명확함 |
| PDF_GENERATED | SUCCEEDED | 실제 PDF 저장·현재 계약 메타데이터 커밋 완료 |
| PDF_GENERATION_FAILED | FAILED | 렌더링/저장 실패와 계약 FAILED 기록이 확정되어 재시도 가능 |
| PDF_STATE_CHANGED | REJECTED | 계약 교체·주문 취소·권한 변경·다른 처리권 등으로 이번 생성 반영 불가 |
| PDF_RESULT_UNCONFIRMED | UNKNOWN | PDF 완료 커밋 등 결과가 불명확함. 실제 상태 확인 전 재생성 성공/실패로 단정하지 않음 |

- 요청 선행 커밋 → **회원 공유 잠금 → 감사 요청 쓰기 잠금 → 충전/주문 쓰기 잠금**에서 실행 관리자/요청 소유자/명령/대상/REQUESTED/감사 기한 검사 → 고유 업무 처리권 커밋 → 외부 조회/렌더링 → 업무 결과 커밋 → 감사 결과 별도 커밋 순서다. Batch는 후보 재검사 트랜잭션을 끝낸 뒤 기존 `TopupRecoveryJobService.restart`를 호출하며 Spring Batch의 최신 실행/중복 실행 방어를 유지한다.
- 충전은 **미확정 PROCESSING/UNKNOWN**만 재확인한다. 자동 복구·Batch를 끈 환경에서도 단건 실행할 수 있고, 최소 대기·백오프·유효 처리권은 지킨다. 수동 조회는 자동 복구 시도 상한과 무관하며 자동 횟수를 초기화/증가시키지 않는다. 자동 작업자와 같은 충전 처리권을 사용하고, 저장된 키·금액/통화/주문번호 검증과 기존 원장 UNIQUE를 유지한다. READY 신규 승인·기존 FAILED 강제 복구·잔액 직접 수정은 없다.
- PDF는 DRAFT/FAILED/만료된 GENERATING의 현재 계약만 처리한다. READY/SIGNED 파일을 덮거나 서명을 바꾸지 않는다. 주문 잠금 전에는 불변 주문 ID만 조회해 오래된 계약 엔티티가 남지 않게 하고, 생성 완료 때도 관리자 권한·주문 상태·현재 계약·생성 키를 재검사한다. 취소/교체로 무효가 된 파일은 참조 여부 확인 후 폐기하고 완료 커밋 결과가 불명확하면 파일을 유지한다. 당사자 다운로드·판매자 생성 API의 401/404 계약은 유지한다.
- 감사 결과 저장 자체가 실패하면 기존 REQUESTED를 유지한다. 같은 키 재전송은 그 이력을 반환하며 작업을 다시 실행하지 않는다. UNKNOWN·감사 기한 만료만으로 강제 지급·강제 완료·자동 재실행하지 않는다.
- 새 테이블·컬럼·환경변수·공개 REST 경로는 추가하지 않았다. V13 감사 테이블만 사용한다. 관리자 화면/목록·실행 API, 원격 CI와 실제 제공자 브라우저 검증은 별도다.

<a id="admin-reprocessing-api"></a>
##### K6 관리자 재처리 실행 API·화면 — main 반영(#168, 김창재, 2026-11-10)

`feature/admin-reprocessing-api`, main `c76b2eb`(#167) 기준. 위 내부 입구를 HTTP와 화면에 연결했다. 새 DDL·마이그레이션·환경변수는 없다. 경로·입력은 [API 표](architecture.md#b-204-1)를 따른다.

| 규칙 | 내용 |
| --- | --- |
| 호출 | `AdminReprocessingController`는 실행마다 `AdminReprocessingService` 메서드 하나만 부른다. begin/complete를 따로 부르거나 결과 코드를 고르지 않는다. 관리자 ID는 `CurrentMemberId.require(authentication)` |
| 입력 | `reason`·`requestKey`는 JSON 문자열만(숫자·boolean 자동 변환 없음), requestKey는 UUID 형식. `version`은 PDF에서만 정수 1 이상. 사유 정리·길이(1~1,000자)는 감사 서비스가 최종 검사 |
| 응답 | `Result.replay=false` → 202, `true` → 200. 본문은 `AdminActionReceipt`. 대상 최신 상태는 기존 GET으로 다시 조회 |
| PDF 대상 | 경로의 주문 ID와 version(생략 시 현재)을 `AdminContractPdfQueryService.inspect`로 계약 ID로 바꾼다. 계약이 없으면 감사 기록 없이 404. 해석 후 계약이 바뀌면 실행 재검사가 `PDF_STATE_CHANGED`로 거절 |
| 감사 목록 | `AdminActionLogService`(현재 DB ADMIN 재확인) + `AdminActionLogQueries`(QueryDSL, 잠금 없음). action·targetId(action과 함께, 대상 종류로 한정)·orderId, ID 내림차순 |

화면:

- `/admin/finance` 충전 탭은 `manualRecheck.eligible`, 충전 복구 작업 탭은 `restart.eligible`이고 실행 번호가 있을 때만 "재처리" 칸(접힌 상태)을 보여 준다. `/admin/finance/orders/:id`의 계약 PDF 칸은 현재 버전이고 생성 후보일 때, 조회한 version으로 요청한다.
- 버튼을 누를 때 `crypto.randomUUID()`로 requestKey를 한 번 만든다. 보낸 요청은 계정별 Query 메모리(`['admin','reprocess',명령,대상]`)에 보관해 화면을 이동해도 남고 로그아웃·계정 전환 때 지워진다.
- 통신 오류·5xx는 "서버가 이미 처리했을 수 있어요"로 바꾸고 **같은 키·같은 사유로만** "같은 요청으로 결과 확인"을 허용한다(후보에서 빠져도 이 확인은 가능). 첫 요청의 명확한 4xx는 기록이 없으므로 지우고, 불명확했던 요청의 재전송이 거절되면 첫 요청 결과를 단정하지 않고 그대로 둔다.
- 자동 재시도·처리 중 재전송은 없다(ref와 `sending` 상태로 두 번 클릭 방지). 클릭 시 계정 경계(`captureAccountScope`)를 잡아 계정이 바뀌면 보내지 않고 응답도 반영하지 않는다. 응답 뒤 금융 조회와 작업 이력을 다시 읽는다.
- 결과는 receipt의 상태(처리 중·처리됨·반영 안 됨·실패·결과 확인 필요)와 결과 코드 설명으로 보여 준다. `BATCH_RESTART_ACCEPTED`는 "접수"이지 작업 완료가 아니고, `TOPUP_RESOLVED_FAILED`는 충전되지 않았음을 표시한다. 모르는 코드는 코드만 보여 준다.
- "관리자 작업 이력" 탭(작업 종류 필터, URL `action`)과 주문 금액 화면의 이력 칸(orderId)은 조회만 한다. 요청 키는 화면에 보여 주지 않는다.
- 알림 FAILED 재발송은 여전히 범위 밖이다.

##### 실행 설정

기본값은 다음과 같다. 기존 팀원에게 새 개인 설정이나 Redis 설치를 요구하지 않는다.

| 설정 | 기본값 | 의미 |
| --- | --- | --- |
| `TOPUP_RECOVERY_ENABLED` | `false` | 복구 서비스·Batch Job 등록 |
| `TOPUP_RECOVERY_SCHEDULING_ENABLED` | `false` | 등록된 복구 Job의 주기 실행 |
| `lancerpick.wallet.topup-recovery.stale-seconds` | 120초 | 처리 시작 후 자동 조회까지 최소 대기 |
| `lancerpick.wallet.topup-recovery.lease-seconds` | 180초 | 한 작업자가 확보하는 처리권 유효 시간 |
| `lancerpick.wallet.topup-recovery.max-attempts` | 8회 | 자동 조회 처리권 확보 최대 횟수 |
| `lancerpick.wallet.topup-recovery.retry-seconds` | 60초 | 재시도 간격의 시작값 |
| `lancerpick.wallet.topup-recovery.delay-ms` | 60000ms | 전용 스케줄러의 초기/완료 후 대기 |

자동 실행할 환경에서만 Git 제외 개인 설정 또는 환경변수에 앞의 두 값을 `true`로 넣고 서버를 재시작한다. 기존 토스 테스트 키와 `TOSS_TOPUP_ENABLED=true`도 필요하다. 토스가 비활성 상태면 처리권/잔액을 변경하기 전에 실행이 실패한다. 키 원문은 문서·Job 파라미터에 넣지 않는다. `demo` 프로필에는 복구 기능을 등록하지 않는다.

`spring.batch.job.enabled=false`로 애플리케이션 시작 시 Job 자동 실행을 막고, `spring.batch.jdbc.initialize-schema=never`로 프레임워크의 자동 DDL을 막는다. 위 기능 설정과 관계없이 Flyway V9는 시스템 테이블을 적용한다.

#### 처리 순서·동시성·재전송

##### 트랜잭션·동시성

1. QueryDSL로 처리 시작 시각이 오래되고, 재시도 시각이 지났으며, 처리권이 없거나 만료된 ID를 찾는다. 실행 시작의 최대 ID를 상한으로 고정하고 ID 오름차순으로 조회한다.
2. 짧은 JPA 트랜잭션에서 해당 충전을 쓰기 잠금하고 조건을 재검사한다. UUID 처리권·만료 시각·시도 수를 기록한 뒤 커밋한다.
3. `TopupRecoveryService.recover`의 `NOT_SUPPORTED` 경계가 Step 트랜잭션을 일시 중단한다. 외부 토스 조회 시 활성 업무 트랜잭션이나 충전·지갑 잠금이 없다.
4. 새 JPA 트랜잭션에서 충전을 다시 잠그고 같은 처리권·유효 시간·미확정 상태인지 검사한다. 유효하면 기존 지급 트랜잭션을 재사용한다. 충전 → 지갑 잠금 순서와 원장 UNIQUE를 유지한다.
5. 수동 재확인이 먼저 완료했거나 새 작업자가 처리권을 얻었다면 예전 작업자의 늦은 응답은 `STALE`로 끝내고 금액/상태를 덮어쓰지 않는다.

Step 자체는 기본 트랜잭션으로 실행 컨텍스트와 체크포인트를 저장한다. 업무 변경은 그 Step 트랜잭션과 별도로 커밋되므로, 지급 직후 체크포인트 저장 전에 실패해도 재실행 시 기존 상태/원장으로 중복 지급을 막는다. Step 트랜잭션의 JDBC 연결은 외부 조회 동안에도 풀에 예약될 수 있다. 단일 전용 스케줄러에서 동기 실행하고 HTTP 대기 시간을 제한하며, 여러 병렬 worker로 확장하는 설정은 넣지 않았다. 조회 전체를 하나의 주문·지갑 트랜잭션으로 묶지 않는다.

자동 재시도는 기본 60·120·240·480·960·1920·3600초 간격이다. 실패 항목은 다음 시각을 기록하고 이후 ID를 계속 처리한다. 상한에 도달하면 자동 대상에서 제외하며 지급 실패로 단정하지 않는다. 처리 중 프로세스가 종료되면 상태가 `PROCESSING`으로 남을 수도 있다. 기존 본인 재확인 API는 횟수 상한 이후에도 사용할 수 있다.

#### 데이터·제약

##### DB·라이브러리

V5에 이미 있는 `wallet_topups`의 재시도 수·다음 시각·처리권·만료 컬럼과 인덱스를 사용한다. 별도 복구 시도 업무 테이블은 만들지 않는다. 업무 데이터 접근은 JPA/QueryDSL을 유지한다. JDBC는 Spring Batch 프레임워크의 실행 메타데이터 저장 구현에 한정되며, 업무 Repository에 JdbcTemplate·직접 SQL을 추가하지 않았다.

<a id="domain-communication"></a>
## 채팅·알림·자동 확정

<a id="communication-chat"></a>
### 저장·실시간 채팅·미확정 전송

주문 당사자의 저장형 1:1 채팅·WebSocket/STOMP·REST 복구를 제공한다. 순번/clientMessageId로 중복·누락을 방어하고 커밋 뒤 전달한다. 헤더 목록·별도 창·최소화·경로 유지, 계정/세션 폐기, 종료 거래 조회 전용을 적용한다. 방/창을 닫아도 미확정 UUID·본문을 계정/주문별 메모리에서 같은 키로 재확인하며 계정 변경 때 정리한다. 브라우저 종료를 넘는 영속 보관은 제공하지 않는다.

#### 업무 규칙·연결

##### 문제와 수정

`ChatDock`은 방을 바꾸거나 닫으면 기존 `ChatConversation`을 해제한다. 기존 전송 중복 방지 키는 그 컴포넌트의 ref에만 있어, 서버가 저장했지만 응답을 확인하지 못한 상태에서 다른 방을 열면 사라졌다. 같은 본문을 새 UUID로 보내면 기존 서버 UNIQUE 제약으로는 별개의 메시지이므로 중복 저장될 수 있다.

미확정 명령의 UUID·본문·전송 상태를 계정/주문별 QueryClient 메모리 캐시로 옮겼다. 일반 미전송 입력은 계속 컴포넌트 상태이며 방 전환/닫기 시 지운다. 미확정 명령만 방/전체 창을 닫아도 남겨 다시 열면 복원한다. 소켓/REST 전송 자체는 기존 경로를 재사용하고 임의의 자동 재전송을 추가하지 않는다.

| 상황 | 동작 |
| --- | --- |
| 전송 시작 | UUID·정리된 본문을 먼저 보관, 같은 명령의 동시 중복 클릭 차단 |
| 방 전환/창 닫기 | 컴포넌트를 해제해도 미확정 명령 유지. 다시 열면 본문과 진행 상태 복원 |
| 응답 불명확 | 편집을 막고 같은 본문·UUID로 재시도. 미확정 키를 GC 시간으로 자동 폐기하지 않음 |
| 성공 응답/본인 저장 이력 확인 | 해당 UUID만 정리. 해제된 컴포넌트의 성공 응답도 같은 계정이면 현재 방 조회를 갱신 |
| 최초 요청의 확정 거절 | 명령을 정리하고 같은 화면에서는 입력을 복원해 수정 허용 |
| 재시도의 거절 | 최초 요청의 성공 여부를 입증하지 못하므로 UUID를 보존. 재시도 제한 등으로 새 키가 생기지 않음 |
| 늦은 실패 | 재시도 회차를 비교해 더 최근 전송 상태를 덮어쓰지 않음. 오래된 성공도 다른 UUID는 지우지 않음 |
| 로그아웃·계정 전환 | 공통 개인 캐시 정리가 미확정 본문/UUID도 제거. 이전 계정의 늦은 응답은 세대 검사로 무시 |

메모리 전용이며 localStorage·sessionStorage·DB는 추가하지 않았다. 브라우저 새로고침/종료 후까지 UUID를 복구하거나 다른 기기와 동기화하는 기능은 제공하지 않는다. 계정 경계와 서버의 회원·당사자·상태·UNIQUE 검증은 유지한다.

##### 구현 위치

- `pending.ts`: 회원/주문별 캐시 키, 명령 시작·확정/불명확·재시도 회차·계정 검사.
- `queries.ts`: `skipToken`을 사용한 데이터 전용 관찰. 캐시 무효화가 재전송이나 원천 초기화를 유발하지 않는다.
- `useChatSend`: 일반 입력과 미확정 명령 분리, 해제 후 응답 처리, 저장 이력과 UUID/작성자 대조, 기존 전송 경로 재사용. `OrderChatPanel`은 이 훅과 `ChatMessages`(목록)로 화면만 구성한다.

##### 제공 범위와 담당

- J1: 주문 상세의 실시간 전송/수신, 재연결, 순번 기반 누락 복구. 전송 실패 시 같은 UUID로 사용자가 재확인/재전송한다.
- C2: HTTP 세션·회원 인증 버전 검사, 계정 전환·로그아웃·비밀번호 변경/재설정에 따른 소켓 종료. 기존 `AuthVersionLookup` 계약을 사용하며 회원 기능 정책을 바꾸지 않는다.
- 구매자·판매자만 해당 주문의 메시지를 작성/조회한다. 종료 주문은 조회 전용이고 기존 키의 재확인은 중복 저장하지 않는다. 관리자 권한도 타인의 대화 전문 열람을 허용하지 않는다.
- 읽음 표시·파일 첨부·편집/삭제·미접속 메시지 알림·관리자 상담·Redis Pub/Sub는 이 구현에 포함하지 않는다.

##### 세션·개인정보·자원 한도

- handshake에서 HTTP 세션과 인증 버전을 캡처한다. CONNECT·SEND·SUBSCRIBE 등 수신 프레임과 발송 직전마다 현재 세션 ID/로그인 주체/DB `auth_version`을 다시 검사한다. 세션 ID 회전·다른 계정·세션 무효화·인증 버전 변경은 거부한다.
- 로그아웃 세션 파괴 이벤트로 관련 소켓을 닫는다. 유휴 연결은 기본 5초 주기로 재검사한다. 이미 네트워크로 전달된 프레임을 회수할 수 있다는 뜻은 아니다.
- 소켓 하트비트나 수신 검사는 HTTP 세션의 유휴 시간을 연장하지 않는다. 화면의 정상 HTTP 조회는 기존 서버 세션 정책에 따라 세션 사용에 해당한다.
- 인증 버전 조회 실패도 차단한다. 계정 전환/화면 이탈 시 소켓·재연결/복구 타이머·진행 중 조회를 정리하고 이전 계정 캐시에 늦은 메시지를 넣지 않는다.
- Spring 프로토콜 처리기의 프레임 오류 로그에는 본문·CSRF 헤더가 포함될 수 있어 해당 logger를 OFF로 두고, 전용 보안 처리기에서 고정 오류 코드만 기록한다. 브라우저 STOMP debug 로그도 끈다. 프레임 원문을 찍는 DEBUG/TRACE를 임의로 켜지 않는다.
- 연결은 회원당 8개·서버 전체 256개, 구독은 연결당 2개다. 연결당 10초에 SEND 20개, 순서 보존 큐 진입 전 원시 프레임 60개까지 허용한다. 정상 heartbeat도 원시 프레임에 포함한다.
- 메시지 프레임 한도 16KiB, 송신 버퍼 64KiB, 송신/첫 프레임 대기 10초. 입·출력 실행기는 각각 2개 스레드·큐 100개로 제한한다. 본문은 기존 Unicode 2,000자 제한이다.
- 현재는 **단일 서버의 simple broker**다. 여러 인스턴스로 확장할 때는 세션 공유·Pub/Sub/외부 브로커·폐기 이벤트 전달(C4)을 함께 설계한다. 지금 구현이 다중 서버 전달을 지원하는 것으로 표현하지 않는다. 세션별 DB 인증 검사 비용도 성능 계측 대상이다.

##### 로컬 확인

1. 일반 창·시크릿 창에 각 주문의 구매자/판매자로 로그인하고 `/orders/{id}`를 연다.
2. ‘실시간 연결됨’을 확인하고 양측 전송·즉시 수신을 확인한다.
3. 네트워크를 잠시 끊은 동안 상대가 전송한 뒤 복구하면 누락분이 순번대로 나타나는지 확인한다. 동일 메시지를 두 번 표시하지 않아야 한다.
4. 로그아웃·다른 계정 로그인·다른 창에서 비밀번호 변경 시 이전 연결/작성창/캐시가 남지 않는지 확인한다.
5. 종료한 주문은 내역만 유지하고 작성할 수 없는지 확인한다.

##### 구조와 계정 경계

- 서버: `OrderChatThreadController` → `OrderChatThreadQueryService` → `OrderChatThreadQueries`. JPA/QueryDSL로 당사자 조건·주문 보관 조건·상대방·마지막 메시지를 조회한다. 마지막 메시지는 주문의 `chatSequence`와 기존 유일 제약을 활용해 조인하므로 주문마다 추가 조회하지 않는다. 회원 비밀번호 등 인증 데이터는 응답 조회에 적재하지 않는다.
- 목록과 기존 전송은 `OrderChatService.canSend`로 동일 상태 정책을 사용한다. 거래·금액 정책은 바꾸지 않았다.
- 화면: 공통 `AppLayout`의 `ChatWorkspaceProvider`가 창 선택·최소화만 관리한다. 기능의 `api.ts`·`queries.ts`·`types.ts`와 `components`를 나누고 기존 `OrderChatPanel`을 재사용한다.
- 목록은 열린 동안 30초 간격으로 재조회한다(브라우저 배경 주기 조회 제외). 선택 방의 메시지를 수신·전송하면 목록 캐시도 갱신한다. 다른 방의 즉시 푸시 목록 갱신은 제공하지 않는다.
- 캐시 키에는 회원·검색어·페이지를 포함한다. 공통 계정 변경 세대와 현재 회원을 검사해 이전 창이 다시 나타나지 않도록 한다. 전송 전 세션을 재확인하고 REST 경로는 CSRF 조회 중 같은 탭에서 계정이 바뀌었을 때도 POST를 중단한다. 서버는 매 요청 principal과 당사자 권한을 재검증한다. 다른 탭의 쿠키 변경과 전송 의도를 원자적으로 묶는 기능을 보장한다는 뜻은 아니다.
- 기존 STOMP·REST 대체 전송·UUID 멱등·순번 복구를 유지한다. 창 중복 생성으로 같은 방을 여러 번 구독하지 않는다.

#### API·입력·오류

##### 연결 계약

| 구분 | 경로·데이터 | 검사·결과 |
| --- | --- | --- |
| WebSocket handshake | 같은 출처 `/ws` | 기존 HTTP 로그인 세션 필요. Origin 기본 동일 출처 검사. SockJS·쿠키/토큰 URL 전달 없음 |
| STOMP CONNECT | 새 `GET /api/auth/csrf`의 `headerName: token`, `X-Chat-Member-Id: 현재 회원 ID` | Spring `XorCsrfChannelInterceptor`로 마스킹 CSRF 검증. 회원 ID 헤더는 계정 전환 경쟁 검출용이며 인증 주체는 서버 세션 |
| SUBSCRIBE | `/user/queue/chat`, `/user/queue/chat-errors` | 연결별로 두 종류만, 중복/임의 destination 거부. 회원 ID를 넣는 공개 topic은 제공하지 않음 |
| SEND | `/app/orders/{orderId}/chat/messages` | JSON `{clientMessageId, content}`. 발신자는 서버 연결에서 결정. HTTP와 같은 저장 서비스의 권한·상태·멱등 검증 |
| 저장 메시지/재확인 응답 | `/user/queue/chat` | 기존 `ChatMessage` JSON. 문자열 BIGINT ID/순번. 새 메시지는 커밋 이후 양측 회원의 유효한 연결에 전달. 동일 키 재확인은 요청 연결에 응답 |
| 업무 오류 | `/user/queue/chat-errors` | `{orderId, clientMessageId, status, code, message, fieldErrors, traceId}`(C1, [공통 계약](architecture.md#b-425-0)). `CHAT_CLOSED`, `RESOURCE_NOT_FOUND`, `IDEMPOTENCY_KEY_REUSED` 등 저장 서비스 오류는 그 HTTP 상태, 형식 오류는 400 `INVALID_REQUEST`, 예상치 못한 실패는 503 `CHAT_SEND_FAILED`로 원문 숨김 |

구독·전송은 승인 목록 외 기본 거부한다. STOMP의 login/passcode나 클라이언트 senderId로 인증하지 않는다. 서버 연결별 임의 라우팅 ID를 사용하고 HTTP 세션 ID를 메시지/클라이언트에 노출하지 않는다.

인증 실패는 WebSocket close `4401`, CSRF·허용되지 않은 구독/프레임은 `4403`, 연결/전송 제한은 `4429`다. 안전한 고정 코드만 close reason에 포함한다. 구문 파싱 오류는 안전한 STOMP ERROR 후 종료할 수 있다. 클라이언트는 인증/권한 거부 후 무한 재접속하지 않고 대화를 숨겨 재확인을 요구한다. 용량 제한 후에는 수동 재연결을 제공한다.

Spring의 순서 보존 채널은 인터셉터 예외를 내부에서 처리할 수 있다. 따라서 `ChatInboundSecurity`가 CSRF/권한 거부를 잡아 **연결을 직접 종료하고 프레임을 폐기**한다. 예외만 던지고 연결이 닫혔다고 가정하지 않는다.

##### API

기존 보호 API 규칙을 따르며 서버가 `CurrentMemberId.require()`로 발신자를 결정한다. POST는 CSRF가 필요하다. 응답은 `Cache-Control: no-store`다.

| API | 입력·응답 |
| --- | --- |
| `POST /api/orders/{orderId}/chat/messages` | `{"clientMessageId":"UUID","content":"본문"}`. 최초 201, 같은 요청 재전송 200. 메시지 DTO 반환 |
| `GET /api/orders/{orderId}/chat/messages` | 최신 메시지 조회. 기본 limit=50, 1~100 |
| 같은 GET + `beforeSequence=순번` | 해당 순번 미만의 과거 메시지 조회 |
| 같은 GET + `afterSequence=순번` | 해당 순번 초과의 누락 메시지 복구. 처음부터 복구할 때 0 허용 |

두 커서는 동시에 받지 않는다. before는 양수, after는 0 이상이며 Long 범위로 검증한다. UUID는 소문자 표준형으로 정리한다. senderId·sequence·시각은 서버 값이며 클라이언트가 정할 수 없다.

메시지 응답 필드: `id`, `orderId`, `sequence`, `senderId`, `clientMessageId`, `content`, `createdAt`. ID/순번은 **JSON 문자열**, 시각은 UTC ISO 8601이다. 목록은 `items`, `direction`(BEFORE/AFTER), `nextCursor`(추가 데이터가 없으면 null), `hasMore`, `lastSequence`, `canSend`를 반환한다. 두 방향 모두 items는 순번 오름차순이다. lastSequence는 해당 조회 스냅샷에서 확인한 주문의 마지막 순번이며 nextCursor와 역할이 다르다.

| 오류 | 의미 |
| --- | --- |
| 400 VALIDATION_FAILED | 잘못된 본문·UUID·커서·limit |
| 401 AUTHENTICATION_REQUIRED | 세션 없음/폐기 |
| 403 CSRF_INVALID | 변경 요청 CSRF 검증 실패 |
| 404 RESOURCE_NOT_FOUND | 타인/없는 주문 |
| 409 CHAT_CLOSED | 종료 주문에 새 메시지 작성 |
| 409 IDEMPOTENCY_KEY_REUSED | 같은 발신자의 키를 다른 정리된 본문에 재사용 |

##### 대화 목록 API

<a id="b-680-0"></a>
`GET /api/me/order-chats?q=검색어&page=0&size=20`

로그인 principal의 회원 ID만 사용한다. 요청의 회원 ID로 조회 대상을 바꿀 수 없다. `Cache-Control: no-store`이며 공통 페이지 응답(`items`, `page`, `size`, `totalElements`, `totalPages`)을 반환한다.

| 조건 | 계약 |
| --- | --- |
| page / size | 0부터 시작 / 기본 20, 1~100 |
| q | 앞뒤 공백 제거, 최대 100 Unicode 코드 포인트, 빈 값은 전체 |
| 숫자로만 된 q | 양의 주문번호 정확 일치. 0·범위 초과·없는 번호는 빈 결과 |
| 그 외 q | 주문 당시 상품명 또는 현재 상대방 표시 이름 부분 일치. `%`, `_`, `!`는 검색 문자로 취급 |
| 정렬 | 마지막 메시지 시각 내림차순. 메시지가 없으면 주문 생성 시각. 동률은 주문번호 내림차순 |

각 항목의 필드는 아래와 같다. 모든 ID는 JSON 문자열이다. 날짜는 오프셋을 포함하며 화면은 기존 한국 시간 포맷을 사용한다.

| 필드 | 의미 |
| --- | --- |
| orderId / productTitle | 주문번호 / 보관된 상품명 |
| counterparty.id / displayName | 상대방 ID / 현재 표시 이름 |
| side / orderStatus / canSend | 본인 BUYER·SELLER 구분 / 주문 상태 / 현재 전송 가능 여부 |
| lastMessagePreview / lastSenderId | 최근 메시지 최대 140 코드 포인트와 초과 시 생략 부호 / 작성자. 메시지가 없으면 null |
| lastMessageAt / activityAt | 최근 메시지 시각(null 가능) / 정렬 기준 시각 |

잘못된 페이지·크기·검색어는 400 `VALIDATION_FAILED`, 익명은 401이다. 주문 보관 조건이나 메시지 순번의 필수 기록이 유실됐다면 임의 데이터로 대체하지 않고 500 `DATA_INTEGRITY_ERROR`를 반환한다. 목록은 읽기 트랜잭션이며 새 잠금을 잡지 않는다. 메시지 전송 시의 기존 서버 권한·상태 검증이 최종 기준이다.

#### 처리 순서·동시성·재전송

##### 저장·트랜잭션·전달 경계

1. 회원 공유 잠금 → 주문 쓰기 잠금 → UUID 재확인 → 참여자/상태 검증 → 순번 증가·메시지 저장을 하나의 DB 트랜잭션으로 처리한다.
2. 같은 트랜잭션에 인메모리 `ChatMessageStored` 이벤트를 발행한다. `AFTER_COMMIT` 리스너가 양측의 유효한 소켓에 전달하므로 롤백한 메시지는 전달하지 않는다. 소켓 I/O로 DB 잠금 시간을 늘리지 않는다.
3. 전달 실패는 이미 커밋된 메시지를 취소하지 않는다. 메시지 원문을 아웃박스에 복제하지 않고 저장된 채팅을 순번으로 재조회한다. 이 이벤트는 영속 아웃박스가 아니며 전달 자체를 보장하지 않는다.
4. 재연결 시 개인 큐를 구독한 뒤 `GET .../messages?afterSequence=마지막복구순번&limit=100`을 끝까지 조회한다. 실시간 프레임의 큰 순번만 보고 cursor를 건너뛰지 않는다.
5. 마지막 한 건의 전달 누락과 구독 준비 경쟁도 회복하도록 15초마다 보충 조회한다. 메시지 ID 중복 제거·정확한 BIGINT 순번 정렬을 적용한다. 전송 중 보충 조회에서 같은 UUID가 발견되어도 저장 확인으로 처리한다.

화면은 실시간 연결이 끊겨도 기존 REST 조회/전송을 사용할 수 있다. 소켓 SEND 후 응답이 8초 내 확인되지 않으면 결과 불명확으로 유지한다. 자동으로 다른 UUID를 만들거나 쓰기를 재시도하지 않는다. 재연결은 1~30초 지수 지연·jitter, 연속 실패 최대 8회 후 수동 재연결이다. 소켓 연결마다 현재 회원·새 CSRF를 다시 확인한다.

##### DB·트랜잭션

기존 V5 `order_chat_messages`·V6 `orders.chat_sequence`를 사용하며 migration·테이블·컬럼 추가는 없다. 쓰기에는 JPA, 커서 조회에는 QueryDSL을 사용한다.

전송은 READ_COMMITTED에서 **실행 회원 공유 잠금 → 주문 배타 잠금 → 기존 키 확인 → 상태 확인 → chat_sequence 증가·메시지 저장**을 한 트랜잭션으로 처리한다. 주문의 순번을 사용하며 전역 메시지 AUTO_INCREMENT를 커밋 순서로 가정하지 않는다. 같은 주문의 전송과 거래 상태 변경은 같은 주문 잠금으로 직렬화된다. 기존 UNIQUE(order_id,sequence)·UNIQUE(order_id,sender_id,client_message_id)도 유지한다. 같은 UUID라도 발신자가 다르면 각자의 요청이다.

메시지 저장 실패/상위 트랜잭션 실패 시 순번도 롤백된다. 이 경계에는 소켓·메일·외부 통신이 없다. 요청/응답 DTO의 toString은 본문을 숨긴다.

#### 화면·캐시·계정 경계

##### 프런트 연결과 확인 방법

메시지 조회 키에 현재 회원 ID를 포함한다. 계정 전환 시 공통 accountCache가 조회 취소/삭제, 컴포넌트 key가 입력 상태를 초기화한다. 전송 전후 서버의 회원을 다시 확인하고 변경 응답을 개인 캐시에 직접 삽입하지 않는다. 배경 세션 재확인으로 같은 계정의 입력을 지우지 않으며, 조회/권한 실패 시 이전 대화는 숨긴다.

POST를 자동 재전송하지 않는다. 결과 불명확 시 같은 화면에서 본문/UUID를 유지하고 편집을 잠근다. 사용자가 ‘대화 새로고침’으로 저장된 키를 확인하거나 **동일 키·동일 내용**으로 재전송한다. 브라우저 전체 새로고침·탭 종료 시 초안/키는 사라지므로 서버에 저장된 내역부터 확인한다. 브라우저 저장소에 대화 초안을 보관하지 않는다.

##### 화면과 동작

- 최소화: 선택한 대화와 입력 중인 내용을 유지한다. 선택 방의 소켓·누락 복구도 유지하고 목록의 주기 조회는 멈춘다.
- 방 전환·대화창 닫기: 해당 대화 입력과 연결을 정리한다. 전체 닫기는 목록도 정리한다.
- Escape: 대화창에서 목록으로, 목록에서 전체 닫기로 동작한다. 최소화·전체 닫기 후에는 헤더 버튼으로 포커스를 돌린다.
- 알림 열기: 채팅을 최소화해 두 패널의 겹침을 줄인다. 알림 컴포넌트는 선택적 `onOpen`만 제공한다.
- 로그아웃·계정 전환·세션 만료: 이전 회원의 창·입력·개인 캐시·연결을 정리한다.

읽음 추적·채팅 미확인 건수·온라인 표시·첨부·운영 상담 채팅은 추가하지 않았다. Redis와 새 테이블·인덱스도 추가하지 않았다.

<a id="communication-notifications"></a>
### 아웃박스·알림·읽음

거래 상태 변경과 아웃박스를 같은 트랜잭션에 저장하고 이벤트별 전달·재시도·실패 격리를 수행한다. 알림 목록·읽음·읽지 않음 조건·헤더 패널·대상 이동을 제공한다. V3 계약/금액/기한/자동 확정의 알림과 실패 후속 범위를 구분한다.

#### API·입력·오류

##### 화면·API 연결

- 로그인한 헤더의 종 버튼 → 현재 화면 위에 알림 패널. 전체/읽지 않음, 내용·상품명·시각·읽지 않음 구분, 모두 읽음, 서버 페이지 이동, 전체 알림 보기(`/notifications`). 흑백·회색으로 구분한다.
- 패널은 최대 420px 너비·600px 높이이며 화면 크기 안으로 위치를 제한한다. 좁거나 높이가 낮은 화면에서도 내부 목록을 스크롤한다. 배경 페이지를 다른 경로로 바꾸지 않는다.
- 기존 알림 API·캐시 키·30초 배지 조회·계정 정리를 재사용한다. 패널의 목록 조회는 열려 있는 동안만 연결한다. 기존 전체 페이지가 열려 있으면 그 페이지의 조회는 계속 동작한다.
- 알림 링크는 읽음 응답을 기다리지 않고 목적지로 이동한다. `NotificationRow`와 읽음 실패 안내를 전체 목록/패널이 공유하며, 읽음 변경은 공통 mutation 옵션의 계정 경계·CSRF·자동 재시도 금지를 유지한다.
- 목록 실패를 빈 결과로 표시하지 않는다. 다시 조회, 세션 만료 안내, 범위 밖 빈 페이지의 첫 페이지 이동을 제공한다. 읽지 않은 수 조회 실패는 배지만 숨기고 버튼은 유지한다.

API·서버·DB·라이브러리 의존성은 변경하지 않았다. 백엔드 U4는 정은찬의 #131 제공 범위다. 새 알림 유형·이메일·실시간 전송·설정 기능은 추가하지 않았다.

##### 첫 계약 서명 알림

<a id="b-648-0"></a>
사용자 확정에 따라 `feature/first-contract-signature-notification`에서 첫 서명도 알리도록 추가했다. 기존 아웃박스·알림 저장 구조를 재사용하며 새 테이블·컬럼·API·주문 이력 행동은 추가하지 않는다.

| 사건 | 알림 유형 | 수신자 | 상태 변화 |
| --- | --- | --- | --- |
| 현재 버전 첫 서명 저장 | `ORDER_CONTRACT_SIGNATURE_REQUESTED` | 아직 서명하지 않은 상대방 한 명 | 계약 READY·주문 AWAITING_CONTRACT 유지 |
| 두 번째 서명으로 계약 성립 | 기존 `ORDER_CONTRACT_SIGNED` | 마지막 서명자의 상대방 한 명 | 계약 SIGNED·주문 AWAITING_PAYMENT |

첫 알림은 "상대방이 계약에 먼저 서명했습니다. 주문 상세에서 계약과 서명 상태를 확인해 주세요.", 완료 알림은 "양측 서명이 완료되어 계약이 성립했습니다. 주문 상세에서 다음 절차를 확인해 주세요."로 구분한다. 실제 배달은 기존 아웃박스 처리와 알림 조회 주기를 따른다. 새로운 소켓·메일 알림은 아니다.

첫 서명 행과 아웃박스 이벤트는 같은 트랜잭션이다. 이벤트 저장 실패 시 서명도 롤백하며, 커밋 후 알림 전달 실패는 기존 백오프/재시도로 처리한다. 같은 서명 재요청은 기존 결과를 반환하고 이벤트를 만들지 않는다. 주문 잠금·서명 UNIQUE와 `CONTRACT_SIGNATURE_REQUESTED:<signatureId>`의 이름 기반 UUID·아웃박스/알림 UNIQUE를 함께 사용한다. 새 계약 버전은 새 서명 ID이므로 해당 버전의 첫 서명도 한 번 알릴 수 있다. 기존에 이미 첫 서명을 마친 계약을 일괄 보정 발송하지 않는다.

페이로드는 `orderId`, `contractId`, `contractVersion`, `signatureId`, `action=CONTRACT_SIGNATURE_REQUESTED`, `recipientIds`다. ID는 문자열이며 해시·PDF 원문·요구사항·개인정보를 넣지 않는다. 주문 이력 `CONTRACT_SIGNED`는 계속 양측 완료만 의미한다. 첫 서명의 보존 원천은 `contract_signatures`이므로 새 이력 행동이나 Flyway 변경은 필요 없다.

##### API·화면

API는 [API 명세 7-1](architecture.md#api)을 따른다. 화면은 다음과 같다.

#### 업무 규칙·연결

##### 닫기·개인 정보 경계

키보드 Enter/Space로 열고 패널로 포커스를 이동한다. Escape·닫기 버튼은 종 버튼으로 포커스를 돌린다. 바깥 클릭·포커스 이탈·페이지 스크롤·창 크기 변경으로도 닫는다. 패널 내부 스크롤은 닫지 않는다. 화면 이동은 패널을 다시 만들고, 회원 ID 변경은 헤더 알림 전체를 다시 만들어 필터·페이지·열린 상태를 버린다. 로그아웃·조회 401에 따른 공통 세션 재확인에서도 개인 패널이 사라진다.

##### 다음 연결

##### 읽지 않은 알림만 보기 (U4)

<a id="b-647-0"></a>
[화면 개편 결정 §16.1](change-history.md#b-993-0)의 U4 계약(main 반영 #131). 기존 목록·읽지 않은 수·읽음·모두 읽음 API는 그대로 두고 목록에 `unreadOnly`만 추가했다(정은찬, 브랜치 `feature/notification-unread-filter`). DB·Flyway 변경 없음.

- `GET /api/notifications?unreadOnly=true`: `read_at`이 없는 본인 알림만 기존과 같은 순서(생성 시각·ID 내림차순)로 반환한다. `totalElements`·`totalPages`도 같은 조건으로 센다(읽지 않은 수 API와 같은 값). 생략·`false`는 기존 전체 목록이고, 불리언이 아닌 값은 400 `VALIDATION_FAILED`. 읽음 처리한 항목은 다음 조회부터 빠진다.
- `/notifications`: "전체"/"읽지 않음" 버튼(`aria-pressed`)으로 보기를 바꾸고 URL `?filter=unread`에 둔다. 바꾸면 첫 페이지로 간다. 알 수 없는 `filter` 값은 전체로 본다. 읽지 않음 보기의 빈 결과는 "읽지 않은 알림이 없습니다."로 구분한다. "모두 읽음" 뒤에는 기존처럼 알림 조회 전체를 다시 불러온다.
- 프런트 queryKey `['notifications', 'list', 'all'|'unread', page]`. 계정 전환 시 기존과 같이 `accountCache.ts`가 정리한다.
- 주문 링크는 읽음 응답을 기다리지 않고 즉시 이동한다. 이동을 mutation 완료 콜백에 두지 않아 행 제거·페이지 이탈·응답 순서가 나중에 경로를 바꾸지 않는다. Ctrl/Cmd/Shift 클릭과 가운데 버튼은 기본 브라우저 동작을 유지하며 읽음 요청을 보낸다. 우클릭 메뉴의 새 탭 열기는 읽음 요청을 보장하지 않는다.
- 개별/모두 읽음은 자동 재시도하지 않고, 같은 계정이면 성공·실패 모두 알림 전체 키를 무효화해 목록·건수·페이지 수·배지를 다시 확인한다. SPA 내 이동으로 행이 언마운트돼도 옵션 onSettled는 실행된다. 새로고침·탭 닫기까지 완료를 보장하지 않는다.
- 실패 정책: 이동은 유지하고 읽음 상태는 후속 조회로 확인한다. 서버 반영 뒤 응답만 유실될 수 있으므로 “실패하면 읽지 않음으로 남음”을 보장하지 않는다. 링크 없는 항목은 실패 안내를 보여 주지만 재조회로 행이 사라지면 안내도 사라질 수 있다.
- 계정 경계: 클릭 시 세대·회원 ID를 캡처해 오프라인 보류·CSRF 대기 후 전송 직전과 완료 후 무효화 전에 검사한다(개별/모두 읽음 공통). 이전 요청의 늦은 401·CSRF 오류가 새 CSRF 캐시를 지우지 않도록 요청 세대도 확인한다. 이미 전송된 명령의 서버 처리를 취소하는 기능은 아니며 다른 탭/기기의 계정 전환을 즉시 감지하지는 않는다.
- offset 한계: 같은 화면의 읽음 처리, 다른 탭/기기의 변경, 신규 알림 유입으로 페이지 경계가 이동해 순회 중 항목이 겹치거나 건너뛰어 보일 수 있다. 전체 무효화는 재조회 시점의 서버 상태를 반영하며 페이지 간 스냅샷을 보장하지 않는다. 빈 후속 페이지에서는 첫 페이지 복귀 버튼을 사용한다.
- 범위 밖: 헤더 알림 패널 화면(§3의 위치·키보드·바깥 클릭 처리)은 이 변경에 넣지 않았다.

##### 범위

V3 주문의 기한 통지(E8)·보관/지급/반환 알림 유형·자동 확정 사전 안내(E9)는 [V3 알림](features.md#communication-timer)에 정리했다. 아래는 최초 구현 기록이다.

##### 처리 흐름

1. 주문 명령 서비스가 주문 이력을 저장한 직후 `OrderEventPublisher.publish`를 호출한다. 이벤트는 **같은 트랜잭션**(`MANDATORY`)에 `PENDING`으로 저장된다. 이벤트 저장이 실패하면 주문 변경도 롤백된다.
2. `OutboxDispatchScheduler`가 2초 간격(`lancerpick.notifications.dispatch-delay-ms`)으로 기한이 된 이벤트를 최대 100건 고른다.
3. `OutboxDispatcher`는 이벤트마다 **별도 트랜잭션**에서 행을 잠그고 상태를 다시 확인한 뒤 알림을 만들고 `COMPLETED`로 바꾼다. 여러 처리기가 같은 이벤트를 골라도 한 번만 완료된다.
4. 처리 실패는 다른 이벤트를 막지 않는다. 시도 수를 늘리고 30초부터 두 배씩(최대 1시간) 미룬다. 최초 실패를 포함해 5회 실패하면 `FAILED`로 멈춘다. 알림 실패는 이미 커밋된 거래를 되돌리지 않는다.
   - 다시 시도해도 성공할 수 없는 오류(지원하지 않는 집계·스키마 버전·유형, JSON·수신자 ID 형식 오류, 주문 당사자가 아니거나 집계 회원이 아닌 수신자, 없는 주문·회원)는 첫 실패에서 바로 `FAILED`로 멈춘다.
   - 실패 기록은 기한이 지난 `PENDING`에만 남긴다. 같은 이벤트를 고른 다른 처리기가 이미 다음 시도를 미뤘다면 횟수를 다시 올리지 않아, 동시 경합만으로 `FAILED`가 되지 않는다.
   - 실패 기록 자체가 실패해도(잠금 대기 초과 등) 예외를 이벤트 단위로 삼키고 로그만 남긴다. 이벤트는 `PENDING`으로 남아 다음 배치에서 다시 처리되며, 같은 배치의 다른 이벤트는 계속 처리된다.

`lease_owner`·`lease_until` 컬럼은 트랜잭션 밖 장시간 처리용으로 남겨 두었고, 현재 처리는 DB 안에서 끝나므로 사용하지 않는다. `FAILED` 이벤트의 재처리 명령은 관리자 고도화(김창재)에서 연결할 후속 범위다.

##### 수신자와 유형

- 행동한 당사자를 제외한 상대 당사자에게 알린다. 시스템 처리(actor 없음)는 양측에 알린다(V2 타이머 대비, 현재 LEGACY 경로에는 없음).
- 유형은 `ORDER_` + 주문 이력 행동이다. 취소 요청 승인은 같은 트랜잭션의 `ORDER_CANCELLED`와 수신자가 같아 별도 알림을 만들지 않는다.
- 알림 UNIQUE `(recipient_id, event_id, type)`로 같은 이벤트의 중복 알림을 막는다.
- 처리기는 페이로드의 수신자가 현재 주문의 구매자·판매자인지 다시 확인하고, 아니면 이벤트를 실패 처리한다.
- 읽음 처리는 `read_at IS NULL` 조건부 UPDATE라 동시 요청에도 먼저 기록한 시각을 덮어쓰지 않는다.
- 페이로드에는 주문 ID·이력 ID·행동·수신자 ID만 넣는다. 사유·요구사항·결제 정보는 넣지 않는다. 처리 실패 로그에는 이벤트 ID와 예외 유형만 남긴다.

##### 회원 알림: 비밀번호 변경 권고(U6)

주문과 무관한 회원 알림이다. 정책(회차·기준 재확인)은 [비밀번호 변경 권고 발송](#password-recommendation)을 따른다.

- 발행: `AuthController.login`이 로그인을 마친 뒤 `PasswordChangeRecommendations.issueAfterLogin(memberId)`를 부른다. 별도 `REQUIRES_NEW`·READ_COMMITTED 트랜잭션이고 실패는 회원 ID·예외 유형만 로그로 남긴다(로그인 응답 200 유지).
- 대상 판정: 잠금 없는 `PasswordRecommendationQuery`로 첫 권고 시각 전이면 끝낸다. 이번 회차 이벤트가 이미 있으면 잠그지 않고 끝낸다(비밀번호를 바꾸지 않은 회원의 매 로그인 잠금 방지). 그 밖에는 회원 배타 잠금 → 기준일 재조회 → 회차 계산 → `existsByEventId` 재확인 → 저장.
- 이벤트 ID: `UUID.nameUUIDFromBytes("PASSWORD_CHANGE_RECOMMENDED:<회원>:<기준일 µs>:<회차>")`. 구성 문자열을 바꾸면 이미 저장된 권고와 중복되므로 바꾸지 않는다. 디스패치 전 재로그인·동시 로그인도 회차당 1건이다.
- 아웃박스: `aggregate_type=MEMBER`, `aggregate_id`=회원, `schema_version=1`, 유형 `PASSWORD_CHANGE_RECOMMENDED`, 페이로드 `memberId`·`round`·`basisAt`·`recipientIds`(본인만).
- 디스패처: MEMBER 집계는 이 유형만 받는다(그 밖은 `UNSUPPORTED_EVENT`). 회원이 없으면 `AGGREGATE_NOT_FOUND`, 수신자가 집계 회원이 아니면 `INVALID_RECIPIENT`로 바로 `FAILED`. 알림은 `order_id` NULL이라 목록의 `orderId`·`productTitle`이 null이다. ORDER 집계의 처리 순서·오류 코드는 그대로다.
- 화면: 문구 "비밀번호를 바꾼 지 6개월이 지났습니다. 계정 보호를 위해 내 정보에서 비밀번호 변경을 권장합니다." 선택 시 `/mypage/profile`(내 정보, 비밀번호 변경 폼)로 이동한다. `notificationTarget`은 주문 ID가 있으면 주문 상세가 우선이고, 이동할 곳이 정해지지 않은 회원 알림은 링크 없이 읽음 버튼으로 남는다. 읽음 실패 안내의 링크 문구는 주문이 아니면 "읽음 처리 없이 바로 가기"다.
- DB·스케줄러 변경 없음. 다른 영역: auth `AuthController` 호출 한 줄, member `MemberChangePolicy` 회차 계산 메서드(기존 `sixMonthsAfter` 결과 동일).

##### 다른 영역 영향

- 주문 서비스 8개(생성·수락/거절·결제·납품·수정·확정·취소·취소 요청)에 이벤트 발행 호출을 추가했다. 기존 응답·오류 계약은 바꾸지 않았다.
- `features/auth/components/MemberNav.tsx`(김창재)에 알림 링크 한 줄, `app/router.tsx`에 `/notifications` 경로를 추가했다.
- `@EnableScheduling`을 처음 도입했다. 테스트 프로필과 DB 없는 테스트는 `lancerpick.notifications.dispatch-enabled=false`로 스케줄러를 끈다.

<a id="communication-timer"></a>
### 기한 통지·검수·자동 확정

V3는 최초/재납품 검수 무응답 48시간 후 판매자가 7일 자동 확정 타이머를 시작하며 만료 24시간 전 사전 안내를 보낸다. 수정/취소 응답·관리자 해제·보안 잠금이 기산점/회차/활성 상태에 영향을 준다. 구매자 보안 잠금 시 새 시작을 막고 기존 타이머를 중단하며 해제 후 자동 재개하지 않는다. 자동 전달 재시도 로컬 후속과 기존 만료 재검사 방어를 구분한다.

#### 처리 순서·동시성·재전송

구현 위치: 명령·트랜잭션·잠금은 `order/service/ReviewTimerService`, 상태 응답·기산점·시작 가능 판단(읽기만)은 `ReviewTimerViews`, 만료 스케줄은 `ReviewTimerExpiryScheduler`.

##### 구매자 보안 잠금 연결(U8)

<a id="b-242-0"></a>
[개편 정책](change-history.md#b-971-0) §11.3·§16 U8 결정과 [제재 기반](features.md#members-sanctions)(#134)의 `SecurityLockTimerStopper` 계약을 연결했다. 일반 이용 제한은 기존 타이머를 그대로 둔다. 판매자 보안 잠금도 영향이 없다. DB·Flyway 변경 없음.

##### 스케줄러·트랜잭션

- `ReviewTimerExpiryScheduler`가 `lancerpick.order.review-timer.expiry-delay-ms`(기본 30초) 간격으로 실행 시작 시각까지 만료된 타이머를 50건씩 (만료 시각, ID) 커서로 끝까지 순회한다. 계속 실패하는 타이머가 앞쪽에 쌓여도 같은 실행에서 뒤의 정상 타이머를 처리한다(Codex 리뷰 반영). `lancerpick.order.review-timer.expiry-enabled=false`로 끌 수 있으며 test 프로필은 끄고 `ReviewTimerService.expireDue`를 직접 호출한다.
- 타이머마다 별도 READ_COMMITTED 트랜잭션: 구매자 회원 공유 잠금(U8, 보안 잠금 재검사용) → 주문 배타 잠금 → 타이머 재확인(ACTIVE·만료) → 구매자 보안 잠금 재검사 → 판매자 지갑 배타 잠금. 한 건의 실패(예: 아웃박스 저장 실패)는 그 주문 전체를 롤백하고 타이머를 ACTIVE로 남겨 다음 실행에서 다시 시도한다. 실패는 타이머 ID와 오류 코드만 로그에 남긴다.
- 여러 서버가 동시에 실행해도 주문 잠금 후 재확인하므로 한 번만 지급한다.

#### 업무 규칙·연결

##### 흐름

1. 납품(또는 재납품)으로 `AWAITING_CONFIRMATION`이 되면 **검수 기산점**이 생긴다. 기산점 = 마지막 납품 시각과, 거절·철회된 취소 요청의 처리 시각 중 늦은 값.
2. 기산점 + 48시간 이후 **판매자가** 7일 타이머를 시작한다. 시스템이 자동으로 시작하지 않는다. 타이머는 `AUTO_CONFIRM`, 대상 납품(`delivery_id`)과 같은 납품의 회차(`cycle_no`)를 기록한다.
3. 시작 + 168시간이 지나면 만료 스케줄러가 시스템 구매 확정을 한다. 수동 확정과 같은 10% 수수료·판매자 구매용 적립금 지급·정산·보관금 해제를 같은 트랜잭션에서 처리하고, 종료 기록은 `reason=AUTO_CONFIRMED`·`actor_type=SYSTEM`·`source_timer_id`, 주문 이력은 `COMPLETED`(SYSTEM, `actor_id` NULL, 원천 키 `AUTO_CONFIRM:<timerId>`), 타이머는 `EXPIRED`/`TIMEOUT`이다.

| 구매자 응답(만료 전) | 타이머 | 다음 기산점 |
| --- | --- | --- |
| 수정 요청 | `CANCELLED`/`BUYER_RESPONSE` | 재납품 시각 |
| 취소 요청 | `CANCELLED`/`BUYER_RESPONSE`. 대기 중에는 시작 불가 | 거절·철회 시각(같은 납품의 다음 회차). 승인되면 주문 취소 |
| 구매 확정 | `CANCELLED`/`ORDER_CLOSED` 후 정상 확정 | 없음 |

- **만료 우선**: 활성 타이머의 만료 시각 이후에 들어온 구매자 응답·관리자 해제는 타이머를 취소하지 않고 409 `AUTO_CONFIRM_DUE`로 응답한다. 스케줄러가 곧 자동 확정한다. 판단은 모두 주문 배타 잠금 아래 서버 시각으로 한다.
- **관리자 해제**: `ReviewTimerService.adminStop(adminId, orderId, reason)`. 2026-11-09 김창재가 K5 API·화면을 연결했다(`feature/admin-cancellation-inquiries`): `GET /api/admin/review-timers`(진행 중 타이머, 만료 이른 순)·`POST /api/admin/orders/{orderId}/review-timer/stop`(reason, [API 9-2절](architecture.md#b-203-1)), 화면 `/admin/review-timers`. ADMIN 역할·사유 1~1000자 확인, `CANCELLED`/`ADMIN_STOP`, 이력 `TIMER_ADMIN_STOPPED`. 해제된 기산점으로는 다시 시작할 수 없고 새 납품이나 취소 요청 처리 이후 다시 시작할 수 있다(2026-11-06 조장 결정, [정책](features.md#finance-funds) §2-5에 반영).
- 납기 초과·미납품은 자동 확정 사유가 아니다(검수 대기에서만 시작).

##### E8 납기·재납품 기한 통지

| 사건 | 이력(같은 트랜잭션) | 원천 키 | 알림 유형(양측) |
| --- | --- | --- | --- |
| V3 전액 결제 | `DEADLINE_NOTIFIED`, IN_PROGRESS 유지 | `DEADLINE:PAYMENT:<결제 ID>` | `ORDER_DELIVERY_DUE_NOTIFIED` |
| V3 수정 요청 | `DEADLINE_NOTIFIED`, REVISION_IN_PROGRESS 유지 | `DEADLINE:REVISION:<수정 요청 ID>` | `ORDER_REDELIVERY_DUE_NOTIFIED` |

- 실행자는 SYSTEM(`actor_id` NULL)이라 구매자·판매자 모두에게 알린다. 이력 시각은 기준 시각(결제·수정 요청 시각)과 같다.
- `details`: `kind`(DELIVERY_DUE/REDELIVERY_DUE), `basisAt`, `dueAt`(UTC ISO 8601), `deliverySequence`(다음 납품 회차 1/2), `contractVersion`(서명된 현재 계약 버전). 기한 값은 `orders.due_at`·`revision_requests.due_at`과 같다.
- **중복 없음**: 원천 키가 결제·수정 요청 ID로 정해지고 `uk_order_history_source` UNIQUE라 같은 사건으로 두 번 기록될 수 없다(재전송은 기존 기록 확인 경로라 통지를 만들지 않는다). 통지 저장이 실패하면 결제·수정 요청도 롤백된다.
- 최초 계약 PDF는 갱신하지 않는다. 거래 이력 화면은 "납기 통지 · 시스템"/"재납품 기한 통지 · 시스템"으로 표시한다.
- 구현: `order/service/DeadlineNotices`(호출: `FullFundingService.pay`, `OrderRevisionService.request`), `OrderHistory.deadlineNotified`.

##### E9 V3 금액 알림 문구

알림 유형은 이벤트 저장 시점에 정해져 이후 정책 변경에 바뀌지 않는다. V3 주문만 아래 유형으로 바꾸고 나머지는 기존 `ORDER_` + 행동을 유지한다.

| 이력 | V3 알림 유형 | 수신자 |
| --- | --- | --- |
| PAID | `ORDER_FUNDS_HELD` (대금 보관) | 판매자 |
| COMPLETED(구매자) | `ORDER_FUNDS_RELEASED` (수수료 제외 적립금 지급) | 판매자 |
| COMPLETED(SYSTEM) | `ORDER_AUTO_CONFIRMED` (자동 확정·적립금 지급, 문구 보강) | 양측 |
| CANCELLED(결제 후 상태에서) | `ORDER_FUNDS_REFUNDED` (보관금 전액 반환) | 행동하지 않은 당사자(관리자 승인 취소는 양측) |
| CANCELLATION_REJECTED(관리자 실행) | `ORDER_CANCELLATION_ADMIN_REJECTED` (운영 문의 검토 거절, 2026-11-06 K4) | 양측 |

결제 전 V3 취소는 기존 `ORDER_CANCELLED`다. 잔금 미지급 종료 알림은 만들지 않는다.

##### E9 자동 구매 확정 사전 안내

- 만료 24시간 전부터 만료 전까지(`OrderResponseTimer.NOTICE_BEFORE_HOURS`) 활성 타이머의 **구매자**에게 `ORDER_AUTO_CONFIRM_SOON`을 한 번 보낸다. 판매자는 타이머를 시작한 당사자라 보내지 않는다.
- 기존 만료 스케줄러(`ReviewTimerExpiryScheduler`, 기본 30초)가 만료 처리 뒤 `ReviewTimerService.noticeDue(50)`을 호출한다. 후보는 (만료 시각, ID) 커서로 끝까지 순회하고, 타이머마다 별도 트랜잭션에서 구매자 회원 공유 잠금 → 주문 배타 잠금(만료·보안 중단과 같은 순서) → 타이머가 활성·구간 안·검수 대기인지, 시작 이후 구매자 보안 잠금이 없는지 재확인 → 이벤트 저장 순서다. 판정 직후 커밋되는 보안 잠금과의 경합만 없앤다. 안내 트랜잭션이 먼저 직렬화되면 안내 저장 뒤 잠금이 적용될 수 있다.
- 상태 변화가 아니므로 주문 이력은 남기지 않는다. 이벤트 ID를 타이머 ID로 정한 UUID(`AUTO_CONFIRM_NOTICE:<timerId>` 이름 기반)로 저장해 `event_id` UNIQUE가 여러 서버·반복 실행의 중복을 막는다. 이미 안내한 타이머는 잠금 없이 먼저 건너뛴다.
- 안내 후 구매자가 수정·취소 요청·확정을 하면 타이머는 기존대로 끝나고 안내는 그대로 남는다. 만료 시각이 이미 지난 타이머에는 안내 대신 자동 확정 결과(`ORDER_AUTO_CONFIRMED`)만 간다.
- 결과 알림(`ORDER_AUTO_CONFIRMED`)·타이머 시작(`ORDER_TIMER_STARTED`)·관리자 해제(`ORDER_TIMER_ADMIN_STOPPED`)는 E7 그대로다.

#### API·입력·오류

##### API

| API | 권한·처리 |
| --- | --- |
| `GET /api/orders/{id}/review-timer` | 구매자·판매자. `Cache-Control: no-store`. 타인·없는 주문·V3가 아닌 주문은 404 |
| `POST /api/orders/{id}/review-timer` | 판매자, 본문 없음·CSRF 필요. 이미 활성 타이머가 있으면 새로 만들지 않고 현재 상태를 돌려준다 |

응답 필드: `status`, `basisAt`, `eligibleAt`, `startedAt`, `expiresAt`(UTC ISO 8601, 해당 없으면 null), `canStart`(판매자이고 `STARTABLE`이며 구매자가 보안 잠금 중이 아닐 때만 true).

| status | 의미 |
| --- | --- |
| `NOT_IN_REVIEW` | 검수 대기가 아님(화면에서 숨김) |
| `CANCELLATION_PENDING` | 취소 요청 대기 |
| `WAITING` | 기산점 + 48시간 전 |
| `STARTABLE` | 판매자가 시작 가능 |
| `ADMIN_STOPPED` | 현재 기산점의 타이머를 관리자가 해제 |
| `ACTIVE` | 진행 중 |
| `AUTO_CONFIRMED` | 타이머 만료로 구매 확정됨 |

| HTTP·코드 | 조건 |
| --- | --- |
| 403 `FORBIDDEN` | 판매자가 아닌 시작 요청, 관리자가 아닌 해제 |
| 409 `TIMER_NOT_ELIGIBLE` | 기산점 + 48시간 전 |
| 409 `CANCELLATION_PENDING` | 취소 요청 대기 중 시작 |
| 409 `ORDER_STATE_CONFLICT` | 검수 대기가 아닌 주문 |
| 409 `TIMER_ADMIN_STOPPED` | 관리자가 해제한(보안 잠금 중단 포함) 같은 기산점으로 재시작 |
| 409 `BUYER_SECURITY_LOCKED` | 구매자 보안 잠금 중 시작 |
| 403 `MEMBER_LOCKED` | 보안 잠금 중인 판매자의 시작(인증 필터를 통과한 진행 중 요청 포함) |
| 409 `TIMER_NOT_ACTIVE` | 활성 타이머 없이 관리자 해제 |
| 409 `AUTO_CONFIRM_DUE` | 만료 시각이 지난 타이머가 있는 주문의 구매 확정·수정 요청·취소 요청·관리자 해제 |
| 409 `ORDER_POLICY_NOT_READY` | V3가 아닌 주문의 시작 요청 |

기존 금액 처리의 409 `TIMER_INTEGRATION_NOT_READY`는 없앴다. 결제·승인 환불 시점에 활성 타이머가 있으면 앞 단계에서 끝났어야 하므로 500 `DATA_INTEGRITY_ERROR`다.

#### 화면·캐시·계정 경계

##### 알림·화면

- 이력·아웃박스: `TIMER_STARTED`(구매자에게 `ORDER_TIMER_STARTED`), `TIMER_ADMIN_STOPPED`(양측), 자동 확정 `COMPLETED`(양측에 `ORDER_AUTO_CONFIRMED`). 구매자 응답에 따른 `TIMER_CANCELLED`는 이력만 남기고 알림을 보내지 않는다(함께 기록되는 수정 요청·취소 요청·구매 확정 알림이 대신함).
- 만료 24시간 전 구매자 사전 안내(`ORDER_AUTO_CONFIRM_SOON`)와 V3 금액 알림 문구는 E9에서 추가했다([V3 알림](features.md#communication-timer)).
- 주문 상세(V3)에 `ReviewTimerPanel`: 상태 안내, 자동 확정 예정 시각, 서버 `canStart`일 때만 판매자 시작 버튼. 시작 요청은 자동 재전송하지 않고 성공·실패 모두 `['wallet']` 조회를 다시 한다. 조회 키 `['wallet', 'order-review-timer', id]`는 기존 계정 전환 정리 대상이다. 검수 대기 중 상태(`WAITING`·`STARTABLE`·`ACTIVE`·`CANCELLATION_PENDING`·`ADMIN_STOPPED`)는 시간 경과나 상대방 행동(판매자의 타이머 시작, 취소 요청 처리, 재납품)으로 바뀔 수 있어 1분마다 다시 조회하고, `NOT_IN_REVIEW`·`AUTO_CONFIRMED`와 백그라운드 탭·조회 실패 시에는 멈춘다, 상태 변화(자동 확정 완료 등)를 발견하면 `['wallet']`을 무효화해 주문 상세·대금도 다시 확인한다(Codex 리뷰 반영).
- 거래 이력에서 실행 회원이 없는 `COMPLETED`는 "자동 구매 확정 · 시스템"으로 표시한다.

##### 화면

`features/notifications/messages.ts`에 새 유형 6개 문구를 추가하고 `ORDER_AUTO_CONFIRMED` 문구에 적립금 지급을 덧붙였다. `features/orders/history.ts`가 `DEADLINE_NOTIFIED`를 대상 상태로 구분해 표시한다. 새 API·queryKey는 없다.

<a id="domain-screens"></a>
## 화면과 공통 정책

<a id="screens-navigation"></a>
### 화면 흐름·마이페이지·조회 조건

홈·전체 상품 탐색, 헤더 알림/대화, 일반/판매자 마이페이지, 구매/판매 내역·지갑·수익·관리자 화면을 연결한다. 화면은 캐시·URL 조건·중복 클릭/불명확 응답·401/403 계정 경계를 다루고 최종 허용/금액은 서버가 판단한다. U1~U5의 구현과 후속 정책 전체 완료를 혼동하지 않는다.

#### 화면·캐시·계정 경계

##### 랜서픽 MVP 화면 흐름 v1.0 — 검토 반영본

역할 배정 갱신 2026-10-29: [확정 담당·지원 원칙·업무 비중](team-roles.md#b-1131-0)을 따른다. 상세 일정·검토자·배포 담당은 별도 확정한다.

##### 화면 묶음

| 구분 | 화면 |
| --- | --- |
| 탐색 | 상품 목록·상세·판매자 소개 |
| 인증 | 가입·로그인 |
| 내 계정 | 정보·판매자 설정·적립금·판매 수익 |
| 상품 관리 | 내 상품·등록·수정·판매 중지 |
| 거래 | 주문 작성·구매/판매 목록·거래 상세 |
| 운영 | 관리자 상품 조회·숨김·복구 |

계약 조건 확인·결제·납품·취소·후기는 거래 상세 안에서 처리한다.

##### 예외 경로

로그인 후 원래 상품으로 복귀하며 자동 주문하지 않는다.

판매자 설정 전 상품 등록 시 설정 화면으로 안내한다.

주문 제출 시 조건이 변경됐다면 재확인한다.

숨김·판매 중지 상품은 신규 주문만 차단한다.

잔액 부족 시 차감·상태 변경 없이 안내한다.

반복 요청은 API별로 기존 결과를 반환하거나 충돌을 안내한다. 금액과 기록은 중복 생성하지 않으며, 화면은 최신 거래 상태를 확인한다.

화면 조회 후 상태가 바뀌면 최신 상태를 안내한다.

잘못된 결과물 URL은 제출 단계에서 차단한다.

구매자 무응답은 검수 대기로 남는다.

타인 거래 접근은 내용을 노출하지 않고 거절한다.

재납품 후 결과에 합의하지 못한 경우 취소 요청은 가능하지만 강제 분쟁 해결은 제공하지 않는다.

##### 예외 경로 / React 공통 처리

주문 상세 API의 allowedActions를 기준으로 버튼을 표시한다. 서버는 실제 요청 때 권한과 상태를 다시 검증한다.

결제·환불·완료 성공 후 관련 주문·잔액·정산 데이터를 다시 조회한다. 요청 중에는 중복 클릭을 제한한다.

인증 만료 시 로그인으로 안내하며 변경 요청을 자동 재실행하지 않는다. 통신 오류로 결과가 불명확하면 먼저 최신 상태를 조회한다.

로딩·빈 결과·접근 불가·오류를 공통 처리하고 직접 주소 접속·새로고침을 지원한다.

로그인·로그아웃·계정 전환 시 이전 사용자 관련 캐시를 정리한다.

상품의 판매 중지·관리자 숨김 상태를 구분한다. 판매 재개와 관리자 복구가 서로의 상태를 변경하지 않는다.

최초 납기와 수정 납기를 구분한다. 현재 적용 기한은 조회 시 결정하며 새 DB 컬럼을 추가하지 않는다. 완료·취소·거절 주문에는 진행 중 지연 표시를 하지 않는다.

금액·상태 정책은 MVP 명세서, 공통 요청·인증·기술 버전은 공통 개발 규칙을 참조한다.

취소 요청 폼은 최초 제출 시 요청 키를 만들고 결과 불명확·통신 재시도 때 같은 키를 사용한다. 기존 요청이 해결된 경우 그 결과를 표시하고 자동으로 새 요청을 만들지 않는다.

취소 요청 응답의 기존 요청 ID·상태와 현재 주문을 구분해 표시한다. 사용자가 새 취소 요청을 명시적으로 작성하면 새 키를 발급한다.

결제 API가 PAYMENT_ALREADY_REFUNDED를 반환하면 결제 성공 안내를 하지 않고 주문·환불·잔액을 다시 조회한다. HTTP 200이어도 주문의 현재 상태를 확인하며 무조건 진행 중 화면으로 이동하지 않는다.

납품 폼은 최초 제출 1회차·수정 제출 2회차를 명시하며 재전송에서 바꾸지 않는다. 서버 오류 DELIVERY_ALREADY_SUBMITTED 또는 DELIVERY_SEQUENCE_CONFLICT를 받으면 최신 납품·주문을 조회한다. 기존 요청을 새 회차로 자동 변환하거나 자동 제출하지 않는다.

지연 표시는 작업 중 기한 초과와 납품 제출 시각의 지각 여부를 구분한다. 확인 대기·종료 상태를 현재 작업 지연으로 표시하지 않는다. 계산값을 위한 DB 컬럼은 추가하지 않으며 상태별 계산·경계 시각 기준은 [API 명세 5절](architecture.md#b-195-1)을 따른다.

#### 업무 규칙·연결

##### 정상 거래

상품 탐색

→ 로그인

→ 주문 조건·요구사항 입력

→ 판매자 수락

→ 구매자 조건 확인·테스트 결제

→ 판매자 납품

→ 구매자 구매 확정

→ 판매 수익 계산·후기

##### 수정 흐름

최초 납품

├─ 구매 확정 → 완료

├─ 수정 요청 1회 → 재납품

│                  ├─ 구매 확정 → 완료

│                  └─ 취소 요청 → 판매자 응답

└─ 취소 요청 → 판매자 응답

재납품 후에는 수정 요청 버튼을 다시 제공하지 않는다. 서버도 추가 요청을 차단한다.

##### 취소 흐름

결제 전

→ 구매자는 취소, 판매자는 수락 전 거절·수락 후 취소

→ 환불 없이 종료

결제 후 구매자

→ 취소 요청

→ 판매자 승인: 전액 환불·취소

→ 판매자 거절 또는 구매자 철회: 기존 거래 계속

결제 후 판매자

→ 구매 확정 전 직접 취소

→ 전액 환불·취소

취소 요청 대기 중에는 납품·수정 요청·구매 확정을 차단한다. 판매자의 승인·거절·직접 취소와 구매자의 철회는 허용한다. 요청 자체와 환불 완료를 구분해 표시한다.

##### 거래 상세 배치

현재 상태·다음 행동·납기.

상품·상대방·가격.

수락·구매자 조건 확인 기록.

납품·수정 이력.

결제·환불·거래 이력.

후기.

##### 시연 준비

개발·시연용으로 다음 계정과 주문 데이터를 준비한다.

구매자·판매자·관리자 계정

결제 대기·진행 중·검수 대기·완료 주문

수정 요청·환불 완료 사례

권한 거절·중복 요청 검증 사례

시연 데이터 초기화는 개발·시연 환경에 한정한다. 일반 사용자가 잔액·주문 상태를 임의 변경하는 기능은 제공하지 않는다.

#### API·입력·오류

##### 예외 경로 / 입력·상품 수정·관리자 목록 연결

문자열의 필수값·코드 포인트 길이·공백·선택값 정규화는 [API 명세 10절](change-history.md#b-205-0)을 따른다. 상품 수정은 바뀐 필드와 expectedVersion만 제출하고, 생략은 유지·null과 빈 필수값은 오류로 처리한다. 상품 버전 충돌 시 최신 상품과 사용자 입력을 비교하고 자동 덮어쓰지 않는다. 관리자 상품 목록은 숨김·판매 상태 필터를 생략하면 전체 상태를 조회하며 API 9절의 목록 필드·페이지 규칙을 따른다.

<a id="screens-versions"></a>
### MVP·V2·V3 정책 경계

MVP의 무응답 대기·판매 수익 분리는 LEGACY_V1에 적용한다. V3의 전액 보관·자동 확정·판매자 구매용 적립금 지급과 구분한다. V2 과거 선금/잔금·보상 금액·종료 정책은 이력/기존 계약에만 남긴다. 계약 주문 최소 금액 1만원과 일반 MVP 가격 하한 1원도 정책별로 구분한다.

<a id="product-minimum"></a>
## 판매 중 상품과 신규 주문 최소 금액 — main #153

정은찬, `fix/product-min-order-price`(main `69a6d94` 기준). CONTRACT_V3 생성을 켜면 10,000원 미만 주문이 거부되는데(`CONTRACT_MINIMUM_AMOUNT_NOT_MET`), 상품은 1원부터 판매 중으로 등록돼 "판매 중이지만 누구도 주문할 수 없는 상품"이 생겼다. 원인은 판매 가능 가격(상품)과 주문 가능 가격(주문 정책)을 따로 정한 것이라, 가격이 들어오는 상품 쪽에서 같은 기준을 쓰도록 맞췄다.

- 기준: `NewOrderPolicy.minimumAmount(current())`. 생성 스위치를 따라 바뀌므로(V3면 10,000원, LEGACY_V1이면 없음) DB CHECK(1~1,000,000원)는 그대로 두고 `ProductManagementService`에서 검사한다.
- 등록, 판매 중(ON_SALE) 상품 수정, 판매 중으로 재개할 때 가격이 하한 미만이면 400 `PRODUCT_PRICE_BELOW_ORDER_MINIMUM`(메시지에 하한 금액). 판매 중지 상품은 가격을 올리기 전에도 수정할 수 있다. 화면은 서버 메시지를 그대로 보여 주므로 프런트 변경은 없다.
- 이미 있는 저가 상품: 가격·판매 상태를 바꾸지 않는다. 공개 목록·검색(판매자별 목록 포함)에서 빠지고, 상세는 기존처럼 최소 금액 안내로 주문 버튼을 대신한다. 판매자가 수정하려면 가격을 올리거나 판매를 중지해야 한다.
- 관리자 상품 목록·내 상품 목록은 그대로 보인다(판매자가 고칠 수 있게).
- 자동 테스트: `ProductOrderMinimumPriceIntegrationTests` 4건(등록 거부·LEGACY 허용, 판매 중 저가 상품 수정 거부 후 가격 수정, 중지 상품 수정 허용·재개 거부, 공개 목록 제외·상세 안내). 실서버·브라우저 확인은 하지 않았다.

### 관리자 운영 조회 연결

현재 /admin/dashboard와 /admin/outbox-events 화면 및 관리자 GET API를 제공한다. 문의/타이머/알림/UNKNOWN 충전 건수를 보고, FAILED·RETRYING 알림을 조회한다. 재처리 실행·감사 이력과 관리자 회원 제재 HTTP/화면은 포함하지 않는다. 자세한 필드·필터·권한은 [API 계약](architecture.md#admin-observability)을 따른다.
