# 시스템 구조와 기술 설계

[목차](README.md) · [기능 규칙](features.md) · [DB 설계](database-design.md) · [진행 현황](project-progress.md)

## 전체 구조

React 화면 → 공통 HTTP 클라이언트(/api, 세션 쿠키·CSRF) → Spring MVC Controller/DTO → 도메인 Service → JPA/QueryDSL Repository → MySQL 구조다. 화면은 입력·조회·상태 안내를 담당하고 권한·상태 전환·금액·동시성은 서버가 최종 판단한다.

K6 재처리 로컬 후속은 `AdminReprocessingService`가 감사 요청 선행 커밋 → 업무 처리권 확보 → 트랜잭션 밖 외부 조회/PDF 생성/Batch 재시작 → 업무 결과 → 감사 결과를 조율한다. Controller는 이 내부 서비스 하나를 호출하고 기존 후보 조회를 처리권으로 취급하지 않는다. [메서드·결과 코드·잠금 순서](features.md#admin-reprocessing)를 따른다. 공개 실행/감사 목록 API·화면은 `AdminReprocessingController`와 `/admin/finance`(충전·복구 작업 칸, "관리자 작업 이력" 탭)·`/admin/finance/orders/:id`(계약 PDF 칸)로 로컬 연결했다(2026-11-10, 김창재, [API 표](#b-204-1)).

| 경계 | 책임·현재 코드 위치 |
| --- | --- |
| 프런트 앱 | [router.tsx](../LancerPickFrontend/src/app/router.tsx), providers, shared/api·components, features/{auth,products,seller,orders,wallet,settlements,reviews,notifications,order-chat,admin,inquiries,contracts} |
| 백엔드 | [도메인 패키지](../LancerPick/src/main/java/com/example/lancerpick): auth/member/seller/product/order/payment/wallet/settlement/review/admin/inquiry/contract/chat/notification/common |
| HTTP 계약 | 아래 API 명세의 요청·응답·상태 코드·입력 길이·재전송·권한 규칙. 후속 기능별 계약도 [기능 명세](features.md)에 함께 보존 |
| 영속화 | JPA/QueryDSL로 통일. 기존 JdbcTemplate/네이티브 SQL 구현 설명은 당시 이력. Flyway가 DDL 소유, ddl-auto=validate, open-in-view=false, UTC 저장 |
| 외부 연동 | Toss 테스트 충전, Spring Mail, 비공개 PDF 파일 어댑터. 외부 네트워크 호출을 장기 금액 트랜잭션 안에 넣지 않음 |
| 비동기 | MySQL 아웃박스·스케줄러, 커밋 후 채팅 전달·보안 잠금 전달, Batch 충전 복구. Redis/다중 서버는 보류 |

## 인증·권한·계정 경계

[SecurityConfig](../LancerPick/src/main/java/com/example/lancerpick/common/security/SecurityConfig.java)는 세션 인증과 기본 CSRF 보호를 유지한다. GET /api/auth/csrf로 토큰을 받고 변경 요청 헤더에 보낸다. 가입·로그인·비밀번호 복구·이메일 인증도 CSRF 대상이다. 공개 상품/판매자/후기 GET은 허용하고 /api/admin/**는 ADMIN, 나머지는 인증이 필요하다. 주문·계약·금융 데이터는 도메인에서 본인/당사자·현재 DB 권한을 추가 검증한다.

SessionAuthVersionFilter와 auth_version으로 자격 증명 변경·보안 잠금 후 기존 세션을 폐기한다. 로그인·로그아웃·계정 전환 때 프런트 CSRF·개인 Query 캐시·채팅창/미확정 요청 상태를 정리한다. 뒤늦은 응답이 다른 계정 캐시를 채우지 않도록 계정 경계를 유지하며 변경 요청은 새 키로 자동 재전송하지 않는다.

## 거래·동시성·실패 처리

주문 당시 조건은 orders/order_snapshots에 고정한다. 정책 버전은 주문마다 보존하며 LEGACY_V1·CONTRACT_V2·CONTRACT_V3를 한 상태 머신으로 섞지 않는다. 금액 명령은 주문 상태·지갑·결제·보관금·원장·정산/종료·이력·아웃박스가 함께 성공/실패하도록 한다. 회원 ID 순서·회원 공유/배타 잠금·주문/지갑 잠금 순서는 각 명령 계약을 따른다. DB UNIQUE/FK/CHECK와 서비스 검증의 책임은 [DB 설계](database-design.md)에 정리했다.

채팅은 주문별 순번·clientMessageId 멱등 저장, 커밋 뒤 전달, 마지막 순번 이후 조회로 누락을 복구한다. [WebSocket 설정](../LancerPick/src/main/java/com/example/lancerpick/chat/config/ChatWebSocketConfiguration.java)은 /ws, /app, /user, /queue와 단일 서버 simple broker를 사용한다. 세션·인증 버전·당사자 권한을 소켓에서도 검증한다. 종료 주문은 조회만 가능하다.

PDF는 생성 시도 키·문서 버전·해시·짧은 DB 트랜잭션·비공개 저장·당사자 다운로드를 분리한다. 알림은 상태 변경과 아웃박스를 같은 트랜잭션에 넣고 전달 실패를 분리한다. 충전 복구는 기존 payment_key의 제공자 조회 결과를 재확인하며 신규 승인이나 중복 지급을 만들지 않는다.

## 실행 기본값과 환경

현재 main은 #152로 V3 신규 생성 기본값을 true로 변경했다. V2는 false이며 test 프로필은 기존 LEGACY_V1 회귀를 위해 V3를 false로 고정한다. 기존 주문의 policy_version은 바꾸지 않는다. MAIL_ENABLED=false, Batch 시작 시 자동 실행 금지, TOPUP_RECOVERY_ENABLED=false·TOPUP_RECOVERY_SCHEDULING_ENABLED=false다. Toss 일반 실행은 TOSS_TOPUP_ENABLED 기본 true이며 개인 테스트 시크릿이 필요하고, test 프로필은 외부 충전을 비활성화한다. 실제 개인 설정 값은 이번 정리에서 읽거나 변경하지 않았다.

보안 잠금 전달 재시도 로컬 후속은 `SECURITY_LOCK_RETRY_ENABLED=true`(기본), `SECURITY_LOCK_RETRY_DELAY_MS=30000`, `SECURITY_LOCK_RETRY_BATCH_SIZE=50`이다. 전용 스케줄러로 기존 전달 서비스를 호출하며 공통 test 프로필과 demo는 자동 실행하지 않는다. [재시도 계약·제한](features.md#members-sanctions)을 따른다. 신규 테이블이나 Batch Job은 추가하지 않는다.

아래에는 현재 저장소의 실행·개발 환경을 정리했다. 초기 프로젝트 생성 예시는 변경 이력으로 분리했다. [루트 실행 안내](../README.md)와 실제 빌드·설정 파일을 함께 확인하고, 테스트는 TEST_DB_*로 격리한다. SMTP 메모리 큐 유실 등 한계는 [남은 작업](project-progress.md#remaining)에 유지한다. 오래된 PENDING의 UNKNOWN 정리(#166)는 아래 메일 인프라와 기능 명세를 따른다.

## 주제별 목차

- [HTTP API·입력·오류](#api)
- [인증·권한·계정 경계](#security)
- [계층·공통 개발 규칙](#conventions)
- [영속화·트랜잭션](#persistence)
- [이벤트 ID·버전·오류](#events)
- [메일·비동기·복구 경계](#mail)
- [실행·환경·검증 절차](#environment)

<a id="api"></a>
## HTTP API·입력·오류

HTTP 공통 형식과 기본 MVP API를 정리한다. LEGACY_V1·V3 금액/취소·타이머를 섞지 않고 [기능 명세](features.md)의 정책별 최신 계약을 함께 적용한다. 현재 MemberResponse는 displayNameChangeAvailableAt과 emailVerified(2026-11-10)를 포함한다.

### API·입력·오류

#### 랜서픽 MVP API 명세서

채팅 후속 로컬: 당사자 GET/POST `/api/orders/{orderId}/chat/messages`의 UUID 멱등·순번 커서를 유지하고 `/ws` WebSocket/STOMP를 연결했다. `/app/orders/{orderId}/chat/messages` SEND, `/user/queue/chat`·`/user/queue/chat-errors` SUBSCRIBE만 허용한다. 종료 후 조회 전용. [HTTP 요청·응답](features.md#communication-chat), [소켓 계약·CSRF·세션 폐기·복구](features.md#communication-chat). 원격 병합·배포 활성화 여부와 구분한다.

#### 공통 계약

기본 경로 /api. JSON·UTF-8, 세션 쿠키 인증, 변경 요청 CSRF 보호. ID는 JSON 문자열, 금액은 원 단위 정수, 시간은 시간대가 포함된 ISO 8601이다. 비밀번호·토큰·내부 오류를 응답·로그에 노출하지 않는다.

목록은 page=0, size=20, 최대 100. 최신 생성순과 ID 내림차순을 기본으로 하며 categories는 displayOrder·ID 오름차순이다. 목록 응답은 items, page, size, totalElements, totalPages를 가진다.

GET·수정·업무 명령 성공은 200, 리소스 생성은 201, 로그아웃은 204. 안전한 생성 재호출은 200과 기존 결과. 오류는 code, message, fieldErrors(배열), traceId를 반환한다. 채팅 소켓 오류도 같은 필드에 HTTP 상태와 같은 의미의 status를 더해 보낸다([공통 이벤트·오류 계약](architecture.md#events)). 비공개 타인 자원은 404, 관리자 기능 권한 부족은 403으로 처리한다.

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

#### 판매자와 상품 박소현 / 상품 등록·부분 수정 계약

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

#### 주문 조회와 생성 정은찬 / 주문 상세 내부 응답 계약

productSnapshot: title,description,scopeDescription,preparationDescription,productVersion. termsSnapshot은 보존된 거래 조건 문자열이다. category는 id,name, seller·counterparty는 id,displayName을 제공한다.

deliveries 항목: id,sequenceNo,description,resultUrl,submittedAt. revisionRequest: id,deliveryId,reason,requestedAt,dueAt. histories 항목: id,actorId,action,fromStatus,toStatus,reason,createdAt. 최초 이력의 fromStatus와 사유가 없는 행동의 reason은 null이다.

payment: id,amount,feeRateBps,status,paidAt,refundedAt. review는 8절의 Review 계약을 따른다. cancellationRequests는 6절 cancellationRequest 계약의 배열이다.

#### 취소 요청과 재전송 계약

<a id="b-196-1"></a>
| POST 경로 | 실행자·입력 | 결과 |
| --- | --- | --- |
| /orders/{id}/cancellation-requests | 구매자; reason,requestKey 필수 | 201 CancellationResult / 기존 요청 200 |
| /orders/{id}/cancellation-requests/{requestId}/approve | 판매자; 없음 | 200 CancellationResult; 취소·환불 |
| /orders/{id}/cancellation-requests/{requestId}/reject | 판매자; responseReason | 200 CancellationResult |
| /orders/{id}/cancellation-requests/{requestId}/withdraw | 구매자; 없음 | 200 CancellationResult |

CancellationResult: cancellationRequest와 order(OrderDetail). cancellationRequest: id,orderId,requestKey,reason,status,requestedAt,resolvedAt,resolvedBy,responseReason. 상태: PENDING,APPROVED,REJECTED,WITHDRAWN. 기존 요청 응답은 해당 요청 ID·해결 상태와 현재 주문을 함께 반환한다.

취소 요청 생성은 requestKey(UUID)를 필수로 받는다. (order_id, requested_by, request_key)를 고유하게 관리하며 주문 잠금 안에서 기존 키를 먼저 조회한다. 같은 키·같은 사유는 요청이 이미 승인·거절·철회되었어도 해당 기존 요청과 현재 주문을 반환한다. 같은 키·다른 사유는 409 IDEMPOTENCY_KEY_REUSED다. 새로운 요청 키는 사용자가 명시적으로 새 취소 요청을 작성할 때만 생성한다.

입력 사유는 앞뒤 공백을 제거한 저장값으로 비교한다. 동일 키·다른 입력은 거부한다. 새 키라도 대기 요청이 있으면 409 CANCELLATION_PENDING. 이전 요청 해결 후 새 키는 현재 상태가 허용하는 경우에만 새 요청이 된다.

승인·거절·철회는 요청 ID가 해당 주문에 속하는지 확인한다. 동일 해결 동작 반복은 200 기존 결과, 다른 해결 상태로 바꾸려는 요청은 409 REQUEST_ALREADY_RESOLVED. 해결 시각·이력을 덮어쓰지 않는다. 판매자 직접 취소는 대기 요청이 있으면 함께 APPROVED로 종료한다.

#### 결제 재요청과 충돌 응답

<a id="b-197-0"></a>
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

#### 관리자 상품 운영 김창재 / 관리자 상품 목록 계약

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

#### 3 관리자 금융·계약 PDF 운영 조회 김창재

<a id="b-204-1"></a>
| 메서드·경로 | 입력 | 출력 |
| --- | --- | --- |
| GET /admin/finance/topups | memberId,status,page,size | 충전 목록(자동 복구·관리자 재확인 후보 판단 포함) |
| GET /admin/finance/topups/{id} | 없음 | 충전 상세 |
| GET /admin/finance/payments | orderId,buyerId,sellerId,status,policyVersion,page,size | 결제·환불 목록 |
| GET /admin/finance/orders/{orderId} | 없음 | 주문 금액·결제·보관금·정산·종료 분배(없는 기록은 null) |
| GET /admin/finance/recovery-jobs | page,size | 충전 복구 Job 인스턴스별 최신 실행·재시작 후보 판단 |
| GET /admin/finance/recovery-jobs/{executionId} | 없음 | 실행 상세 |
| GET /admin/orders/{orderId}/contract-pdf | version(선택) | 계약 PDF 메타데이터·생성 후보 판단 |
| POST /admin/finance/topups/{id}/recheck | reason(1~1,000자), requestKey(UUID) | 감사 receipt. 새 요청 202, 같은 키 재전송 200(재실행 없음) |
| POST /admin/finance/recovery-jobs/{executionId}/restart | reason, requestKey | 감사 receipt(202/200). 재시작 수락은 Job 완료가 아님 |
| POST /admin/orders/{orderId}/contract-pdf/regenerate | reason, requestKey, version(선택, 생략 시 현재 계약) | 감사 receipt(202/200) |
| GET /admin/action-logs | action, targetId(action과 함께만), orderId, page, size(1~100) | 관리자 작업 감사 이력(요청 ID 내림차순) |

- 충전 status는 READY·PROCESSING·UNKNOWN·COMPLETED·FAILED, 결제 status는 PAID·REFUNDED, policyVersion은 LEGACY_V1·CONTRACT_V2·CONTRACT_V3만(대소문자 그대로). ID 필터는 양수. 잘못된 값 400 `VALIDATION_FAILED`, 없는 대상 404 `RESOURCE_NOT_FOUND`.
- 결제 키·처리권·파일 경로/해시·회원 이메일은 응답에 없다. 후보 판단(eligible·reason)은 조회 순간의 조건일 뿐 실행 권한이 아니다.
- K6 실행 POST(2026-11-10, 김창재)는 CSRF·ADMIN·no-store이고 `AdminReprocessingService` 하나만 호출한다. 본문은 `reason`·`requestKey`(문자열 UUID)와 PDF의 `version`(정수)만 받으며 관리자 ID·결과·금액·결제 키는 받지 않는다. 충전·Batch에 version을 보내거나 형식이 틀리면 400 `VALIDATION_FAILED`. 감사 접수 전 거절은 기존 400/401/403/404/409(`ADMIN_ACTION_IN_PROGRESS`·`ADMIN_ACTION_NOT_ELIGIBLE`·`ADMIN_ACTION_REQUEST_CONFLICT`)이고, 접수 뒤 업무 거절·실패·불명확은 receipt의 status·resultCode로 알린다. PDF는 주문·버전을 기존 PDF 운영 조회로 계약 ID로 바꾼 뒤 실행한다. 결과 코드는 [K6 실행 계약](features.md#admin-reprocessing).
- `GET /admin/action-logs`는 현재 DB 역할이 ADMIN일 때만(강등 세션 403) 관리자가 쓴 사유를 포함한 receipt를 돌려준다. 결제 키·외부 응답·파일 정보·처리권 키·회원 이메일은 없다.

#### 오류와 입력 기준

<a id="b-205-0"></a>
| HTTP | 주요 코드 |
| --- | --- |
| 400 | VALIDATION_FAILED, PRODUCT_PRICE_BELOW_ORDER_MINIMUM, TERMS_AGREEMENT_REQUIRED, INVALID_URL, CURRENT_PASSWORD_INCORRECT, PASSWORD_UNCHANGED, PASSWORD_RESET_TOKEN_INVALID, SIGNUP_EMAIL_TOKEN_INVALID, EMAIL_VERIFICATION_INVALID |
| 401 | AUTHENTICATION_REQUIRED, INVALID_CREDENTIALS |
| 403 / 404 | FORBIDDEN, CSRF_INVALID, MEMBER_RESTRICTED(주문 생성·판매자 수락, U7) / RESOURCE_NOT_FOUND |
| 409 | EMAIL_ALREADY_EXISTS, PRODUCT_VERSION_CONFLICT, PRODUCT_NOT_ORDERABLE, SELF_PURCHASE_NOT_ALLOWED, CONTRACT_MINIMUM_AMOUNT_NOT_MET(V2·V3 계약 주문 생성 시 10,000원 미만) |
| 409 | ORDER_STATE_CONFLICT, INSUFFICIENT_BALANCE, IDEMPOTENCY_KEY_REUSED |
| 409 | CANCELLATION_PENDING, REQUEST_ALREADY_RESOLVED, REVISION_ALREADY_USED, REVIEW_ALREADY_EXISTS, PAYMENT_ALREADY_REFUNDED, DELIVERY_ALREADY_SUBMITTED, DELIVERY_SEQUENCE_CONFLICT |
| 405 / 406 / 415 | METHOD_NOT_ALLOWED(`Allow` 헤더 포함) / NOT_ACCEPTABLE / UNSUPPORTED_MEDIA_TYPE |
| 500 | INTERNAL_ERROR, DATA_INTEGRITY_ERROR |

#### 오류와 입력 기준 / 문자열·필수값 공통 기준

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

#### 오류와 입력 기준 / 비밀번호 원문 정책

<a id="b-207-1"></a>
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

#### 오류와 입력 기준 / 이메일 형식 기준

정규화(앞뒤 공백 제거·Locale.ROOT 소문자화) 후 3~254자를 검사하고 다음 형식을 요구한다. 위반은 400 VALIDATION_FAILED다.

- 로컬 파트: 영문·숫자와 ``!#$%&'*+/=?^_`{|}~-``, 점은 맨 앞·맨 뒤·연속 불가, 최대 64자
- 도메인: 점으로 구분된 라벨 2개 이상. 라벨은 영문·숫자·하이픈 1~63자이며 하이픈으로 시작·끝나지 않는다. 마지막 라벨은 영문 2자 이상
- 거절 예: `review@domain..com`, `.a@b.com`, `a@-b.com`, `a@b`

DB의 이메일 UNIQUE(`uk_members_email`) 위반만 409 EMAIL_ALREADY_EXISTS로 변환한다. 그 밖의 DB 제약 위반은 이메일 중복으로 응답하지 않는다.

#### 오류와 입력 기준 / 남은 구현 전 결정

문자열·비밀번호 원문 정책·상품 기본값·상품 PATCH·관리자 목록 계약은 위 보완 기준을 따른다. 실제 카테고리 목록은 별도 결정한다. 인증·주문·취소·결제의 상세 JSON 예시와 실행 결과는 해당 기능 구현 시 추가하며, 이 문서의 예시를 실행 검증 기록으로 보지 않는다.

주문·취소 요청 키의 중복 처리와 납품 회차·지연 표시의 상세 API 규칙은 이 문서 4~7절을 기준으로 한다. 다른 문서에는 목적과 데이터 제약을 남기고 상세 계약은 이 문서를 참조한다.

### 업무 규칙·연결

#### 인증과 회원 김창재

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
| POST /me/email-verification-requests | 회원+CSRF; 본문 없음 | 202 `{message}`; 기존 회원 이메일 인증 메일(15분 일회용 링크). 이미 인증 409 EMAIL_ALREADY_VERIFIED. [규칙](features.md#existing-email-verification) |
| POST /me/email-verifications | 회원+CSRF; token | 204; 로그인 회원 본인 토큰일 때만 email_verified_at 기록. 그 밖은 400 EMAIL_VERIFICATION_INVALID. 세션·비밀번호 유지 |
| POST /auth/account-guide-requests | 비로그인 가능+CSRF; email | 202 `{message}`; 아이디 안내(가입한 이메일이면 그 주소로 안내 메일, 토큰 없음). 가입 여부·요청 제한·발송 결과와 무관하게 같은 응답. [규칙](features.md#account-guide) |

이메일 인증 가입(2026-11-06 구현): 요청 email은 가입과 같이 정규화·형식 검사하며 형식 오류만 400 VALIDATION_FAILED다. 그 밖에는 이미 가입된 이메일·이메일+목적당 60초 간격·1시간 5회 제한 초과·발송 실패 모두 같은 202다(이미 가입된 주소에도 인증 메일을 보낸다). 메일 링크 `/signup/verify#token=`의 token으로 인증하며 위조·만료(15분)·이미 인증·목적 다름은 400 SIGNUP_EMAIL_TOKEN_INVALID다. 인증하면 링크 토큰은 무효가 되고 가입 증명 proof(일회용, 인증 시점부터 30분)를 받는다. `alreadyRegistered`는 메일을 받은 본인이 인증한 뒤에만 알려 준다. 가입 시 증명 없음은 400 VALIDATION_FAILED, 위조·만료·사용됨·다른 이메일·인증 전 링크 토큰·재설정 토큰은 400 EMAIL_VERIFICATION_INVALID, 이미 가입된 이메일은 409 EMAIL_ALREADY_EXISTS다. 증명 없이 가입하는 경로는 없다. 상세는 [로그인 문서](features.md#b-234-0).

비밀번호 재설정(2026-11-05 구현): 요청 email은 가입과 같이 정규화·형식 검사하며 형식 오류만 400 VALIDATION_FAILED다. 그 밖에는 모두 같은 202·본문·`no-store`다(가입 안 된 이메일, 이메일+목적당 60초 간격·1시간 5회 제한 초과, 메일 발송 실패 포함). 재설정 token은 메일 링크 `#token=`의 값이며, 위조·만료(15분)·이미 사용·발급 후 비밀번호 변경은 모두 400 PASSWORD_RESET_TOKEN_INVALID다. newPassword는 10절 비밀번호 원문 정책(위반 400 VALIDATION_FAILED, field=newPassword)이고 현재 비밀번호와 같아도 거절하지 않는다. 상세는 [로그인 문서](features.md#b-225-0).

#### 판매자와 상품 박소현

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

SellerProfile: id(회원 ID),displayName,introduction,specialty,portfolioUrl. 이메일·잔액은 공개하지 않는다. ProductInput: categoryId,title,description,scopeDescription,preparationDescription,priceAmount,durationDays. 가격 1~1,000,000원, 기간 1~30일. 판매 중 상품(등록, ON_SALE 상품 수정, ON_SALE으로 재개)의 가격이 현재 신규 주문 최소 금액(`minimumOrderAmount`, CONTRACT_V3면 10,000원) 미만이면 400 `PRODUCT_PRICE_BELOW_ORDER_MINIMUM`이고 아무것도 바꾸지 않는다. 판매 중지 상품은 가격을 올리기 전에도 수정할 수 있다. 공개 상품 목록은 최소 금액 미만 상품을 빼고, 공개 상세는 그대로 보여 주며 `minimumOrderAmount`로 주문 불가를 안내한다. portfolioUrl은 선택이다.

판매자 공개 화면은 `GET /sellers/{id}`로 소개를 조회하고 `GET /products?sellerId={id}`로 같은 판매자의 상품을 조회한다. `sellerId`는 선택이며 양의 BIGINT 십진 문자열을 받는다. 형식이나 범위가 잘못되면 400 `VALIDATION_FAILED`를 반환한다. 집계와 목록에는 같은 판매자 조건을 적용하며 숨김·판매 중지 상품은 제외한다.

상품 목록의 선택 조건은 검색어·카테고리와 함께 적용한다. `minPrice`와 `maxPrice`는 각 1~1,000,000원이며 경곗값을 포함한다. `maxDurationDays`는 1~30일이며 해당 일수 이하를 조회한다. 숫자는 1~7자리 십진 정수로 받는다. 빈 값·범위 초과·최저가가 최고가보다 큰 경우·지원하지 않는 정렬은 400 `VALIDATION_FAILED`다. `sort`는 `NEWEST`(기본, 등록 시각 내림차순), `PRICE_ASC`, `PRICE_DESC`를 지원하고 주 정렬 값이 같으면 상품 ID 내림차순이다.

ProductSummary: id,title,priceAmount,durationDays,category,seller. ProductDetail은 description,scopeDescription,preparationDescription,version,saleStatus,createdAt,updatedAt을 추가한다. 본인·관리자 조회는 isHidden 추가. 상품 변경·판매 상태 변경·숨김 변경은 버전 충돌 검사와 일관되게 관리한다. 숨김·중지 상품의 기존 거래는 주문 스냅샷으로 조회한다.

#### 주문 조회와 생성 정은찬

<a id="b-193-1"></a>
| 메서드·경로 | 입력·권한 | 출력 |
| --- | --- | --- |
| GET /orders | 회원; side=BUYER 또는 SELLER 필수, status·q·from·to·sort 선택, page,size | OrderSummary 목록 |
| POST /orders | 구매자; productId,expectedProductVersion,requirements,requestKey | 201 OrderDetail / 중복 200. 구매자·판매자 중 하나라도 일반 이용 제한·보안 잠금이면 신규 요청은 403 MEMBER_RESTRICTED(같은 키 재전송은 기존 주문 200) |
| GET /orders/{id} | 주문 참여자 | OrderDetail |

목록 검색 조건(U3): `q`는 앞뒤 공백을 지운 뒤 비면 무시하고 최대 100자(코드 포인트)다. ASCII 숫자만이면 주문번호 정확 일치, 그 밖에는 저장된 상품명·상대방 현재 표시 이름 부분 일치(`%`·`_`는 글자 그대로)다. `from`·`to`는 한국 날짜 `yyyy-MM-dd`이고 주문 시각을 [from 00:00 KST, to+1일 00:00 KST)로 거른다. `sort`는 `latest`(기본, 최신 주문 먼저)·`oldest`. 형식 오류·없는 날짜·from>to·다른 sort 값은 400 VALIDATION_FAILED. 건수와 항목에 같은 조건을 적용한다. 상세는 [주문 조회](features.md#b-735-0).

생성 시 상품 상태·버전·자기 상품 구매 금지를 검증한다. 가격·판매자·거래 조건은 서버에서 복사한다. 같은 구매자·요청 키·입력은 기존 주문 반환, 같은 키·다른 입력은 409 IDEMPOTENCY_KEY_REUSED. 기존 키 확인을 현재 상품 조건 검사보다 먼저 수행하여 성공한 주문의 재전송을 새 주문으로 처리하지 않는다.

OrderSummary: id,status,productTitle,priceAmount,counterparty,createdAt,currentDueAt. OrderDetail: id,status,buyer,seller,productId,productSnapshot,termsSnapshot,priceAmount,durationDays,requirements,createdAt,acceptedAt,buyerConfirmedTermsAt,paidAt,originalDueAt,revisionDueAt,currentDueAt,completedAt,cancelledAt,deliveries,revisionRequest,cancellationRequests,histories,payment,review,allowedActions. 판매자 수익은 본인 정산 API에서 조회한다.

저장 구조 분리: 상품명·상품 버전·설명·범위·준비사항·거래 규칙·요구사항은 `order_snapshots`에서 조회한다. `priceAmount`와 `durationDays`는 `orders.price_amount`, `orders.duration_days_snapshot`에서 조회한다. 주문과 스냅샷은 함께 생성하며 요청·응답 필드 및 기존 재전송 계약은 변경하지 않는다. 기존 키의 입력 비교도 최초 저장된 스냅샷을 사용한다.

buyer·seller는 id·displayName만 제공한다. originalDueAt=orders.due_at, revisionDueAt=revision_requests.due_at이다. currentDueAt은 수정 요청이 있으면 수정 기한, 없으면 최초 기한이며 결제 전에는 null이다. 별도 DB 컬럼을 추가하지 않는다. 종료 거래에 지연 경고를 표시하지 않는다.

주문 상태: REQUESTED, AWAITING_PAYMENT, IN_PROGRESS, AWAITING_CONFIRMATION, REVISION_IN_PROGRESS, COMPLETED, REJECTED, CANCELLED. 허용 행동: ACCEPT, REJECT, PAY, CANCEL, DELIVER, REQUEST_REVISION, CONFIRM, REQUEST_CANCELLATION, APPROVE_CANCELLATION, REJECT_CANCELLATION, WITHDRAW_CANCELLATION, WRITE_REVIEW.

#### 주문 명령 정은찬과 황준범

<a id="b-195-1"></a>
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

#### 1 주문 알림 정은찬

| 메서드·경로 | 권한·입력 | 출력 |
| --- | --- | --- |
| GET /notifications | 회원; page 0 이상, size 1~100(기본 20), unreadOnly `true`/`false`(기본 false) | Notification 목록, 생성 시각·ID 내림차순. unreadOnly=true면 읽지 않은 본인 알림만 같은 순서로, totalElements·totalPages도 같은 조건. 불리언이 아니면 400 VALIDATION_FAILED |
| GET /notifications/unread-count | 회원 | count |
| POST /notifications/{id}/read | 본인+CSRF; 양수 알림 ID | 204. 이미 읽음도 204, 최초 읽은 시각 유지. 타인·없음 404, 형식 오류 400 |
| POST /notifications/read-all | 회원+CSRF; 본문 없음 | 204. 요청 시점까지 생성된 본인 알림만 읽음 처리 |

Notification: id,type,orderId,productTitle,read,createdAt. type은 `ORDER_` + 주문 이력 행동(CREATED·ACCEPTED·REJECTED·PAID·DELIVERED·REVISION_REQUESTED·CANCELLATION_REQUESTED·CANCELLATION_REJECTED·CANCELLATION_WITHDRAWN·CANCELLED·COMPLETED)이다. CONTRACT_V3 주문은 PAID·구매자 COMPLETED·결제 후 CANCELLED를 각각 `ORDER_FUNDS_HELD`·`ORDER_FUNDS_RELEASED`·`ORDER_FUNDS_REFUNDED`로 보내고, 시스템 확정 `ORDER_AUTO_CONFIRMED`, 기한 통지 `ORDER_DELIVERY_DUE_NOTIFIED`·`ORDER_REDELIVERY_DUE_NOTIFIED`(양측), 만료 24시간 전 `ORDER_AUTO_CONFIRM_SOON`(구매자), 타이머 `ORDER_TIMER_STARTED`·`ORDER_TIMER_ADMIN_STOPPED`, 운영 문의를 검토한 관리자의 취소 요청 거절 `ORDER_CANCELLATION_ADMIN_REJECTED`(양측, [관리자 취소 검토](features.md#orders-cancel))가 있다([V3 알림](features.md#communication-timer)). 주문과 무관한 회원 알림 `PASSWORD_CHANGE_RECOMMENDED`(비밀번호 권고 기준일부터 6개월마다, 로그인 때 본인에게, [권고 알림](features.md#password-recommendation))는 orderId·productTitle이 null이다. 문구는 화면이 type으로 정하고, 모르는 type은 일반 문구로 표시한다. productTitle은 주문 시점 스냅샷이다. 조회 응답은 `Cache-Control: no-store`다.

#### 지갑·정산 담당 황준범, 후기 담당 박소현

<a id="b-199-2"></a>
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

#### 관리자 상품 운영 김창재

<a id="b-200-0"></a>
| 메서드·경로 | 입력 | 출력 |
| --- | --- | --- |
| GET /admin/products | keyword,isHidden,saleStatus,page,size | AdminProductSummary 목록 |
| GET /admin/products/{id} | 없음 | 상품 상세 |
| GET /admin/products/{id}/moderation-logs | page,size | id,adminId,action,reason,createdAt 목록 |
| PATCH /admin/products/{id}/visibility | hidden,reason,expectedVersion | 관리자 ProductDetail |

모든 경로는 관리자 전용이다. 숨김 상태와 이력을 함께 저장하며 판매 상태는 변경하지 않는다. 관리자에게 거래·지갑·강제 환불 API를 제공하지 않는다.

관리자 상품 상세는 본인 조회와 같은 ProductDetail 필드에 isHidden을 포함한다. 숨김·복구는 상품 잠금 후 현재 상태를 확인한다. 이미 요청한 hidden 상태라면 버전 차이와 관계없이 현재 상세를 반환하고 버전·운영 이력을 변경하지 않는다. 실제 상태 변경이 필요하면 expectedVersion을 검사하고, 불일치는 409 PRODUCT_VERSION_CONFLICT로 처리한다. 변경 성공 시 상품 version 증가와 숨김 상태·운영 이력을 같은 트랜잭션에서 반영한다. 판매자 상품 수정·판매 상태 변경도 이 version과 일관되게 처리한다.

#### 1 운영 문의·관리자 취소 검토 김창재

<a id="b-202-1"></a>
| 메서드·경로 | 입력 | 출력 |
| --- | --- | --- |
| POST /orders/{orderId}/cancellation-requests/{requestId}/review-inquiries | subject | 201 새 문의, 200 같은 요청의 열린 문의(재전송) |
| GET /me/inquiries | orderId(선택),page,size | 내 문의 목록 |
| GET /admin/inquiries | status,type,page,size | 관리자 문의 목록(신청자 이름 포함) |
| GET /admin/inquiries/{id} | 없음 | 문의 상세 + 주문 요약 + 취소 요청 + allowedActions |
| POST /admin/inquiries/{id}/approve | reason | AdminCancellationReviewResponse |
| POST /admin/inquiries/{id}/reject | reason | AdminCancellationReviewResponse |
| POST /admin/inquiries/{id}/close | 없음 | 문의 상세 |

#### 2 관리자 자동 구매 확정 타이머 해제 김창재

<a id="b-203-1"></a>
| 메서드·경로 | 입력 | 출력 |
| --- | --- | --- |
| GET /admin/review-timers | page,size | 진행 중(ACTIVE) 타이머 목록: timerId,orderId,cycleNo,startedAt,expiresAt,buyer,seller |
| POST /admin/orders/{orderId}/review-timer/stop | reason | ReviewTimerResponse(해제 후 status `ADMIN_STOPPED`) |

- 목록은 만료 시각 오름차순(먼저 자동 확정될 주문부터), 같은 시각은 timerId 오름차순. 현재 DB 역할이 ADMIN이 아니면 403.
- 해제는 CSRF 필수·no-store. 오류는 서비스 그대로: 사유 공백 정리 후 1~1000자 아님 400 `VALIDATION_FAILED`, 관리자 아님 403 `FORBIDDEN`, V3 주문이 아니거나 없음 404, 활성 타이머 없음 409 `TIMER_NOT_ACTIVE`, 만료 시각이 지남 409 `AUTO_CONFIRM_DUE`(자동 확정 우선). 해제된 기산점으로는 판매자가 다시 시작할 수 없다.

<a id="security"></a>
## 인증·권한·계정 경계

### API·입력·오류

#### 인증·입력·오류

보호 Controller는 CurrentMemberId.require(authentication)로 회원 ID를 얻는다. 클라이언트 회원 ID를 권한 근거로 사용하지 않는다. 로그인 principal은 AuthenticatedMember 계약을 구현한다. 관리자 경로는 Security에서 ADMIN을 확인하고 변경 서비스는 현재 DB 권한도 확인한다.

DTO는 형식, 서비스는 범위·정규화·소유권·상태를 검증한다. 공통 BusinessException·ApiExceptionHandler·SecurityErrorWriter의 code·message·fieldErrors·traceId 형식을 사용한다. 비밀번호·세션·토스 시크릿·요청 본문을 로그로 남기지 않는다.

### 업무 규칙·연결

#### 인증과 권한

Spring Security 기반 세션 인증을 사용한다.

배포 환경의 세션 쿠키에 HttpOnly·Secure와 적절한 SameSite 설정을 적용한다.

React는 사용자 정보 조회 API로 로그인 상태를 확인한다.

CSRF 보호를 유지하고, 공통 요청 코드에서 토큰을 전달한다.

로그인·로그아웃 후 CSRF 토큰 갱신도 처리한다.

개발 환경은 Vite의 /api 프록시, 배포 환경은 같은 도메인의 화면·API 구성을 사용한다.

화면 접근 안내와 별개로 서버에서 모든 보호 API의 권한을 검증한다.

주문 참여자·상품 소유자·리뷰 작성 자격 등 대상별 권한을 확인한다.

관리자 권한은 일반 사용자가 요청값으로 선택하거나 변경할 수 없다.

비밀번호는 Spring Security의 비밀번호 인코더로 해시 처리한다.

<a id="conventions"></a>
## 계층·공통 개발 규칙

### 업무 규칙·연결

#### 패키지와 책임

com.example.lancerpick 아래 auth·member·seller·product·order·wallet·payment·settlement·admin 등 기능별 패키지를 사용한다. 기능 안에서 controller·dto·service·repository·entity를 구분한다. 외부 제공자 통신은 wallet/gateway에 둔다.

Controller는 입력·인증 정보 수신과 응답 상태를 처리한다. Service는 업무 규칙·권한·트랜잭션을 관리한다. Repository는 데이터 접근·잠금을 담당한다. Entity는 저장 상태와 상태 변경 규칙을 표현한다. API 응답은 dto에 정의하고 Entity나 Service 내부 응답 타입을 직접 노출하지 않는다.

정산 조회는 settlement/service/SettlementQueryService와 settlement/dto의 SettlementResponse·SettlementSummaryResponse를 사용한다. 내부 처리 결과 record와 Repository 조회 projection은 API 응답 DTO와 역할이 다르다.

주문 채팅도 `chat/controller`·`service`·`dto`·`repository`·`entity`를 구분한다. 소켓 설정은 `chat/config`, 커밋 이벤트는 `chat/event`, handshake·프레임/세션 보안은 `chat/realtime`에 둔다. HTTP·소켓 Controller 모두 `OrderChatService`의 같은 저장·권한 검증을 재사용한다. [실시간 연결 계약](features.md#communication-chat)을 따른다.

#### 데이터 접근·변경

Entity 저장·변경은 JPA, 검색·조인·집계는 QueryDSL을 사용한다. 운영 코드에 JdbcTemplate·직접 JDBC·네이티브 SQL을 추가하지 않는다. Flyway와 테스트 fixture·DB 원문 검증 SQL은 별도로 관리한다. 지갑 최초 생성의 기존 HQL 충돌 처리와 useAffectedRows 설정은 함께 유지한다.

J5의 Spring Batch JDBC 저장소는 프레임워크 실행 메타데이터(`BATCH_*`) 전용이다. 업무 Repository는 JPA/QueryDSL을 유지하며 시스템 테이블을 업무 엔티티로 재구현하지 않는다. [충전 복구 경계](features.md#finance-batch)를 따른다. `wallet/config`는 Job·설정 값·전용 스케줄러 구성, `wallet/batch`는 Tasklet, `wallet/service`는 복구 조율·짧은 지급 트랜잭션을 담당한다.

기존에 적용한 Flyway 마이그레이션을 수정하지 않고 후속 버전을 추가한다. 서버 시작과 검사에서 ddl-auto=validate로 매핑을 확인한다. ID는 JSON 문자열, 금액은 정수 원, API 시각은 UTC ISO 8601 기준이다. MySQL DATETIME과 LocalDateTime의 UTC 보관 규칙을 유지한다. 저장용 현재 시각은 `common/time/UtcTimes.nowMicros(clock)`(UTC, 마이크로초 절삭)으로 구하며, 잠금 뒤 시각이 필요한 곳은 잠금 뒤에 호출한다. 요청 키·클라이언트 메시지 ID 같은 UUID 문자열은 `common/text/UuidText.parse`(8-4-4-4-12 16진수만 허용)로 검사하고, 오류 코드·문구는 호출자가 정한다. 목록 요청 범위(page 0 이상, size 1~100)는 `PageResponse.isValidRequest`로 판정하고(문구·다른 조건과의 순서는 서비스가 유지), 올림 페이지 수는 `PageResponse.totalPages`(int 초과 시 예외)를 쓴다. 관리자 서비스의 DB 권한 재검사는 `admin/service/AdminAuthorization`으로 한다: 조회는 잠금 없는 `requireAdmin`, 감사 요청·재처리 실행처럼 회원 → 감사 기록 → 대상 순서로 잠그는 경로는 `lockAdmin`(회원 공유 잠금, 기존 트랜잭션 필수). 주문 잠금 순서에 들어가는 관리자 경로(문의 닫기·관리자 계약 PDF)는 주문 저장소의 실행 회원 잠금을 그대로 쓴다.

#### 랜서픽 공통 개발 규칙 v1.0

역할 배정 갱신 2026-10-29: [확정 담당·지원 원칙·업무 비중](team-roles.md#b-1131-0)을 따른다. 상세 일정·검토자·배포 담당은 별도 확정한다.

#### 목적과 적용 원칙

이 문서는 4명이 일관된 방식으로 MVP를 구현·통합·배포하고, 이후 필요한 기능을 점진적으로 확장하기 위한 개발 기준이다.

MVP 기능과 운영 정책은 MVP 명세서를 따른다.

공통 개발 규칙을 이유로 기능 범위를 추가하지 않는다.

하나의 저장소에서 React 프런트엔드와 Spring Boot 백엔드를 관리한다.

백엔드는 하나의 애플리케이션으로 운영하고 기능별로 코드를 구분한다.

미래 기능을 위한 빈 테이블·범용 구조·외부 연동을 미리 구현하지 않는다.

규칙을 변경하면 관련 담당자와 검토하고 변경 이유를 기록한다.

#### 담당과 공동 개발

| 구분 | 책임 |
| --- | --- |
| React 공통 기반 | 황준범 주담당, 기능별 화면·연결은 각 기능 담당자가 개발 |
| 공통 UI | 황준범이 초기 예제를 제공하고 기능 담당자가 보완, 정은찬이 통합 지원 |
| 기능 개발 | 각 기능 담당자가 화면·API·DB 변경·검증의 완료 책임을 가짐 |
| 코드 검토 | 기본 검토자가 확인하고, 다른 기능에 영향이 있으면 해당 담당자도 참여 |
| 일정·통합 조율 | 정은찬이 취합하고 전원이 문제 해결에 참여 |

주담당이 모든 코드를 혼자 작성해야 한다는 의미는 아니다. 공동 구현과 지원이 가능하다.

공동 구현 시 작업 범위와 기여를 PR·작업 기록에 남긴다.

공통 개발·지원·검토·조율 시간도 개인 작업량에 포함한다.

첫 통합과 고도화 단계 전환 때 실제 부담을 기준으로 업무를 조정한다.

각자 본인이 설명할 수 있는 핵심 구현과 검증 결과를 확보한다.

#### React 개발

| 항목 | 기준 |
| --- | --- |
| 화면 이동 | React Router 사용. 직접 주소 접속·새로고침 지원 |
| 서버 데이터 | TanStack Query로 조회·갱신 관리 |
| 화면 내부 상태 | 입력값·모달 등은 useState, 필요한 공통 상태만 Context 사용 |
| API 요청 | 공통 fetch 함수로 인증·CSRF·오류 처리를 통일 |
| 타입 | 요청·응답·컴포넌트 입력 타입 정의. 불필요한 any 사용 지양 |
| 공통 UI | 레이아웃·버튼·입력·로딩·빈 결과·오류 표시 공유 |
| 변경 후 갱신 | 결제·취소·완료 등 성공 후 영향을 받은 데이터를 다시 조회 |
| 중복 클릭 | 처리 중 버튼 비활성화. 서버의 중복 처리 방어도 필수 |

서버 데이터를 별도의 전역 상태에 중복 저장하지 않는다.

로그인·로그아웃·계정 전환 시 이전 사용자의 관련 캐시를 정리한다.

금액·권한·거래 가능 여부는 서버가 최종 판단한다.

주문 상세 API의 허용 행동 목록을 기준으로 버튼을 표시한다.

서버는 버튼 표시 여부와 관계없이 실제 요청의 권한·상태를 다시 검증한다.

통신 오류로 처리 결과가 불명확하면 실패를 단정하지 않고 최신 상태를 확인한다.

결제·환불 등 변경 요청을 무조건 자동 재시도하지 않는다.

<a id="ui-consistency"></a>
#### 화면 문구·스타일 일관성 (2026-11-11 기준안)

2026-11-11 사용자·관리자 화면 전수 점검 결과 관리자 화면(해요체·제목 영역·회색 필터 상자·`AdminPager`·주 버튼 40px/8px)이 일관되어 이를 기준으로 삼는다. 2026-11-11 `feature/ui-consistency-all`(#200)에서 사용자·관리자 화면 문구 해요체 전환, 마이페이지 하위 화면 제목 영역(`shared/components/PageHeading`), 사용자 목록 페이지 이동(`shared/components/Pager`), 관리자 버튼 높이를 이 기준에 맞췄다. 이어 #201에서 흰 바탕 보조 글자색을 `--muted`로 맞추고 남은 "~하세요" 설명 문구와 계약 패널 버튼 규격을 정리했다. 이어 #202(`refactor/ui-heading-buttons`)에서 거래 내역·알림·판매 수익·내 상품·상품 등록 제목을 `PageHeading`(제목 옆 링크는 `actions`)으로 바꾸고, 관리자 버튼을 `.page button` 대신 `.button`/`.button.secondary` 클래스로 옮기고, 상태 색 토큰(`--danger`·`--success`·`--warning`과 각 `-surface`)을 `index.css`에 두었다. 이어 `fix/ui-disabled-opacity`에서 버튼 비활성 투명도를 `--disabled-opacity`(.4) 토큰 하나로 맞췄다. 남은 차이: 글자 크기·모서리·중단점 값은 화면별로 다르며, 서버 오류 `message` 말투는 2026-11-11 Codex 검토 후 현행 예외로 유지하기로 했다(프런트 code별 문구 매핑은 같은 `VALIDATION_FAILED`에 서로 다른 상세 안내가 있어 기각, 일괄 해요체 전환은 실제 응답으로 나가는 `BusinessException`·인증 오류만 대상으로 담당별 검토를 묶은 별도 작업. 검증 오류는 `ApiExceptionHandler`가 이미 해요체 고정 문구로 바꾼다). 홈 소개 문구("결과까지 함께하세요")는 홍보 문구로 남겨 두었다.

| 항목 | 기준 |
| --- | --- |
| 말투 | 사용자에게 보이는 화면 문구는 해요체(`~해요`, `~해 주세요`). 계약서 본문·법적 고지·서버가 내려준 오류 `message` 원문은 예외로 그대로 보여 준다(서버 문구 통일은 백엔드 담당과 별도 결정) |
| 불러오기 안내 | "○○을 불러오는 중이에요." / "○○을 불러오지 못했어요." + 보조 버튼 "다시 조회". 로그인 필요·오류·빈 결과를 서로 다른 문구로 구분 |
| 제목 영역 | 사용자 목록·관리 화면은 영문 소제목(10px) + 제목(28px, 좁은 화면 24px) + 회색 설명(13px) + 하단 2px 선(`PageHeading` 수치). 관리자 화면은 현행 예외로 소제목 11px + 제목 `clamp(26px, 4vw, 34px)` + 설명 14px를 쓴다. 회색 글자는 흰 바탕 대비 4.5:1 이상인 `--muted`(#707070)를 쓰고 #888보다 옅은 색은 placeholder·장식에만 쓴다. 오류·완료·주의 안내는 `--danger`/`--success`/`--warning`(바탕은 `-surface`) 토큰을 쓴다. 마이페이지 안 화면은 레이아웃이 이미 "홈·MY ACCOUNT"를 보여 주므로 경로 표시·"← 마이페이지" 링크를 중복해 두지 않는다 |
| 버튼 역할 | 주 버튼(검정, 화면당 핵심 행동 하나: 조회·저장·결제 등) / 보조 버튼(흰 바탕·회색 테두리: 초기화·다시 조회·모두 읽음·페이지 이동) / 선택 칩(`aria-pressed`로 선택 표시: 전체·읽지 않음 등). 높이 40px 이상·모서리 8px(공통 `Button`은 44px), hover는 옅은 회색, disabled는 `--disabled-opacity`(0.4)(예외: 관리자 보조 버튼·알림 행 본문은 투명도 1에 색으로 구분), `:focus-visible` 2px 윤곽선. 화면 CSS에서 `.page button`처럼 넓은 선택자로 스타일을 주지 않고 버튼마다 클래스를 붙인다(목록 행 버튼에 번지지 않게) |
| 페이지 이동 | 사용자 화면은 `Pager`, 관리자 화면은 `AdminPager`. `page > 0 || totalPages > 1`일 때만 표시한다(첫 페이지·1페이지 이하면 숨김). 사용자 `Pager`는 "이전 · N페이지 / 총 M페이지 · 다음"이고, 주소의 page가 전체 페이지를 넘으면 페이지 번호 대신 "첫 페이지로"만 보여 준다(화면별 별도 복구 버튼 없음). 헤더 알림 패널·채팅은 좁은 영역용 자체 구현을 유지한다 |
| 빈 결과 | 실선 상자 안 가운데 정렬 문구(필요하면 조건 변경 안내 한 줄) |
| 관리자 상태 배지 | 같은 칸의 상태 값은 모두 같은 배지 모양(`admin/components/StatusBadge`)으로 보여 주고 색만 뜻에 따라 바꾼다(한 값은 보통 글자, 다른 값은 배지로 섞지 않는다). 문맥별 코드→톤 표는 `admin/statusTone.ts`. 초록(ok)=성공적으로 끝난 처리(결제 완료·충전 완료·지급 완료·서명 완료·구매 확정·문의 처리 완료·판매 중), 기본(neutral)=대기·진행·되돌림·기록상 종료와 표에 없는 코드(환불·보관 중·만료·철회·판매 중지·공개·일반 회원·제한 없음, 접수만 뜻할 수 있는 재처리 "처리됨"), 주의(warn)=관리자 확인·조치 필요 또는 제한 적용(결과 확인 필요·처리 중 충전·검토 대기·제재 적용 중·일반 이용 제한·재시도 대기), 위험(danger)=실패·중단·차단(충전·작업·PDF 실패, 처리 중단, 보안 잠금), 검정(strong)=역할·노출 통제 강조(관리자·숨김)만. 주문 상태는 배지가 아닌 글자로 둔다 |

#### Git 협업과 코드 검토 / 11.1 기본 원칙

랜서픽은 하나의 GitHub 저장소에서 React 프런트엔드와 Spring Boot 백엔드를 함께 관리한다.

모든 기능 개발, 오류 수정, 설정 변경은 작업 브랜치에서 수행하고 Pull Request(PR)를 통해 main에 통합한다.

main은 팀의 통합 브랜치로 사용하며 직접 Push하지 않는다.

작업은 가능한 한 작은 기능·수정 단위로 나누되, 화면·API·DB가 함께 변경되어야 검증할 수 있는 기능은 하나의 PR로 구성할 수 있다.

기능 담당자는 화면·API·DB 변경·검증의 완료 책임을 가진다. 공동 구현과 지원은 가능하며 실제 기여 범위를 PR 또는 작업 기록에 남긴다.

#### Git 협업과 코드 검토 / 11.2 브랜치 운영

main은 통합 브랜치로 사용한다.

기능 개발과 오류 수정은 별도 작업 브랜치에서 진행한다.

새로운 작업 브랜치는 최신 main을 기준으로 생성한다.

하나의 작업 브랜치는 명확한 작업 목적을 가진다.

병합 완료 후 다음 작업은 최신 main에서 새로운 브랜치를 생성한다.

공유 중인 브랜치의 이력을 팀원 동의 없이 재작성하거나 강제 Push하지 않는다.

브랜치 이름의 세부 형식은 별도 Git 협업 가이드에서 관리한다.

#### Git 협업과 코드 검토 / 11.3 Commit 및 Push

Commit은 작업 목적을 이해할 수 있는 단위로 작성한다.

Commit 전에 변경 파일과 Diff를 검토하고, 비밀번호·인증 토큰·API 키·실제 환경변수 파일 등 비밀정보가 포함되지 않았는지 확인한다.

Gradle Wrapper, npm 잠금 파일, Flyway 마이그레이션 파일 등 실행과 변경 이력 재현에 필요한 파일은 적절하게 버전 관리한다.

Commit 메시지의 형식과 예시는 별도 Git 협업 가이드에서 관리한다.

#### Git 협업과 코드 검토 / 11.4 Pull Request

모든 main 병합은 PR을 통해 수행한다.

PR에는 다음 내용을 기록한다.

변경 목적

주요 변경 사항

영향 범위

실행한 검증과 결과

관련 문서·API·DB 변경

알려진 제한과 미완료 사항

API·DB·공통 코드 변경은 관련 담당자에게 공유하고 필요한 검토를 요청한다.

완료되지 않은 작업에 대한 사전 검토가 필요하면 Draft PR을 사용할 수 있다. Draft PR은 병합하지 않는다.

#### Git 협업과 코드 검토 / 11.5 코드 리뷰

PR 작성자 외 최소 1명의 승인을 받아야 병합할 수 있다.

기본 검토자는 기능 담당과 작업 범위를 고려하여 지정한다. 다른 기능에 영향이 있으면 관련 담당자도 검토에 참여한다.

검토자는 다음 항목을 우선 확인한다.

요구사항 및 업무 규칙 충족 여부

인증과 접근 권한

거래 상태 변경의 유효성

금액 계산과 데이터 무결성

중복·동시 요청 처리

오류 및 실패 시 복구

API·DB·공통 코드 변경의 영향

관련 테스트와 검증 결과

필수 수정 의견이 해결되지 않은 PR은 병합하지 않는다.

코드 리뷰와 공동 구현·지원 기여는 작업 기록에 남긴다.

#### Git 협업과 코드 검토 / 11.6 CI 및 병합 조건

다음 조건을 모두 만족한 경우에만 병합한다.

작성자 외 최소 1명 승인

필수 CI 검사 성공

미해결 필수 수정 의견 없음

관련 문서 및 DB 변경 반영

알려진 제한과 미완료 사항 기록

필수 CI 검사에 실패한 PR은 병합하지 않는다.

CI 검사 항목과 테스트 기준은 12장 ‘테스트와 완료 기준’을 따른다.

PR 병합은 Squash merge를 사용한다.

Squash merge로 통합된 작업은 다음 작업에서 새 브랜치로 진행한다.

#### Git 협업과 코드 검토 / 11.7 충돌 및 공통 변경 관리

다른 담당자의 변경 사항을 임의로 덮어쓰지 않는다.

Merge Conflict가 발생하면 양쪽 변경의 목적을 확인하고 필요한 코드를 보존하여 해결한다.

충돌 해결 후 영향을 받은 기능과 관련 테스트를 다시 검증한다.

API·DB·공통 코드 충돌은 관련 담당자와 해결 결과를 확인한다.

#### Git 협업과 코드 검토 / 11.8 작업 완료 및 브랜치 정리

PR 병합 후 로컬 main을 최신화한다.

병합된 작업 브랜치는 작업 상태를 확인한 뒤 정리할 수 있다.

문제 수정이 필요한 경우 기존 병합 이력을 무단으로 변경하지 않고 새로운 작업 브랜치와 PR로 처리한다.

#### Git 협업과 코드 검토 / 11.9 보안 및 예외 처리

비밀정보를 GitHub에 Push한 경우 해당 정보를 즉시 폐기·재발급하고 팀에 공유한다. 파일을 삭제하는 것만으로 Git 이력의 노출이 해결되었다고 판단하지 않는다.

긴급 오류 수정도 원칙적으로 PR 승인과 필수 CI 검사를 거친다.

CI 실패 시 병합 금지 정책을 임의로 우회하지 않는다. 향후 별도 예외 절차가 필요하면 팀 합의와 문서 개정을 먼저 진행한다.

#### Git 협업과 코드 검토 / 11.10 운영 규칙 변경

Git 협업 규칙을 변경할 때는 관련 담당자와 검토하고 변경 이유를 기록한다.

GitHub Ruleset, PR 템플릿, CI 설정은 본 문서의 운영 규칙과 일치하도록 관리한다.

#### 테스트와 완료 기준 / 기능 완료 조건

화면·API·DB가 연결된다.

관련 문서·DB 변경 파일을 반영한다.

알려진 제한과 남은 문제를 기록한다.

CI에서는 프런트 타입 검사·빌드, 백엔드 빌드·관련 테스트를 실행한다.

#### 테스트와 완료 기준 / CI 자동 검사 운영 기준

GitHub Actions를 이용하여 main 대상 Pull Request와 main 브랜치 Push 시 자동 검사를 실행한다.

필수 CI 검사는 다음 항목으로 구성한다.

프런트엔드 의존성 설치 및 TypeScript 타입 검사

프런트엔드 프로덕션 빌드

백엔드 Gradle 빌드 및 관련 테스트

DB를 사용하는 테스트의 격리된 MySQL 환경 구성 및 Flyway 적용 검증

프런트엔드 자동 테스트는 테스트 코드와 실행 스크립트가 준비된 기능부터 CI에 연결한다. 현재 `LancerPickFrontend/package.json`의 `test:api`를 Frontend checks에서 실행한다. 이 검사는 공통 요청·지갑·주문·상품·판매자 API 대역 테스트이며 실제 브라우저 거래나 외부 결제 검증을 의미하지 않는다.

거래·결제·정산 등 데이터 무결성과 동시성이 중요한 기능은 해당 위험에 맞는 자동 테스트를 우선 작성한다.

필수 CI 검사 실패는 기능 완료 및 PR 병합 조건을 충족하지 못한 것으로 판단한다.

CI 설정 변경도 PR을 통해 검토한다.

CI 실행 결과와 실패 원인은 GitHub Actions에서 확인하며, PR에 필요한 검증 결과를 기록한다.

#### 배포와 로그

초기에 로그인 → 보호된 API 조회 → DB 연결 → 배포 환경 확인 흐름을 완성한다.

HTTPS·DB 접근 제한·영속 저장을 설정한다.

비밀번호·API 비밀키를 저장소에 올리지 않는다.

프런트 환경변수에 비밀키를 넣지 않는다.

로그에는 요청 추적 식별자와 처리 결과를 남긴다.

비밀번호·세션 쿠키·인증 토큰·민감한 요청 본문을 기록하지 않는다.

실행·배포·로그 확인·장애 시 대응 방법을 문서화한다.

담당자 외 팀원 1명이 문서대로 실행·배포할 수 있는지 확인한다.

DB 백업을 만들고 테스트 환경에서 복구 가능 여부를 확인한다.

#### 고도화와 포트폴리오

고도화 기능은 별도 확장 계획서에 따라 선택한다.

단계 전체 완료보다 선택한 기능의 선행 조건과 검증 충족 여부로 착수한다.

MVP에서는 책임 경계를 유지하고, 외부 연동·재시도 구조는 해당 기능 착수 시 구현한다.

외부 결제·파일 저장소 호출은 내부 업무 처리와 구분한다.

외부 시스템과 DB가 하나의 트랜잭션처럼 동작한다고 가정하지 않는다. 결과 확인·중복 방지·실패 복구를 별도로 설계한다.

알림 실패 때문에 완료된 결제나 거래를 다시 실행하지 않는다.

실시간 문의는 저장형 문의 구현·검증 이후 진행한다.

소셜 로그인과 외부 메시지 알림은 별개로 다룬다.

외부 API의 지원 조건은 구현 시점의 공식 문서로 확인한다.

실제 수행한 설계 결정·실패 사례·검증 결과·측정 조건을 기록한다.

성능 개선은 같은 조건의 전후 결과를 비교한다.

12월 5일부터 기능 추가를 중단하고 안정화·발표·포트폴리오 정리에 집중한다.

### 실행·환경

#### 프로젝트 구조와 버전

| 경로 | 용도 |
| --- | --- |
| LancerPickFrontend/ | React 프로젝트 |
| LancerPick/ | Spring Boot 프로젝트 |
| docs/ | 명세서·API·설계 결정·실행·배포 안내 |

실제 기본 패키지는 `com.example.lancerpick`이다. 현재 구조와 구현 상태는 [프로젝트 구조 검토](change-history.md#b-65-0), 구체적인 실행은 [루트 README](../README.md)를 따른다. 구조가 준비된 것과 기능 구현·실행 검증이 끝난 것은 구분한다.

#### 프로젝트 구조와 버전 / 백엔드

회원·상품·주문·결제 등 기능별 패키지를 사용한다.

기능 내부에서 Controller·Service·Repository·DTO의 책임을 구분한다.

실제 반복되거나 공통 정책이 필요한 코드만 공통 영역으로 옮긴다.

#### 프로젝트 구조와 버전 / 버전 관리

Java·Node·라이브러리의 정확한 버전은 초기 빌드·인증·DB 연결 검증 후 고정한다.

Gradle Wrapper와 npm 잠금 파일을 저장소에 포함한다.

CI에서는 잠금 파일 기준으로 의존성을 설치한다.

주요 버전 변경은 영향 범위와 필요성을 검토한 뒤 진행한다. Java 기준은 25이며 팀원 JDK 패치를 통일한다. IDE SDK·Gradle JVM·CI·Docker 빌드 및 실행 환경도 25로 맞춘다. 미리보기 기능은 사용하지 않으며 추가 플러그인의 Java 25 호환성을 확인한다.

상세 기술 목록과 기준 버전은 이 문서의 기술 스택 표에서 관리한다. 다른 문서는 이 표를 참조한다.

#### 프로젝트 구조와 버전 / 기술 스택과 기준 버전

아래 표는 프로젝트의 기술 기준이다. 현재 설치·빌드·인증·DB 연결 및 테스트 실행 근거는 현재 현황과 검증 이력에서 관리한다. 정확한 의존성 버전은 Gradle 설정·Wrapper와 package-lock.json을 확인하고, 배포 준비 상태는 별도로 구분한다.

| 영역 | 선정 기술·기준 | 고정 방법 |
| --- | --- | --- |
| 백엔드 | Java 25 / Eclipse Temurin, Spring Boot 4.1.2 | JDK 패치·이미지 버전 기록 |
| 서버 라이브러리 | Spring MVC, Security, Data JPA, Bean Validation | Spring Boot 의존성 관리 사용 |
| 충전 복구 Batch | Spring Batch 6.0.6 / `spring-boot-starter-batch-jdbc` | Boot 4.1.2 관리 버전. 기본 비활성, Flyway V9 공식 시스템 스키마, 업무는 JPA/QueryDSL 유지 |
| 백엔드 빌드 | Gradle Wrapper 9.1.0 이상 9.x | Java 25·Boot 호환 버전 검증 후 고정 |
| 화면 | React·React DOM 19.3.0, TypeScript | 두 React 버전 일치, TS 호환 안정 버전 |
| 프런트 빌드 | Node.js 24 LTS, Vite 8.3 계열, npm | Node 버전·package-lock.json 기록 |
| 라우팅·조회 | React Router 7 계열, TanStack Query 5 계열 | 공통 fetch·CSRF 처리, 잠금 파일 |
| 주문 채팅 실시간 연결 | Spring WebSocket/STOMP·Security Messaging, `@stomp/stompjs` 7.2.0 | 서버는 Boot 관리 버전, 클라이언트는 package-lock.json. 단일 서버 simple broker·세션/CSRF·누락 복구([연결 계약](features.md#communication-chat)) |
| 스타일 | CSS Modules, 공통 UI | 필요한 컴포넌트부터 공유 |
| DB·변경 | MySQL 8.4 LTS, Flyway | DB 이미지 고정, Boot 관리 버전·MySQL 모듈 |
| 배포 | Docker Compose, Nginx, HTTPS | 도구·이미지 버전은 초기 배포 시 고정 |
| 검증 | Spring Boot Test·JUnit·MockMvc, Node.js test runner·React SSR | 실제 실행 스크립트 기준, 브라우저 검증은 별도 기록 |
| 협업 | GitHub PR, GitHub Actions | 타입 검사·빌드·관련 테스트 |

버전 확인 근거: Spring Boot 공식 시스템 요구사항, React 공식 버전 안내, Node.js 공식 릴리스 안내, Vite 공식 릴리스 안내. 초기 설치 시 배포 가능 여부·보안 수정·호환성을 다시 확인한다.

https://docs.spring.io/spring-boot/system-requirements.html

https://react.dev/versions | https://nodejs.org/en/about/previous-releases | https://vite.dev/releases

문서 기준: 업무 정책은 MVP 명세서, 데이터 관계는 ERD, API 계약은 API 명세서, 기술·협업 방식은 이 문서를 따른다. 변경 시 영향을 받는 문서를 함께 갱신하고 개정일·사유·검증 상태를 기록한다.

김창재, 박소현, 정은찬, 황준범으로 배정한다. 정은찬은 조장·통합 조율, 황준범은 React 공통 기반을 겸한다. 지원·조율·검토 시간을 작업량에 포함한다. 검토자·배포 담당은 작업표에서 별도로 확정한다.

### 화면·캐시·계정 경계

#### 프로젝트 구조와 버전 / 프런트엔드

features: 기능별 화면·API·관련 컴포넌트

shared: 공통 UI·API 요청·공통 유틸리티

라우팅과 전역 설정은 한곳에서 관리한다.

### API·입력·오류

#### API 설계 / 기본 형식

API 주소는 /api로 시작하며 화면 경로와 구분한다.

조회는 GET, 생성·거래 명령은 POST, 부분 수정은 PATCH를 기본으로 한다.

상품 PATCH의 생략·null·빈 값·버전 처리는 API 명세 3절, 관리자 목록은 9절, 공통 입력 검증·정규화는 10절을 기준으로 DTO·화면을 맞춘다. 문서 계약 보완과 실제 구현·DB 검증 완료를 구분한다.

구매 확정·취소 승인 등은 명시적인 명령으로 설계한다.

클라이언트가 주문 상태를 임의로 지정하는 API는 만들지 않는다.

JSON과 요청·응답 DTO를 사용한다. JPA 엔티티를 직접 반환하지 않는다.

현재 사용자 ID는 인증 정보에서 가져온다.

판매자·가격·수수료 등 거래 기준값은 서버가 조회·검증한다.

성공 응답은 필요한 데이터 자체를 반환한다.

#### API 설계 / 오류 형식

```
{
  "code": "INSUFFICIENT_BALANCE",
  "message": "잔액이 부족합니다.",
  "fieldErrors": [],
  "traceId": "요청 추적 식별자"
}
```

| 상태 코드 | 사용 기준 |
| --- | --- |
| 200 | 조회·처리 성공 |
| 201 | 생성 성공 |
| 204 | 응답 본문 없는 성공 |
| 400 | 요청 형식·필수값·허용 범위 오류 |
| 401 | 로그인 필요·인증 만료 |
| 403 | 권한 부족 |
| 404 | 대상 없음 또는 존재를 공개하지 않는 대상 |
| 409 | 거래 상태 충돌·잔액 부족·중복 요청 키의 내용 불일치 |
| 500 | 예상하지 못한 서버 오류 |

React는 오류 문구가 아닌 code를 기준으로 필요한 처리를 한다.

오류 응답에 SQL·스택 추적·비밀값을 포함하지 않는다.

타인의 비공개 거래 존재를 감춰야 하는 조회는 404로 처리한다.

#### API 설계 / 목록 조회

기본적으로 page·size를 사용한다.

페이지는 0부터 시작하고 기본 크기는 20, 최대 크기는 100으로 한다.

정렬 항목은 서버가 허용한 값만 받는다.

API별 기본 정렬을 명시하고, 같은 값에서는 ID 등으로 순서를 고정한다.

정렬 순서를 고정해도 조회 사이 데이터가 변경되면 페이지 결과가 달라질 수 있다. 더 강한 일관성이 필요한 기능은 별도로 설계한다.

#### API 설계 / API별 필수 기록

요청·응답·권한·입력 조건·거래 상태 조건·오류·중복 요청 처리·관련 데이터 갱신 범위를 기록한다.

<a id="persistence"></a>
## 영속화·트랜잭션

### 처리 순서·동시성·재전송

#### 거래와 트랜잭션

업무를 시작하는 서비스가 전체 DB 트랜잭션을 관리한다. 주문·지갑·결제·환불·정산·원장·이력의 함께 성공해야 하는 변경은 같은 트랜잭션에 참여한다. 현재 주문 명령은 실행 회원 공유 잠금, 주문 잠금, 필요 시 지갑 잠금(결제·환불은 구매자, V3 구매 확정·자동 확정은 판매자) 순서로 처리한다. 주문·문의·계약·계약 문서·채팅 명령의 회원·주문 잠금과 401/404는 `order/service/OrderCommandLocks`(기존 트랜잭션 필수)로 얻고, 정책 검사는 각 서비스에서 명시적으로 한다. 채팅은 404 문구만 호출자가 정하고(`lockParticipantOrder(…, notFound)`), 계약 문서(PDF) 경로는 회원이 없을 때도 401 대신 404로 숨긴다(`lockParticipantOrderHidingActor`). 계약 경로는 V2 계약 주문도 다루므로 V1·V3 필터가 있는 `lockFulfillmentOrder`를 쓰지 않는다. 주문 생성은 관련 회원 ID 오름차순 잠금과 상품 버전을 확인한다. 변경 시 [주문 생성](features.md#orders-create) 및 금융 문서의 순서를 대조한다.

외부 토스 승인 조율은 WalletTopupApprovalService의 NEVER 경계와 TopupApprovalTransactions를 사용한다. 외부 네트워크 호출을 DB 잠금 안에 넣지 않는다. 승인 결과 불명확 상태의 재확인은 같은 승인 요청을 무조건 반복하는 방식으로 바꾸지 않는다.

상태 변경·금액 검증·이력 기록은 한 줄에 압축하지 않는다. 조건과 실패 사유를 읽을 수 있게 작성한다. 기능 변경과 포맷 정리를 검토 가능한 범위로 구분한다.

#### 거래 처리와 트랜잭션

Controller는 요청 수신·입력 검증·응답 변환을 담당한다.

Service는 거래 규칙·권한·상태 변경·금액 계산을 담당한다.

여러 담당자의 기능 연결은 애플리케이션 내부 서비스 호출로 처리한다.

업무 흐름을 시작하는 서비스가 전체 트랜잭션을 관리하며, 내부 DB 처리는 같은 트랜잭션에 참여한다.

예외를 숨겨 일부 변경만 반영하거나, 별도 트랜잭션으로 원자성을 깨뜨리지 않는다.

| 업무 | 함께 성공하거나 함께 취소될 처리 |
| --- | --- |
| 회원가입 | 회원·지갑 생성, 최초 테스트 지급 |
| 결제 | 잔액 차감·변동 이력·결제 기록·주문 상태·상태 이력 |
| 유료 주문 취소 | 취소 확정·환불·변동 이력·관련 요청 처리·주문 상태·상태 이력 |
| 구매 확정 | 주문 완료·정산 기록·상태 이력 |

테스트 적립금, 주문 결제, 판매자 정산 계산을 구분한다. 정산 금액을 구매용 지갑에 자동으로 합산하지 않는다.

#### 동시 요청과 중복 처리 / 잠금

거래 상태를 변경할 때 주문을 잠그고 현재 상태·권한을 다시 확인한다.

주문과 구매자 지갑을 함께 잠그는 경우 주문 → 지갑 순서를 따른다.

서로 다른 주문의 동시 결제도 지갑 잠금으로 잔액을 보호한다.

상품 수정과 주문 스냅샷 생성에는 일관된 상품 버전 확인·잠금 규칙을 적용한다.

상품 등 다른 자원까지 함께 잠그는 흐름은 전체 잠금 순서를 별도로 기록한다.

트랜잭션은 필요한 처리만 포함하고 짧게 유지한다.

#### 동시 요청과 중복 처리 / 중복 처리

서버 검증과 DB 고유 제약을 함께 사용한다.

같은 주문 요청 키와 같은 내용이면 기존 처리 결과를 반환한다.

같은 요청 키로 다른 내용을 요청하면 충돌 오류를 반환한다.

이미 처리된 결제·환불·정산으로 금액이나 이력을 다시 생성하지 않는다.

각 API에 중복 판단 기준·응답·추가 이력 생성 여부를 명시한다.

동일 작업의 재요청과 허용되지 않는 새로운 상태 변경을 구분한다.

취소 승인과 구매 확정이 동시에 발생하면 잠금 후 유효한 상태 전환만 반영한다.

결제 재요청은 주문·결제 상태를 함께 확인하며 재차감하거나 기존 시각을 덮어쓰지 않는다. 환불 후 충돌 응답과 유효한 기존 결제 재호출의 상세 계약은 [API 명세 7절](architecture.md#b-197-0)을 따른다.

사용자가 거래를 새로 시작한 경우와 통신 재시도를 구분한다. 요청 키는 결과 확인 전까지 유지하고 통신 재시도에서 새로 발급하지 않는다.

납품 요청은 최초 1·수정 2 회차를 명시하고 재전송에서 바꾸지 않는다. 서버는 주문 잠금 아래 권한·상태·기존 회차·대기 취소 요청을 검증한다. 상세 입력·처리 순서·충돌 응답은 [API 명세 5절](architecture.md#b-195-1)을 따른다.

### 업무 규칙·연결

#### 금액과 시간

금액은 원 단위 정수로 처리한다.

수수료 계산에 부동소수점을 사용하지 않는다.

필드와 계산 중간값은 충분한 범위를 가진 자료형을 사용한다.

프런트와 API에서 표현 가능한 금액 범위를 명시한다.

수수료율·반올림 또는 버림·기한 계산은 MVP 명세서를 따른다.

서버·DB 저장 시간 기준은 UTC로 통일한다.

API는 시간대가 포함된 ISO 8601 형식으로 전달하고, 화면은 한국 시간으로 표시한다.

거래 시각과 기한은 서버에서 계산한다.

결제 시각 등 이미 발생한 사건의 기록은 이후 취소되더라도 지우지 않는다.

#### 구조

- Payment Entity + PaymentRepository(JpaRepository): 원결제 생성·조회, Entity 환불 전환·변경 감지. 기존 호출부와의 연결을 유지하는 insert/markRefunded 기본 메서드는 JPA에 위임하며 SQL을 실행하지 않는다.
- Settlement Entity + SettlementRepository: 정산 1건 생성·재호출 시 기존 금액 비교. 수수료 버림 계산은 Entity 팩터리에서도 검증한다.
- CancellationRequest Entity/Repository: 직접 취소 경로의 대기 요청 조회·승인. 승인 이력도 기존 OrderHistory 팩터리와 JPA로 저장한다.
- SettlementQueryRepository: QSettlement·QOrder로 본인 주문 조인, 건수·목록·정렬·정산 합계를 구성한다. QueryDSL의 SUM은 BigDecimal로 받아 기존 정확 변환·JSON 안전 정수 검사를 유지한다.
- OrderCancellationService·OrderConfirmationService·SettlementQueryService의 JdbcTemplate/직접 SQL을 제거한다.

Entity는 기존 컬럼에 매핑하고 UTC LocalDateTime에는 LOCAL_DATE_TIME을 적용한다. 새 마이그레이션은 없다. 주문→지갑 잠금, order_id/event_key UNIQUE, 원자성·반복 요청 계약은 유지한다. 상세 응답의 JDBC 조회와 후속 취소 요청 조회 전에 JPA 변경을 flush해 DB에서 같은 결과를 읽도록 한다.

#### 남겨둔 범위

주문 담당자의 CancellationRequestQueries·OrderDetailQueries·OrderListQueries·OrderCreationQueries는 이번에 전면 변경하지 않는다. 취소 요청 승인 경로가 JPA 직접 취소를 호출한 뒤 기존 JDBC 조회로 결과를 확인하는 흐름도 회귀 검사에 포함한다. 추후 해당 담당자와 별도 합의해 바꿀 수 있다.

지갑 최초 생성의 INSERT IGNORE·회원 잠금 및 충전의 기존 JPA 네이티브 쿼리는 유지한다. 이를 JdbcTemplate 제거와 같은 작업으로 취급하지 않는다. 테스트의 JdbcTemplate fixture/DB 원문 검사도 유지한다.

#### 적용 원칙

사용자 결정에 따라 운영 Java 코드에서 JdbcTemplate·직접 JDBC 조회/쓰기·nativeQuery/createNativeQuery를 제거한다. Entity 저장/변경은 JPA, 조건·조인·집계는 QueryDSL로 통일한다. SQL이 필요한 Flyway와 테스트 fixture·DB 원문 검사에는 해당 운영 규칙을 적용하지 않는다. JPA 내부의 JDBC 드라이버 사용 자체를 없애는 것은 아니다.

#### 전환 내용

| 영역 | 변경 |
| --- | --- |
| 주문 생성 | 회원·상품 Entity 조회/잠금, 기존 참여 회원 ID 오름차순 획득 유지 |
| 주문 목록·상세 | QueryDSL 조인·조건·정렬·페이지. 과거 조건은 스냅샷에서 읽고 현재 상품으로 재작성하지 않음 |
| 취소 요청 | Entity 생성·해결·승인 응답 정리와 QueryDSL 조회. 같은 주문 잠금·원자성 유지 |
| 관리자 상품 | QueryDSL 검색/정렬, JPA 잠금·숨김/버전 변경·운영 이력 저장. LIKE의 %, _, ! 일반 문자 검색 유지 |
| 지갑·원장 | JPA 충전 반영/원장 생성, QueryDSL 결제 조인 내역. API의 Instant/UTC 표현 유지 |
| 회원·납품·주문 잠금 | 네이티브 쿼리를 JPQL 및 JPA 잠금으로 전환 |

공유 Product·SellerProfile·Review·ProductModerationLog Entity는 기존 테이블에 매핑하기 위해 추가한다. 상품/판매자/후기 기능 전체의 신규 API를 구현한 것은 아니다. 기존 Entity에는 조회용 getter를 보강하며 공개 setter를 추가하지 않는다. DB 스키마·Flyway·API 필드·업무 정책은 변경하지 않는다.

#### 지갑 최초 생성의 예외적 ORM 기능

없는 지갑을 먼저 잠그면 서로 다른 회원 초기화에서도 간격 잠금 교착이 생길 수 있으므로 insert-first 처리를 유지한다. Hibernate HQL의 `insert ... on conflict do nothing`을 사용하며 이는 네이티브 SQL 쿼리가 아니다. 표준 JPQL에 없는 Hibernate 확장임을 명시한다. [공식 HQL 문서](https://docs.hibernate.org/stable/core/querylanguage/html_single/)를 따른다.

MySQL이 중복 행의 no-op도 1건으로 보고하면 신규 생성과 혼동하므로 공통 Hikari의 `useAffectedRows=true`를 유지한다. 신규 1건·중복 0건 반환을 전제로 기존 지급 원장·소유자를 검증하고, 불완전 상태를 자동 지급으로 복구하지 않는다. 이 설정 변경 또는 DB 교체 시 초기화 반복/동시성과 반환 건수를 함께 재검증해야 한다.

### 데이터·제약

#### DB 변경 관리

MySQL을 기준 DB로 사용하고 Flyway로 스키마 변경을 관리한다.

각 담당자가 자기 기능의 변경 파일을 작성한다.

공유 환경에 적용한 파일은 수정하지 않고 새 변경 파일을 추가한다.

공유·배포 환경에서는 ddl-auto=update 대신 스키마 검증 설정을 사용한다.

외래키·고유 제약·필수값 등 가능한 규칙은 DB에도 적용한다.

거래·결제·정산 이력이 연쇄 삭제되지 않도록 한다.

개발·테스트 데이터와 배포 초기 데이터를 구분한다.

인덱스는 실제 조회 조건과 실행 결과를 근거로 추가한다.

데이터가 있는 환경의 구조 변경은 기존 데이터 보존·변환 방법까지 검토한다.

<a id="events"></a>
## 이벤트 ID·버전·오류

### 업무 규칙·연결

#### 이벤트 ID

| 경우 | 규칙 | 사용처 |
| --- | --- | --- |
| 업무 행동마다 한 번 | 무작위 UUID(v4). `OutboxEvent.pending(type, schemaVersion, …)` | 주문 이력 기반 알림(`OrderEventPublisher.publish`) |
| 같은 원천에서 한 번만 | `OutboxEvent.nameBasedId(kind, sourceId)` = `"<kind>:<sourceId>"`의 이름 기반 UUID(v3) | 자동 구매 확정 사전 안내 `AUTO_CONFIRM_NOTICE:<timerId>`, 첫 계약 서명 `CONTRACT_SIGNATURE_REQUESTED:<signatureId>` |

- 이름 기반 ID는 여러 서버·반복 실행에서 같은 값이 나오므로 `event_id` UNIQUE가 중복 저장을 막는다. 미리 `existsByEventId`로 건너뛰는 것은 잠금 비용을 줄이는 최적화일 뿐이고 최종 보장은 UNIQUE다.
- `kind`는 대문자·숫자·밑줄(`[A-Z][A-Z0-9_]{0,59}`)만 쓴다. 한 번 정한 kind는 바꾸지 않는다(바꾸면 이미 저장된 이벤트와 다른 ID가 되어 중복 방지가 깨진다). `AUTO_CONFIRM_NOTICE`는 #105에서 저장된 값과 같은 ID를 만든다(`OutboxEventContractTests`로 고정).
- 알림 중복은 `notifications (recipient_id, event_id, type)` UNIQUE가 한 번 더 막는다.

### 데이터·제약

#### 스키마 버전

- 버전은 **집계 유형(aggregate_type)의 페이로드 형식** 기준이다. 현재 `ORDER`만 있고 버전은 `OrderEventPublisher.ORDER_SCHEMA_VERSION = 1`.
- 필드를 **추가만** 하면 버전을 올리지 않는다(처리기는 모르는 필드를 무시한다). 기존 필드의 의미를 바꾸거나 지우면 올린다.
- 올릴 때는 처리기가 이전 버전도 계속 읽도록 지원 목록에 함께 추가한다. 이미 저장된 `PENDING` 이벤트가 있기 때문이다.
- 처리기가 지원하지 않는 집계·버전은 다시 시도하지 않고 `UNSUPPORTED_EVENT`로 바로 `FAILED` 처리한다.
- 페이로드 공통 규칙: ID는 JSON 문자열, 시각은 UTC ISO 8601, 사유·요구사항·결제 정보·개인정보는 넣지 않는다. 수신자는 `recipientIds`(문자열 배열)이고 처리기가 현재 주문 당사자인지 다시 확인한다.

### API·입력·오류

#### 아웃박스 오류 코드 (`last_error_code`)

예외 클래스 이름·메시지·페이로드를 저장하지 않고 아래 고정 코드만 저장한다(`notification/entity/OutboxErrorCode`). 재처리 화면(K6)과 운영 조회는 이 코드만 기준으로 삼는다. 코드는 바꾸거나 지우지 않고 추가만 한다.

| 코드 | 재시도 | 의미 |
| --- | --- | --- |
| `UNSUPPORTED_EVENT` | 안 함(즉시 FAILED) | 지원하지 않는 집계 유형·스키마 버전 |
| `INVALID_PAYLOAD` | 안 함 | JSON 형식·필수 필드·수신자 ID 형식 오류 |
| `INVALID_RECIPIENT` | 안 함 | 수신자가 현재 주문 당사자가 아님 |
| `AGGREGATE_NOT_FOUND` | 안 함 | 이벤트가 가리키는 주문이 없음 |
| `LOCK_UNAVAILABLE` | 백오프 후 최대 5회 | 잠금 대기 초과·교착(`PessimisticLockingFailureException` 계열) |
| `PROCESSING_FAILED` | 백오프 후 최대 5회 | 그 밖의 처리 실패 |

#### 채팅 소켓 오류 형식

<a id="b-425-0"></a>
REST 오류(`code`·`message`·`fieldErrors`·`traceId`)와 같은 필드를 쓰고, HTTP 상태가 없으므로 같은 의미의 `status`를 함께 싣는다.

```json
{
  "orderId": "11",
  "clientMessageId": "581f0401-3684-4992-a541-137179be9f70",
  "status": 409,
  "code": "CHAT_CLOSED",
  "message": "종료된 주문의 대화는 조회만 할 수 있습니다.",
  "fieldErrors": [],
  "traceId": "3f2c…"
}
```

- `/user/queue/chat-errors`의 업무 오류: `status`는 `BusinessException`의 HTTP 상태(404·409 등). 형식 오류는 400 `INVALID_REQUEST`(orderId·clientMessageId null), 예상치 못한 실패는 503 `CHAT_SEND_FAILED`.
- `traceId`는 전송 한 건마다 서버가 새로 만든 UUID이며 처리 중 로그 MDC에도 같은 값이 들어간다(HTTP의 `TraceIdFilter`와 같은 규칙, 클라이언트가 보낸 값은 쓰지 않는다). 예상치 못한 실패는 `traceId`와 예외 유형만 로그에 남긴다.
- 연결을 끊는 STOMP `ERROR` 프레임: 본문 `{"code","traceId"}`, `message` 헤더는 code. code는 `FORBIDDEN`·`CSRF_INVALID`·`AUTHENTICATION_REQUIRED`·`CHAT_RATE_LIMITED`만 쓴다.
- 프런트(`order-chat/realtime.ts` `chatErrorStatus`)는 서버의 `status`를 쓰고, 상태가 없는 이전 형식 프레임은 기존처럼 `CHAT_SEND_FAILED`만 503, 나머지는 409로 본다. `traceId`는 `ApiError.traceId`로 전달한다.

### 범위·후속 조건

#### 범위 밖

- 실시간 알림 전송, Redis Pub/Sub(C4), 이벤트 스트림 외부 발행.
- 아웃박스 `lease_owner`·`lease_until` 사용(현재 처리는 DB 트랜잭션 안에서 끝남).
- `FAILED` 재처리 명령·화면(K6, 김창재 + 정은찬·황준범 서비스).

<a id="mail"></a>
## 메일·비동기·복구 경계

### 업무 규칙·연결

#### 메일 발송 인프라 (C3)

담당: 정은찬(작업표 C3). main 반영: #90(2026-11-05). K1 담당자 전달 사항은 [작업표](change-history.md#b-1108-0). 계정 메일(현재 비밀번호 재설정)을 실제 SMTP로 보낸다. 메일 내용·토큰·요청 제한은 김창재 K1 범위이며 [로그인 문서](features.md#b-229-0)의 연결 계약을 따른다. DB·Flyway 변경은 없다.

#### 구성

| 파일 | 역할 |
| --- | --- |
| `auth/mail/SmtpAccountMailSender` | `AccountMailSender` 구현. 발송 스레드 1개 + 고정 크기 큐. `enqueue`는 큐에 넣고 바로 반환하고, SMTP 통신은 발송 스레드에서만 한다 |
| `auth/mail/MailInfrastructureConfiguration` | `lancerpick.mail.enabled=true`이고 demo 프로필이 아닐 때만 위 발송기를 Bean으로 등록 |
| `build.gradle` | `spring-boot-starter-mail` 추가 |
| `application.properties` | `lancerpick.mail.*`, `spring.mail.*`(환경변수로 덮어씀), SMTP 연결·읽기·쓰기 시간 제한 5초 |
| `application-test.properties` | `lancerpick.mail.enabled=false` 고정(테스트는 가짜 발송기 사용, 개인 `MAIL_ENABLED`에 영향받지 않음) |

발송기는 `auth/mail`에 둔다. 연결 계약(`AccountMailSender`)이 이 패키지에 있고 현재 사용처가 계정 메일뿐이라, 범용 `common/mail`을 미리 만들지 않는다.

#### 동작

1. 발급 트랜잭션이 커밋된 뒤 `AccountMailDispatcher`가 `enqueue`를 부른다(요청 스레드).
2. 큐에 넣으면 true → `delivery_status`는 `PENDING`. 큐가 가득 찼거나 서버 종료 중이면 false → Dispatcher가 `FAILED`(`MAIL_QUEUE_FULL`).
3. 발송 스레드(`account-mail-sender`)가 `SimpleMailMessage`(UTF-8 평문)로 보낸 뒤 `AccountMailDeliveryRecorder`에 기록한다.

| 결과 | 기록 |
| --- | --- |
| 성공 | `SENT` |
| 장시간 결과 기록 없음(로컬 후속) | `UNKNOWN` `MAIL_RESULT_UNCONFIRMED`. 미발송 확정 아님 |
| SMTP 인증 실패(`MailAuthenticationException`) | `FAILED` `MAIL_AUTH_FAILED` |
| 메시지 구성 오류(`MailParseException`) | `FAILED` `MAIL_INVALID_MESSAGE` |
| 연결·전송 실패(`MailSendException`) | `FAILED` `MAIL_SEND_FAILED` |
| 그 밖의 예외 | `FAILED` `MAIL_FAILED` |

- 기록이 실패해도 발송 스레드는 멈추지 않는다(행은 `PENDING`으로 남음).
- 로그에는 challengeId와 오류 코드·예외 클래스 이름만 남긴다. 수신자·본문(토큰)·예외 메시지는 남기지 않는다.
- 자동 재발송은 하지 않는다. 같은 토큰을 다시 보내지 않고, 사용자는 요청 제한(60초 간격·1시간 5회) 안에서 다시 요청한다.

#### 로컬 Mailpit 확인

<a id="b-590-0"></a>
Docker Desktop을 켠 뒤:

```powershell
docker run -d --name lancerpick-mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit:v1.33.0
```

Docker가 없으면 Mailpit 공식 Windows 실행 파일(GitHub 릴리스 `mailpit-windows-amd64.zip`, SHA-256 확인)을 `mailpit.exe --smtp 127.0.0.1:1025 --listen 127.0.0.1:8025`로 실행해도 같다(2026-11-06 김창재 확인). 2026-11-06부터 화면 회원가입도 이메일 인증이 필수라, 로컬에서 새 계정을 만들려면 이 설정이 필요하다([이메일 인증 가입](features.md#b-234-0)).

`LancerPick/local-application.properties`에 `MAIL_ENABLED=true`를 넣고 서버를 켠다(호스트·포트 기본값이 Mailpit). 화면 `/password-reset/request`에서 가입한 이메일로 요청하면 http://localhost:8025 받은편지함에 메일이 오고, 링크로 새 비밀번호를 설정할 수 있다. 끝나면 `docker rm -f lancerpick-mailpit`.

### 실행·환경

#### 설정

| 속성 | 환경변수 | 기본값 |
| --- | --- | --- |
| `lancerpick.mail.enabled` | `MAIL_ENABLED` | `false` |
| `lancerpick.mail.from` | `MAIL_FROM` | `LancerPick <no-reply@lancerpick.local>` |
| `lancerpick.mail.queue-capacity` | `MAIL_QUEUE_CAPACITY` | `100` |
| `lancerpick.mail.reconciliation.enabled` | `MAIL_RECONCILIATION_ENABLED` | `true`. SMTP 켜짐 여부와 독립, demo/test 자동 실행 제외 |
| `lancerpick.mail.reconciliation.stale-minutes` | `MAIL_RECONCILIATION_STALE_MINUTES` | `30`. 1 이상의 분, 발급 시각 기준 |
| `lancerpick.mail.reconciliation.batch-size` | `MAIL_RECONCILIATION_BATCH_SIZE` | `50`. 1~100 |
| `lancerpick.mail.reconciliation.delay-ms` | `MAIL_RECONCILIATION_DELAY_MS` | `60000`. 최초 지연·실행 종료 후 지연, 양수 밀리초 |
| `spring.mail.host` / `port` | `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025`(Mailpit) |
| `spring.mail.username` / `password` | `MAIL_USERNAME` / `MAIL_PASSWORD` | 비어 있음 |

- 꺼져 있으면(기본) 메일 서버 없이 앱·테스트·CI가 켜지고, 계정 메일은 `FAILED`(`MAIL_NOT_CONFIGURED`)로 남는다.
- 켰는데 `JavaMailSender`를 만들 수 없거나 `from`이 비어 있으면 서버 시작이 실패한다(조용히 발송하지 않는 상태를 막는다).
- 실제 SMTP 계정·비밀번호는 `local-application.properties`나 셸 환경변수에만 둔다. 저장소·문서·로그에 남기지 않는다.
- 기본 설정은 인증·TLS 없는 Mailpit 기준이다. 외부 SMTP를 쓰려면 `spring.mail.properties.mail.smtp.auth=true`, `spring.mail.properties.mail.smtp.starttls.enable=true`(필요 시 `starttls.required=true`)를 개인 설정에 더한다. 이 조합은 검증하지 않았다.

### 범위·후속 조건

#### 한계

- 메모리 큐라 서버가 종료되면 남은 메일은 버려진다. 여러 서버는 각자 자기 큐만 처리한다. 메일 본문·토큰은 DB 큐로 옮기지 않는다.
- 로컬 후속의 `AccountMailReconciliationService`가 오래된 PENDING의 결과 상태만 UNKNOWN으로 정리한다. 조건부 갱신은 발송 상태/오류 코드 두 컬럼뿐이며 만료/인증/사용을 변경하지 않는다. 늦은 실제 결과를 수용하되 SENT/FAILED는 보존한다. 전용 스케줄러는 메일을 보내지 않으며 자동 재발송은 범위에서 제외했다([기능 계약](features.md#mail-delivery-reconciliation)).
- V12가 먼저 적용되는 정상 Boot/Flyway 기동을 사용한다. UNKNOWN 값을 모르는 구버전 앱으로 단순 롤백하면 enum 조회가 실패할 수 있어 코드/스키마 호환을 확인해야 한다. 기본 30분은 현재 15분 인증 링크 만료와 독립된 발송 결과 정리 기준이다.
- HTML 메일·첨부·발송량 제한(전체·IP)은 없다.
- 서버 종료 때 진행 중인 발송은 중단 신호를 받는다. 이미 SMTP 서버가 받은 뒤라면 실제로 전달됐어도 `FAILED`로 기록될 수 있다(사용자에게는 메일이 도착).
- 비밀번호 변경·재설정 완료 안내 메일, 이메일 인증 가입·아이디 안내 메일은 K1(김창재)에서 이 발송기를 그대로 사용해 추가한다. 아이디 안내는 2026-11-10 아래 `untracked` 계약으로 구현했다([규칙](features.md#account-guide)). 가짜 발송기 통합 테스트로 확인했고 실제 SMTP 발송은 아직 확인하지 않았다.
- 토큰이 없어 `account_challenges` 행이 없는 안내 메일(아이디 안내 등)은 `AccountMail.untracked(to, subject, body)`로 만든다. `challengeId`가 null이면 Dispatcher·발송기가 `AccountMailDeliveryRecorder`를 부르지 않고 로그(`challengeId=null`·오류 코드)만 남긴다(2026-11-06 결정, DB 변경 없음). 그래서 이런 메일의 발송 결과는 DB로 확인할 수 없다.

<a id="environment"></a>
## 실행·환경·검증 절차

### 업무 규칙·연결

#### 기능별 구조

| 위치 | 책임 |
| --- | --- |
| src/app | 라우팅·QueryClient Provider |
| src/features/기능/types.ts | 요청·응답·기능 타입 |
| src/features/기능/api.ts | 공통 apiRequest를 사용하는 기능별 HTTP 통신 |
| src/features/기능/queries.ts | 조회 훅·queryKey·재조회·캐시 정책 |
| src/features/기능/pages | 화면 구성·입력 상태·이벤트 처리 |
| src/features/기능/components | 기능 전용 UI |
| src/shared/api | HTTP·CSRF·공통 오류·페이지 응답·계정 캐시 제거 정책 |
| src/shared/components | 레이아웃·입력·버튼·상태 안내 |
| src/shared/lib | 금액·시간·문자열·URL 페이지 처리 |

같은 조회를 여러 화면에서 사용하면 기능 queries.ts를 재사용한다. 변경 이벤트의 입력·진행 상태·화면 이동은 화면에서 관리할 수 있다. 변경 API와 검증 로직은 기능 파일에서 재사용하고, 필요한 경우 훅으로 분리한다. 사용하지 않는 components 폴더나 빈 공통화를 미리 추가하지 않는다.

이미 있는 공통 판별·주소 함수를 쓴다: 화면의 단독 401 판별은 `shared/api/client.ts`의 `isUnauthorized`(세션 재확인·캐시 정리·안내는 호출한 화면이 맡음. 공통 요청 함수의 응답 재처리, 채팅 소켓 오류, 여러 상태를 함께 묻는 판정은 상태 값을 직접 비교한다), 로그인 후 돌아오는 주소는 `features/auth/session.ts`의 `loginPath(next)`, 공백 정리 후 글자 수는 `shared/lib/text.ts`의 `normalizedLength`, 관리자 상세의 목록 복귀 주소는 `features/admin/navigation.ts`의 `adminBackPath`.

#### 실제 조회 흐름

상품 목록은 products/api.ts와 queries.ts, 상품 상세는 useProductDetail을 사용한다. 주문 목록·상세·작성 진입은 orders/queries.ts, 판매자 프로필은 seller/queries.ts, 지갑은 wallet/queries.ts, 정산은 settlements/queries.ts를 사용한다. 화면에서 API 주소를 직접 작성하지 않는다.

개발 시 프런트 경로는 http://localhost:5173, API 프록시 기본 대상은 http://127.0.0.1:8080이다. 프런트 디렉터리에서 npm ci 후 npm run dev로 실행한다. 서버 실행·개인 DB 설정은 [설정 안내](architecture.md#environment)를 따른다.

#### 실제 조회 흐름 / 2-3. 백엔드 포트가 8081일 때만

<a id="b-468-0"></a>
PowerShell에서 API_PROXY_TARGET 환경변수를 http://127.0.0.1:8081로 지정하고 Vite를 다시 실행한다. 기본 포트로 돌아오면 해당 환경변수를 제거한다. vite.config.ts의 설정을 기능별 API 주소로 복제하지 않는다.

#### 완료 확인

프런트 디렉터리에서 npm run test:api, npm run lint, npm run build를 실행한다. 기존 테스트 파일은 tests/*.test.mjs로 CI에 포함된다. React 렌더링 검사와 실제 브라우저 검증은 PR에서 구분한다. API 계약·화면 동선·캐시 키를 바꿨다면 관련 테스트를 확인한다.

#### 이 문서의 목표

한 저장소의 LancerPickFrontend와 LancerPick을 실행하고 MySQL에 연결한다. 처음 구성한 담당자 외 팀원도 복제 후 동일하게 실행할 수 있어야 한다. 이후 인증을 연결하여 로그인과 보호 API까지 확인한다.

기술 기준은 기존 공통 개발 규칙을 따른다. 아래 폴더명·포트·DB 이름·설정 예시는 구축을 위한 추천안이다. 실제 설치와 실행을 완료했다는 기록은 아니며, 완료 여부는 마지막 확인표에 남긴다.

#### 진행 순서

1. 도구 설치와 버전 기록
2. 저장소와 프로젝트 생성 — 초기 생성 참고용
3. 로컬 MySQL 실행 — 현재 방식은 루트 README, Docker는 선택 예시
4. Spring Boot 설정과 DB 변경 관리
5. React 연결과 공통 요청 처리
6. 인증 검증과 자동 검사
7. 팀원 복제 실행과 오류 해결
8. 완료 기록과 첫 배포 연결

명령은 각 단계에 적힌 폴더에서 실행한다. 예시의 비밀번호·주소·계정은 실제 값으로 교체하며, 팀원은 초기 생성 명령을 반복하지 않는다.

#### 인증과 자동 검사 연결 / 로컬 검사

```
# LancerPickFrontend에서 실행
npm.cmd ci
npx.cmd tsc -b
npm.cmd run build
```

```
# DB 환경변수가 설정된 LancerPick 터미널에서 실행
.\gradlew.bat clean build
```

초기 생성 템플릿에 없는 test 스크립트를 실행 안내에 넣지 않는다. Vitest와 React Testing Library는 첫 공통 동작 테스트를 작성할 때 설정하고, 스크립트를 추가한 뒤 CI에 연결한다.

#### 인증과 자동 검사 연결 / GitHub Actions 구성 순서

PR에서 프런트와 백엔드 검사를 실행한다. 프런트는 고정 Node → npm ci → 타입 검사 → 빌드 순서다. 백엔드는 JDK 25 → MySQL 준비 → 테스트 DB 환경변수 → Gradle build 순서로 구성한다.

CI용 DB는 개발 DB와 분리한다. 테스트는 매 실행 새 DB 또는 격리된 스키마를 사용하며 Flyway를 적용한다. Linux 실행기에서는 gradlew 실행 권한도 확인한다. 비밀번호는 GitHub Secrets 또는 격리된 일회성 테스트 값으로 관리한다.

검사 실패를 숨기려고 테스트를 제외하지 않는다. 데이터베이스가 필요한 contextLoads 테스트라면 먼저 테스트 DB와 접속 환경을 마련한다. 정확한 Actions 버전과 이미지 고정값은 CI 구성 시 공식 문서를 확인하여 기록한다.

#### 완료 기록과 다음 작업 / 저장소에 남길 자료

README에는 시작 방법과 자주 쓰는 명령을 둔다. docs/setup-guide.md에는 정확한 버전·필요한 환경변수·오류 해결을 기록한다. .env.example, compose.yaml, Gradle Wrapper, package-lock.json을 함께 관리한다. 실제 비밀값은 저장하지 않는다.

#### 완료 기록과 다음 작업 / 이후 첫 배포로 연결

컨테이너에서 백엔드를 실행하면 DB 주소는 localhost:3307이 아니라 Compose 서비스명 db:3306이다. HTTPS·세션 쿠키·DB 영속 저장·접근 제한·로그를 확인한다. 배포 서버·도메인·실제 비밀값은 별도로 정하고 배포 안내를 작성한다. Docker의 빌드용 JDK와 실행용 Java는 모두 25 기준으로 고정한다. GitHub Actions의 Java 설정도 25로 맞춘다.

고도화 준비는 기능별 경계와 설정 분리로 확보한다. 외부 결제·파일 저장·알림 구현은 해당 기능을 선택한 시점에 추가한다. 지금은 재현 가능한 실행과 최초 통합이 우선이다.

#### 완료 기록과 다음 작업 / 기준 문서와 공식 참고 자료

팀 기준: 랜서픽 공통 개발 규칙 v1.0 및 MVP 기획 명세서 v1.0. 공식 문서 확인일: 2026년 10월 29일. 설치·보안 패치·세부 API는 실제 구축 시 다시 확인한다.

Spring Boot 요구사항  https://docs.spring.io/spring-boot/system-requirements.html | Gradle 호환표 https://docs.gradle.org/current/userguide/compatibility.html

DB 초기화  https://docs.spring.io/spring-boot/how-to/data-initialization.html

Spring Security CSRF  https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html

React 19.3  https://react.dev/blog/2026/09/09/react-19-3

Vite 시작 안내  https://vite.dev/guide/

Compose 환경변수  https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/

#### 검사 구분

검사는 실행 대상에 따라 아래처럼 구분한다.

| 검사 | DB·개인 파일 사용 | 확인하는 것 |
| --- | --- | --- |
| common 패키지 테스트 | 없음; 기본 프로필 검사도 테스트 설정으로 DB·개인 import 제외 | 예시 API·401/403·CSRF·204·정규화 |
| TestDatabaseInitializerTests | 없음 | 테스트 DB URL·설정 보호 |
| @IntegrationTest 전체 컨텍스트·업무 DB 테스트 | TEST_DB_*와 전용 MySQL 필요 | 컨텍스트·마이그레이션·해당 업무 검증 |
| CI 전체 빌드 | 임시 lancerpick_ci | 저장소에 실제 포함된 테스트; 미구현 기능 검증은 아님 |

DB 없는 공통 검사는 다음과 같다.

```powershell
.\gradlew.bat test --tests "com.example.lancerpick.common.*" --tests "com.example.lancerpick.support.TestDatabaseInitializerTests"
```

DB 없이 보호 검사만 실행하려면 백엔드 폴더에서 다음 명령을 사용한다.

```powershell
.\gradlew.bat test --tests com.example.lancerpick.support.TestDatabaseInitializerTests
```

이 검사는 허용 DB, 개발 DB·유사 이름 차단, 실제 설정 override 차단과 오류의 접속 정보 미노출을 확인한다. 실제 DB 연결·Flyway 실행·업무 동시성 검증과는 다르다.

테스트에서 회원을 만들 때(2026-11-06부터): `POST /api/members`는 이메일 인증 증명이 필수다. 통합 테스트는 `support/TestMembers`의 `signupJson`·`signupBody`·`signupRequest`로 인증된 가입 증명을 함께 보낸다(증명 행을 테스트 DB에 직접 넣는 fixture). 메일을 실제로 거치는 검사는 `support/FakeAccountMailSender`를 `@Import(FakeAccountMailSender.Config.class)`로 쓴다. [이메일 인증 가입](features.md#b-234-0).

CI의 임시 MySQL은 실행마다 생성·폐기한다. 로컬 전용 테스트 DB는 자동으로 삭제하거나 초기화하지 않는다. 향후 데이터 변경 테스트에서는 트랜잭션 롤백·데이터 정리·병렬 실행 기준을 별도 설계한다. V1과 엔티티는 함께 추가하고 `validate`를 끄거나 H2로 바꿔 검사를 우회하지 않는다.

#### 관련 문서

- [백엔드 공통 가이드](architecture.md#conventions)
- [프런트 공통 가이드](architecture.md#environment)
- [확장 계획의 결제사 테스트 충전](change-history.md#b-438-0)
- [토스 공식 API 키 안내](https://docs.tosspayments.com/reference/using-api/api-keys)
- [토스 공식 결제 연동 가이드](https://docs.tosspayments.com/guides/v2/payment-widget/integration)
- [토스 공식 API 키 접근 정책](https://docs.tosspayments.com/reference/using-api/api-key-access-policy)

### 화면·캐시·계정 경계

#### 인증·개인 캐시

- auth/api.ts의 getMemberSession은 현재 회원을 조회하고 401을 그대로 전달한다. 지갑·판매자·주문 진입의 회원 확인은 이 API를 재사용한다.
- getCurrentMember는 헤더의 익명 상태를 표현하기 위해 401만 null로 변환한다. 나머지 실패는 호출자에 전달한다.
- 회원 데이터의 공통 타입은 auth/types.ts의 Member다. 일부 필드만 필요한 기능은 Pick으로 정의한다.
- 헤더·지갑·주문 작성·판매자 화면의 조회 키와 재확인 조건은 기능에 맞게 유지한다. 기능별 재확인을 없애거나 401을 임의 데이터로 대체하지 않는다.
- 공통 QueryClient는 조회(query)가 401을 받으면 헤더가 로그인 회원을 보이는 동안에만 `recheckAfterUnauthorized`(`features/auth/session.ts`)로 로그인 상태를 한 번 다시 확인한다. 세션이 끝났으면 헤더를 로그인 안 함으로 바꾸고 개인 캐시를 지운다. 변경 요청(mutation)의 401은 각 화면이 결과를 안내하며 처리한다.
- 로그인·로그아웃 성공은 resetAccountState, 불명확한 결과는 recheckSession을 사용한다. 진행 중인 이전 회원 조회를 먼저 취소한다.
- shared/api/accountCache.ts는 auth 이외의 조회 취소·제거 정책을 관리한다. 인증 계층이 회원 상태를 갱신하고 wallet/cache.ts가 충전 진행 정보도 정리한다.
- 주문·정산의 wallet 하위 키는 기존 로그아웃 계약으로 유지한다. 키를 변경할 때 관련 취소·갱신·제거와 회귀 검사까지 함께 수정한다.

#### 프런트엔드 설정

현재 개인 파일은 `LancerPickFrontend/keys.env`입니다. 기존 파일을 그대로 사용하고 아래 항목을 설정합니다. Vite는 keys.env를 기본 이름으로 읽지 않으므로 저장소의 vite.config.ts가 클라이언트 키 항목만 명시적으로 연결합니다. 표준 .env.local 방식도 대체 설정으로 지원하지만 두 파일을 중복 작성할 필요는 없습니다.

```dotenv
VITE_TOSS_CLIENT_KEY=CHANGE_ME
```

`CHANGE_ME`를 별도로 전달받은 테스트 **클라이언트 키**로 바꿉니다. 파일이 없다면 `LancerPickFrontend/.env.example`을 `keys.env`로 복사합니다. 기존 개인 파일은 덮어쓰지 않습니다. 셸 변수 → keys.env → 표준 .env 계열 순서로 적용합니다.

추후 SDK 연동 코드는 다음으로 클라이언트 키를 읽습니다.

```ts
import.meta.env.VITE_TOSS_CLIENT_KEY
```

**`VITE_`로 시작하는 값은 브라우저 코드에 포함됩니다.** 시크릿 키를 `.env.local`의 `VITE_` 변수나 React 소스에 넣지 않습니다. 파일을 Git에서 제외해도 브라우저에 포함되는 값까지 숨겨지는 것은 아닙니다.

### API·입력·오류

#### API·입력·화면 규칙

ID는 문자열로 보존한다. 금액·권한·주문 상태는 서버가 최종 판단한다. 화면은 API 엔티티 대신 정의된 응답 타입을 사용한다. 로딩·401·404·서버 오류·빈 목록을 구분하고 통신 오류를 빈 결과로 바꾸지 않는다.

API 변경 요청은 자동 재전송하지 않는다. 처리 중 중복 클릭을 막고, 결과 불명확 시 서버 상태를 확인한다. 동일 주문 생성 재확인은 기존 요청 키와 입력을 보존한다. 주문 행동은 allowedActions로 표시하고 서버도 권한·상태를 재검증한다.

목록의 선택·페이지는 URL로 유지한다. 기능 내 상태·입력은 React 상태로 관리하고 서버 데이터를 별도 전역 상태에 복제하지 않는다. 큰 화면은 실제로 구분되는 UI 단위로 분리하고 압축된 JSX를 작성하지 않는다.

#### React와 API 연결

LancerPickFrontend/vite.config.ts의 기존 React 플러그인 설정을 유지하고 server 항목을 추가한다. 프런트에서는 localhost:8080 절대 주소 대신 /api 상대 경로를 사용한다.

```
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
```

```
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
```

#### React와 API 연결 / 첫 연결 확인

백엔드에 로컬 확인용 GET /api/health를 만들고 {"status":"UP"}만 반환하도록 한다. 이 경로는 기존 업무 API가 아닌 환경 확인용 추가안이다. Security에서 이 GET 경로만 공개하고 나머지 보호 규칙은 유지한다.

이 응답은 HTTP 연결 성공만 보여준다. DB 연결 성공은 별도로 SQL 접속과 애플리케이션 DB 로그에서 확인한다. 전체 경로 permitAll이나 CSRF 비활성화로 연결 문제를 우회하지 않는다.

```
# 새 터미널에서 LancerPickFrontend 폴더로 이동
npm.cmd ci
npm.cmd run dev
```

브라우저에서 http://localhost:5173/api/health로 응답을 확인한다. 이후 임시 확인 화면에서 fetch 결과를 표시한다. 확인 후 해당 화면은 실제 상품 목록 화면으로 교체한다.

```
const response = await fetch('/api/health', {
  credentials: 'same-origin',
})
if (!response.ok) throw new Error(`HTTP ${response.status}`)
const result = await response.json()
```

#### React와 API 연결 / 공통 프런트 구조

src/features에는 기능별 화면·API를, src/shared에는 공통 UI·요청 함수를 둔다. 라우팅과 QueryClientProvider는 앱 진입부 한곳에서 구성한다. 로그인 상태 조회, 로딩·빈 목록·오류 표시의 예시를 한 화면에서 먼저 완성한다.

공통 요청 함수는 오류 code·401·403, 204의 빈 본문, CSRF 헤더 전달을 처리한다. 위 fetch 예시는 GET 연결 확인용이므로 결제·취소 등의 최종 공통 요청 함수로 사용하지 않는다. VITE_ 환경변수에는 비밀키를 넣지 않는다.

### 실행·환경

#### 랜서픽 공통 개발 환경 구축 가이드

역할 배정 갱신 2026-10-29: [확정 담당·지원 원칙·업무 비중](team-roles.md#b-1131-0)을 따른다. 상세 일정·검토자·배포 담당은 별도 확정한다.

팀원 4명이 같은 환경에서 실행하고 첫 화면과 API를 연결하기 위한 실습 안내

#### 로컬 MySQL 실행

개발 중에는 DB만 Docker로 실행하고 React와 Spring Boot는 PC에서 실행하는 구성을 추천한다. 아래 파일은 저장소 루트에 만든다. 3307 포트를 사용하여 기존 로컬 MySQL과 충돌 가능성을 줄인다.

이 장은 Docker DB를 새로 선택할 때의 예시다. 현재 로컬 MySQL을 사용하는 팀원에게 필수 전환 절차가 아니며, 기존 DB·개인 설정을 삭제하지 않는다.

#### 로컬 MySQL 실행 / 환경변수 파일

.env.example을 아래 형태로 작성하고 .env로 복사한다. .env에는 각자 로컬 비밀번호를 입력한다. 단순 영문·숫자 조합을 사용하면 초기 인용부호 문제를 줄일 수 있다.

```
MYSQL_IMAGE=mysql:8.4
MYSQL_DATABASE=lancerpick
MYSQL_USER=lancerpick
MYSQL_PASSWORD=REPLACE_WITH_LOCAL_PASSWORD
MYSQL_ROOT_PASSWORD=REPLACE_WITH_DIFFERENT_PASSWORD
```

mysql:8.4는 첫 연결 확인용 계열 태그다. 성공 후 MYSQL_IMAGE를 검증한 패치 또는 digest로 고정하여 .env.example에도 동일하게 기록한다.

#### 로컬 MySQL 실행 / compose.yaml

```
services:
  db:
    image: ${MYSQL_IMAGE:?Set MYSQL_IMAGE}
    environment:
      MYSQL_DATABASE: ${MYSQL_DATABASE}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:?Set MYSQL_PASSWORD}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?Set ROOT password}
      TZ: UTC
    ports:
      - "127.0.0.1:3307:3306"
    volumes:
      - mysql_data:/var/lib/mysql
    command: ["--default-time-zone=+00:00"]
volumes:
  mysql_data:
```

#### 로컬 MySQL 실행 / 실행과 접속 확인

```
Copy-Item .env.example .env
# .env 값을 수정한 뒤 실행
docker compose up -d db
docker compose ps
docker compose logs --tail 50 db
docker compose exec db mysql -u lancerpick -p lancerpick
```

비밀번호 입력 후 SELECT 1; 과 SELECT @@session.time_zone; 을 실행한다. 접속 성공을 확인한 뒤 Spring Boot를 시작한다. DB 준비 전에 앱을 실행해 실패했다면 DB 준비 완료 후 다시 시작한다.

docker compose stop으로 멈춘다. 볼륨은 DB 데이터를 보관한다. down -v는 데이터를 삭제하므로 일상적인 종료에 사용하지 않는다. 기존 볼륨에서는 .env 비밀번호만 바꿔도 DB 계정 비밀번호가 바뀌지 않는다.

#### 팀원이 복제해서 실행하기

현재 저장소의 팀원은 [루트 README](../README.md)에 따라 개인 MySQL 접속 파일을 준비하고 실행한다. 아래 표는 3장의 Docker DB 예시를 선택했을 때의 절차다. Spring Initializr와 create-vite로 프로젝트를 다시 생성하지 않는다.

| 순서 | 작업과 확인 |
| --- | --- |
| 1 | GitHub 저장소 주소로 git clone. README에서 팀 기준 JDK·Node·Docker 버전 확인 |
| 2 | 루트 .env.example을 .env로 복사하고 로컬 비밀번호 입력 |
| 3 | 루트에서 docker compose up -d db. SQL 접속으로 준비 완료 확인 |
| 4 | IDE에 LancerPick을 Gradle 프로젝트로 열고 JDK 25·DB 환경변수 지정 |
| 5 | LancerPick 실행. Flyway·JPA 오류가 없는지 확인 |
| 6 | LancerPickFrontend에서 npm ci 후 npm run dev. localhost:5173 접속 |
| 7 | React → /api 프록시 → Spring 응답 확인. 인증 구현 후 세션 검증 추가 |
| 8 | 로컬 빌드·관련 테스트 실행. 커밋 번호와 결과를 완료표에 기록 |

#### 팀원이 복제해서 실행하기 / 자주 발생하는 문제

| 증상 | 확인 및 해결 |
| --- | --- |
| java 버전 불일치 | 터미널 Java, IDE SDK, Gradle JVM을 각각 확인한다. IDE만 바꾸면 터미널은 그대로일 수 있다. |
| DB 연결 거부 | Docker 실행·DB 로그·3307 포트·준비 완료 여부를 확인한다. 컨테이너 내부 포트는 3306이다. |
| DB Access denied | DB 사용자와 비밀번호를 확인한다. 기존 볼륨은 .env 변경으로 계정이 갱신되지 않는다. |
| Flyway checksum 오류 | 공유된 변경 파일을 임의 수정했는지 확인한다. 이력을 지우거나 repair부터 실행하지 않는다. |
| API에서 401 또는 403 | 세션·CSRF·권한을 순서대로 확인한다. CORS 문제로 단정하지 않는다. |
| 프록시 연결 실패 | 8080 백엔드 실행과 Vite proxy를 확인한다. 프런트 변경 후 서버를 재시작한다. |
| npm ci 실패 | Node·npm 버전 및 package.json과 lock 파일의 일치 여부를 확인한다. 임의로 lock 파일을 삭제하지 않는다. |

에러 공유 시 실행한 명령, 폴더, 도구 버전, 최초 오류 메시지를 남긴다. 비밀번호·쿠키·토큰은 지운다. 해결 방법은 README의 오류 해결 항목에 짧게 추가한다.

#### 완료 기록과 다음 작업 / 공통 환경 완료표

아래 표는 실제 실행 후 작성한다. 작성자 PC만 성공하면 공통 환경 완료로 보지 않는다. React 연결 완료와 인증 검증 완료를 구분하여 기록한다.

| 검증 항목 | 결과와 근거 |
| --- | --- |
| 버전·커밋 기준 | JDK / Node / npm / Gradle / DB 이미지 / 커밋 SHA |
| 실행 재현 | 팀원 1 ___  팀원 2 ___  팀원 3 ___  팀원 4 ___ |
| DB 연결 | SQL 접속, UTC 확인, Flyway 적용 상태 |
| 프런트 연결 | React 화면 → API 응답 표시 |
| 인증 연결 | 로그인 / 보호 API / CSRF 거절 / 로그아웃 |
| 빌드·자동 검사 | 로컬 빌드 결과 / PR 검사 링크 |
| 미완료 | 문제 / 담당자 / 해결 목표일 |

#### 설정 구분 / 전체 검사에서 사용하는 자원 제한

<a id="b-955-0"></a>
테스트 프로필의 Hikari 풀은 최대 6개 연결·최소 유휴 1개로 제한한다. 현재 최대 4개 작업자를 사용하는 동시성 검사를 유지하면서, 서로 다른 Spring 컨텍스트가 각각 기본 크기의 연결 풀을 유지해 MySQL 연결 한도를 소진하는 문제를 줄인다. 운영·개발 DB 풀 설정은 바꾸지 않는다.

Gradle test 작업은 `spring.test.context.cache.maxSize=8`을 테스트 JVM 시스템 속성으로 전달한다. Spring 테스트 컨텍스트 캐시는 test 프로필 설정 파일 대신 JVM 속성으로 제한한다. 캐시에서 제거된 컨텍스트의 연결 풀은 종료된다. 테스트 프로세스는 1개, JUnit 테스트 간 병렬 실행은 비활성화한다. 각 테스트 내부의 Executor·동시 요청 검사는 계속 실행한다. 여러 Gradle 실행을 같은 테스트 DB에 동시에 연결하는 것은 이 제한의 범위 밖이다.

CI는 성공·실패 시 JUnit XML과 HTML 테스트 보고서를 `backend-test-reports` 아티팩트로 7일간 보관한다. 취소된 실행은 업로드를 생략하며, 컴파일 오류처럼 보고서가 생성되지 않은 경우 경고만 남기고 기존 빌드 실패를 유지한다. 저장 경로는 저장소 루트 기준이며 개인 설정 파일과 DB 접속 파일은 업로드하지 않는다. GitHub Actions 실행 결과의 Artifacts에서 내려받아 실패 원인을 확인한다.

| 구분 | 설정 파일·입력 | 적용 내용 |
| --- | --- | --- |
| 공통 | LancerPick/src/main/resources/application.properties | OSIV=false, ddl-auto=validate, JDBC UTC, Flyway 활성화 |
| 일반 로컬 실행 | application-default.properties | 기존 local-application.properties를 선택적으로 import, DB_* 사용 |
| demo 예시 실행 | application-demo.properties | DB·JPA·Flyway 자동 설정 제외, 개인 파일 import 없음, 로컬 127.0.0.1 수신 |
| 통합 테스트 | src/test/resources/application-test.properties | TEST_DB_* 사용, 일반 개인 파일 import 없음 |
| CI | .github/workflows/ci.yml | 임시 lancerpick_ci와 TEST_DB_*, JPA 공통 설정 재사용 |

`@IntegrationTest`는 `@SpringBootTest`, `@ActiveProfiles("test")`, DB 접속 전 검사 Initializer를 묶은 테스트 어노테이션이다. DB를 사용하는 새 Spring 통합 테스트에도 이를 사용한다. 순수 단위 테스트와 DB를 명시적으로 제외한 공통 HTTP 테스트에는 적용하지 않는다. DB를 사용하는 애플리케이션 실행 프로필을 별도로 지정하면 default 프로필의 개인 파일 import가 적용되지 않으므로 DB_* 환경변수를 제공한다. demo는 의도적으로 DB 자동 설정을 제외하므로 DB_*가 필요 없다. prod는 배포용 이름이며 현재 파일만으로 배포 환경·인증·쿠키 정책이 완성되지는 않는다. 예시 API는 prod에서 제외된다.

#### PowerShell에서 실행

저장소 루트에서 새 터미널을 열고 실행한다. 테스트용 URL·계정·비밀번호는 현재 프로세스에만 전달하며 파일로 저장하지 않는다.

```powershell
$env:TEST_DB_URL = 'jdbc:mysql://localhost:3306/lancerpick_test?connectionTimeZone=UTC'
$env:TEST_DB_USERNAME = 'lancerpick_test'
$testDbCredential = Get-Credential -UserName lancerpick_test -Message '테스트 DB 계정 비밀번호'
$env:TEST_DB_PASSWORD = $testDbCredential.GetNetworkCredential().Password
cd .\LancerPick
.\gradlew.bat clean build
```

종료 후 같은 창에서 필요하면 테스트 비밀번호 환경변수를 제거한다.

```powershell
Remove-Item Env:TEST_DB_PASSWORD -ErrorAction SilentlyContinue
$testDbCredential = $null
```

일반 앱은 기존 방식으로 `bootRun`한다. `TEST_DB_*`가 없으면 통합 테스트는 실패하며 개발용 `DB_*`를 대신 사용하지 않는다. URL은 생략 시 `localhost:3306/lancerpick_test?connectionTimeZone=UTC`이고 계정·비밀번호는 필수다. 포트가 3307이면 TEST_DB_URL도 변경한다.

테스트 URL은 단일 MySQL 호스트와 DB 이름 `lancerpick_test` 또는 `lancerpick_ci`만 허용한다. 개발 DB·유사한 접미 이름·다른 DB 드라이버는 컨텍스트 초기화 단계에서 거부한다. 커스텀 TEST_DB_URL에는 `connectionTimeZone=UTC`를 유지한다. IPv6·다중 호스트 등 다른 URL 형식이 필요하면 검토 후 검사와 테스트를 함께 수정한다.

#### 토스페이먼츠 테스트 키 설정 안내

**실제 테스트 키는 황준범이 필요한 팀원에게 별도로 전달합니다.** 이 문서·예시 파일·PR·Git 저장소에는 실제 키 값을 기록하지 않습니다. 아래 `CHANGE_ME`는 실제 키가 아닌 자리 표시자입니다.

#### 현재 실행 설정

<a id="b-962-0"></a>
공유 application.properties의 `toss.topup-enabled=${TOSS_TOPUP_ENABLED:true}`에 따라 일반 실행 기본값은 true다. 외부 호출을 끄려면 `TOSS_TOPUP_ENABLED=false`를 지정한다. test 프로필은 `toss.topup-enabled=false`로 덮어쓰며 CI도 외부 충전을 비활성화한다. 개인 시크릿은 Git 제외 설정 또는 서버 환경변수로 관리한다. 실제 제공자 호출에는 테스트 시크릿이 필요하다.

#### 이번 설정의 범위

랜서픽에서는 실제 돈이 이동하지 않는 토스페이먼츠 테스트 연동을 준비합니다. 현재 확보한 키 종류는 다음과 같습니다.

| 키 | 사용할 곳 | 역할 |
| --- | --- | --- |
| `test_gck`로 시작하는 테스트 클라이언트 키 | React | 주문서형·결제창형 SDK 초기화 |
| `test_gsk`로 시작하는 테스트 시크릿 키 | Spring Boot 서버 | 결제 승인·조회·취소 API 인증 |

**키 설정은 외부 결제 기능의 구현 완료를 뜻하지 않습니다.** 충전 주문·금액 검증·승인·중복 지급 방지·결과 재확인 등은 별도 구현해야 합니다. 현재 확장 계획은 외부 테스트 결제로 적립금을 충전하고 기존 주문의 적립금 결제를 유지하는 방식이며, 이 문서는 MVP 범위를 변경하지 않습니다.

#### 백엔드 설정

파일 위치는 백엔드 프로젝트 루트의 `LancerPick/local-application.properties`입니다. `src/main/resources` 안에 만드는 파일이 아닙니다.

#### 백엔드 설정 / 개인 설정 파일이 없는 경우

처음 한 번만 `LancerPick/local-application.properties.example`을 `local-application.properties`로 복사하고 본인의 DB 정보와 전달받은 테스트 시크릿 키를 입력합니다. 기존 개인 파일을 덮어쓰지 않습니다.

공유 파일인 `LancerPick/src/main/resources/application.properties`에는 실제 키 대신 아래 참조만 보관합니다.

```properties
toss.secret-key=${TOSS_SECRET_KEY}
```

일반적인 IntelliJ 기본 실행은 `application-default.properties`를 통해 개인 설정 파일을 읽습니다. Working directory는 `LancerPick` 폴더로 지정합니다. 별도 프로필 실행에서는 해당 개인 파일이 자동으로 import되지 않을 수 있으므로 실행 방식에 맞춰 서버 환경 변수로 주입해야 합니다.

현재 설정은 일반 로컬 실행을 위한 준비입니다. `demo` 프로필이나 CI·테스트에 실제 토스 키를 추가할 필요는 없으며, 외부 연동 테스트를 도입할 때는 별도로 실행 범위와 설정을 정합니다.

#### 설정 후 확인

1. 클라이언트에는 `test_gck`, 서버에는 같은 세트의 `test_gsk`가 들어갔는지 직접 확인합니다. 키를 로그나 화면에 출력하지 않습니다.
2. 백엔드와 Vite를 재시작합니다. Vite는 실행 시 환경 변수 파일을 읽습니다.
3. 저장소 루트에서 개인 파일이 Git 제외 대상인지 확인합니다.

```powershell
git check-ignore -v LancerPick/local-application.properties LancerPickFrontend/keys.env
git status --short
```

공유하는 파일은 `application.properties`의 변수 참조, `local-application.properties.example`·`.env.example`의 자리 표시자, 이 안내 문서입니다. 실제 개인 설정 파일이 추적되고 있다면 커밋 전에 중단하고 확인합니다. `.gitignore`는 이미 추적된 파일의 이력을 자동으로 제거하지 않습니다.

환경 변수를 읽도록 설정했다고 결제창이 자동으로 생기지는 않습니다. React SDK 초기화와 서버 승인 API 연결은 후속 구현 대상입니다.

### 데이터·제약

#### Spring Boot와 DB 연결

현재 공통 설정 파일은 `LancerPick/src/main/resources/application.properties`다. 아래 YAML은 설정 항목을 설명하는 예시다. 적용할 경우 기존 properties에서 같은 항목을 관리하거나 설정 형식을 하나로 통일하며, 두 파일에 중복 정의하지 않는다. 비밀번호는 개인 접속 파일 또는 실행 환경변수에서 읽고 배포 설정은 별도로 둔다.

```
server:
  port: 8080
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.jdbc.time_zone: UTC
  flyway:
    enabled: true
```

#### Spring Boot와 DB 연결 / 환경변수 전달

Compose가 읽는 루트 .env는 PC에서 실행하는 Spring Boot에 자동 전달되지 않는다. IDE의 실행 구성에 다음 세 값을 넣는다. DB_PASSWORD는 .env의 MYSQL_PASSWORD와 같은 값이다. 개인 실행 구성은 커밋하지 않는다.

```
DB_URL=jdbc:mysql://localhost:3307/lancerpick?connectionTimeZone=UTC
DB_USERNAME=lancerpick
DB_PASSWORD=각자 설정한 로컬 비밀번호
```

터미널에서 실행한다면 같은 PowerShell 창에 아래와 같이 환경변수를 설정한다. 비밀번호는 입력 창에서 받아 명령 이력에 남기지 않는다.

```
$env:DB_URL = 'jdbc:mysql://localhost:3307/lancerpick?connectionTimeZone=UTC'
$env:DB_USERNAME = 'lancerpick'
$dbCredential = Get-Credential -UserName lancerpick
$env:DB_PASSWORD = $dbCredential.GetNetworkCredential().Password
cd LancerPick
.\gradlew.bat --version
.\gradlew.bat bootRun
```

#### Spring Boot와 DB 연결 / Flyway 적용

MySQL 공개키 조회 관련 오류가 발생하면 먼저 Connector/J와 인증·TLS 설정을 확인한다. 로컬 개발용 우회 옵션을 배포 JDBC URL에 그대로 복사하지 않는다.

#### 공통 JPA 설정과 테스트 DB 실행

역할 배정 갱신 2026-10-29: [확정 담당·지원 원칙·업무 비중](team-roles.md#b-1131-0)을 따른다. 상세 일정·검토자·배포 담당은 별도 확정한다.

변경일: 2026-10-29. 개인 접속 파일과 기존 DB 데이터는 변경하지 않았다.

#### 로컬 테스트 DB 준비

```sql
CREATE DATABASE IF NOT EXISTS lancerpick_test CHARACTER SET utf8mb4;
CREATE USER 'lancerpick_test'@'localhost' IDENTIFIED BY 'REPLACE_WITH_TEST_ONLY_PASSWORD';
GRANT ALL PRIVILEGES ON lancerpick_test.* TO 'lancerpick_test'@'localhost';
```

이 계정에는 개발 DB 권한을 부여하지 않는다. 접속 호스트에 따른 MySQL 계정 매칭은 실제 환경에서 확인한다. DB 이름 검사에 더해 계정 권한으로도 실수에 의한 개발 DB 변경을 제한한다. 테스트가 데이터를 생성할 수 있으므로 개인·공유 개발 DB를 테스트용 이름으로 바꿔 재사용하지 않는다.

#### 백엔드 설정 / 이미 개인 DB 설정 파일이 있는 경우

기존 DB 설정은 유지하고 아래 항목만 추가합니다. `CHANGE_ME`를 별도로 전달받은 테스트 **시크릿 키**로 바꿉니다.

```properties
TOSS_SECRET_KEY=CHANGE_ME
```

<a id="admin-observability"></a>
## 관리자 운영 현황·알림 처리 실패 — main #149·#151

### 9-4 관리자 알림 처리 실패 조회 김창재

2026-11-09 `feature/admin-outbox-failures`(K6 알림 `FAILED` 조회). 조회만 하며 재처리(다시 보내기) API는 없다([관리자 작업 감사 이력 설계안](change-history.md#b-19-0) 확정 뒤 별도). GET·`Cache-Control: no-store`·관리자 전용, 현재 DB 역할 재확인(강등 세션 403). DB 변경 없음.

| 메서드·경로 | 입력 | 출력 |
| --- | --- | --- |
| GET /admin/outbox-events | status(`FAILED` 기본·`RETRYING`), orderId(선택), page, size(1~100, 기본 20) | OutboxIssue 목록, 이벤트 ID 내림차순(최근 먼저) |

- OutboxIssue: id, eventId, type, aggregateType, aggregateId, status, attemptCount, lastErrorCode, nextAttemptAt, createdAt. ID는 문자열, 시각은 UTC.
- `FAILED`: 자동 재시도를 멈춘 이벤트(`outbox_events.status='FAILED'`), nextAttemptAt은 null. `RETRYING`: 실패 후 다음 시도를 기다리는 이벤트(`PENDING`이며 attempt_count ≥ 1). 처음 대기 중(시도 0회)·완료 이벤트는 나오지 않는다.
- orderId는 `aggregate_type='ORDER'`인 이벤트만 거른다. lastErrorCode는 [공통 이벤트 계약](architecture.md#events)의 고정 코드다.
- 페이로드(수신자 등)는 응답하지 않는다. status는 대소문자 그대로 두 값만, orderId는 양수, page 0 이상. 그 밖의 값은 400 `VALIDATION_FAILED`.
### 9-5 관리자 운영 현황 김창재

2026-11-09 `feature/admin-dashboard`(K3 운영 현황). 기존 관리자 조회(문의·타이머·알림·충전)의 건수만 모은다. GET·`Cache-Control: no-store`·관리자 전용, 현재 DB 역할 재확인(강등 세션 403). 조회만 하며 행 잠금·DB 변경 없음.

| 메서드·경로 | 입력 | 출력 |
| --- | --- | --- |
| GET /admin/dashboard | 없음 | inquiries{open,inReview}, reviewTimers{active,expiringWithin24Hours}, notifications{failed,retrying}, topups{unknown}, checkedAt |

- inquiries: `support_inquiries` 상태 OPEN·IN_REVIEW 건수(유형 무관). reviewTimers: ACTIVE 전체와 만료 시각이 서버 현재 시각+24시간 **전**인 것(이미 지났지만 처리 전 포함). notifications: [9-4절](architecture.md#admin-observability)의 FAILED·RETRYING 조건. topups: 상태 UNKNOWN.
- 각 숫자는 조회 순간의 값이며 하나의 스냅샷을 보장하지 않는다. checkedAt은 UTC(마이크로초).
