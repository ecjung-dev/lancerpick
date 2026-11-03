# 최초 납품·재납품 API

작성일: 2026-11-03. 담당: 정은찬. 백엔드 API와 React 납품·재납품 입력을 구현했다. 구매자 수정 요청은 [수정 요청 API](order-revision.md)에 연결했다. 실제 로그인 기반 브라우저 검증은 후속이다.

## 화면 연결

주문 상세에서 DELIVER가 허용된 판매자에게 설명·HTTPS 링크 입력을 표시한다. 현재 상태가 IN_PROGRESS면 1회차, REVISION_IN_PROGRESS면 2회차를 정수로 전송한다. 설명 1~5000자·URL 최대 2048자는 공통 Unicode 정규화 기준으로 검사하고 URI의 최종 유효성은 서버에서 검증한다. POST 자동 재전송은 없으며 실패 시 입력을 비우고 상태 재조회를 요구한다. 재조회 후 새 회차가 표시돼도 이전 내용을 자동 제출하지 않는다. 서버·스키마는 변경하지 않았다.

## HTTP 계약

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

## 재전송·상태·이력

실행자 회원 공유 잠금 → 참여자 주문 잠금 → 판매자 확인 → 기존 회차 확인 → 현재 상태/회차 확인 → 대기 취소 요청 없음 확인 → 재납품 근거 확인 → 저장 순서다. 기존 수락·거절의 회원→주문 잠금 순서를 재사용한다.

이미 제출한 회차는 내용이 같거나 현재 상태가 수정 진행으로 바뀌었어도 409 DELIVERY_ALREADY_SUBMITTED다. 미존재 회차라도 상태와 맞지 않으면 409 DELIVERY_SEQUENCE_CONFLICT, 대기 취소 요청이 있으면 409 CANCELLATION_PENDING이다. 클라이언트는 같은 회차의 재전송을 다음 회차로 바꾸지 않고 GET 상세로 실제 기록을 확인한다.

재납품은 같은 주문의 최초 1회차 납품을 참조한 수정 요청 1건이 있어야 한다. 상태만 REVISION_IN_PROGRESS인데 이 근거가 누락되거나 연결이 틀리면 500 DATA_INTEGRITY_ERROR다. 수정 요청 쓰기는 [구매자 수정 요청 API](order-revision.md)를 따른다.

Delivery는 기존 deliveries에 설명·URL·회차·UTC 마이크로초 제출 시각을 저장한다. DELIVERED 이력의 actor는 판매자, fromStatus는 IN_PROGRESS/REVISION_IN_PROGRESS, toStatus는 AWAITING_CONFIRMATION, reason은 null이다. 주문 updated_at을 갱신하고 최초/수정 납기·결제 시각·금액은 보존한다. 제출 시각과 기한을 사용한 화면의 지연 표시는 후속 구현이다.

READ_COMMITTED 트랜잭션 안에서 납품·주문 상태·이력·상세 응답을 함께 처리한다. 어떤 단계든 실패하면 전체 변경을 롤백하며 동일 회차를 정상 재시도할 수 있다. (order_id,sequence_no) UNIQUE를 유지하고 주문 잠금으로 동시 제출을 직렬화한다. 다른 서비스도 취소/수정 등 상태 변경 전에 같은 주문을 잠가야 한다.

## 검증 범위

OrderDeliveryApiIntegrationTests는 실제 HTTP 필터·Controller·Service·MySQL을 사용한다. 로그인은 테스트 principal, 결제·수정 요청·취소 요청의 선행 상태는 SQL fixture다. 정상 1/2회차·이전 회차 재전송·현재 상태/취소 요청 충돌·URL/문자열/회차/ID 경계·권한/CSRF·동시 제출·실제 DB 제약 실패 및 응답 조립 실패의 롤백을 확인한다. 전체 실행 결과는 [작업표](work-plan.md)를 따른다.

스키마·V1/V2·프런트 코드는 변경하지 않았다. 후속 수정 요청 통합 테스트에서 실제 최초 납품→수정 요청→재납품 연결도 확인했다. 실제 결제 API 연결·브라우저·원격 CI·다중 서버 부하·모든 상태 변경 간 경쟁은 별도 검증 대상이다.
