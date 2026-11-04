# 구매자 수정 요청 API

> 문서 읽기 기준(2026-11-04): 아래 날짜·브랜치별 구현 및 검증 설명은 당시 기록이다. 현재 반영 여부·남은 작업은 [현황표](current-status.md), 실행 설정은 [루트 README](../README.md), API 계약은 [API 명세](api-spec.md)를 따른다. 과거의 미구현·병합 전 표현과 검사 수를 현재 상태로 확대하지 않는다.

작성일: 2026-11-03. 담당: 정은찬. 백엔드 API와 React 수정 요청 입력을 구현했다. 결제·구매 확정은 기존 구현을 사용하며 실제 로그인 기반 브라우저 통합은 후속이다.

## 화면 연결

주문 상세에서 REQUEST_REVISION이 허용되고 최초 납품이 있는 경우 사유 입력을 표시한다. 최초 납품 ID를 문자열로 전송하고 공통 Unicode 정규화 기준 사유 1~1000자를 검사한다. 요청된 사유·시각·서버가 계산한 수정 기한을 상세에 표시한다. 기한을 클라이언트에서 계산하거나 전송하지 않는다. 기존 변경 중 잠금·CSRF·오류 후 재조회 흐름을 사용하며 POST는 자동 재전송하지 않는다.

## 계약

```text
POST /api/orders/{id}/revision-request
```

```json
{"deliveryId":"15","reason":"요청 범위 안에서 색상을 수정해 주세요."}
```

- 인증 principal의 구매자만 실행하며 CSRF가 필요하다. 판매자는 403, 비참여자·없는 주문은 동일 404다.
- 주문 ID와 deliveryId는 양의 BIGINT 범위 10진 문자열이다. 숫자 JSON deliveryId를 문자열로 자동 변환하지 않는다.
- reason은 문자열 필수이며 Unicode 공백 제거·줄바꿈 통일 후 1~1000 코드 포인트다.
- AWAITING_CONFIRMATION 상태의 같은 주문에 속한 최초 1회차 납품만 대상으로 한다. 없는/타 주문 납품은 404다.
- 주문당 한 번만 요청할 수 있다. 이미 사용한 경우 재전송과 재납품 후 요청 모두 409 REVISION_ALREADY_USED다. 요청 키 기반 멱등 200이 아니다.
- 사용 전 상태가 잘못되었으면 409 ORDER_STATE_CONFLICT, 대기 취소 요청이 있으면 409 CANCELLATION_PENDING이다.
- 성공은 200 OrderDetail·no-store이며 상태는 REVISION_IN_PROGRESS다. 실제 API 동작과 별개로 수정 범위의 적절성은 주문 당시 범위에 따라 구매자·판매자가 확인한다.

## 기한·이력·저장

수정 요청 시각은 서버 Clock의 Instant를 UTC·마이크로초로 변환한다. 새 기한은 수정 요청 시각 + orders.duration_days_snapshot(1일=24시간)이다. 현재 상품의 소요일이나 클라이언트가 제출한 기한은 사용하지 않는다. 최초 orders.due_at·paid_at·금액은 보존한다. 수정 요청일과 결제일이 다르면 최초 납기와 수정 납기도 다를 수 있다.

기존 revision_requests 테이블에 요청 ID·주문/납품 ID·정규화 사유·requested_at·due_at을 매핑했다. 수정 요청·주문 상태/updated_at·REVISION_REQUESTED 이력·현재 상세 응답을 READ_COMMITTED 한 트랜잭션으로 처리한다. 이력 actor는 구매자, fromStatus는 AWAITING_CONFIRMATION, toStatus는 REVISION_IN_PROGRESS이며 사유는 같은 정규화 원문이다.

회원 공유 잠금 → 참여자 주문 FOR UPDATE → 구매자 확인 → 기존 수정 요청 없음 → 상태 → 납품 소속/회차 → 취소 대기 없음 → 저장 순서다. 상태 변경 서비스는 같은 주문 잠금을 사용해야 한다. (order_id)·(delivery_id) UNIQUE를 유지하며 동시에 두 요청이 오면 한 요청만 성공한다. 어떤 저장/응답 조립 단계에서든 실패하면 새 요청·상태·기한·이력 전체를 롤백한다.

수정 요청 후 판매자는 기존 납품 API로 expectedSequenceNo=2를 제출한다. 이후 상태는 AWAITING_CONFIRMATION, 구매자는 추가 수정 요청 없이 확인할 수 있다. 허용 행동에 CONFIRM이 있다는 것이 구매 확정 API의 구현 완료를 뜻하지는 않는다.

## 검증 범위

OrderRevisionApiIntegrationTests는 실제 생성 서비스·최초 납품 서비스·수정 요청 HTTP API·재납품 서비스·상세 GET을 연결한다. 선행 결제와 취소 요청은 SQL fixture, 로그인은 테스트 principal이다. 요청 시각과 결제일, 현재 상품 소요일과 주문 당시 소요일을 다르게 설정해 기한의 기준을 확인한다.

정상 상태/UTC/기한·권한/CSRF·납품 소속·사유/ID/타입 경계·취소 대기·반복/동시 요청·실제 FK 실패 및 응답 조립 실패의 롤백/재시도를 검사한다. 스키마·V1/V2·프런트 코드는 변경하지 않았다. 전체 실행 결과는 [작업표](work-plan.md)를 따른다. 실제 로그인·결제 쓰기·화면·확정·원격 CI·다중 서버 부하·모든 명령 간 경쟁은 별도 검증 대상이다.
