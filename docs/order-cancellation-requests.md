# 구매자 취소 요청·판매자 처리 API

작성일: 2026-11-03. 담당: 정은찬. 기존 황준범 담당 직접 취소·전액 적립금 환불 서비스를 재사용한다.

## HTTP 계약

| 경로 | 실행자·입력 | 성공 |
| --- | --- | --- |
| POST /api/orders/{id}/cancellation-requests | 구매자; reason,requestKey(UUID) | 신규 201 / 기존 키 200 CancellationResult |
| POST /api/orders/{id}/cancellation-requests/{requestId}/approve | 판매자; 본문 없음 | 200 CancellationResult |
| POST /api/orders/{id}/cancellation-requests/{requestId}/reject | 판매자; responseReason 선택, 본문 생략 가능 | 200 CancellationResult |
| POST /api/orders/{id}/cancellation-requests/{requestId}/withdraw | 구매자; 본문 없음 | 200 CancellationResult |

모두 인증 principal·CSRF를 사용하고 no-store를 반환한다. 반대 역할의 참여자는 403, 비참여자·없는 주문·해당 주문에 속하지 않은 요청은 404다. ID는 양의 BIGINT 문자열, 문자열 입력은 숫자에서 자동 변환하지 않는다. reason은 공백/줄바꿈 정규화 후 1~1000 코드 포인트다. responseReason은 미입력/null이면 null, 입력 시 같은 길이 기준을 적용한다. UUID는 소문자 하이픈 표준형으로 저장한다.

CancellationResult는 cancellationRequest와 현재 order(OrderDetail)다. 요청 항목은 id,orderId,requestKey,reason,status,requestedAt,resolvedAt,resolvedBy,responseReason이며 ID는 문자열, 시각은 UTC다. 내부 requested_by·원장 키·회원 비밀번호를 노출하지 않는다.

## 생성·재전송

회원 공유 잠금 → 참여자 주문 FOR UPDATE → 구매자 확인 → 기존 키와 정규화 사유 비교 → 신규 키인 경우 유료 미완료 상태(IN_PROGRESS/AWAITING_CONFIRMATION/REVISION_IN_PROGRESS) → 대기 요청 없음 → 요청·이력 저장 순서다.

같은 구매자/주문/키·같은 사유는 이미 승인·거절·철회되거나 주문이 완료·취소된 뒤에도 기존 요청과 현재 주문을 200으로 반환한다. 같은 키·다른 사유는 409 IDEMPOTENCY_KEY_REUSED다. 새 키라도 대기 요청이 있으면 409 CANCELLATION_PENDING이다. 해결 후에는 현재 주문 상태가 허용할 때 새 키로 다시 요청할 수 있다.

제출은 PENDING 요청과 CANCELLATION_REQUESTED 이력을 만들며 주문 상태·돈·납기는 바꾸지 않는다. 구매자는 WITHDRAW_CANCELLATION, 판매자는 APPROVE_CANCELLATION/REJECT_CANCELLATION과 기존 직접 취소 행동을 받는다. 대기 중에는 기존 납품·수정·확정 서비스가 요청을 차단한다.

## 승인·거절·철회

요청이 해당 주문에 속하는지 확인하고 같은 주문 잠금 안에서 해결한다. 같은 해결 동작 재전송은 200으로 현재 결과를 반환하며 해결 시각·응답 사유·이력을 덮어쓰지 않는다. 다른 해결 동작은 409 REQUEST_ALREADY_RESOLVED다.

승인은 기존 OrderCancellationService를 같은 트랜잭션에 참여시켜 주문 CANCELLED·원결제 REFUNDED·전액 지갑 복구·REFUND 원장·CANCELLATION_APPROVED/CANCELLED 이력을 함께 처리한다. 같은 환불 코드를 복제하지 않는다. 새 승인 요청에는 responseReason 입력이 없으므로 null로 보존한다. 판매자 직접 취소가 먼저 대기 요청을 승인했다면 승인 재전송은 이미 저장된 결과를 그대로 반환한다.

거절/철회는 요청 상태를 REJECTED/WITHDRAWN으로 바꾸고 대응 이력을 저장한다. 주문 상태·금액·결제는 유지한다. 요청 이력의 fromStatus/toStatus는 당시 주문 상태로 동일하다. 승인만 환불하며, 거절·철회에는 환불이 없다.

READ_COMMITTED 한 트랜잭션에서 요청 해결·주문·금액·이력·상세 응답을 처리한다. 회원 공유 잠금 → 주문 배타 잠금 → 환불 시 구매자 지갑 배타 잠금 순서를 지킨다. 요청 행은 기존 직접 취소 JDBC 갱신과 같은 원문을 조회해 JPA 캐시와의 불일치를 피한다. 단계 중 실패하면 전체를 롤백한다. 대기 요청 복수·요청자 불일치 등 모순은 자동 보정하지 않는다.

## 검증 범위

OrderCancellationRequestIntegrationTests는 실제 지갑 초기화·주문 생성/수락·적립금 결제·납품·수정·취소/환불·확정 서비스와 HTTP API를 연결한다. 로그인만 테스트 principal을 사용한다. 생성/해결 재전송·새 키·권한/CSRF·입력 경계·세 유료 상태·취소 대기 차단·같은/다른 키 경쟁·승인/철회·승인/직접 취소·승인/확정 경쟁과 실제 DB 실패/응답 실패의 롤백을 검사한다.

원격 CI·브라우저·새 취소 요청 UI·모든 명령 조합·다중 서버 부하는 별도다. 스키마·V1/V2·프런트 코드를 변경하지 않는다. 최신 실행 결과와 기존 로그인 예외 경로의 별도 검토 항목은 [작업표](work-plan.md)를 따른다.
