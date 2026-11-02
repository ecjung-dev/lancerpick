# 주문 생성 서비스·HTTP 연결

작성일: 2026-11-02. 담당: 정은찬. 브랜치: `codex/order-create-service`.

## 구현 범위

API 명세 4·10·11절과 기존 SQL을 바탕으로 `OrderCreationService.create(buyerId, request)`를 구현했다. 주문·스냅샷·최초 이력을 실제 서비스 트랜잭션으로 저장한다. 내부 반환값은 `Result(orderId, created)`이며 HTTP `OrderDetail` 응답이 아니다. 후속 `OrderCreationApiService`와 Controller가 인증 principal의 회원 ID를 제공하고 상세 DTO로 조립해 신규 생성은 201, 동일 입력 재전송은 200으로 반환한다. GET 목록/상세는 [주문 조회 문서](order-query.md)를 따르며 실제 로그인·React 연결은 아직 없다.

현재 main에 실제 회원·상품 Entity가 없어 해당 영역을 중복 구현하지 않았다. 주문 전용 `OrderCreationQueries`가 기존 members·products를 조회한다. 테스트 회원·상품은 SQL fixture이며 실제 가입·상품 등록 기능 완료를 의미하지 않는다.

## 처리 순서와 재전송

1. 인증 연결부에서 받은 구매자 ID와 요청 형식을 검증한다. 서비스 자체도 버전 누락·음수, ID·UUID·요구사항을 검사한다.
2. 상품의 판매자 ID만 일반 조회해 잠금 대상을 정한다. 이때 상품 상태·버전·가격 조건을 검사하거나 주문을 생성하지 않는다.
3. 구매자와 사전 조회한 판매자 회원을 **ID 오름차순으로 한 행씩** 잠근다. 구매자 회원이 없으면 AUTHENTICATION_REQUIRED로 거절한다. 이 존재 검사는 실제 로그인을 대신하지 않는다.
4. 구매자·정규화한 요청 키로 기존 주문과 스냅샷을 잠금 조회한다. 기존 주문이면 상품 ID·최초 버전·정규화한 요구사항을 비교해 같으면 기존 ID, 다르면 409 IDEMPOTENCY_KEY_REUSED를 반환한다.
5. 신규 요청이면 상품 행을 잠근다. 상품의 현재 판매자 ID가 사전 조회와 다르면 409 PRODUCT_VERSION_CONFLICT로 거절한다. 잠그지 않은 판매자의 FK를 추가로 참조하지 않는다.
6. 상품 없음·관리자 숨김은 404 RESOURCE_NOT_FOUND, 판매 중지는 409 PRODUCT_NOT_ORDERABLE, 자기 상품은 409 SELF_PURCHASE_NOT_ALLOWED, 버전 불일치는 409 PRODUCT_VERSION_CONFLICT로 처리한다. 상품 잠금 후 검사는 없음/숨김→판매자 변경→판매 중지→자기 상품→버전 순서다.
7. 현재 가격·판매자·소요일·설명은 서버 조회값만 사용한다. UTC 시각을 마이크로초로 준비해 주문 REQUESTED·스냅샷·최초 CREATED 이력을 함께 저장한다.

성공했던 요청은 상품 수정·판매 중지·숨김이나 주문 종료 이후에도 기존 입력으로 재전송할 수 있다. 기존 키 확인은 현재 상품 조건 검사와 현재 거래 규칙 생성보다 먼저 수행한다. UUID는 소문자 하이픈 표준형, 요구사항은 공통 Unicode 공백·줄바꿈 정규화 결과로 비교한다. 새 키는 별도 주문을 생성한다. DB 오류 전부를 멱등 성공으로 바꾸거나 실패한 트랜잭션 안에서 재시도하지 않는다.

## 트랜잭션과 잠금 연결 기준

이번 구현은 주문 생성 진입점에만 READ_COMMITTED를 사용한다. REPEATABLE READ에서 없는 요청 키를 FOR UPDATE로 조회하면 다른 구매자의 신규 INSERT와 간격 잠금이 충돌할 수 있어 이를 피한다. 기본 DB 격리 수준·지갑 초기화의 기존 REPEATABLE READ 검증은 변경하지 않았다.

신규 주문의 잠금 순서는 **참여 회원 ID 오름차순 → 요청 키 조회 → 상품**이다. 구매자만 잠그면 A가 B의 상품을, B가 A의 상품을 구매할 때 orders.seller_id 외래 키의 공유 잠금과 상대 회원의 배타 잠금이 교차해 데드락이 발생한다. 격리 MySQL 두 연결에서 ERROR 1213을 재현했고 두 참여 회원을 같은 순서로 잠그도록 보완했다. 하나의 ORDER BY 쿼리가 아니라 작은 ID 행부터 개별 잠금 조회한다.

같은 구매자뿐 아니라 같은 판매자의 주문도 회원 행에서 직렬화된다. 안전성을 위한 보수적 선택이며 처리량은 측정하지 않았다. 서로 다른 참여 회원 조합은 별도로 진행할 수 있지만 상품·요청 키 등 추가 경합은 가능하다. 교차 구매 회귀 테스트는 실제 서비스 두 호출을 판매자 사전 조회 지점에서 맞춰 5회 실행한다. 모든 경로에서 데드락이 불가능하다는 보장은 아니며 다른 서비스가 역순 회원 잠금을 도입하면 재검토해야 한다.

서비스는 상위 READ_COMMITTED 트랜잭션에는 참여할 수 있으며 상위 롤백 시 세 기록도 함께 롤백한다. 격리 수준이 다르거나 지정되지 않은 기존 상위 트랜잭션에는 쓰기 전에 IllegalTransactionStateException으로 거절한다. REQUIRES_NEW로 상위 업무와 분리 커밋하지 않는다. Controller가 업무 시작 서비스로 호출하는 것이 기본 연결 방식이다. 상위 트랜잭션이 이미 회원·상품·주문 잠금을 보유했다면 전체 획득 순서를 먼저 검토해야 하며 격리 수준 일치만으로 데드락 안전성이 보장되지는 않는다.

이 순서는 이번 구현의 연결 기준이며 전원 합의나 회원·상품 담당자의 구현 완료를 뜻하지 않는다. 상품 수정·판매 상태·관리자 숨김 쓰기는 같은 products 행의 version을 일관되게 변경해야 한다. 다른 경로에서 회원 ID 역순 잠금이나 상품 잠금 후 참여 회원 잠금을 추가하기 전 담당자 간 검토가 필요하다. 결제·취소의 기존 **주문 → 구매자 지갑** 잠금 순서는 변경하지 않는다.

## 거래 규칙 원문

`OrderTerms.currentText()`는 [MVP 정책](mvp-spec.md)에서 주문 조건 보존·수락/결제·납기·수정 1회·취소/환불·구매 확정·수수료 규칙을 구성한 서버 원문 v1이다. 클라이언트가 termsSnapshot을 제출할 수 없다. 원문은 주문 생성 시 스냅샷에 복사하며 이후 서버 규칙이 바뀌어도 과거 원문을 다시 만들지 않는다.

현재는 내부 생성 경로만 구현했다. 구매자 화면에 규칙을 게시하거나 동의 UI·조건 확인 API를 연결한 상태가 아니다. 공개 API 연결 전에 원문과 화면 표시를 함께 리뷰해야 한다. 법적 효력을 보장하는 전자서명으로 표현하지 않는다.

## UTC 저장 보완

기존 Order·OrderHistory의 LocalDateTime/TIMESTAMP 매핑은 서울 JVM에서 DB 저장값이 9시간 어긋나도 JPA 재조회만으로 정상처럼 보이는 문제를 실제 재현했다. 주문·이력의 DATETIME 필드에 `@JdbcTypeCode(SqlTypes.LOCAL_DATE_TIME)`을 사용해 JDBC LocalDateTime으로 직접 전달하도록 보완했다. 서비스는 Clock의 시간대와 관계없이 Instant를 UTC로 변환한다. 기존 공통 JDBC UTC 설정이나 지갑 Entity는 변경하지 않았다.

검증은 JPA 재조회뿐 아니라 SQL DATE_FORMAT으로 orders·order_histories의 실제 저장 원문을 검사한다. 앞선 저장 테스트의 성공을 UTC 원문 검증 완료로 확대하지 않는다. 기존 업무 데이터에 대한 시간 보정 SQL은 실행하지 않았으며, 해당 데이터가 있다면 수정 전 원문·시각 출처를 별도로 점검해야 한다.

## 검증과 남은 작업

테스트: `OrderCreationIntegrationTests`. 격리 MySQL 8.4.13의 lancerpick_ci, 기존 V1·V2를 사용한다. 실행 결과와 테스트 개수는 [작업표](work-plan.md)의 주문 생성 서비스 후속 기록을 따른다.

- 신규 생성·서버 가격/조건 복사·최초 상태·공유 UTC 마이크로초 시각
- 상품/주문 상태 변경 후 동일 키 재전송, 저장한 규칙 원문 유지
- 같은 키의 상품 ID·버전·요구사항 불일치 및 새 키 생성
- 구매자·요청·상품 상태·버전·자기 상품 오류
- 스냅샷 검증 실패와 이력 저장 경계의 실제 FK 실패 시 전체 롤백 및 같은 키 재시도
- 같은 키 4개 동시 요청의 단일 생성, 다른 구매자의 동시 생성, 같은 키·다른 입력 경쟁
- 서로의 상품을 구매하는 두 회원의 동시 생성 5회, 사전 조회 후 판매자 변경 시 무저장 거절
- 상품 변경 트랜잭션과의 직렬화, 호환/비호환 상위 트랜잭션

이력 실패 검사는 Repository spy에서 실제 FK 위반 SQL을 실행해 서비스의 실패 전파·롤백을 확인한다. 정상 이력 저장은 실제 Repository를 사용한다. 아래 HTTP 후속 검증과 구분한다. 실제 로그인·화면·실제 상품 쓰기 연동·결제/취소 동시 실행·다중 서버 부하·원격 CI는 별도다. 스키마와 기존 V1·V2는 변경하지 않았다.

## HTTP 생성 API 후속 구현

`POST /api/orders`는 `CurrentMemberId`가 검증한 `AuthenticatedMember.memberId()`만 구매자로 사용한다. 요청의 buyerId·가격·상태·조건 원문은 신뢰하지 않는다. 기존 세션/CSRF 보안을 재사용하며 로그인 구현 자체를 추가하지 않았다. `demo`에서는 Controller·DB 조회 Bean을 만들지 않는다.

요청 예시(상품 ID·버전은 실제 조회 결과로 교체):

```json
{"productId":"15","expectedProductVersion":0,"requirements":"파란색으로 제작해 주세요.","requestKey":"c00af24a-391c-48d8-9754-d5591ce7df22"}
```

주문 입력에만 전용 JSON 역직렬화를 적용한다. productId·requirements·requestKey는 문자열, expectedProductVersion은 Long 범위의 JSON 정수만 받는다. 문자열/소수/boolean 버전·숫자 ID를 묵시적으로 변환하지 않는다. 필수값/음수/공백은 Bean Validation, ID·UUID·정규화 후 요구사항 길이는 기존 DTO 검증을 따른다. 공통 오류 응답의 code·message·fieldErrors·traceId를 재사용한다.

`OrderCreationApiService`는 READ_COMMITTED 생성 트랜잭션에서 생성 서비스와 상세 조회를 함께 실행한다. 신규 생성/재전송 결과에 따라 201/200, Cache-Control: no-store를 반환한다. DTO 조립 실패는 새 주문·스냅샷·이력까지 롤백한다. 직렬화 이후 네트워크 실패까지 롤백한다는 의미는 아니며, 응답 유실 시 같은 키·입력으로 재전송한다.

`OrderDetailQueries`는 주문 참여자 조건을 확인하고 저장한 스냅샷·현재 상태·현재 참여자 표시 이름·이력·납품·수정·취소 요청·주문 결제·후기를 조립한다. 상품의 현재 설명으로 스냅샷을 재작성하지 않는다. 모든 ID는 문자열, DATETIME은 JDBC LocalDateTime 원문에서 UTC OffsetDateTime으로 변환한다. 미발생 단일 객체/시각은 null, 없는 하위 목록은 빈 배열이다. 서비스의 `OrderAllowedActions` 정책으로 현재 허용 행동을 붙이지만 수락·결제 등 해당 명령 API의 구현 완료를 뜻하지 않는다. 최초 구매자 응답은 REQUESTED·CREATED 이력 1건·allowedActions=[CANCEL]이다.

주문 상태가 바뀐 뒤 같은 생성 요청을 재전송해도 현재 주문 상세를 반환한다. 후속 상태의 읽기 검사는 SQL fixture로 수행하며 후속 업무 서비스를 실행한 검증은 아니다. 원격 CI·실제 로그인·브라우저 검증은 별도다. 최종 테스트 수와 실행 결과는 [작업표](work-plan.md)를 따른다.
