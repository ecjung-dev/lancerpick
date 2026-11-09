# 운영 문의 취소 검토와 관리자 승인 취소(K4)

작성: 2026-11-06, 정은찬, 작업 브랜치 `feature/admin-cancellation-review`(분기 main `92fabc3`, #120). 상태: **main 반영(#121)**, 자동 테스트 통과. 관리자 API·화면 연결과 실서버·브라우저 확인은 남음. 정책 경계는 [전액 보관 정책](full-funding-policy.md) §1, 금액 처리는 [전액 보관 구현](full-funding-payment.md)을 따른다.

## 결론

- 판매자가 취소 요청에 응답하지 않을 때 구매자는 운영 문의(취소 검토)를 남기고, 관리자가 그 문의를 보고 대기 중인 취소 요청을 **승인하거나 거절**한다.
- 승인: 보관금 전액 반환·종료 기록 `ADMIN_CANCELLATION`(`inquiry_id` 포함)·취소 요청 `APPROVED`(`resolution_source=ADMIN`)·주문 `CANCELLED`·문의 `CLOSED`가 **한 트랜잭션**으로 커밋된다.
- 거절: 취소 요청 `REJECTED`(`ADMIN`)·문의 `CLOSED`. 대금은 계속 보관되고, 거절 시각이 다음 검수 기산점(48시간 → 판매자 7일 타이머)이 된다.
- 이번 변경은 **내부 서비스와 문의 엔티티까지**다. 관리자 API·화면, 구매자 문의 접수 API·화면, 문의 메시지(`support_messages`)는 김창재가 이 계약으로 연결한다.
- DB·Flyway 변경 없음(V5 `support_inquiries`, V6·V7 `order_closures` 제약을 그대로 사용).

## 문의 엔티티 (`inquiry` 패키지)

김창재 선행 작업이던 문의 엔티티를 K4 진행을 위해 정은찬이 먼저 만들었다(2026-11-06 조장 결정). 문의 접수·목록·답변 API는 김창재 담당으로 남는다.

| 항목 | 내용 |
| --- | --- |
| 엔티티 | `inquiry/entity/SupportInquiry` ↔ `support_inquiries`(V5). 유형 `CANCELLATION_REVIEW`·`GENERAL`, 상태 `OPEN`·`IN_REVIEW`·`CLOSED` 문자열 상수 |
| 생성 | `SupportInquiry.cancellationReview(requesterId, orderId, cancellationRequestId, subject, at)`, `SupportInquiry.general(requesterId, orderId 또는 null, subject, at)`. 제목은 공백 정리 후 1~150자(벗어나면 `IllegalArgumentException`, 접수 서비스가 400으로 바꾼다) |
| 종료 | `close(adminId, at)`: 열린 문의만, 처리 시각이 생성·최근 변경 시각 이후. `assigned_admin_id`=종료한 관리자, `resolved_at`·`updated_at`=처리 시각. 되돌리는 메서드는 없다 |
| 저장소 | `inquiry/repository/SupportInquiryRepository`: `findTarget(id)`(잠금 전 유형·주문 ID만 읽는 projection), `findForUpdate(id)`(배타 잠금) |
| 접수 서비스가 검증할 것(김창재) | 신청자 = 주문 구매자, 연결한 취소 요청이 그 주문의 `PENDING` 요청, V3 결제 후 주문. 같은 취소 요청의 열린 문의 중복 여부(DB UNIQUE 없음) |

DB CHECK(`ck_inquiry_type`·`ck_inquiry_status`·`ck_inquiry_time`)와 같은 상태 조합을 엔티티가 먼저 지킨다. 복합 FK(`cancellation_request_id, order_id`)가 다른 주문의 취소 요청 연결을 막는다.

## 서비스 계약

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

## 잠금 순서·트랜잭션

관리자 회원 공유 잠금 → 주문 배타 잠금(`findForSystemUpdate`) → 문의 배타 잠금 → (승인 시) 구매자 지갑 잠금. 문의의 주문 ID는 바뀌지 않으므로 잠금 전에는 projection으로 유형·주문 ID만 읽고, 주문을 잠근 뒤 문의를 잠가 다시 확인한다(먼저 엔티티를 읽으면 잠금 조회가 캐시된 값을 돌려줄 수 있다).

판매자 승인(`/cancel`·취소 요청 승인)과 구매자 철회도 주문 잠금으로 직렬화되므로 둘 중 먼저 잠근 쪽만 반영된다. **취소 검토 문의의 상태를 바꾸는 다른 경로(문의 영역의 종료·담당 지정)는 주문 → 문의 순서를 지키거나 문의만 잠가야 한다**(문의 → 주문 순서는 교착 위험).

승인 금액 처리는 `FullFundingService.adminRefund`가 판매자 승인 환불과 같은 검증(결제·보관·원장 일치, 보관 HELD, 종료·정산 없음, 활성 타이머 없음)을 거쳐 구매자 지갑 환불·`REFUND` 원장·결제/보관 `REFUNDED`·종료 기록을 남긴다. 이력은 관리자 실행자의 `CANCELLATION_APPROVED`(알림 없음)·`CANCELLED`, 거절은 `CANCELLATION_REJECTED`다.

## 알림·화면

- 승인: 기존 V3 규칙대로 `ORDER_FUNDS_REFUNDED`. 실행자가 당사자가 아니라 구매자·판매자 **양측**에 간다.
- 거절: 새 유형 `ORDER_CANCELLATION_ADMIN_REJECTED`(양측). 판매자 거절 문구("판매자가 거절했습니다")와 구분한다. 프런트 `notifications/messages.ts`에 문구 추가.
- 주문 이력에서 당사자가 아닌 회원 실행자를 "기록자" 대신 **"관리자"**로 표시한다(타이머 해제 이력에도 적용). `orders/history.ts`.
- 관리자 승인으로 취소된 주문에 판매자가 `/cancel`을 다시 보내면 판매자 승인 재전송으로 보지 않고 409 `ORDER_STATE_CONFLICT`다.

## 검증

로컬 `lancerpick_test`(3307) 실제 커밋·잠금. `AdminCancellationReviewIntegrationTests` 9건:

1. 승인: 구매자 잔액 +결제액·판매자 변화 없음, 종료 기록(`ADMIN_CANCELLATION`·관리자·문의·요청·반환액), 요청 `APPROVED`/`ADMIN`/처리 사유, 문의 `CLOSED`·담당 관리자, 보관 `REFUNDED`, 관리자 이력 2건, 양측 `ORDER_FUNDS_REFUNDED`.
2. 같은 결정 재전송은 같은 결과·환불 원장 1건, 다른 결정 `INQUIRY_CLOSED`, 판매자 `/cancel` `ORDER_STATE_CONFLICT`, 구매자 철회 `REQUEST_ALREADY_RESOLVED`.
3. 거절: 보관 `HELD` 유지·종료 기록 없음, 요청 `REJECTED`/`ADMIN`, 양측 `ORDER_CANCELLATION_ADMIN_REJECTED`, 48시간 전 타이머 시작 불가 → 기산점(거절 시각)+48시간 후 시작 가능.
4. 권한·입력: 판매자·구매자 403, 사유 공백/null/1001자·ID 0 → 400, 없는 문의 404, 없는 회원 401, 일반 문의 409. 모두 문의·요청 상태 불변.
5. 판매자가 먼저 거절한 요청: 승인·거절 모두 `REQUEST_ALREADY_RESOLVED`, 문의 `OPEN` 유지.
6. 마지막 이력 저장에서 실패 주입: 잔액·주문·요청·문의·종료 기록 전체 롤백, 이후 재처리 성공.
7. 동시 관리자 승인 vs 판매자 승인: 하나만 성공, 환불·종료 기록 1회(활성화 게이트 C8).
8. 동시 관리자 승인 vs 구매자 철회: 하나만 반영, 결과에 맞는 잔액·문의 상태.
9. 동시 관리자 승인 vs 구매자 구매 확정: 승인만 성공(대기 요청 때문에 확정 409), 판매자 지급·정산 없음(C8).

백엔드 전체 `clean build` 917건 통과(실패·오류·건너뜀 0). 모든 검사 뒤 [V3 금액 불변식](v3-activation-gate.md#4-금액-불변식-조회) 0행. 프런트 알림 문구·이력 실행자 단위 테스트 보강.

실행하지 않은 검증: 관리자 API·화면이 없어 실서버·브라우저 확인 없음, 원격 CI.

## API·화면 연결 — 2026-11-09 (김창재, `feature/admin-cancellation-inquiries`)

이 서비스와 문의 엔티티를 그대로 사용해 구매자 접수·관리자 처리 경로를 연결했다. 경로·오류는 [API 9-1절](api-spec.md#9-1-운영-문의관리자-취소-검토-김창재). DB·Flyway 변경 없음.

| 구분 | 백엔드 | 화면 |
| --- | --- | --- |
| 구매자 검토 요청 | `inquiry/service/InquiryService`·`InquiryController`. 회원 공유 잠금 → 주문 배타 잠금(`OrderCommandLocks`) 아래에서 구매자·대기 요청·V3 결제 후를 확인하고, 같은 요청의 열린 문의가 있으면 그 문의를 돌려준다(UNIQUE 없이 중복 방지) | 주문 상세 취소 요청 아래 `CancellationReviewRequest`(V3 주문에서 내 문의 상태 표시, 구매자 대기 요청이고 열린 문의가 없을 때만 입력 칸) |
| 내 문의 | `GET /api/me/inquiries`(`SupportInquiryQueries`, QueryDSL) | 위 컴포넌트가 주문별로 조회(`['wallet', 'order-inquiries', id]`, 주문 명령 뒤 함께 갱신) |
| 관리자 목록·상세 | `admin/service/AdminInquiryService`·`AdminInquiryQueries`. 현재 DB 역할 재확인, 상세에 주문 요약·취소 요청·allowedActions | `/admin/inquiries`(상태·유형 필터 URL 보존), `/admin/inquiries/:id`, 관리자 탭(`AdminNav`) |
| 승인/거절 | `AdminInquiryController`가 이 서비스(`approve`·`reject`)를 그대로 호출 | 처리 사유 1~1000자, 승인은 되돌릴 수 없음 확인 체크 후에만 버튼 활성 |
| 처리 불가 문의 종료 | `AdminInquiryService.close`: 관리자 공유 → 주문 배타 → 문의 배타 잠금(이 서비스와 같은 순서), 요청이 대기 중이면 409 `REQUEST_STILL_PENDING`, 닫힌 문의는 200 재전송 | 상세의 "문의 닫기"(allowedActions에 CLOSE일 때만) |

화면 명령은 처리 중 버튼 차단·ref 이중 클릭 차단·자동 재전송 없음이다. 409는 서버 안내를 보여 주고 최신 상태를 다시 불러오며, 통신 오류·5xx는 서버가 처리했을 수 있어 실패로 단정하지 않고 다시 조회한다.

결정(2026-11-09 사용자 확정): 판매자 무응답 대기 시간 조건 없음(정책에 없음, 관리자가 판단), 구매자 입력은 제목 1~150자만(문의 본문 칸 없음), 같은 요청 재접수는 기존 문의 200, 종료 사유 없이 닫기(DB 칸 없음, 메시지 기능에서 후속).

## 남은 일

- 김창재: 문의 메시지(`support_messages`)·담당 지정(`IN_REVIEW`), 일반 문의(GENERAL) 접수 화면. 문의 상태를 바꾸는 새 경로도 주문 → 문의 잠금 순서를 지킨다.
- G1: 실서버·브라우저로 B4 확인(정은찬과 함께), 활성화 판정.
