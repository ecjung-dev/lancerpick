# V3 신규 생성 활성화 게이트(G1) 시나리오

2026-11-06, 정은찬·황준범(G1). main `9f9aa8f`(#105) 기준. `lancerpick.order.full-funding-creation-enabled`(기본 false)를 켜서 신규 주문을 CONTRACT_V3로 만들기 전에 확인할 시나리오·판정 기준·현재 근거를 정리한다. 정책은 [결제 금액 보관 정책](full-funding-policy.md), 금액 처리는 [금액 처리 구현](full-funding-payment.md), 타이머는 [자동 구매 확정](auto-confirm-timer.md), 알림은 [V3 알림](v3-notices.md)을 따른다.

이 문서는 확인 목록이다. 아래 "현재 근거"는 해당 테스트가 존재한다는 뜻이며, G1 판정은 같은 커밋에서 다시 실행한 결과로만 한다.

## 1. 판정 규칙

- **같은 소스**: 활성화 후보 커밋 하나에서 백엔드 `clean build`, 프런트 `typecheck`·`lint`·`test:api`·`build`, 실서버·브라우저 확인을 모두 한다. 이전 커밋의 결과를 합산하지 않는다.
- **세 가지 증거를 구분**해 기록한다: 자동 테스트(로컬 `lancerpick_test`/CI), 실서버+실제 DB(개인 DB에서 스위치를 켠 실행), 브라우저(두 계정 실제 로그인).
- **필수 조건**: 아래 표의 필수(●) 항목이 모두 통과하고, 4절 금액 불변식 조회 결과가 0행이며, 열린 결함이 없을 것.
- **켜지 않는 사유**: K4 미연결이면 결제 후 판매자 무응답 취소를 처리할 운영 경로가 없으므로 활성화하지 않는다(K4 결정 전까지 G1 보류).
- 판정 기록은 이 문서 6절과 [현재 현황](current-status.md)에 남긴다.

## 2. 시나리오

표기: ● 필수, ○ 권장. 근거 클래스는 `LancerPick/src/test/java/com/example/lancerpick/` 기준.

### A. 정상 흐름

| ID | 시나리오 | 기대 결과 | 현재 근거 | 남은 확인 |
| --- | --- | --- | --- | --- |
| A1 ● | 스위치 on 상태에서 HTTP 주문 생성 → 수락 → 초안 → PDF → 양측 서명 → 결제 → 납품 → 구매 확정 | 정책 V3 고정, 결제액만 차감·HELD, 확정 시 판매자 +90%·수수료 10%·완료, 이력·알림 순서 | 단계별로 나뉨: `OrderCreationIntegrationTests.fullFundingOrderFreezesNewTerms…`, `ContractDocumentIntegrationTests.httpAcceptDraftPdf…`(V2 기준), `FullFundingTradeIntegrationTests.httpPaymentIgnoresClientMoney…`(주문·수락은 fixture) | **한 주문으로 처음부터 끝까지 이어지는 통합 테스트 없음**. 신규 1건 추가 + 실서버·브라우저 |
| A2 ● | 수정 요청 1회 → 재납품 → 확정 | 재납품 기한=요청 시각+소요일, 두 번째 수정 요청 거절 | `ContractV3FulfillmentApiIntegrationTests.v3DeliveryRevisionAndRedelivery…`, `ReviewTimerIntegrationTests.revisionNotifiesRedeliveryDeadline…` | 브라우저 |
| A3 ● | 납품 후 48시간 → 판매자 타이머 시작 → 7일 만료 → 자동 확정 | SYSTEM 완료·AUTO_CONFIRMED closure·수수료 동일·양측 알림·후기 가능 | `ReviewTimerIntegrationTests.sellerStartsAfter48Hours…`, `concurrentExpiryRunsPaySellerOnlyOnce` | 실서버 스케줄러 실제 실행(시간 단축 설정은 개인 DB에서만), 후기 작성 브라우저 |
| A4 ○ | 만료 24시간 전 구매자 사전 안내 | 구매자에게 1회, 판매자 없음 | `autoConfirmNoticeIsSentOnceToBuyer…`, `…NotSentForEndedOrExpiredTimers` | 실서버 알림 목록 |
| A5 ● | 결제 시 납기 통지 | `DEADLINE_NOTIFIED` 1건·`orders.due_at`과 일치 | `paymentNotifiesDeliveryDeadlineOnce…` | 거래 이력 화면 표시 |

### B. 취소·반환

| ID | 시나리오 | 기대 결과 | 현재 근거 | 남은 확인 |
| --- | --- | --- | --- | --- |
| B1 ● | 결제 전 취소(수락 전·계약 대기·서명 후) | 금액 이동 없음, `ORDER_CANCELLED` | `OrderContractIntegrationTests.contractOrderCanBeCancelledBeforeDeposit`, `signedContractCanBeCancelledBeforeDeposit…`(V2 기준) | V3 주문으로 같은 검사 1건 |
| B2 ● | 결제 후 구매자 취소 요청 → 판매자 승인 | 보관금 전액 반환·REFUNDED·수익/수수료 0·`ORDER_FUNDS_REFUNDED` | `FullFundingTradeIntegrationTests.approvedCancellationRefundsOnce…`, `manualConfirmationAndApprovedRefundUseV3FundsWording` | 브라우저 |
| B3 ● | 취소 요청 거절·철회 후 재개 | 타이머 종료, 해결 시각이 새 기산점 | `cancellationRequestCancelsTimerAndRejectionRestartsReviewWindow` | — |
| B4 ● | 판매자 무응답 → 운영 문의 → 관리자 승인 취소(K4) | 전액 반환·`ADMIN_CANCELLATION`(`inquiry_id`)·문의 종료 한 트랜잭션 | **미구현**(김창재 문의 엔티티 선행) | K4 구현 후 통합 테스트·화면 |
| B5 ● | 결제 후 판매자 단독 취소 시도 | 409 `CANCELLATION_REQUEST_REQUIRED` | `pendingCancellationBlocksConfirmationAndWrongActors…` | — |

### C. 경합(실제 커밋·동시 스레드)

| ID | 시나리오 | 기대 결과 | 현재 근거 | 남은 확인 |
| --- | --- | --- | --- | --- |
| C1 ● | 같은 지갑으로 두 주문 동시 결제 | 잔액 초과 없음 | `simultaneousPaymentsUseSharedWalletWithoutOverspending` | — |
| C2 ● | 같은 주문 동시 결제·재전송 | 차감 1건 | `repeatedConcurrentPaymentsCreateOneDebit` | — |
| C3 ● | 동시 구매 확정 | 판매자 지급 1건 | `simultaneousConfirmationsCreditSellerOnlyOnce` | — |
| C4 ● | 취소 승인 vs 구매 확정 | 하나만 성공 | `cancellationAndConfirmationCannotBothWin` | — |
| C5 ● | 타이머 만료 vs 구매자 확정/수정/취소 요청 | 만료 시각 이상이면 만료 우선(409 `AUTO_CONFIRM_DUE`), 응답이 먼저 커밋되면 만료 작업은 지급 안 함 | 순차 검사만: `ReviewTimerIntegrationTests`(AUTO_CONFIRM_DUE 3건) | **동시 스레드 검사 추가**(만료 처리와 수정 요청을 동시에) |
| C6 ● | 관리자 해제 vs 자동 확정 | 같은 잠금으로 직렬화, 만료 후 해제는 409 | 순차 검사: `adminCannotStopDueTimerBecauseExpiryWins` | **동시 스레드 검사 추가** |
| C7 ● | 여러 서버의 만료 스케줄러 동시 실행 | 지급 1건 | `concurrentExpiryRunsPaySellerOnlyOnce` | — |
| C8 ○ | 관리자 승인 취소 vs 판매자 승인/구매 확정 | 하나만 성공 | K4 미구현 | K4와 함께 |

### D. 실패 시 전체 롤백

| ID | 시나리오 | 기대 결과 | 현재 근거 |
| --- | --- | --- | --- |
| D1 ● | 결제 중 아웃박스 실패 | 차감·결제·보관·이력 모두 없음 | `paymentFailureRollsBackDebitPaymentAndHolding` |
| D2 ● | 확정 중 판매자 지급 이후 실패 | 판매자 잔액·원장·closure·완료 모두 롤백 | `payoutFailureRollsBackWalletLedgerClosureAndOrderTogether` |
| D3 ● | 승인 환불 중 실패 | 요청 승인·잔액·closure 롤백 | `refundFailureRollsBackRequestApprovalWalletAndClosure` |
| D4 ● | 자동 확정 실패 | 지급 롤백·타이머 활성 유지·다른 타이머 계속 처리 | `autoConfirmFailureRollsBack…`, `persistentlyFailingTimerDoesNotBlock…` |
| D5 ● | 기한 통지 저장 실패 | 결제·수정 요청도 롤백 | 같은 원천 키 UNIQUE 위반 검사(`paymentNotifiesDeliveryDeadlineOnce…`) |

### E. 권한·보안

| ID | 시나리오 | 기대 결과 | 현재 근거 | 남은 확인 |
| --- | --- | --- | --- | --- |
| E1 ● | 제3자·반대 역할·익명의 결제/확정/승인/대금 조회 | 401/403/404, 금액 불변 | `pendingCancellationBlocksConfirmationAndWrongActors…`, `httpEndpointsCheckParticipantRoleAndCsrf` | — |
| E2 ● | 클라이언트가 보낸 금액·회원 ID 무시 | 서버 계약 금액으로 차감 | `httpPaymentIgnoresClientMoney…` | — |
| E3 ● | 서명 누락·해시 불일치 상태에서 결제 | 차감 없음 | `missingSignatureCannotDebit…`, `OrderContractIntegrationTests.fileHashMismatch…` | — |
| E4 ● | 계약 PDF 다운로드는 당사자만, 변조 파일은 503 | 바이트 노출 없음 | `ContractDocumentIntegrationTests.finishedDocument…OnlyToParties`, `missingAndTamperedFiles…` | V3 계약으로 실제 다운로드·해시 대조(브라우저) |
| E5 ○ | 계정 전환 시 대금·주문 캐시 정리 | 이전 계정 금액 미표시 | 프런트 `test:api`(order-funds queryKey) | 브라우저 두 계정 |

### F. 호환·회귀

| ID | 시나리오 | 기대 결과 | 현재 근거 |
| --- | --- | --- | --- |
| F1 ● | 스위치 on 전후 기존 LEGACY_V1 주문 처리 | 정책 값 불변, 기존 결제·환불·정산 그대로 | `replayKeepsPolicyFixedAtCreationEvenAfterSwitch`, `LegacyPaymentCompatibilityTests`, 기존 주문 통합 테스트 |
| F2 ● | CONTRACT_V2 기존 주문 | 결제 후 명령 계속 차단, 계약 원문·해시 보존 | `v2PaidOrderStaysBlocked`, `OrderContractIntegrationTests.replacementPreservesOldSignature…`, `OrderCreationIntegrationTests.fullFundingOrderFreezesNewTerms…`(기존 V1/V2 원문 보존) |
| F3 ● | 금액 하한·상한 | 10,000원 미만 생성·확정·결제 거절 | `contractOrderRejectsTotalBelowMinimum…`, `contractOrderAcceptsMinimumBoundary`, DB CHECK |
| F4 ● | 스위치를 다시 끄면 | 신규만 LEGACY_V1, 이미 만든 V3 주문은 끝까지 처리 | 정책 고정 검사(F1)로 간접 확인 | 실서버에서 on→off 후 V3 주문 확정 1건 |

### G. 실서버·브라우저(자동 테스트로 대체 불가)

| ID | 확인 | 담당 |
| --- | --- | --- |
| G1 ● | 두 계정 브라우저로 A1·A2·B2 전체 거래: 계약 화면, 서명, 대금 패널(보관→지급/반환), 지갑 원장 `SELLER_PAYOUT`/`REFUND`, 판매 수익 화면 | 정은찬·황준범 |
| G2 ● | 실서버 스케줄러: 타이머 만료 자동 확정, 사전 안내, 아웃박스 → 알림 생성 | 정은찬 |
| G3 ● | 거래 이력 "납기 통지 · 시스템"·알림 문구(보관·지급·반환·자동 확정) 표시 | 정은찬 |
| G4 ○ | 좁은 화면에서 계약·대금·타이머 패널 | 황준범 |
| G5 ● | 실제 V3 PDF 한글·금액·특약 표시, 다운로드 파일 해시가 화면 값과 일치 | 황준범 |

## 3. 추가할 자동 테스트

현재 근거에서 빠진 것만 추가한다. 모두 `@IntegrationTest`, 실제 커밋.

1. **A1 처음부터 끝까지**: 스위치 on 프로퍼티로 HTTP 생성 → 수락 → 초안·PDF·양측 서명 → 결제 → 납품 → 확정. 단계마다 상태·이력·아웃박스, 끝에서 4절 불변식.
2. **B1 V3 결제 전 취소**: 수락 전/계약 대기/서명 후 각각 금액 이동 0.
3. **C5 만료 vs 수정 요청 동시**: 만료 시각 직전·직후 두 경우. 어느 쪽이 이기든 지급·타이머·수정 요청이 한 결과로만 남는지.
4. **C6 관리자 해제 vs 만료 처리 동시**.
5. **불변식 공통 검사 메서드**: 각 V3 금융 테스트 끝에서 4절 조건을 확인(테스트 지원 코드, `src/test`라 JDBC 허용).

K4 구현 시 B4·C8을 같이 추가한다.

## 4. 금액 불변식 조회

결제된 V3 주문마다 `결제액 = 미해결 보관액 + 구매자 반환액 + 판매자 지급액 + 수수료`이고, 상태별로 한쪽만 존재해야 한다. 아래는 확인용 읽기 전용 조회다(운영 코드에 넣지 않는다). 결과가 **0행**이어야 통과다.

```sql
SELECT * FROM (
  SELECT o.id, o.buyer_id, o.seller_id, p.amount AS paid,
         f.order_id AS funds_order, f.payment_id AS funds_payment, f.amount AS funds_amount,
         f.status, f.remaining_amount AS held,
         pay.member_id AS debit_member, -pay.amount_delta AS debit,
         r.member_id AS refund_member, COALESCE(r.amount_delta, 0) AS refund,
         s.member_id AS payout_member, COALESCE(s.amount_delta, 0) AS payout,
         c.order_id AS closure_order, COALESCE(c.fee_amount, 0) AS fee, c.reason, p.id AS payment_id
  FROM orders o
  JOIN payments p ON p.order_id = o.id
  LEFT JOIN order_funds f ON f.order_id = o.id
  LEFT JOIN wallet_entries pay ON pay.payment_id = p.id AND pay.type = 'PAYMENT'
  LEFT JOIN wallet_entries r ON r.payment_id = p.id AND r.type = 'REFUND'
  LEFT JOIN wallet_entries s ON s.payment_id = p.id AND s.type = 'SELLER_PAYOUT'
  LEFT JOIN order_closures c ON c.order_id = o.id
  WHERE o.policy_version = 'CONTRACT_V3'
) t
WHERE (
  funds_order IS NOT NULL AND funds_payment = payment_id AND funds_amount = paid
  AND debit_member = buyer_id AND debit = paid
  AND (refund_member IS NULL OR refund_member = buyer_id)
  AND (payout_member IS NULL OR payout_member = seller_id)
  AND paid = held + refund + payout + fee
  AND ( (status = 'HELD'     AND closure_order IS NULL AND refund = 0 AND payout = 0)
     OR (status = 'RELEASED' AND reason IN ('COMPLETED','AUTO_CONFIRMED') AND refund = 0 AND payout > 0)
     OR (status = 'REFUNDED' AND reason IN ('MUTUAL_CANCELLATION','ADMIN_CANCELLATION') AND payout = 0 AND fee = 0) )
) IS NOT TRUE;
```

보관금·차감 원장·종료 기록은 모두 LEFT JOIN으로 읽고, 정상 조건 전체에 `IS NOT TRUE`를 적용해 행 누락(NULL)도 위반으로 잡는다. 원장은 회원 조건 없이 결제 ID로 읽고 회원 일치를 따로 검사해 다른 회원에게 연결된 원장도 잡는다. 컬럼 이름이 바뀌면 이 조회도 함께 고친다.

확인 기록(2026-11-06, 로컬 `lancerpick_test`): 조회 실행은 오류 없이 0행(V3 결제 데이터 0건). 바깥 판정식은 가짜 행 9개(정상 HELD/RELEASED/REFUNDED, 보관금 누락, 차감 원장 누락, 차감 원장 다른 회원, REFUNDED인데 closure 누락, 합계 불일치, HELD인데 closure 존재)로 위반 6건만 반환되는 것을 확인했다. 실제 V3 거래 데이터에서의 확인은 G1 판정 때 한다.

## 5. 활성화 절차

1. 후보 커밋 확정 → 1절 같은 소스 검사 → 2절 필수 항목·4절 0행 확인 → 6절 기록.
2. 정은찬·황준범이 결과를 함께 검토하고 팀에 공유한 뒤 `application.properties`의 기본값을 true로 바꾸는 PR을 올린다(설정 한 줄 + 문서 갱신만, 다른 변경 섞지 않음).
3. 되돌리기: 기본값을 false로 되돌리면 신규 주문만 LEGACY_V1로 돌아가고 이미 만든 V3 주문은 정책 고정으로 끝까지 처리된다(F4). 기존 주문의 `policy_version`을 DB에서 바꾸지 않는다.

## 6. 판정 기록

| 날짜 | 커밋 | 자동 테스트 | 실서버·브라우저 | 불변식 | 판정 |
| --- | --- | --- | --- | --- | --- |
| — | — | 아직 | 아직 | 아직 | 보류(K4 미연결, 3절 테스트 미추가) |
