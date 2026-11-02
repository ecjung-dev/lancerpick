# 주문 목록·상세 조회 API

## 구현 범위

정은찬 담당 GET /api/orders와 GET /api/orders/{id}를 구현했다. CurrentMemberId의 인증 principal 계약을 사용하며 실제 로그인 필터·React 주문 화면·후속 주문 상태 변경은 포함하지 않는다. 읽기 요청은 CSRF 토큰 없이 사용할 수 있고, 기존 Security의 인증 요구는 유지한다. 응답은 Cache-Control: no-store다. demo 프로필에서는 DB 기반 주문 Controller·서비스를 만들지 않는다.

## 목록

```text
GET /api/orders?side=BUYER&page=0&size=20
GET /api/orders?side=SELLER&status=REQUESTED
```

- side는 BUYER 또는 SELLER 필수다. 요청의 memberId·buyerId·sellerId로 조회 소유자를 바꿀 수 없다.
- status는 선택이며 지정하면 기존 주문 상태 enum의 정확한 대문자 값이어야 한다. 빈 문자열·잘못된 값은 400이다.
- page 기본 0, size 기본 20/최대 100. 음수 page·범위 밖 size·정수로 변환할 수 없는 값은 400이다.
- 응답은 공통 PageResponse(items,page,size,totalElements,totalPages), 항목은 API 명세의 OrderSummary다.
- 로그인 회원의 해당 side와 status를 건수·항목 조회에 동일하게 적용한다. created_at DESC, id DESC로 정렬한다.
- productTitle은 저장 스냅샷, priceAmount는 주문 당시 금액이다. counterparty는 BUYER 조회 시 판매자, SELLER 조회 시 구매자의 현재 id·displayName만 제공한다.
- currentDueAt은 결제 전 null, 결제 후 수정 요청 기한 우선/없으면 최초 납기다. 미발생 시각은 null이다. 별도의 지연 표시 필드나 DB 컬럼을 추가하지 않는다.
- 큰 page는 long offset으로 계산하며 범위를 벗어나면 빈 items와 정확한 전체 건수를 반환한다. 목록이 없으면 totalPages는 0이다.
- 스냅샷이 누락된 주문을 목록에서 조용히 제외하지 않는다. 해당 항목을 읽으면 500 DATA_INTEGRITY_ERROR다.

## 상세

```text
GET /api/orders/15
```

id는 양의 BIGINT 범위 10진 문자열이다. 형식·범위 오류는 400, 없는 주문과 비참여자의 주문은 모두 404 RESOURCE_NOT_FOUND다. 구매자·판매자만 같은 상세 데이터에 접근하며 allowedActions는 실행자 역할과 현재 상태에 따라 달라진다. 관리자 권한만으로 참여자 제한을 우회하지 않는다.

기존 OrderDetailQueries·OrderDetailResponse·OrderAllowedActions를 재사용한다. 상품이 수정·숨김·판매 중지되어도 과거 주문 조회를 막거나 당시 조건을 다시 만들지 않는다. 하위 기록의 DTO·null/빈 배열·문자열 ID·UTC 표현은 [API 명세](api-spec.md#4-주문-조회와-생성-정은찬)와 [생성 API의 상세 응답](order-creation.md#http-생성-api-후속-구현)을 따른다.

## 읽기 일관성과 검증 경계

OrderQueryService는 readOnly·REPEATABLE_READ 트랜잭션을 사용해 count/목록과 상세의 여러 테이블을 같은 읽기 스냅샷으로 조회한다. FOR UPDATE를 추가하지 않으며 생성의 READ_COMMITTED와 쓰기 잠금 순서는 변경하지 않는다. 시각은 DATETIME 원문을 LocalDateTime으로 읽어 UTC OffsetDateTime으로 변환한다. 조회가 주문·지갑·이력을 생성하거나 상태를 바꾸지 않는다.

필수 side 누락의 Spring 예외도 공통 400 VALIDATION_FAILED로 변환했다. 오류 응답의 fieldErrors·traceId 계약을 유지한다. 읽기 범위·입력 경계·DB 조합에 집중한 OrderQueryApiIntegrationTests를 추가했다. 후속 납기 상태는 SQL fixture로 구성했으며 실제 결제·수정 요청 서비스 실행 검증은 아니다. 최종 실행 결과는 [작업표](work-plan.md)를 따른다.
