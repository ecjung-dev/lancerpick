# 랜서픽 MVP API 명세서

2026-10-29 구조 검토 반영: [변경 기록](review-changes.md). 실제 구현·실행 검증과 구분한다.

개정 기록 2026-10-29 | 정책·역할·기술 정리, 취소 요청 식별, 환불 후 결제 재요청, 납품 회차·지연 표시 보완 | 구현·설치·페이지 시각 검증 미완료

버전 1.0 | 작성일 2026-10-29

MVP의 화면과 서버가 사용할 요청·응답·권한·중복 처리 기준이다. 구현 기준을 정리한 문서이며 실제 API 구현·테스트 완료를 의미하지 않는다. 업무 정책은 MVP 기획 명세서, 데이터 구조는 ERD, 개발 방식과 기술 버전은 공통 개발 규칙을 따른다.

## 1 공통 계약

기본 경로 /api. JSON·UTF-8, 세션 쿠키 인증, 변경 요청 CSRF 보호. ID는 JSON 문자열, 금액은 원 단위 정수, 시간은 시간대가 포함된 ISO 8601이다. 비밀번호·토큰·내부 오류를 응답·로그에 노출하지 않는다.

목록은 page=0, size=20, 최대 100. 최신 생성순과 ID 내림차순을 기본으로 하며 categories는 displayOrder·ID 오름차순이다. 목록 응답은 items, page, size, totalElements, totalPages를 가진다.

GET·수정·업무 명령 성공은 200, 리소스 생성은 201, 로그아웃은 204. 안전한 생성 재호출은 200과 기존 결과. 오류는 code, message, fieldErrors(배열), traceId를 반환한다. 비공개 타인 자원은 404, 관리자 기능 권한 부족은 403으로 처리한다.

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

## 2 인증과 회원 A

| 메서드·경로 | 입력·권한 | 출력·처리 |
| --- | --- | --- |
| GET /auth/csrf | 전체 | token, headerName |
| POST /members | 비로그인; email,password,displayName | 201 Member; 회원·지갑·100만원 지급 원자적 생성 |
| POST /auth/login | 비로그인; email,password | 200 Member; 세션 발급 |
| POST /auth/logout | 회원; 없음 | 204; 세션 무효화 |
| GET /me | 회원 | Member |
| PATCH /me | 회원; displayName | Member |

Member: id,email,displayName,role,hasSellerProfile. 이메일은 공백 제거·소문자 정규화 후 중복 확인한다. 가입 후 로그인 화면으로 이동하며 자동 로그인하지 않는다. 로그인·로그아웃 후 CSRF 토큰을 다시 획득한다.

## 3 판매자와 상품 B

| 메서드·경로 | 입력·권한 | 출력 |
| --- | --- | --- |
| GET /categories | 전체 | id,name,displayOrder 배열 |
| GET /sellers/{id} | 전체 | SellerProfile |
| GET /me/seller-profile | 회원 | 본인 SellerProfile, 미등록 404 |
| PUT /me/seller-profile | 회원; introduction,specialty,portfolioUrl | 생성 201 / 수정 200 |
| GET /products | 전체; keyword,categoryId,page,size | ProductSummary 목록 |
| GET /products/{id} | 전체; 공개·판매 중 상품 | ProductDetail |
| GET /me/products | 회원; page,size | 본인 상품 목록; 중지·숨김 포함 |
| GET /me/products/{id} | 소유자 | 본인 ProductDetail |
| POST /products | 판매자 프로필 보유; ProductInput | 201 ProductDetail |
| PATCH /products/{id} | 소유자; ProductInput,expectedVersion | ProductDetail |
| PATCH /products/{id}/sale-status | 소유자; saleStatus,expectedVersion | ProductDetail |
| GET /products/{id}/reviews | 전체; 공개·판매 중 상품, page,size | Review 목록 |

SellerProfile: id(회원 ID),displayName,introduction,specialty,portfolioUrl. 이메일·잔액은 공개하지 않는다. ProductInput: categoryId,title,description,scopeDescription,preparationDescription,priceAmount,durationDays. 가격 1~1,000,000원, 기간 1~30일. portfolioUrl은 선택이다.

ProductSummary: id,title,priceAmount,durationDays,category,seller. ProductDetail은 description,scopeDescription,preparationDescription,version,saleStatus,createdAt,updatedAt을 추가한다. 본인·관리자 조회는 isHidden 추가. 상품 변경·판매 상태 변경·숨김 변경은 버전 충돌 검사와 일관되게 관리한다. 숨김·중지 상품의 기존 거래는 주문 스냅샷으로 조회한다.

## 4 주문 조회와 생성 C

| 메서드·경로 | 입력·권한 | 출력 |
| --- | --- | --- |
| GET /orders | 회원; side=BUYER 또는 SELLER 필수, status 선택, page,size | OrderSummary 목록 |
| POST /orders | 구매자; productId,expectedProductVersion,requirements,requestKey | 201 OrderDetail / 중복 200 |
| GET /orders/{id} | 주문 참여자 | OrderDetail |

생성 시 상품 상태·버전·자기 상품 구매 금지를 검증한다. 가격·판매자·거래 조건은 서버에서 복사한다. 같은 구매자·요청 키·입력은 기존 주문 반환, 같은 키·다른 입력은 409 IDEMPOTENCY_KEY_REUSED. 기존 키 확인을 현재 상품 조건 검사보다 먼저 수행하여 성공한 주문의 재전송을 새 주문으로 처리하지 않는다.

OrderSummary: id,status,productTitle,priceAmount,counterparty,createdAt,currentDueAt. OrderDetail: id,status,buyer,seller,productId,productSnapshot,termsSnapshot,priceAmount,durationDays,requirements,createdAt,acceptedAt,buyerConfirmedTermsAt,paidAt,originalDueAt,revisionDueAt,currentDueAt,completedAt,cancelledAt,deliveries,revisionRequest,cancellationRequests,histories,payment,review,allowedActions. 판매자 수익은 본인 정산 API에서 조회한다.

buyer·seller는 id·displayName만 제공한다. originalDueAt=orders.due_at, revisionDueAt=revision_requests.due_at이다. currentDueAt은 수정 요청이 있으면 수정 기한, 없으면 최초 기한이며 결제 전에는 null이다. 별도 DB 컬럼을 추가하지 않는다. 종료 거래에 지연 경고를 표시하지 않는다.

주문 상태: REQUESTED, AWAITING_PAYMENT, IN_PROGRESS, AWAITING_CONFIRMATION, REVISION_IN_PROGRESS, COMPLETED, REJECTED, CANCELLED. 허용 행동: ACCEPT, REJECT, PAY, CANCEL, DELIVER, REQUEST_REVISION, CONFIRM, REQUEST_CANCELLATION, APPROVE_CANCELLATION, REJECT_CANCELLATION, WITHDRAW_CANCELLATION, WRITE_REVIEW.

### 주문 상세 내부 응답 계약

productSnapshot: title,description,scopeDescription,preparationDescription,productVersion. termsSnapshot은 보존된 거래 조건 문자열이다. category는 id,name, seller·counterparty는 id,displayName을 제공한다.

deliveries 항목: id,sequenceNo,description,resultUrl,submittedAt. revisionRequest: id,deliveryId,reason,requestedAt,dueAt. histories 항목: id,actorId,action,fromStatus,toStatus,reason,createdAt. 최초 이력의 fromStatus와 사유가 없는 행동의 reason은 null이다.

payment: id,amount,feeRateBps,status,paidAt,refundedAt. review는 8절의 Review 계약을 따른다. cancellationRequests는 6절 cancellationRequest 계약의 배열이다.

생성 전 단일 객체 payment·revisionRequest·review는 null, 이력·납품·취소 요청·allowedActions는 빈 배열로 표현한다. 미발생 시각 acceptedAt·buyerConfirmedTermsAt·paidAt·originalDueAt·revisionDueAt·currentDueAt·completedAt·cancelledAt 및 refundedAt·resolvedAt·resolvedBy는 null이다. 선택 입력 portfolioUrl·review.content·responseReason은 미입력 시 null이다. 나머지 필수 필드는 생략하지 않는다.

## 5 주문 명령 C와 D

| POST 경로 | 실행자·입력 | 조건·결과 |
| --- | --- | --- |
| /orders/{id}/accept | 판매자; 없음 | REQUESTED → AWAITING_PAYMENT |
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

## 8 지갑 정산 리뷰 D와 B

| 메서드·경로 | 권한·입력 | 출력 |
| --- | --- | --- |
| GET /me/wallet | 회원 | balance,currency=KRW,isTest=true |
| GET /me/wallet/entries | 회원; page,size | WalletEntry 목록 |
| GET /me/settlements | 본인 판매 수익; page,size | Settlement 목록 |
| GET /me/settlements/summary | 본인 판매 수익 | completedCount,grossAmount,feeAmount,sellerAmount |
| POST /orders/{id}/review | 완료 주문 구매자; rating,content | 201 Review |

WalletEntry: id,type,amountDelta,balanceAfter,orderId,createdAt. 초기 지급의 orderId는 null. Settlement: id,orderId,grossAmount,feeRateBps,feeAmount,sellerAmount,createdAt. Review: id,orderId,authorDisplayName,rating,content,createdAt. 공개 리뷰 조회에는 orderId를 제외한다. 별점 1~5 필수, 본문 선택, 주문당 1개.

## 9 관리자 상품 운영 A

| 메서드·경로 | 입력 | 출력 |
| --- | --- | --- |
| GET /admin/products | keyword,isHidden,saleStatus,page,size | 관리자 상품 목록 |
| GET /admin/products/{id} | 없음 | 상품 상세 |
| GET /admin/products/{id}/moderation-logs | page,size | id,adminId,action,reason,createdAt 목록 |
| PATCH /admin/products/{id}/visibility | hidden,reason,expectedVersion | 관리자 ProductDetail |

모든 경로는 관리자 전용이다. 숨김 상태와 이력을 함께 저장하며 판매 상태는 변경하지 않는다. 관리자에게 거래·지갑·강제 환불 API를 제공하지 않는다.

관리자 상품 상세는 본인 조회와 같은 ProductDetail 필드에 isHidden을 포함한다. 숨김·복구는 상품 잠금 후 현재 상태를 확인한다. 이미 요청한 hidden 상태라면 버전 차이와 관계없이 현재 상세를 반환하고 버전·운영 이력을 변경하지 않는다. 실제 상태 변경이 필요하면 expectedVersion을 검사하고, 불일치는 409 PRODUCT_VERSION_CONFLICT로 처리한다. 변경 성공 시 상품 version 증가와 숨김 상태·운영 이력을 같은 트랜잭션에서 반영한다. 판매자 상품 수정·판매 상태 변경도 이 version과 일관되게 처리한다.

## 10 오류와 입력 기준

| HTTP | 주요 코드 |
| --- | --- |
| 400 | VALIDATION_FAILED, TERMS_AGREEMENT_REQUIRED, INVALID_URL |
| 401 | AUTHENTICATION_REQUIRED, INVALID_CREDENTIALS |
| 403 / 404 | FORBIDDEN, CSRF_INVALID / RESOURCE_NOT_FOUND |
| 409 | EMAIL_ALREADY_EXISTS, PRODUCT_VERSION_CONFLICT, PRODUCT_NOT_ORDERABLE, SELF_PURCHASE_NOT_ALLOWED |
| 409 | ORDER_STATE_CONFLICT, INSUFFICIENT_BALANCE, IDEMPOTENCY_KEY_REUSED |
| 409 | CANCELLATION_PENDING, REQUEST_ALREADY_RESOLVED, REVISION_ALREADY_USED, REVIEW_ALREADY_EXISTS, PAYMENT_ALREADY_REFUNDED, DELIVERY_ALREADY_SUBMITTED, DELIVERY_SEQUENCE_CONFLICT |
| 500 | INTERNAL_ERROR, DATA_INTEGRITY_ERROR |

확정 업무 입력: 가격 1~1,000,000원, 기간 1~30일, 별점 1~5, requestKey UUID, 결과물 URL HTTPS. 문자열 길이는 물리 SQL·DTO·화면을 함께 맞출 때 확정한다. 앞서 제안된 길이 수치는 합의된 업무 정책으로 간주하지 않는다.

### 구현 전 확정할 계약

다음 항목은 아직 확정되지 않았으며 담당자가 [작업표](work-plan.md)에 결정과 검증 결과를 기록한 뒤 이 문서에 반영한다. 임시 구현값을 합의된 정책으로 표현하지 않는다.

| 항목 | 함께 정할 내용 |
| --- | --- |
| 문자열 검증 | 필드별 필수 여부·최소/최대 길이·공백 정규화, DTO·DB·화면의 일치 |
| 상품 생성 | 최초 saleStatus와 isHidden의 서버 기본값 |
| 상품 수정 | ProductInput 전체 제출인지 부분 제출인지, 생략·null·빈 문자열의 구분 |
| 관리자 목록 | 목록 항목의 필드와 필터의 생략 시 동작 |
| 요청·응답 예시 | 인증·상품·주문·취소·결제의 정상/오류 JSON, ID·시각·null 표현 |

주문·취소 요청 키의 중복 처리와 납품 회차·지연 표시의 상세 API 규칙은 이 문서 4~7절을 기준으로 한다. 다른 문서에는 목적과 데이터 제약을 남기고 상세 계약은 이 문서를 참조한다.

## 11 DB 변경과 완료 검증

기존 15개 테이블을 유지한다. cancellation_requests.request_key 필수 컬럼과 (order_id, requested_by, request_key) UNIQUE를 추가한다. DB 미생성 환경은 최초 스키마에 반영한다. 이미 데이터가 있는 환경은 새 마이그레이션으로 기존 행에 고유 키를 채운 후 NOT NULL·UNIQUE를 적용한다. 기존 주문·잔액을 삭제하지 않는다.

| 시나리오 | 기대 결과 |
| --- | --- |
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

위 항목은 실행할 테스트 목록이며 현재 테스트 통과를 의미하지 않는다. 구현 후 실제 MySQL·API·React 연결을 검증한다. 실제 담당자·검토자·배포 담당과 첫 통합·배포일은 팀 작업표에서 확정하고, 라이브러리 패치는 설치 검증 후 기록한다.
