# 주문 목록·상세 조회 API

> 문서 읽기 기준(2026-11-04): 아래 날짜·브랜치별 구현 및 검증 설명은 당시 기록이다. 현재 반영 여부·남은 작업은 [현황표](current-status.md), 실행 설정은 [루트 README](../README.md), API 계약은 [API 명세](api-spec.md)를 따른다. 과거의 미구현·병합 전 표현과 검사 수를 현재 상태로 확대하지 않는다.

2026-11-03 저장소 후속 변경: [프로젝트 JPA·QueryDSL 통일](project-persistence.md)에 따라 현재 운영 구현에서 JdbcTemplate·네이티브 SQL을 제거했다. 아래 이전 SQL 구현 설명·실행 결과는 당시 기록으로 보존하며 현재 구조와 최종 검증 범위는 후속 문서를 따른다.

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

## 주문 목록 화면 후속 (2026-11-04)

`/orders`는 구매/판매 구분·상태 필터·페이지를 URL에 보존한다. 전체 선택과 잘못된 상태 값은 status 없이 조회하며, 구분·상태 변경 시 첫 페이지로 돌아간다. 조회 실패 중에도 필터를 다시 고를 수 있다. 한국어 상태·주문 시각·현재 납기를 표시하고 결제 전처럼 납기가 없으면 생략한다. 최신 main의 직접 URL·페이지 복원 동작을 유지한 채 프런트 대역 테스트와 빌드를 확인했다. 실제 브라우저 거래 검증은 별도다.

후속 `codex/order-navigation-polish`에서는 목록에서 상세로 이동할 때 현재 side·status·page 쿼리를 전달하고 상세의 거래 내역 링크에서 복원한다. 목록·상세의 상태 이름은 같은 변환 함수를 사용한다. URL과 상세 렌더링 검사는 통과했으며 실제 로그인 브라우저 이동은 별도다.

주문 이력에서도 같은 상태 이름을 사용하고 취소 요청의 대기·승인·거절·철회를 한국어로 표시한다. 서버 상태 값과 허용 행동 판단은 바꾸지 않는다.

후속 `codex/order-screen-states`에서는 목록의 빈 결과·인증 만료·서버 오류를 구분하고 필터를 유지한다. 상세 조회 오류에는 API 오류 안내·재조회·필터를 유지한 목록 복귀를 제공한다. 좁은 화면에서 탭 버튼·입력·필드셋이 가로로 넘치지 않도록 CSS를 보완했다. 렌더링 검사와 빌드 결과이며 실제 모바일 브라우저 검증은 별도다.

## 주문 이력 화면 후속 (2026-11-03)

`/orders/{id}`는 서버의 histories 배열을 받은 순서대로 표시한다. 서버 조회는 created_at·id 오름차순이다. 행동·구매자/판매자 역할·시각·이전/이후 상태를 보여주고, 상태가 그대로인 취소 요청·해결 이벤트는 상태 유지로 구분한다. reason이 null이면 사유를 표시하지 않으며 빈 배열은 안내 문구를 표시한다. 이력은 조회용이고 클라이언트에서 새 이력이나 상태를 만들지 않는다. 프런트 lint·build·API 테스트 33개 통과. 실제 브라우저 조작은 별도다.

## 주문 상세 지연 표시 후속 (2026-11-03)

IN_PROGRESS와 REVISION_IN_PROGRESS에서만 현재 적용 기한을 현재 시각과 비교해 작업 지연을 표시한다. 확인 대기·종료 상태에는 현재 지연 경고를 표시하지 않는다. 각 납품은 1회차 원래 기한, 2회차 수정 기한과 제출 시각을 마이크로초 단위로 비교한다. 같은 시각은 지각이 아니며, 1마이크로초라도 늦으면 지각이다. 현재 시각은 브라우저의 밀리초 정밀도를 사용한다. 기한이 없는 비정상 응답에는 지각 단정을 표시하지 않는다. 별도 DB 필드나 API 계산값은 추가하지 않았다. 화면 새로고침 시 다시 계산하며 실시간 타이머는 없다. 이전 34개 검사는 마이크로초 보완 전 기록이며, 실제 새 상세 표시의 브라우저 조작은 후속이다.

주문 상세의 보관 조건 화면은 주문 시점의 상품 설명·작업 범위·준비 사항·거래 규칙과 요구사항을 구분해 표시한다. 현재 상품이 수정되어도 상세 응답의 스냅샷만 사용한다.

## 결제·후기 기록 표시 후속 (2026-11-03)

주문 상세의 선택 객체 payment가 있으면 현재 결제 상태·금액·결제 시각과 환불 시각을 표시한다. review가 있으면 작성자 표시 이름·별점·작성 시각·선택 본문을 보여준다. 두 값이 null이면 해당 영역을 만들지 않는다. 조회 응답만 사용하며 결제나 후기의 새 쓰기 기능을 의미하지 않는다.
