# 랜서픽 MVP API 명세서

2026-11-09 로컬 U6: Member 응답에 `displayNameChangeAvailableAt`(첫 변경 null, 이후 UTC 오프셋 시각)을 추가했다. PATCH `/api/me`는 다른 이름으로 바꾸려는 요청이 마지막 변경 후 한국 달력 6개월 전이면 409 `DISPLAY_NAME_CHANGE_NOT_ALLOWED`다. 같은 이름 저장은 기준일을 바꾸지 않는다. 비밀번호 변경/권고 시각은 회원 응답에 노출하지 않는다. [정책·원자성·연결 계약](member-change-policy.md). V11 포함·main 반영 전이다.

2026-11-09 로컬 U5 후속: `GET /api/me/order-chats?q=&page=0&size=20` 본인 주문 대화 목록을 추가했다. principal 기준·no-store·페이지 크기 1~100·검색어 100 코드 포인트 이하. 숫자 검색은 주문번호 정확 일치, 나머지는 보관 상품명/현재 상대방 이름 부분 일치이며 최근 메시지(없으면 주문 생성)순이다. 종료/메시지 없는 주문도 포함한다. [필드·오류·검색·계정 경계](order-chat-workspace.md#대화-목록-api). 기존 메시지 GET/POST·소켓 계약과 DB는 유지한다. 로컬 구현이며 push·PR·병합 전이다.

2026-11-09 로컬 R2: [결제·보관금 내부 enum](payment-status-enums.md)을 적용해도 주문 상세·V3 대금·관리자 금융 JSON의 상태 필드는 기존 문자열을 유지한다. PAID/REFUNDED, HELD/RELEASED/REFUNDED 및 미결제 조회의 NOT_PAID, 관리자 입력 오류 계약을 변경하지 않는다. 신규 API·Flyway는 없다.

2026-11-09 로컬 B1/B2 후속: 보안 잠금 계정은 `POST /api/auth/login`의 올바른 비밀번호 확인 후 403 `MEMBER_LOCKED`를 반환한다. 잘못된 자격 증명의 기존 401은 유지한다. 일반 이용 제한은 로그인 가능하며, 보안 잠금 시 기존 HTTP 세션은 인증 버전 재검사에서 401이 된다. [제재 공통 기반](member-sanction-foundation.md)의 변경 명령은 내부 서비스로만 제공한다(409 `SANCTION_REQUEST_CONFLICT` 포함). 신규 관리자 HTTP 경로·요청/응답은 아직 없다.

채팅 후속 로컬: 당사자 GET/POST `/api/orders/{orderId}/chat/messages`의 UUID 멱등·순번 커서를 유지하고 `/ws` WebSocket/STOMP를 연결했다. `/app/orders/{orderId}/chat/messages` SEND, `/user/queue/chat`·`/user/queue/chat-errors` SUBSCRIBE만 허용한다. 종료 후 조회 전용. [HTTP 요청·응답](order-chat-storage.md), [소켓 계약·CSRF·세션 폐기·복구](order-chat-realtime.md). 원격 병합·배포 활성화 여부와 구분한다.

2026-11-06 main 반영(#99, `eb6b40e`, `feature/v3-auto-confirm-timer`): V3 당사자 `GET /api/orders/{id}/review-timer`(no-store)와 판매자 `POST /api/orders/{id}/review-timer`(본문 없음, 7일 자동 구매 확정 타이머 시작)를 추가했다. 만료 후 구매자 응답은 409 `AUTO_CONFIRM_DUE`, 금액 명령의 `TIMER_INTEGRATION_NOT_READY`는 제거. [상태·오류·처리](auto-confirm-timer.md). 2026-11-09(U8, `feature/security-lock-timer-stop`): 구매자 보안 잠금 중 시작은 409 `BUYER_SECURITY_LOCKED`, 조회의 `canStart`는 false. 응답 필드·상태 값은 그대로다.

2026-11-05 후속 로컬 `feature/full-funding-payments`: V3는 기존 payment/confirm/취소 요청 승인 API로 결제 금액 보관·수동 확정 지급·전액 반환을 처리한다. 당사자 `GET /api/orders/{id}/funds`를 추가했다. [요청·응답·오류·트랜잭션](full-funding-payment.md). 기존 V1 동작·V2 금융 차단 유지, 자동 확정/관리자 승인 후속, 생성 플래그 기본 false. 아래 과거 정책 경계와 구분한다.

2026-11-05 최신 변경: CONTRACT_V3 결제 금액 보관 정책/V7/새 계약 본문과 계약 PDF·수락/초안/서명 HTTP API·주문 상세 계약 화면이 main `2fec099`에 반영됐다. 기존 V1/V2/서명 문서는 유지한다. 상세 정책·DB·미구현 금액 명령·활성화 조건은 [결제 금액 보관 설계](full-funding-policy.md)를 따른다.

## 고도화 기반 후속 계약 — 2026-11-05

V3 납품·수정 요청(#93, 본 금융 브랜치에 통합): 경로·입력은 그대로이며 CONTRACT_V3 주문도 POST `/orders/{id}/deliveries`·`/orders/{id}/revision-request`를 쓸 수 있다. 판매자 작업 중·수정 중 `DELIVER`, 구매자 검수 대기(수정 미사용) `REQUEST_REVISION`을 제공하고 보관금이 HELD가 아니면 500 `DATA_INTEGRITY_ERROR`다. 본 브랜치에서는 V3 `PAY`·`CONFIRM`·취소 요청/해결도 추가 연결했다([금융 API](full-funding-payment.md)). V2 금융 명령 차단은 유지한다.

수락 경로 단일화(#92): 계약 전용 POST `/api/orders/{orderId}/contract/accept`를 삭제했다. 계약 주문도 일반 주문과 같은 POST `/api/orders/{id}/accept`(주문 상세 응답, `allowedActions`의 `ACCEPT`)로 수락하며 REQUESTED → AWAITING_CONTRACT가 된다.

후속 3차(main `2fec099` 반영): PDF 흐름용 POST `/api/orders/{orderId}/contract/accept`(위 단일화로 삭제)·`/draft`·`/sign` 추가. V2 전용 수락→계약 대기→초안/PDF→양측 서명→선금 대기이며 기존 전액 결제는 차단 상태다. 요청·권한·반복 처리·트랜잭션은 [PDF 연결 3차](contract-pdf-delivery.md)를 따른다. 생성 플래그 기본 false는 유지한다.

PDF 후속(`feature/contract-pdf-delivery`, main `2fec099` 반영): 당사자 GET `/api/orders/{orderId}/contract`·GET `/api/orders/{orderId}/contract/file`(선택 `version`), 판매자 POST `/api/orders/{orderId}/contract/generate`(`{"version":1}`, CSRF) 추가. 성공/진행 중 200/202, 버전·상태 충돌 409, 생성/파일 검사 실패 503. 요청·응답·오류·권한·비공개 보관의 상세 계약은 [PDF API 문서](contract-pdf-delivery.md)를 따른다. 기존 V2 주문 생성/초안/서명 HTTP 활성화와 구분한다.

기존 MVP API 경로·입력은 유지한다. main `1e10d78`에는 주문 목록 items와 상세/명령 응답의 `policyVersion: LEGACY_V1 | CONTRACT_V2` 및 아래 정책 경계가 반영됐다. 신규 주문 생성 API는 아직 LEGACY_V1만 생성한다(V2 생성 코드는 있으나 설정 `lancerpick.order.contract-v2-creation-enabled` 기본 false로 닫혀 있음, [주문 생성](order-creation.md) 8단계). 고도화 기능이 활성화됐다는 뜻이 아니다.

| 응답/검사 | 정책 |
| --- | --- |
| V2 허용 행동 | 선금 전 단계만 연결: `REQUESTED` 구매자 `CANCEL`·판매자 `ACCEPT`·`REJECT`, `AWAITING_CONTRACT`·`AWAITING_PAYMENT`(선금 대기) 양측 `CANCEL`. 그 밖의 상태는 빈 배열이며 PAY·CONFIRM 등 기존 버튼을 노출하지 않음 |
| 기존 전액 결제 API | V2에 409 CONTRACT_PAYMENT_NOT_READY |
| 기존 수락/거절 | V2도 처리. V2 수락은 `AWAITING_CONTRACT`로 전환하고 `ACCEPTED` 이력의 toStatus도 `AWAITING_CONTRACT` |
| 기존 직접 취소 `POST /api/orders/{id}/cancel` | V2는 선금 전(`paidAt` 없음)만 처리: 구매자 `REQUESTED`, 양측 `AWAITING_CONTRACT`·`AWAITING_PAYMENT`. 금액 이동 없이 `CANCELLED`. 선금 후 V2는 409 ORDER_POLICY_NOT_READY |
| 기존 취소 요청/처리·납품·수정·구매 확정 | V2에 409 ORDER_POLICY_NOT_READY. 해당 주문 당사자인지 확인한 뒤 적용 |
| 조회 | 본인 V2 목록/상세는 정책 버전과 함께 조회 가능 |

실제 V2 계약/분할 결제 경로는 별도 API 계약과 상태 전환 검증 후 추가한다. 내부 계약 서비스 prepare/sign을 현재 공개 HTTP API로 표현하지 않는다. [정책 경계 보완](advanced-policy-boundary.md)을 따른다.

구현 대조 2026-11-04: 이 문서는 API 계약이다. 가입·세션 인증·주문·거래·지갑·정산·상품 검색/상세·판매자 프로필에 더해 상품 등록·부분 수정·소유자 상세·판매 상태 관리와 관리자 화면이 현재 문서 대조 기준에 포함된다. 내 상품 목록·후기 API의 계약 기재는 구현 완료를 뜻하지 않는다. 구현·검증 범위는 [현황표](current-status.md)를 따른다.

역할 배정 갱신 2026-10-29: [확정 담당·지원 원칙·업무 비중](work-plan.md#확정-역할-배정)을 따른다. 상세 일정·검토자·배포 담당은 별도 확정한다.

2026-10-29 구조 검토 반영: [변경 기록](review-changes.md). 실제 구현·실행 검증과 구분한다.

개정 기록 2026-10-29 | 정책·역할·기술 정리, 취소 요청 식별, 환불 후 결제 재요청, 납품 회차·지연 표시 보완 | 구현·설치·페이지 시각 검증 미완료

버전 1.1 | 작성일 2026-10-29 | 계약 보완 2026-10-30

2026-10-30 사용자 요청으로 기존 물리 설계의 입력 기준을 채택하고 상품 부분 수정·관리자 목록 계약을 보완했다. 문서 기준의 결정이며 팀원 리뷰·DTO·화면 구현·실제 DB 검증 완료를 뜻하지 않는다.

MVP의 화면과 서버가 사용할 요청·응답·권한·중복 처리 기준이다. 구현 기준을 정리한 문서이며 실제 API 구현·테스트 완료를 의미하지 않는다. 업무 정책은 MVP 기획 명세서, 데이터 구조는 ERD, 개발 방식과 기술 버전은 공통 개발 규칙을 따른다.

## 1 공통 계약

기본 경로 /api. JSON·UTF-8, 세션 쿠키 인증, 변경 요청 CSRF 보호. ID는 JSON 문자열, 금액은 원 단위 정수, 시간은 시간대가 포함된 ISO 8601이다. 비밀번호·토큰·내부 오류를 응답·로그에 노출하지 않는다.

목록은 page=0, size=20, 최대 100. 최신 생성순과 ID 내림차순을 기본으로 하며 categories는 displayOrder·ID 오름차순이다. 목록 응답은 items, page, size, totalElements, totalPages를 가진다.

GET·수정·업무 명령 성공은 200, 리소스 생성은 201, 로그아웃은 204. 안전한 생성 재호출은 200과 기존 결과. 오류는 code, message, fieldErrors(배열), traceId를 반환한다. 채팅 소켓 오류도 같은 필드에 HTTP 상태와 같은 의미의 status를 더해 보낸다([공통 이벤트·오류 계약](event-contract.md)). 비공개 타인 자원은 404, 관리자 기능 권한 부족은 403으로 처리한다.

fieldErrors의 각 항목은 field(요청 필드 경로 문자열), code(검증 오류 코드 문자열), message(안내 문자열)를 가진다. 필드와 무관한 오류는 빈 배열이다. rejectedValue는 반환하지 않는다. 다음은 응답 형식 예시이며 문자열 길이 정책을 확정하는 예시는 아니다.

```json
{
  "code": "VALIDATION_FAILED",
  "message": "입력값을 확인해 주세요.",
  "fieldErrors": [
    { "field": "rating", "code": "OUT_OF_RANGE", "message": "별점은 1~5입니다." }
  ],
  "traceId": "example-trace-id"
}
```

다음 표의 경로는 /api 이후 경로다. 거래 명령은 허용 행동을 표시하는 화면과 별개로 서버가 신원·권한·상태를 다시 확인한다.

## 2 인증과 회원 김창재

| 메서드·경로 | 입력·권한 | 출력·처리 |
| --- | --- | --- |
| GET /auth/csrf | 전체 | token, headerName |
| POST /auth/signup-email-requests | 비로그인 가능+CSRF; email | 202 `{message}`; 가입 여부·요청 제한·발송 결과와 무관하게 같은 응답 |
| POST /auth/signup-email-verifications | 비로그인 가능+CSRF; token | 200 `{email, proof, proofExpiresAt, alreadyRegistered}`; 메일 링크 토큰으로 인증하고 가입 증명 발급 |
| POST /members | 비로그인+CSRF; email,password,displayName,**emailVerificationProof**(2026-11-06부터 필수) | 201 Member; 증명 소비·회원(email_verified_at)·지갑·100만원 지급 원자적 생성 |
| POST /auth/login | 비로그인; email,password | 200 Member; 세션 발급 |
| POST /auth/logout | 회원; 없음 | 204; 세션 무효화 |
| GET /me | 회원 | Member |
| PATCH /me | 회원; displayName | Member |
| POST /me/password | 회원+CSRF; currentPassword,newPassword | 204; 현재 비밀번호 재확인 후 변경, auth_version 증가로 모든 세션 폐기. 자동 로그인 없음 |
| POST /auth/password-reset-requests | 비로그인 가능+CSRF; email | 202 `{message}`; 가입 여부·요청 제한·발송 결과와 무관하게 같은 응답 |
| POST /auth/password-resets | 비로그인 가능+CSRF; token,newPassword | 204; 일회용 토큰으로 비밀번호 변경, auth_version 증가로 모든 세션·다른 재설정 링크 폐기. 자동 로그인 없음 |

Member: id,email,displayName,role,hasSellerProfile(email_verified_at은 응답에 넣지 않는다). 이메일은 공백 제거·소문자 정규화 후 중복 확인한다. 가입 후 로그인 화면으로 이동하며 자동 로그인하지 않는다. 로그인·로그아웃 후 CSRF 토큰을 다시 획득한다.

비밀번호 변경(2026-11-05 구현): 두 값 모두 정리하지 않는다. newPassword는 10절 비밀번호 원문 정책, currentPassword는 형식 검사 없음. 현재 비밀번호 불일치 400 CURRENT_PASSWORD_INCORRECT, 새 비밀번호가 현재와 같으면 400 PASSWORD_UNCHANGED, 폐기된 세션·동시 변경의 늦은 요청은 401 AUTHENTICATION_REQUIRED. 로그인 세션은 로그인 당시 auth_version을 갖고, DB 값과 다르면 다음 요청에서 폐기되어 비로그인으로 처리된다. 상세는 [로그인 문서](auth-login.md#비밀번호-변경세션-폐기--2026-11-05-featurepassword-change).

이메일 인증 가입(2026-11-06 구현): 요청 email은 가입과 같이 정규화·형식 검사하며 형식 오류만 400 VALIDATION_FAILED다. 그 밖에는 이미 가입된 이메일·이메일+목적당 60초 간격·1시간 5회 제한 초과·발송 실패 모두 같은 202다(이미 가입된 주소에도 인증 메일을 보낸다). 메일 링크 `/signup/verify#token=`의 token으로 인증하며 위조·만료(15분)·이미 인증·목적 다름은 400 SIGNUP_EMAIL_TOKEN_INVALID다. 인증하면 링크 토큰은 무효가 되고 가입 증명 proof(일회용, 인증 시점부터 30분)를 받는다. `alreadyRegistered`는 메일을 받은 본인이 인증한 뒤에만 알려 준다. 가입 시 증명 없음은 400 VALIDATION_FAILED, 위조·만료·사용됨·다른 이메일·인증 전 링크 토큰·재설정 토큰은 400 EMAIL_VERIFICATION_INVALID, 이미 가입된 이메일은 409 EMAIL_ALREADY_EXISTS다. 증명 없이 가입하는 경로는 없다. 상세는 [로그인 문서](auth-login.md#이메일-인증-가입--2026-11-06-featureemail-verification).

비밀번호 재설정(2026-11-05 구현): 요청 email은 가입과 같이 정규화·형식 검사하며 형식 오류만 400 VALIDATION_FAILED다. 그 밖에는 모두 같은 202·본문·`no-store`다(가입 안 된 이메일, 이메일+목적당 60초 간격·1시간 5회 제한 초과, 메일 발송 실패 포함). 재설정 token은 메일 링크 `#token=`의 값이며, 위조·만료(15분)·이미 사용·발급 후 비밀번호 변경은 모두 400 PASSWORD_RESET_TOKEN_INVALID다. newPassword는 10절 비밀번호 원문 정책(위반 400 VALIDATION_FAILED, field=newPassword)이고 현재 비밀번호와 같아도 거절하지 않는다. 상세는 [로그인 문서](auth-login.md#비밀번호-재설정요청-제한--2026-11-05-featurepassword-recovery).

## 3 판매자와 상품 박소현

| 메서드·경로 | 입력·권한 | 출력 |
| --- | --- | --- |
| GET /categories | 전체 | id,name,displayOrder 배열 |
| GET /sellers/{id} | 전체 | SellerProfile |
| GET /me/seller-profile | 회원 | 본인 SellerProfile, 미등록 404 |
| PUT /me/seller-profile | 회원; introduction,specialty,portfolioUrl | 생성 201 / 수정 200 |
| GET /products | 전체; keyword,categoryId,minPrice,maxPrice,maxDurationDays,sort,sellerId,page,size | ProductSummary 목록 |
| GET /products/{id} | 전체; 공개·판매 중 상품 | ProductDetail, 신규 계약 주문 최소 금액 `minimumOrderAmount` (기존 주문 정책은 `null`) |
| GET /me/products | 회원; page,size | OwnedProductSummary 목록; 중지·숨김 포함 |
| GET /me/products/{id} | 소유자 | 본인 ProductDetail |
| POST /products | 판매자 프로필 보유; ProductInput | 201 ProductDetail |
| PATCH /products/{id} | 소유자; ProductPatch,expectedVersion 필수 | ProductDetail |
| PATCH /products/{id}/sale-status | 소유자; saleStatus,expectedVersion | ProductDetail |
| GET /products/{id}/reviews | 전체; 공개·판매 중 상품, page,size, 선택 rating(1~5) | Review 목록, 전체 평균 별점·후기 건수 |
| GET /me/favorites | 회원; page 기본 0, size 기본 20(1~100) | 공개·판매 중인 관심 상품 ProductSummary 페이지, 최근 등록순 |
| GET /me/favorites/{productId} | 회원 | 200 `{ favorited: boolean }` |
| PUT /me/favorites/{productId} | 회원+CSRF; 공개·판매 중 상품 | 204, 중복 등록도 204. 상품이 없거나 비공개면 404 |
| DELETE /me/favorites/{productId} | 회원+CSRF | 204, 이미 해제된 상품도 204 |

SellerProfile: id(회원 ID),displayName,introduction,specialty,portfolioUrl. 이메일·잔액은 공개하지 않는다. ProductInput: categoryId,title,description,scopeDescription,preparationDescription,priceAmount,durationDays. 가격 1~1,000,000원, 기간 1~30일. portfolioUrl은 선택이다.

판매자 공개 화면은 `GET /sellers/{id}`로 소개를 조회하고 `GET /products?sellerId={id}`로 같은 판매자의 상품을 조회한다. `sellerId`는 선택이며 양의 BIGINT 십진 문자열을 받는다. 형식이나 범위가 잘못되면 400 `VALIDATION_FAILED`를 반환한다. 집계와 목록에는 같은 판매자 조건을 적용하며 숨김·판매 중지 상품은 제외한다.

상품 목록의 선택 조건은 검색어·카테고리와 함께 적용한다. `minPrice`와 `maxPrice`는 각 1~1,000,000원이며 경곗값을 포함한다. `maxDurationDays`는 1~30일이며 해당 일수 이하를 조회한다. 숫자는 1~7자리 십진 정수로 받는다. 빈 값·범위 초과·최저가가 최고가보다 큰 경우·지원하지 않는 정렬은 400 `VALIDATION_FAILED`다. `sort`는 `NEWEST`(기본, 등록 시각 내림차순), `PRICE_ASC`, `PRICE_DESC`를 지원하고 주 정렬 값이 같으면 상품 ID 내림차순이다.

ProductSummary: id,title,priceAmount,durationDays,category,seller. ProductDetail은 description,scopeDescription,preparationDescription,version,saleStatus,createdAt,updatedAt을 추가한다. 본인·관리자 조회는 isHidden 추가. 상품 변경·판매 상태 변경·숨김 변경은 버전 충돌 검사와 일관되게 관리한다. 숨김·중지 상품의 기존 거래는 주문 스냅샷으로 조회한다.

### 상품 등록·부분 수정 계약

OwnedProductSummary는 ProductSummary에 saleStatus,isHidden,version,createdAt,updatedAt을 추가한다. 회원 ID는 인증 정보에서만 결정하며 타인의 숨김·중지 상품을 포함하지 않는다. 상품 상세 설명·판매자 이메일·비밀번호는 목록에 포함하지 않는다. 기본 최신 생성 시각·ID 내림차순, 공통 페이지 계약을 사용한다. 판매자 프로필이나 상품이 없는 회원에게도 빈 목록을 반환한다. 공개 ProductSummary에는 이 전용 필드를 추가하지 않는다.

등록의 ProductInput은 categoryId,title,description,scopeDescription,preparationDescription,priceAmount,durationDays를 모두 필수로 받는다. 서버가 saleStatus=ON_SALE, isHidden=false, version=0으로 생성한다. 클라이언트가 sellerId·saleStatus·isHidden·version을 등록 본문으로 지정하지 않는다.

ProductPatch는 ProductInput의 7개 필드 중 바꿀 필드만 보내는 JSON 객체다. `Content-Type: application/json`을 사용하며 JSON Merge Patch 형식은 사용하지 않는다. expectedVersion은 현재 조회한 0 이상의 정수로 항상 제출하고, 수정 필드는 최소 1개 필요하다.

| 제출 형태 | 처리 |
| --- | --- |
| 수정 필드 생략 | 기존 값 유지 |
| 수정 필드에 null | 400 VALIDATION_FAILED, 전체 변경 없음 |
| 필수 문자열에 빈 문자열·공백만 제출 | 정규화 후 400 VALIDATION_FAILED |
| expectedVersion만 제출 | 400 VALIDATION_FAILED |
| 정의하지 않은 필드·잘못된 JSON 타입 제출 | 400 VALIDATION_FAILED; 숫자·boolean 문자열을 자동 변환하지 않음 |
| 상품 version 불일치 | 409 PRODUCT_VERSION_CONFLICT, 전체 변경 없음 |

서버는 소유권·버전을 확인하고 기존 값과 제출값을 합친 최종 상품을 검증한 뒤 한 트랜잭션으로 저장한다. 동일한 값을 보낸 경우에도 expectedVersion을 검사한다. 일치하고 실제 값 변경이 없으면 현재 ProductDetail을 반환하며 version·updatedAt을 바꾸지 않는다. 변경이 있으면 version을 증가시킨다. saleStatus는 전용 판매 상태 API, isHidden은 관리자 API에서만 변경한다. 기존 주문·스냅샷에는 수정값을 반영하지 않는다.

```json
{
  "title": "랜딩 페이지 제작",
  "priceAmount": 150000,
  "expectedVersion": 2
}
```

위 요청은 제목·가격만 수정하며 카테고리·설명·범위·준비사항·기간을 유지한다. 충돌 시 화면은 최신 상품을 다시 조회하고 사용자의 수정 내용을 확인받으며 자동 덮어쓰기하지 않는다.

판매자 프로필 PUT은 introduction·specialty를 매번 제출하는 전체 교체다. portfolioUrl은 생략·null·정규화 후 빈 값이면 NULL로 지운다. PATCH의 생략=유지 규칙을 PUT에 적용하지 않는다.

## 4 주문 조회와 생성 정은찬

2026-11-02 구현 후속: [주문 생성 서비스·HTTP 연결](order-creation.md)에 저장·재전송·잠금·응답을, [주문 조회](order-query.md)에 GET 목록/상세의 권한·필터·페이지·읽기 일관성과 검증 범위를 기록한다. POST /orders의 201/200 OrderDetail과 GET 목록/상세를 인증 principal 계약에 연결했다. 실제 로그인·후속 주문 명령·React 연결은 별도다.

| 메서드·경로 | 입력·권한 | 출력 |
| --- | --- | --- |
| GET /orders | 회원; side=BUYER 또는 SELLER 필수, status·q·from·to·sort 선택, page,size | OrderSummary 목록 |
| POST /orders | 구매자; productId,expectedProductVersion,requirements,requestKey | 201 OrderDetail / 중복 200. 구매자·판매자 중 하나라도 일반 이용 제한·보안 잠금이면 신규 요청은 403 MEMBER_RESTRICTED(같은 키 재전송은 기존 주문 200) |
| GET /orders/{id} | 주문 참여자 | OrderDetail |

목록 검색 조건(U3): `q`는 앞뒤 공백을 지운 뒤 비면 무시하고 최대 100자(코드 포인트)다. ASCII 숫자만이면 주문번호 정확 일치, 그 밖에는 저장된 상품명·상대방 현재 표시 이름 부분 일치(`%`·`_`는 글자 그대로)다. `from`·`to`는 한국 날짜 `yyyy-MM-dd`이고 주문 시각을 [from 00:00 KST, to+1일 00:00 KST)로 거른다. `sort`는 `latest`(기본, 최신 주문 먼저)·`oldest`. 형식 오류·없는 날짜·from>to·다른 sort 값은 400 VALIDATION_FAILED. 건수와 항목에 같은 조건을 적용한다. 상세는 [주문 조회](order-query.md#목록-검색기간정렬-조건-u3--2026-11-09).

생성 시 상품 상태·버전·자기 상품 구매 금지를 검증한다. 가격·판매자·거래 조건은 서버에서 복사한다. 같은 구매자·요청 키·입력은 기존 주문 반환, 같은 키·다른 입력은 409 IDEMPOTENCY_KEY_REUSED. 기존 키 확인을 현재 상품 조건 검사보다 먼저 수행하여 성공한 주문의 재전송을 새 주문으로 처리하지 않는다.

OrderSummary: id,status,productTitle,priceAmount,counterparty,createdAt,currentDueAt. OrderDetail: id,status,buyer,seller,productId,productSnapshot,termsSnapshot,priceAmount,durationDays,requirements,createdAt,acceptedAt,buyerConfirmedTermsAt,paidAt,originalDueAt,revisionDueAt,currentDueAt,completedAt,cancelledAt,deliveries,revisionRequest,cancellationRequests,histories,payment,review,allowedActions. 판매자 수익은 본인 정산 API에서 조회한다.

저장 구조 분리: 상품명·상품 버전·설명·범위·준비사항·거래 규칙·요구사항은 `order_snapshots`에서 조회한다. `priceAmount`와 `durationDays`는 `orders.price_amount`, `orders.duration_days_snapshot`에서 조회한다. 주문과 스냅샷은 함께 생성하며 요청·응답 필드 및 기존 재전송 계약은 변경하지 않는다. 기존 키의 입력 비교도 최초 저장된 스냅샷을 사용한다.

buyer·seller는 id·displayName만 제공한다. originalDueAt=orders.due_at, revisionDueAt=revision_requests.due_at이다. currentDueAt은 수정 요청이 있으면 수정 기한, 없으면 최초 기한이며 결제 전에는 null이다. 별도 DB 컬럼을 추가하지 않는다. 종료 거래에 지연 경고를 표시하지 않는다.

주문 상태: REQUESTED, AWAITING_PAYMENT, IN_PROGRESS, AWAITING_CONFIRMATION, REVISION_IN_PROGRESS, COMPLETED, REJECTED, CANCELLED. 허용 행동: ACCEPT, REJECT, PAY, CANCEL, DELIVER, REQUEST_REVISION, CONFIRM, REQUEST_CANCELLATION, APPROVE_CANCELLATION, REJECT_CANCELLATION, WITHDRAW_CANCELLATION, WRITE_REVIEW.

### 주문 상세 내부 응답 계약

productSnapshot: title,description,scopeDescription,preparationDescription,productVersion. termsSnapshot은 보존된 거래 조건 문자열이다. category는 id,name, seller·counterparty는 id,displayName을 제공한다.

deliveries 항목: id,sequenceNo,description,resultUrl,submittedAt. revisionRequest: id,deliveryId,reason,requestedAt,dueAt. histories 항목: id,actorId,action,fromStatus,toStatus,reason,createdAt. 최초 이력의 fromStatus와 사유가 없는 행동의 reason은 null이다.

payment: id,amount,feeRateBps,status,paidAt,refundedAt. review는 8절의 Review 계약을 따른다. cancellationRequests는 6절 cancellationRequest 계약의 배열이다.

하위 기록이 아직 없으면 단일 객체 payment·revisionRequest·review는 null, 이력·납품·취소 요청은 빈 배열로 표현한다. 최초 주문 생성 응답에는 CREATED 이력 1건이 있으며 allowedActions는 현재 상태·실행자에 따라 계산한다(신규 REQUESTED 주문의 구매자는 CANCEL). 허용 행동이 없으면 빈 배열이다. 미발생 시각 acceptedAt·buyerConfirmedTermsAt·paidAt·originalDueAt·revisionDueAt·currentDueAt·completedAt·cancelledAt 및 refundedAt·resolvedAt·resolvedBy는 null이다. 선택 입력 portfolioUrl·review.content·responseReason은 미입력 시 null이다. 나머지 필수 필드는 생략하지 않는다.

## 5 주문 명령 정은찬과 황준범

2026-11-03 구현 후속: [판매자 수락·거절](order-decision.md), [최초 납품·재납품](order-delivery.md), [구매자 수정 요청](order-revision.md)에 더해 [주문 결제](order-wallet-payment.md)·[직접 취소/환불](order-refund.md)·[확정/정산](order-settlement.md)이 main에 연결되었다. 세션 로그인·내 정보 API도 구현되었으며 실제 화면 통합·브라우저 검증과 구분한다.

| POST 경로 | 실행자·입력 | 조건·결과 |
| --- | --- | --- |
| /orders/{id}/accept | 판매자; 없음 | REQUESTED → AWAITING_PAYMENT(LEGACY_V1) / AWAITING_CONTRACT(계약 주문). 판매자 일반 이용 제한·보안 잠금이면 403 MEMBER_RESTRICTED(거절은 허용) |
| /orders/{id}/reject | 판매자; reason | REQUESTED → REJECTED |
| /orders/{id}/cancel | 구매자·판매자; reason | 구매자 결제 전; 판매자 수락 후 미완료. 유료 취소 전액 환불 |
| /orders/{id}/payment | 구매자; agreedToTerms=true | 결제 대기·잔액 확인 → IN_PROGRESS |
| /orders/{id}/deliveries | 판매자; description,resultUrl,expectedSequenceNo | 진행·수정 진행 → AWAITING_CONFIRMATION |
| /orders/{id}/revision-request | 구매자; deliveryId,reason | 첫 납품 확인 대기 → REVISION_IN_PROGRESS |
| /orders/{id}/confirm | 구매자; 없음 | 확인 대기 → COMPLETED·정산 |

모든 명령은 200 OrderDetail을 반환한다. 납품·수정 요청·구매 확정은 대기 취소 요청이 없어야 한다. 서버는 요청의 expectedSequenceNo와 현재 상태가 허용한 회차를 대조한다. 결과물은 HTTPS URL만 저장하고 서버에서 접속하지 않는다. 수정은 동일 주문의 첫 납품에 1회만 허용한다.

결제 시 금액·수수료는 서버가 결정한다. 결제·환불·구매 확정의 기록과 금액·상태 변경은 같은 트랜잭션에서 실행한다. 환불·정산·초기 지급을 독립 실행하는 공개 API는 없다.

납품 요청은 expectedSequenceNo(정수 1 또는 2)를 필수로 포함한다. 최초 납품은 1, 수정 납품은 2로 요청하며 재전송 시 값을 유지한다. 서버는 주문 참여자·판매자 권한 확인 후 주문을 잠그고, 이미 존재하는 회차면 409 DELIVERY_ALREADY_SUBMITTED를 반환한다. 미존재 회차라도 현재 상태가 허용한 회차와 다르면 409 DELIVERY_SEQUENCE_CONFLICT다. 최초 납품은 IN_PROGRESS에서 1회차, 수정 납품은 REVISION_IN_PROGRESS에서 2회차만 허용한다. 대기 취소 요청도 확인한 뒤 저장하며 클라이언트 회차를 그대로 신뢰하지 않는다.

기존 회차 재전송은 내용이 같아도 409를 반환하고 저장된 납품·상태·기한을 변경하지 않는다. 프런트는 GET /orders/{id}로 실제 기록을 확인한다. 다음 회차 납품은 판매자가 수정 결과물을 명시적으로 제출하는 별도 행동이다.

지연 표시는 현재 작업 지연과 제출 결과를 구분한다. 현재 작업 지연은 IN_PROGRESS에서 최초 기한, REVISION_IN_PROGRESS에서 수정 기한을 기준으로 현재 시각이 기한을 넘었을 때만 표시한다. AWAITING_CONFIRMATION은 구매자 확인 대기이며 현재 납품 지연으로 표시하지 않는다. 각 납품의 지각 여부는 submitted_at이 해당 회차 기한을 초과했는지로 계산한다. 완료·취소·거절 후 현재 지연 경고는 표시하지 않되 과거 제출 시각·기한은 보존한다. 경계 시각과 같으면 지각이 아니다. 계산값을 위한 DB 컬럼은 추가하지 않는다.

## 6 취소 요청과 재전송 계약

2026-11-03 후속: [취소 요청 제출·승인·거절·철회](order-cancellation-requests.md)를 추가하고, 승인은 기존 [직접 취소·전액 적립금 환불](order-refund.md)에 연결했다. [구매 확정·정산](order-settlement.md)과의 경쟁도 검증한다. 기존 [거래 금융 화면](order-finance-ui.md)의 주문 상세에 취소 요청 제출·승인·거절·철회 UI를 연결했다. 실제 로그인 기반 브라우저 통합은 후속이다.

| POST 경로 | 실행자·입력 | 결과 |
| --- | --- | --- |
| /orders/{id}/cancellation-requests | 구매자; reason,requestKey 필수 | 201 CancellationResult / 기존 요청 200 |
| /orders/{id}/cancellation-requests/{requestId}/approve | 판매자; 없음 | 200 CancellationResult; 취소·환불 |
| /orders/{id}/cancellation-requests/{requestId}/reject | 판매자; responseReason | 200 CancellationResult |
| /orders/{id}/cancellation-requests/{requestId}/withdraw | 구매자; 없음 | 200 CancellationResult |

CancellationResult: cancellationRequest와 order(OrderDetail). cancellationRequest: id,orderId,requestKey,reason,status,requestedAt,resolvedAt,resolvedBy,responseReason. 상태: PENDING,APPROVED,REJECTED,WITHDRAWN. 기존 요청 응답은 해당 요청 ID·해결 상태와 현재 주문을 함께 반환한다.

취소 요청 생성은 requestKey(UUID)를 필수로 받는다. (order_id, requested_by, request_key)를 고유하게 관리하며 주문 잠금 안에서 기존 키를 먼저 조회한다. 같은 키·같은 사유는 요청이 이미 승인·거절·철회되었어도 해당 기존 요청과 현재 주문을 반환한다. 같은 키·다른 사유는 409 IDEMPOTENCY_KEY_REUSED다. 새로운 요청 키는 사용자가 명시적으로 새 취소 요청을 작성할 때만 생성한다.

처리 순서: 인증·주문 구매자 확인 → 주문 잠금 → 기존 키 조회·입력 비교 → 신규 키인 경우 유료 미완료 상태 확인 → 대기 요청 없음 확인 → 요청·이력 저장. 같은 키 재전송에는 현재 주문이 완료·취소되었더라도 과거 요청을 반환하고 새 부작용은 발생시키지 않는다.

입력 사유는 앞뒤 공백을 제거한 저장값으로 비교한다. 동일 키·다른 입력은 거부한다. 새 키라도 대기 요청이 있으면 409 CANCELLATION_PENDING. 이전 요청 해결 후 새 키는 현재 상태가 허용하는 경우에만 새 요청이 된다.

승인·거절·철회는 요청 ID가 해당 주문에 속하는지 확인한다. 동일 해결 동작 반복은 200 기존 결과, 다른 해결 상태로 바꾸려는 요청은 409 REQUEST_ALREADY_RESOLVED. 해결 시각·이력을 덮어쓰지 않는다. 판매자 직접 취소는 대기 요청이 있으면 함께 APPROVED로 종료한다.

## 7 결제 재요청과 충돌 응답

결제 재요청은 주문 잠금 후 현재 주문·결제 상태를 확인한다. 환불 기록이 있으면 409 PAYMENT_ALREADY_REFUNDED, 미결제 취소 주문이면 409 ORDER_STATE_CONFLICT를 반환한다. 기존 결제가 PAID이고 주문이 결제 후 유효 상태라면 차감·기록 추가 없이 현재 주문을 반환한다. 재호출마다 buyerConfirmedTermsAt·paidAt·dueAt을 덮어쓰지 않는다.

기존 PAID에 허용되는 현재 상태는 IN_PROGRESS, AWAITING_CONFIRMATION, REVISION_IN_PROGRESS, COMPLETED다. 최초 결제만 AWAITING_PAYMENT에서 수행한다. 주문·결제 기록이 이 규칙과 모순되면 500 DATA_INTEGRITY_ERROR로 처리하고 traceId를 남기며 자동 복구·재차감하지 않는다.

PAYMENT_ALREADY_REFUNDED 응답에는 재결제 성공 의미를 담지 않는다. React는 주문·잔액을 재조회하고 환불 완료 상태를 안내한다. 200 재호출 응답도 현재 주문 상태를 표시하며 결제 성공 화면으로 강제 이동하지 않는다.

| 작업 | 반복 처리 |
| --- | --- |
| 결제 | PAID·유효 주문이면 현재 주문; REFUNDED는 409 |
| 구매 확정 | COMPLETED·정산 존재면 현재 주문, 재생성 없음 |
| 직접 취소 | CANCELLED면 현재 주문, 재환불 없음 |
| 수락·거절 | 현재 상태에서 재실행 불가 시 409 |
| 납품·수정·리뷰 | 이미 기록·상태 변경 시 409, 추가 생성 없음 |
| 관리자 숨김·복구 | 동일 상태면 현재 상태, 이력 추가 없음 |

반복 요청에도 실행자 권한을 확인한다. 주문 → 지갑 잠금 순서를 통일한다. 결과 불명확 시 프런트는 조회로 확인하며 변경 요청 자동 재시도는 하지 않는다.

## 7-1 주문 알림 정은찬

2026-11-05 고도화 1단계: 기존 MVP 주문 이력이 생길 때 같은 트랜잭션에 아웃박스 이벤트를 저장하고, 처리기가 상대 당사자 알림을 만든다. 처리 기준과 검증 범위는 [주문 알림·아웃박스](notifications.md)를 따른다.

| 메서드·경로 | 권한·입력 | 출력 |
| --- | --- | --- |
| GET /notifications | 회원; page 0 이상, size 1~100(기본 20), unreadOnly `true`/`false`(기본 false) | Notification 목록, 생성 시각·ID 내림차순. unreadOnly=true면 읽지 않은 본인 알림만 같은 순서로, totalElements·totalPages도 같은 조건. 불리언이 아니면 400 VALIDATION_FAILED |
| GET /notifications/unread-count | 회원 | count |
| POST /notifications/{id}/read | 본인+CSRF; 양수 알림 ID | 204. 이미 읽음도 204, 최초 읽은 시각 유지. 타인·없음 404, 형식 오류 400 |
| POST /notifications/read-all | 회원+CSRF; 본문 없음 | 204. 요청 시점까지 생성된 본인 알림만 읽음 처리 |

Notification: id,type,orderId,productTitle,read,createdAt. type은 `ORDER_` + 주문 이력 행동(CREATED·ACCEPTED·REJECTED·PAID·DELIVERED·REVISION_REQUESTED·CANCELLATION_REQUESTED·CANCELLATION_REJECTED·CANCELLATION_WITHDRAWN·CANCELLED·COMPLETED)이다. CONTRACT_V3 주문은 PAID·구매자 COMPLETED·결제 후 CANCELLED를 각각 `ORDER_FUNDS_HELD`·`ORDER_FUNDS_RELEASED`·`ORDER_FUNDS_REFUNDED`로 보내고, 시스템 확정 `ORDER_AUTO_CONFIRMED`, 기한 통지 `ORDER_DELIVERY_DUE_NOTIFIED`·`ORDER_REDELIVERY_DUE_NOTIFIED`(양측), 만료 24시간 전 `ORDER_AUTO_CONFIRM_SOON`(구매자), 타이머 `ORDER_TIMER_STARTED`·`ORDER_TIMER_ADMIN_STOPPED`, 운영 문의를 검토한 관리자의 취소 요청 거절 `ORDER_CANCELLATION_ADMIN_REJECTED`(양측, [관리자 취소 검토](admin-cancellation-review.md))가 있다([V3 알림](v3-notices.md)). 문구는 화면이 type으로 정하고, 모르는 type은 일반 문구로 표시한다. productTitle은 주문 시점 스냅샷이다. 조회 응답은 `Cache-Control: no-store`다.

## 8 지갑·정산 담당 황준범, 후기 담당 박소현

2026-11-04 후기 후속: `codex/reviews`에서 후기 작성·공개 조회 API와 주문/상품 상세 화면을 구현했다. 구현·검증·미병합 상태는 [후기 작성·조회](reviews.md)를 따른다. POST는 회원+CSRF, 구매 확정 주문 구매자만 허용하며 미완료409 ORDER_STATE_CONFLICT·중복409 REVIEW_ALREADY_EXISTS·판매자403·타인404다. 별점은 원본 JSON 정수1~5만 허용하고 content 생략/null을 허용한다. 공개 GET은 공개·판매 중 상품만 조회(그 외404), page0/size20 기본·size1~100·작성 시각/ID 내림차순이며 공개 응답에 orderId를 포함하지 않는다.

2026-11-03 후속: [구매 확정·판매 수익 정산](order-settlement.md)의 POST /orders/{id}/confirm, GET /me/settlements, GET /me/settlements/summary 및 판매 수익 화면을 추가했다. 주문 상세의 구매 확정 버튼과 실제 세션 기반 서버 검사도 연결되었다. 인증 화면을 포함한 브라우저 검증은 별도이며, 아래 후기 API의 구현 완료를 뜻하지 않는다.

| 메서드·경로 | 권한·입력 | 출력 |
| --- | --- | --- |
| GET /me/wallet | 회원 | balance,currency=KRW,isTest=true |
| GET /me/wallet/entries | 회원; page,size | WalletEntry 목록 |
| POST /me/wallet/topups | 회원+CSRF; amount 정수 1~1000000 | 201 충전 신청, READY; 잔액 무변경 |
| GET /me/wallet/topups/{id} | 본인; 양수 충전 ID | 충전 신청/현재 상태, 타인·없음은 404 |
| POST /me/wallet/topups/{id}/confirm | 본인+CSRF; paymentKey,orderId,amount | 충전 승인·지급 결과; 완료/종료 200, 확인 중 202 |
| POST /me/wallet/topups/{id}/reconcile | 본인+CSRF; 본문 없음 | 저장된 결제 키 외부 조회·지급 복구; 확인 중 202 |
| GET /me/settlements | 본인 판매 수익; page,size,from,to,sort | Settlement 목록 |
| GET /me/settlements/summary | 본인 판매 수익; from,to | completedCount,grossAmount,feeAmount,sellerAmount |
| POST /orders/{id}/review | 완료 주문 구매자; rating,content | 201 Review |

2026-11-09 로컬 후속: [판매 수익 기간 조회 계약](settlement-period-query.md). from/to는 정산 기록일의 한국 날짜(`yyyy-MM-dd`, 1000~9999년, 종료일 당일 포함), sort는 목록의 latest(기본)/oldest다. 조건 생략 시 기존 전체 기간 조회다. 목록·합계 모두 principal 판매자와 같은 기간을 적용한다. 잘못된 날짜·역순 기간·목록 정렬은 400 `VALIDATION_FAILED`, 인증·no-store·응답 필드는 유지한다. 정산 생성/금액 정책은 변경하지 않는다.

WalletEntry: id,type,amountDelta,balanceAfter,orderId,createdAt. 초기 지급과 V2 TOPUP 내역의 orderId는 null이다. [충전 신청·조회 계약](wallet-topup-api.md)과 [승인·지급·재확인 계약](wallet-topup-approval.md)을 따른다. 실제 키 결제창 연동은 검증 전이다. WalletTopupResponse: id,tossOrderId,amount,currency,isTest,status,createdAt. 신청 POST는 호출마다 별도 READY 건을 만들며 멱등 재요청은 아직 제공하지 않는다. 승인 POST는 같은 신청·같은 조건이면 기존 처리 상태를 반환하며 중복 승인/지급하지 않는다. 외부 결과 불명확·진행 중은 202, 불일치는 502 TOPUP_PAYMENT_MISMATCH, 조건/결제 키 충돌은 409다. 제공자 기본 비활성화 시 새 승인은 503이다. 내부 승인 멱등키와 paymentKey는 응답하지 않는다. Settlement: id,orderId,grossAmount,feeRateBps,feeAmount,sellerAmount,createdAt. Review: id,orderId,authorDisplayName,rating,content,createdAt. 공개 리뷰 조회에는 orderId를 제외한다. 별점 1~5 필수, 본문 선택, 주문당 1개.

## 9 관리자 상품 운영 김창재

| 메서드·경로 | 입력 | 출력 |
| --- | --- | --- |
| GET /admin/products | keyword,isHidden,saleStatus,page,size | AdminProductSummary 목록 |
| GET /admin/products/{id} | 없음 | 상품 상세 |
| GET /admin/products/{id}/moderation-logs | page,size | id,adminId,action,reason,createdAt 목록 |
| PATCH /admin/products/{id}/visibility | hidden,reason,expectedVersion | 관리자 ProductDetail |

모든 경로는 관리자 전용이다. 숨김 상태와 이력을 함께 저장하며 판매 상태는 변경하지 않는다. 관리자에게 거래·지갑·강제 환불 API를 제공하지 않는다.

관리자 상품 상세는 본인 조회와 같은 ProductDetail 필드에 isHidden을 포함한다. 숨김·복구는 상품 잠금 후 현재 상태를 확인한다. 이미 요청한 hidden 상태라면 버전 차이와 관계없이 현재 상세를 반환하고 버전·운영 이력을 변경하지 않는다. 실제 상태 변경이 필요하면 expectedVersion을 검사하고, 불일치는 409 PRODUCT_VERSION_CONFLICT로 처리한다. 변경 성공 시 상품 version 증가와 숨김 상태·운영 이력을 같은 트랜잭션에서 반영한다. 판매자 상품 수정·판매 상태 변경도 이 version과 일관되게 처리한다.

### 관리자 상품 목록 계약

AdminProductSummary: id,title,priceAmount,durationDays,category(id,name),seller(id,displayName),saleStatus,isHidden,version,createdAt,updatedAt. 응답은 공통 목록의 items,page,size,totalElements,totalPages를 사용한다. 상품 상세 설명·이메일·지갑 정보·운영 이력은 목록에 포함하지 않는다.

| 쿼리 | 생략 시 | 제출 시 |
| --- | --- | --- |
| keyword | 검색 제한 없음 | 앞뒤 공백 제거 후 빈 값도 제한 없음; 최대 100 코드 포인트. 제목 또는 설명에 포함된 문자열 검색 |
| isHidden | 숨김·미숨김 모두 | true 또는 false만 허용; 빈 값·다른 문자열은 400 |
| saleStatus | ON_SALE·STOPPED 모두 | ON_SALE 또는 STOPPED만 허용; 빈 값·다른 문자열은 400 |
| page | 0 | 0 이상의 정수 |
| size | 20 | 1~100의 정수 |

필터는 AND로 결합하며 keyword의 제목·설명 조건만 OR로 묶는다. keyword의 `%`·`_`·이스케이프 문자는 일반 검색 문자로 처리하고 검색값은 바인딩한다. 정렬은 createdAt 내림차순, 같은 시각은 id 내림차순으로 고정한다. 없는 결과나 범위를 벗어난 page는 items=[]이며 집계는 같은 필터의 전체 결과를 기준으로 한다. 잘못된 쿼리·길이 초과는 400 VALIDATION_FAILED다.

예: `GET /api/admin/products?isHidden=true&saleStatus=STOPPED&page=0&size=20`은 숨김이면서 판매 중지인 상품만 조회한다. 공개 상품 목록은 기존처럼 판매 중·미숨김만 노출하며 관리자 필터를 그대로 적용하지 않는다. 공개 목록의 keyword 정규화·길이·문자 그대로 검색 기준은 위와 같다.

```json
{
  "items": [
    {
      "id": "101",
      "title": "랜딩 페이지 제작",
      "priceAmount": 150000,
      "durationDays": 7,
      "category": { "id": "1", "name": "웹 개발" },
      "seller": { "id": "20", "displayName": "판매자 예시" },
      "saleStatus": "STOPPED",
      "isHidden": true,
      "version": 3,
      "createdAt": "2026-10-30T00:00:00Z",
      "updatedAt": "2026-10-30T01:00:00Z"
    }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

위 값은 응답 형식 설명용이며 실제 회원·상품·카테고리 데이터가 아니다.

## 9-1 운영 문의·관리자 취소 검토 김창재

2026-11-09 `feature/admin-cancellation-inquiries`(K4 API·화면). 승인/거절 처리 자체는 주문 영역 `AdminCancellationReviewService`(#121, [계약](admin-cancellation-review.md))가 한다. DB 변경 없음.

| 메서드·경로 | 입력 | 출력 |
| --- | --- | --- |
| POST /orders/{orderId}/cancellation-requests/{requestId}/review-inquiries | subject | 201 새 문의, 200 같은 요청의 열린 문의(재전송) |
| GET /me/inquiries | orderId(선택),page,size | 내 문의 목록 |
| GET /admin/inquiries | status,type,page,size | 관리자 문의 목록(신청자 이름 포함) |
| GET /admin/inquiries/{id} | 없음 | 문의 상세 + 주문 요약 + 취소 요청 + allowedActions |
| POST /admin/inquiries/{id}/approve | reason | AdminCancellationReviewResponse |
| POST /admin/inquiries/{id}/reject | reason | AdminCancellationReviewResponse |
| POST /admin/inquiries/{id}/close | 없음 | 문의 상세 |

- 모든 명령은 CSRF 필수, 응답은 `Cache-Control: no-store`. 관리자 경로는 세션 역할(ROLE_ADMIN)과 현재 DB 역할을 모두 확인한다(강등된 세션 403 `FORBIDDEN`).
- 검토 요청: subject는 앞뒤 공백 정리 후 1~150 코드 포인트(빈 값·초과·문자열 아닌 값 400 `VALIDATION_FAILED`). 주문 당사자가 아니면 404, 판매자는 403 `FORBIDDEN`, 그 주문의 취소 요청이 없으면 404. 요청이 대기(PENDING)가 아니면 409 `REQUEST_ALREADY_RESOLVED`, V3가 아니면 409 `ORDER_POLICY_NOT_READY`, 결제 후 진행 중(IN_PROGRESS·AWAITING_CONFIRMATION·REVISION_IN_PROGRESS)이 아니면 409 `ORDER_STATE_CONFLICT`. 판매자 무응답 시간 조건은 정책에 없어 두지 않았다(관리자가 판단).
- 중복 방지: DB UNIQUE가 없으므로 회원 공유 잠금 → 주문 배타 잠금 아래에서 같은 요청의 열린 문의를 확인하고 저장한다(관리자 승인/거절과 같은 순서). 같은 요청에 열린 문의가 있으면 새로 만들지 않고 200으로 돌려준다.
- 관리자 목록: status는 OPEN·IN_REVIEW·CLOSED, type은 CANCELLATION_REVIEW·GENERAL만 허용(다른 값 400). createdAt 내림차순, 같은 시각은 id 내림차순.
- allowedActions: 열린 취소 검토 문의이고 연결된 요청이 대기 중이면 `APPROVE`·`REJECT`, 열린 문의인데 승인·거절할 대기 요청이 없으면 `CLOSE`, 닫힌 문의는 빈 배열. 화면 표시용이며 서버가 처리할 때 다시 검사한다.
- 종료(close): 관리자 공유 잠금 → 주문 배타 잠금 → 문의 배타 잠금. 연결된 요청이 아직 대기 중이면 409 `REQUEST_STILL_PENDING`(승인·거절로 처리). 이미 닫힌 문의는 현재 상세 200(재전송). 주문·대금·취소 요청은 바꾸지 않는다. 종료 사유 칸은 DB에 없어 받지 않는다(메시지 기능에서 후속).
- 승인/거절 오류는 [K4 계약](admin-cancellation-review.md#서비스-계약) 표를 따른다.

## 9-2 관리자 자동 구매 확정 타이머 해제 김창재

2026-11-09 같은 브랜치(K5). 해제 처리는 `ReviewTimerService.adminStop`(#99, [자동 구매 확정](auto-confirm-timer.md))이 한다. DB 변경 없음.

| 메서드·경로 | 입력 | 출력 |
| --- | --- | --- |
| GET /admin/review-timers | page,size | 진행 중(ACTIVE) 타이머 목록: timerId,orderId,cycleNo,startedAt,expiresAt,buyer,seller |
| POST /admin/orders/{orderId}/review-timer/stop | reason | ReviewTimerResponse(해제 후 status `ADMIN_STOPPED`) |

- 목록은 만료 시각 오름차순(먼저 자동 확정될 주문부터), 같은 시각은 timerId 오름차순. 현재 DB 역할이 ADMIN이 아니면 403.
- 해제는 CSRF 필수·no-store. 오류는 서비스 그대로: 사유 공백 정리 후 1~1000자 아님 400 `VALIDATION_FAILED`, 관리자 아님 403 `FORBIDDEN`, V3 주문이 아니거나 없음 404, 활성 타이머 없음 409 `TIMER_NOT_ACTIVE`, 만료 시각이 지남 409 `AUTO_CONFIRM_DUE`(자동 확정 우선). 해제된 기산점으로는 판매자가 다시 시작할 수 없다.

## 9-3 관리자 금융·계약 PDF 운영 조회 김창재

2026-11-09 `feature/admin-finance-views`(K3·K6 조회). 조회·권한·후보 판단은 황준범의 `AdminFinanceQueryService`(#117, [계약](admin-finance-support.md))·`AdminContractPdfQueryService`(#125, [계약](admin-contract-pdf-inspection.md))가 한다. 모두 GET·`Cache-Control: no-store`·관리자 전용이며 현재 DB 역할을 다시 확인한다(강등 세션 403). DB 변경 없음.

| 메서드·경로 | 입력 | 출력 |
| --- | --- | --- |
| GET /admin/finance/topups | memberId,status,page,size | 충전 목록(자동 복구·관리자 재확인 후보 판단 포함) |
| GET /admin/finance/topups/{id} | 없음 | 충전 상세 |
| GET /admin/finance/payments | orderId,buyerId,sellerId,status,policyVersion,page,size | 결제·환불 목록 |
| GET /admin/finance/orders/{orderId} | 없음 | 주문 금액·결제·보관금·정산·종료 분배(없는 기록은 null) |
| GET /admin/finance/recovery-jobs | page,size | 충전 복구 Job 인스턴스별 최신 실행·재시작 후보 판단 |
| GET /admin/finance/recovery-jobs/{executionId} | 없음 | 실행 상세 |
| GET /admin/orders/{orderId}/contract-pdf | version(선택) | 계약 PDF 메타데이터·생성 후보 판단 |

- 충전 status는 READY·PROCESSING·UNKNOWN·COMPLETED·FAILED, 결제 status는 PAID·REFUNDED, policyVersion은 LEGACY_V1·CONTRACT_V2·CONTRACT_V3만(대소문자 그대로). ID 필터는 양수. 잘못된 값 400 `VALIDATION_FAILED`, 없는 대상 404 `RESOURCE_NOT_FOUND`.
- 결제 키·처리권·파일 경로/해시·회원 이메일은 응답에 없다. 후보 판단(eligible·reason)은 조회 순간의 조건일 뿐 실행 권한이 아니며, 재확인·재시작·재생성 실행 API는 없다(관리자 감사 이력 설계 후 별도).

## 9-4 관리자 알림 처리 실패 조회 김창재

2026-11-09 `feature/admin-outbox-failures`(K6 알림 `FAILED` 조회). 조회만 하며 재처리(다시 보내기) API는 없다([관리자 작업 감사 이력 설계안](admin-action-audit-design.md) 확정 뒤 별도). GET·`Cache-Control: no-store`·관리자 전용, 현재 DB 역할 재확인(강등 세션 403). DB 변경 없음.

| 메서드·경로 | 입력 | 출력 |
| --- | --- | --- |
| GET /admin/outbox-events | status(`FAILED` 기본·`RETRYING`), orderId(선택), page, size(1~100, 기본 20) | OutboxIssue 목록, 이벤트 ID 내림차순(최근 먼저) |

- OutboxIssue: id, eventId, type, aggregateType, aggregateId, status, attemptCount, lastErrorCode, nextAttemptAt, createdAt. ID는 문자열, 시각은 UTC.
- `FAILED`: 자동 재시도를 멈춘 이벤트(`outbox_events.status='FAILED'`), nextAttemptAt은 null. `RETRYING`: 실패 후 다음 시도를 기다리는 이벤트(`PENDING`이며 attempt_count ≥ 1). 처음 대기 중(시도 0회)·완료 이벤트는 나오지 않는다.
- orderId는 `aggregate_type='ORDER'`인 이벤트만 거른다. lastErrorCode는 [공통 이벤트 계약](event-contract.md)의 고정 코드다.
- 페이로드(수신자 등)는 응답하지 않는다. status는 대소문자 그대로 두 값만, orderId는 양수, page 0 이상. 그 밖의 값은 400 `VALIDATION_FAILED`.

## 9-5 관리자 운영 현황 김창재

2026-11-09 `feature/admin-dashboard`(K3 운영 현황). 기존 관리자 조회(문의·타이머·알림·충전)의 건수만 모은다. GET·`Cache-Control: no-store`·관리자 전용, 현재 DB 역할 재확인(강등 세션 403). 조회만 하며 행 잠금·DB 변경 없음.

| 메서드·경로 | 입력 | 출력 |
| --- | --- | --- |
| GET /admin/dashboard | 없음 | inquiries{open,inReview}, reviewTimers{active,expiringWithin24Hours}, notifications{failed,retrying}, topups{unknown}, checkedAt |

- inquiries: `support_inquiries` 상태 OPEN·IN_REVIEW 건수(유형 무관). reviewTimers: ACTIVE 전체와 만료 시각이 서버 현재 시각+24시간 **전**인 것(이미 지났지만 처리 전 포함). notifications: [9-4절](#9-4-관리자-알림-처리-실패-조회-김창재)의 FAILED·RETRYING 조건. topups: 상태 UNKNOWN.
- 각 숫자는 조회 순간의 값이며 하나의 스냅샷을 보장하지 않는다. checkedAt은 UTC(마이크로초).

## 10 오류와 입력 기준

| HTTP | 주요 코드 |
| --- | --- |
| 400 | VALIDATION_FAILED, TERMS_AGREEMENT_REQUIRED, INVALID_URL, CURRENT_PASSWORD_INCORRECT, PASSWORD_UNCHANGED, PASSWORD_RESET_TOKEN_INVALID, SIGNUP_EMAIL_TOKEN_INVALID, EMAIL_VERIFICATION_INVALID |
| 401 | AUTHENTICATION_REQUIRED, INVALID_CREDENTIALS |
| 403 / 404 | FORBIDDEN, CSRF_INVALID, MEMBER_RESTRICTED(주문 생성·판매자 수락, U7) / RESOURCE_NOT_FOUND |
| 409 | EMAIL_ALREADY_EXISTS, PRODUCT_VERSION_CONFLICT, PRODUCT_NOT_ORDERABLE, SELF_PURCHASE_NOT_ALLOWED, CONTRACT_MINIMUM_AMOUNT_NOT_MET(V2 생성 활성화 후에만) |
| 409 | ORDER_STATE_CONFLICT, INSUFFICIENT_BALANCE, IDEMPOTENCY_KEY_REUSED |
| 409 | CANCELLATION_PENDING, REQUEST_ALREADY_RESOLVED, REVISION_ALREADY_USED, REVIEW_ALREADY_EXISTS, PAYMENT_ALREADY_REFUNDED, DELIVERY_ALREADY_SUBMITTED, DELIVERY_SEQUENCE_CONFLICT |
| 405 / 406 / 415 | METHOD_NOT_ALLOWED(`Allow` 헤더 포함) / NOT_ACCEPTABLE / UNSUPPORTED_MEDIA_TYPE |
| 500 | INTERNAL_ERROR, DATA_INTEGRITY_ERROR |

2026-11-09 B4 후속(김창재): 지원하지 않는 메서드·응답 형식(Accept)·요청 본문 형식은 클라이언트 요청 문제이므로 500·ERROR 로그 대신 405·406·415 공통 오류로 응답한다. 예: `GET /api/orders/{id}/contract/file`(PDF)에 `Accept: application/json`만 보내면 406 `NOT_ACCEPTABLE`. JSON도 받지 않는 Accept면 본문 없는 406이다.

### 문자열·필수값 공통 기준

2026-10-30 보완 기준: 기존 물리 설계의 아래 길이·정규화와 상품 기본값을 구현 계약으로 채택한다. 가격 1~1,000,000원, 기간 1~30일, 별점 1~5, requestKey UUID 등 기존 업무 정책은 유지한다. DB 미생성 상태에서도 DTO·화면이 동일한 계약을 사용하도록 하며 SQL 실행 성공을 의미하지 않는다.

| 입력 필드 | 필수·정규화 후 길이 |
| --- | --- |
| email | 필수 3~254; 이메일 형식 검사, 앞뒤 공백 제거 후 Locale.ROOT 소문자화 |
| displayName | 필수 1~30; 중복 허용 |
| introduction / specialty | 필수 1~2000 / 1~100 |
| category.name / title | 필수 1~50 / 1~100; 카테고리명은 고정 데이터 기준이며 등록 API를 추가하지 않음 |
| description / scopeDescription / preparationDescription | 필수 1~10000 / 1~3000 / 1~3000; 준비사항이 없으면 없다는 설명 작성 |
| requirements / 납품 description | 필수 1~5000 / 1~5000 |
| 주문 거절·직접 취소·수정 요청·취소 요청·관리자 운영 reason | 필수 1~1000 |
| 취소 요청 거절 responseReason / 후기 content | 선택, 입력 시 1~1000 / 1~2000 |
| portfolioUrl / resultUrl | 선택 / 필수, 입력 시 1~2048; HTTPS URL |
| termsSnapshot | 서버가 생성·보존, 필수 1~10000; 클라이언트 입력 불가 |
| keyword | 선택 1~100; 생략·정규화 후 빈 값은 검색 제한 없음 |

필수는 생성·해당 명령에서 생략·null·정규화 후 빈 문자열을 거절한다는 뜻이다. 상품 PATCH는 제출한 필드에만 필수값 검증을 적용하고, 생략은 기존 값을 유지한다. 선택 문자열은 생략·null·빈 값·공백만 제출하면 NULL로 통일한다. history.reason은 업무에서 발생한 사유를 보존하며 사유가 없는 사건만 NULL이다.

일반 문자열은 CRLF·CR을 LF로 바꾸고 앞뒤 공백을 제거한 뒤 길이를 검사·저장·재전송 비교한다. 앞뒤 제거 대상은 Unicode White_Space 집합(U+0009~000D, U+0020, U+0085, U+00A0, U+1680, U+2000~200A, U+2028~2029, U+202F, U+205F, U+3000)으로 통일한다. 내부 공백·줄바꿈은 보존하며 Unicode 정규화나 대소문자 변경은 하지 않는다. 이메일 소문자화와 UUID 표준형 변환은 별도 예외다. 서버의 정규화를 기준으로 하며 DB TRIM이 모든 Unicode 공백을 제거한다고 가정하지 않는다.

길이는 UTF-16 길이나 바이트 수가 아닌 Unicode 코드 포인트 수로 센다. 서버는 codePointCount, 화면은 `[...value].length`, DB는 CHAR_LENGTH 기준이다. 정규화 후 상한 초과는 자르지 않고 400 VALIDATION_FAILED로 반환한다. 화면도 같은 기준을 적용하며 HTML maxlength나 기본 @Size만으로 검증을 대체하지 않는다.

URL은 URI 파서로 https 스킴·호스트 존재·userinfo 부재를 확인한다. 형식·스킴 오류는 400 INVALID_URL이며 서버는 해당 주소에 접속하지 않는다. ID는 양의 BIGINT 범위의 10진 문자열, expectedVersion·expectedProductVersion은 0 이상의 정수, 숫자·boolean 필드는 정확한 JSON 타입으로 받는다. requestKey는 UUID 파싱 후 소문자 하이픈 표준형으로 저장·비교한다.

비밀번호 원문은 위 trim·줄바꿈·소문자화 규칙에서 제외하고 입력 그대로 인코더에 전달한다. 조용한 절삭을 허용하지 않는다. password_hash의 VARCHAR(255)는 원문 비밀번호 길이 제한이 아니다.

### 비밀번호 원문 정책

2026-11-02 김창재 결정(PR #14). 회원가입 입력과 비밀번호 변경·재설정의 새 비밀번호(2026-11-05)에 적용한다. 서버는 `PasswordRules.PATTERN` 한 곳의 상수를 쓴다.

| 항목 | 기준 |
| --- | --- |
| 허용 문자 | ASCII U+0021~U+007E(영문 대소문자·숫자·특수문자). 공백·한글·이모지 불가 |
| 길이 | 8~64자 |
| 조합 | 영문 1자 이상과 숫자 1자 이상 필수, 특수문자 선택 |
| 정규화 | 하지 않음. 앞뒤 공백을 제거하지 않고 규칙 위반으로 거절 |
| 검증식 | `^(?=.*[A-Za-z])(?=.*[0-9])[\x21-\x7E]{8,64}$` |
| 인코더 | `PasswordEncoderFactories.createDelegatingPasswordEncoder()`(기본 BCrypt). 저장값은 `{bcrypt}` 접두어 포함 |
| 실패 응답 | 가입: 400 VALIDATION_FAILED, fieldErrors의 field=password. 로그인: 형식을 검사하지 않고 이메일 없음·비밀번호 불일치를 구분하지 않는 401 INVALID_CREDENTIALS |

ASCII만 허용하므로 64자는 64바이트이며 BCrypt의 72바이트 입력 한도를 넘지 않는다. 따라서 절삭이 발생하지 않는다. 비밀번호 값은 응답·로그에 포함하지 않는다.

### 이메일 형식 기준

정규화(앞뒤 공백 제거·Locale.ROOT 소문자화) 후 3~254자를 검사하고 다음 형식을 요구한다. 위반은 400 VALIDATION_FAILED다.

- 로컬 파트: 영문·숫자와 ``!#$%&'*+/=?^_`{|}~-``, 점은 맨 앞·맨 뒤·연속 불가, 최대 64자
- 도메인: 점으로 구분된 라벨 2개 이상. 라벨은 영문·숫자·하이픈 1~63자이며 하이픈으로 시작·끝나지 않는다. 마지막 라벨은 영문 2자 이상
- 거절 예: `review@domain..com`, `.a@b.com`, `a@-b.com`, `a@b`

DB의 이메일 UNIQUE(`uk_members_email`) 위반만 409 EMAIL_ALREADY_EXISTS로 변환한다. 그 밖의 DB 제약 위반은 이메일 중복으로 응답하지 않는다.

### 남은 구현 전 결정

문자열·비밀번호 원문 정책·상품 기본값·상품 PATCH·관리자 목록 계약은 위 보완 기준을 따른다. 실제 카테고리 목록은 별도 결정한다. 인증·주문·취소·결제의 상세 JSON 예시와 실행 결과는 해당 기능 구현 시 추가하며, 이 문서의 예시를 실행 검증 기록으로 보지 않는다.

주문·취소 요청 키의 중복 처리와 납품 회차·지연 표시의 상세 API 규칙은 이 문서 4~7절을 기준으로 한다. 다른 문서에는 목적과 데이터 제약을 남기고 상세 계약은 이 문서를 참조한다.

## 11 DB 변경과 완료 검증

`order_snapshots` 추가로 총 16개 테이블로 구성한다. 가격·소요일은 `orders`에 유지하고 상품 설명·조건·요구사항은 스냅샷으로 분리한다. `cancellation_requests.request_key` 필수 컬럼과 `(order_id, requested_by, request_key)` UNIQUE도 유지한다. DB 미생성 환경은 최초 스키마에 반영한다. 이미 주문 데이터가 있는 환경은 새 마이그레이션에서 당시 주문에 저장된 값으로 스냅샷을 채우고 누락·중복·원문 일치를 확인한 뒤 원래 컬럼의 제거 시점을 정한다. 현재 상품값으로 과거 조건을 복원하지 않는다. 기존 취소 요청에 키가 없다면 고유 키를 채운 후 NOT NULL·UNIQUE를 적용한다. 기존 주문·잔액을 삭제하지 않는다.

| 시나리오 | 기대 결과 |
| --- | --- |
| 주문 생성 중 스냅샷·최초 이력 저장 실패 | 주문·스냅샷·이력 전체 롤백 |
| 상품 변경 후 기존 주문 재조회·동일 키 재전송 | 기존 스냅샷과 orders의 확정 가격·소요일 유지, 기존 주문 반환 |
| 같은 키·사유의 취소 요청 동시 제출 | 요청 1개, 같은 요청 ID |
| 취소 요청 거절·철회 후 같은 키 재전송 | 기존 해결 요청 반환, 새 요청·이력 없음 |
| 같은 키·다른 사유 | 409 IDEMPOTENCY_KEY_REUSED |
| 새 키·대기 요청 있음 | 409 CANCELLATION_PENDING |
| 새 키·기존 요청 해결·주문 유효 | 새 요청 1개 생성 |
| 환불 완료 후 결제 재요청 | 409 PAYMENT_ALREADY_REFUNDED, 잔액·이력 불변 |
| 완료 주문의 기존 결제 재호출 | 현재 COMPLETED 반환, 재차감·시각 변경 없음 |
| 결제와 취소의 동시 요청 | 잠금 순서에 따라 유효 처리만, 금액·상태 일치 |
| 취소 승인과 구매 확정 충돌 | 취소·환불과 완료·정산이 동시에 성립하지 않음 |
| 최초 납품 성공 → 수정 요청 → 최초 납품 재전송 | 409 DELIVERY_ALREADY_SUBMITTED, 2회차 미생성·상태 유지 |
| 진행 중 expectedSequenceNo=2 요청 | 409 DELIVERY_SEQUENCE_CONFLICT, 납품 미생성 |
| 동일 회차 납품 동시 제출 | 한 건만 생성, 나머지 409 |
| 기한 내 납품 후 확인 대기가 기한을 초과 | 현재 작업 지연 아님, 최초 제출 지각 아님 |
| 수정 납품 제출 시각이 수정 기한과 같음 | 지각 아님 |
| 관리자 숨김 변경 시 조회 버전과 현재 버전 불일치 | 409 PRODUCT_VERSION_CONFLICT, 숨김 상태·운영 이력 불변 |
| 이미 숨김 상태인 상품에 같은 숨김 요청 재전송 | 현재 관리자 ProductDetail 반환, 버전·운영 이력 추가 없음 |
| 판매자 상품 수정과 관리자 숨김 동시 실행 | 같은 상품 version·잠금 기준으로 처리, 허용된 변경만 반영 |

위 항목은 실행할 테스트 목록이며 현재 테스트 통과를 의미하지 않는다. 구현 후 실제 MySQL·API·React 연결을 검증한다. 기능 담당·조장·React 공통 주담당은 확정 배정을 따르며, 검토자·배포 담당과 첫 통합·배포일은 팀 작업표에서 별도로 확정하고, 라이브러리 패치는 설치 검증 후 기록한다.
