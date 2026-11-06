# V3 신규 생성 활성화 게이트(G1) 시나리오

2026-11-06, 정은찬·황준범(G1). main `9f9aa8f`(#105) 기준. `lancerpick.order.full-funding-creation-enabled`(기본 false)를 켜서 신규 주문을 CONTRACT_V3로 만들기 전에 확인할 시나리오·판정 기준·현재 근거를 정리한다. 정책은 [결제 금액 보관 정책](full-funding-policy.md), 금액 처리는 [금액 처리 구현](full-funding-payment.md), 타이머는 [자동 구매 확정](auto-confirm-timer.md), 알림은 [V3 알림](v3-notices.md)을 따른다.

이 문서는 확인 목록이다. 아래 "현재 근거"는 해당 테스트가 존재한다는 뜻이며, G1 판정은 같은 커밋에서 다시 실행한 결과로만 한다.

## 1. 판정 규칙

- **같은 소스**: 활성화 후보 커밋 하나에서 백엔드 `clean build`, 프런트 `typecheck`·`lint`·`test:api`·`build`, 실서버·브라우저 확인을 모두 한다. 이전 커밋의 결과를 합산하지 않는다.
- **세 가지 증거를 구분**해 기록한다: 자동 테스트(로컬 `lancerpick_test`/CI), 실서버+실제 DB(개인 DB에서 스위치를 켠 실행), 브라우저(두 계정 실제 로그인).
- **필수 조건**: 아래 표의 필수(●) 항목이 모두 통과하고, 4절 금액 불변식 조회 결과가 0행이며, 열린 결함이 없을 것.
- **켜지 않는 사유**: K4 미연결이면 결제 후 판매자 무응답 취소를 처리할 운영 경로가 없으므로 활성화하지 않는다. 주문 쪽 서비스는 #121로 반영됐고, 관리자 API·화면 연결 전까지 G1 보류.
- 판정 기록은 이 문서 6절과 [현재 현황](current-status.md)에 남긴다.

## 2. 시나리오

표기: ● 필수, ○ 권장. 근거 클래스는 `LancerPick/src/test/java/com/example/lancerpick/` 기준.

### A. 정상 흐름

| ID | 시나리오 | 기대 결과 | 현재 근거 | 남은 확인 |
| --- | --- | --- | --- | --- |
| A1 ● | 스위치 on 상태에서 HTTP 주문 생성 → 수락 → 초안 → PDF → 양측 서명 → 결제 → 납품 → 구매 확정 | 정책 V3 고정, 결제액만 차감·HELD, 확정 시 판매자 +90%·수수료 10%·완료, 이력·알림 순서 | `V3ActivationGateIntegrationTests`.switchOnHttpTradeRuns…(실제 설정 on, 전 단계 HTTP·이력·아웃박스 순서·대금 조회). 실서버 API 실행 기록: [V3 알림](v3-notices.md#실서버브라우저-확인--2026-11-06-main-9f9aa8f) | 화면 버튼으로 하는 전체 거래(G1 행) |
| A2 ● | 수정 요청 1회 → 재납품 → 확정 | 재납품 기한=요청 시각+소요일, 두 번째 수정 요청 거절 | `ContractV3FulfillmentApiIntegrationTests.v3DeliveryRevisionAndRedelivery…`, `ReviewTimerIntegrationTests.revisionNotifiesRedeliveryDeadline…` | 브라우저 |
| A3 ● | 납품 후 48시간 → 판매자 타이머 시작 → 7일 만료 → 자동 확정 | SYSTEM 완료·AUTO_CONFIRMED closure·수수료 동일·양측 알림·후기 가능 | `ReviewTimerIntegrationTests.sellerStartsAfter48Hours…`, `concurrentExpiryRunsPaySellerOnlyOnce` | 실서버 스케줄러 실제 실행(시간 단축 설정은 개인 DB에서만), 후기 작성 브라우저 |
| A4 ○ | 만료 24시간 전 구매자 사전 안내 | 구매자에게 1회, 판매자 없음 | `autoConfirmNoticeIsSentOnceToBuyer…`, `…NotSentForEndedOrExpiredTimers` | 실서버 알림 목록 |
| A5 ● | 결제 시 납기 통지 | `DEADLINE_NOTIFIED` 1건·`orders.due_at`과 일치 | `paymentNotifiesDeliveryDeadlineOnce…` | 거래 이력 화면 표시 |

### B. 취소·반환

| ID | 시나리오 | 기대 결과 | 현재 근거 | 남은 확인 |
| --- | --- | --- | --- | --- |
| B1 ● | 결제 전 취소(수락 전·계약 대기·서명 후) | 금액 이동 없음, `ORDER_CANCELLED` | `V3ActivationGateIntegrationTests`.cancellationBeforePaymentMovesNoMoney…(V3 수락 전·계약 대기·서명 후 구매자/판매자), V2 기준 `OrderContractIntegrationTests` | — |
| B2 ● | 결제 후 구매자 취소 요청 → 판매자 승인 | 보관금 전액 반환·REFUNDED·수익/수수료 0·`ORDER_FUNDS_REFUNDED` | `FullFundingTradeIntegrationTests.approvedCancellationRefundsOnce…`, `manualConfirmationAndApprovedRefundUseV3FundsWording` | 브라우저 |
| B3 ● | 취소 요청 거절·철회 후 재개 | 타이머 종료, 해결 시각이 새 기산점 | `cancellationRequestCancelsTimerAndRejectionRestartsReviewWindow` | — |
| B4 ● | 판매자 무응답 → 운영 문의 → 관리자 승인 취소(K4) | 전액 반환·`ADMIN_CANCELLATION`(`inquiry_id`)·문의 종료 한 트랜잭션 | 서비스 통합 테스트(`AdminCancellationReviewIntegrationTests`, 승인·거절·재전송·실패 롤백, main 반영 #121) | 관리자 API·화면 연결 후 실서버·브라우저 |
| B5 ● | 결제 후 판매자 단독 취소 시도 | 409 `CANCELLATION_REQUEST_REQUIRED` | `pendingCancellationBlocksConfirmationAndWrongActors…` | — |

### C. 경합(실제 커밋·동시 스레드)

| ID | 시나리오 | 기대 결과 | 현재 근거 | 남은 확인 |
| --- | --- | --- | --- | --- |
| C1 ● | 같은 지갑으로 두 주문 동시 결제 | 잔액 초과 없음 | `simultaneousPaymentsUseSharedWalletWithoutOverspending` | — |
| C2 ● | 같은 주문 동시 결제·재전송 | 차감 1건 | `repeatedConcurrentPaymentsCreateOneDebit` | — |
| C3 ● | 동시 구매 확정 | 판매자 지급 1건 | `simultaneousConfirmationsCreditSellerOnlyOnce` | — |
| C4 ● | 취소 승인 vs 구매 확정 | 하나만 성공 | `cancellationAndConfirmationCannotBothWin` | — |
| C5 ● | 타이머 만료 vs 구매자 확정/수정/취소 요청 | 만료 시각 이상이면 만료 우선(409 `AUTO_CONFIRM_DUE`), 응답이 먼저 커밋되면 만료 작업은 지급 안 함 | 순차: `ReviewTimerIntegrationTests`(AUTO_CONFIRM_DUE 3건). 동시: `V3ActivationGateIntegrationTests`.expiryRacingBuyerResponses…(만료 후 만료 처리·수정·취소 요청·확정 4개 동시), responseCommittedBeforeExpiry…(만료 전 동시 → 끝난 타이머는 이후 만료 실행에서도 지급 안 함) | — |
| C6 ● | 관리자 해제 vs 자동 확정 | 같은 잠금으로 직렬화, 만료 후 해제는 409 | 순차: `adminCannotStopDueTimerBecauseExpiryWins`. 동시: `V3ActivationGateIntegrationTests`.adminStopRacingDueExpiry…, adminStopBeforeExpiry… | — |
| C7 ● | 여러 서버의 만료 스케줄러 동시 실행 | 지급 1건 | `concurrentExpiryRunsPaySellerOnlyOnce` | — |
| C8 ○ | 관리자 승인 취소 vs 판매자 승인/구매 확정 | 하나만 성공 | 관리자 승인 vs 판매자 승인·구매자 철회·구매 확정 동시 실행(`AdminCancellationReviewIntegrationTests`, main 반영 #121) | — |

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

## 3. 추가한 자동 테스트 (2026-11-06)

현재 근거에서 빠진 것만 추가했다. 모두 `@IntegrationTest`, 실제 커밋. 위치 `order/V3ActivationGateIntegrationTests`(실제 설정 `lancerpick.order.full-funding-creation-enabled=true`로 컨텍스트를 띄우고 주문도 실제 생성 경로로 만든다).

1. **A1 처음부터 끝까지**: HTTP 생성 → 수락 → 초안·PDF·양측 서명 → 결제 → 납품 → 확정. 정책·거래 조건 고정, 잔액, 대금 조회, 이력 8단계·아웃박스 8종 순서.
2. **B1 V3 결제 전 취소**: 수락 전(판매자 취소는 409)·계약 대기·서명 후(구매자·판매자 각각). 결제·보관·종료·정산·원장 0건, 취소 후 결제 거절.
3. **C5 만료 vs 구매자 응답 동시**: 만료 시각 이후 4개 동시 실행은 자동 확정 하나만 남고 수정·취소 요청 행이 생기지 않음. 만료 전 동시 실행은 수정 요청이 타이머를 끝내고, 그 타이머의 만료 시각이 지나도 이후 만료 실행이 지급하지 않음.
4. **C6 관리자 해제 vs 만료 처리 동시**: 같은 두 경우를 관리자 해제로 검사.
5. **불변식 공통 검사**: `support/V3FundsInvariant`(4절 조회와 같은 조건, `src/test`라 JDBC 허용). 이 클래스와 `FullFundingTradeIntegrationTests`·`ReviewTimerIntegrationTests`의 모든 검사 뒤 정리 단계에서 판정한다. 차감 원장을 지운 상태를 위반으로 잡는지도 검사한다.

동시 검사는 두 스레드를 같은 시점에 출발시키지만 잠금 획득 순서를 강제하지 않는다. 그래서 순서와 관계없이 성립해야 하는 최종 상태(지급 1회, 남은 요청 행, 타이머 상태)를 판정한다. K4 구현 시 B4·C8을 같이 추가한다.

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
| 2026-11-06 | `test/v3-activation-gate` 작업 트리 | 로컬 `lancerpick_test` 백엔드 `clean build` 835건 통과(실패·오류·건너뜀 0). 프런트 변경 없음 | API 실서버·일부 브라우저([V3 알림](v3-notices.md#실서버브라우저-확인--2026-11-06-main-9f9aa8f)), 화면 버튼 전체 거래 아직 | 실서버 3건 0행, 자동 테스트 정리 단계 판정 | 보류(K4 미연결) |
| 2026-11-06 | main `f82c49d` + `test/v3-gate-browser-check` 작업 트리(main 반영 #116) | 백엔드 재실행 안 함(운영 코드 변경 없음). 프런트 typecheck·lint·`test:api` 219건·build 통과 | [화면 버튼 전체 거래](#7-화면-버튼-전체-거래-확인--2026-11-06): G1(A1·B2)·G5 확인, A2는 앞선 API 실서버 기록 | 주문 18~22 전체 0행 | 보류(K4 미연결, G2 타이머 진행 중 패널·G4 미확인) |
| 2026-11-06 | main `7329be1` + `test/v3-gate-remaining-check` 작업 트리(main 반영 #120) | 재실행 안 함(운영 코드 변경 없음) | [남은 화면 확인](#8-남은-화면-확인--2026-11-06): G2 타이머 시작 가능→진행 중 패널(판매자·구매자), G4 320px(주문 17·21·22·23) | 주문 18~23 전체 0행 | 보류(K4 미연결, B4·C8) |

## 7. 화면 버튼 전체 거래 확인 — 2026-11-06

환경: main `f82c49d` 백엔드 `bootRun`(개인 개발 DB, 실행 인자로만 `--lancerpick.order.full-funding-creation-enabled=true`, 설정 파일 변경 없음) + Vite 개발 서버 + Playwright(Chromium) 격리 컨텍스트 3개(구매자 14·판매자 15·제3자 12). 실행 중 개인 DB에 V8·V9가 적용됐다. 모든 단계는 API 직접 호출이 아니라 **화면 버튼**으로 진행했다.

- **A1 정상 거래(주문 21)**: 상품 상세 → 주문 요청 → 판매자 수락 → 특약 입력·초안 저장 → PDF 생성 → 판매자 PDF 다운로드·동의·서명(1/2) → 구매자 PDF 다운로드·서명(2/2, 결제 대기) → 결제 동의·적립금 결제(보관금 50,000원, 납기 표시) → 1차 납품 → 수정 요청 → 2차 납품 → 구매 확정. 대금 패널 "판매자 지급 완료 · 수수료 5,000원 · 지급 45,000원". 납품 후 E7 타이머 패널 "검수 응답 대기"와 시작 가능 시각 표시. 거래 이력 13단계(납기·재납품 기한 통지 포함), 종료 후 대화는 조회만.
- **B2 결제 후 취소(주문 22)**: 특약 없이 같은 계약 절차 → 결제 → 구매자 취소 요청 → 판매자 "승인 및 환불". 대금 패널 "구매자 전액 반환 완료 · 반환 50,000원 · 수수료 0원".
- **지갑·판매 수익 화면**: 구매자 잔액 850,000원(#21 결제, #22 결제·결제 환불), 판매자 잔액 1,135,000원(#21 판매대금 지급 +45,000원), 판매 수익 화면에 #21 결제액·수수료·수익 표시.
- **DB 대조**: 주문 21 `RELEASED`/closure `COMPLETED` 수수료 5,000원/`SELLER_PAYOUT` 45,000원, 주문 22 `REFUNDED`/`MUTUAL_CANCELLATION`/`REFUND` 50,000원. 4절 불변식 조회 0행(주문 18~22 전체). 아웃박스 이벤트 모두 `COMPLETED`, 알림 수신자와 일치.
- **G5 PDF**: 두 당사자가 화면에서 받은 PDF의 SHA-256이 `order_contracts.file_hash`와 일치하고, 양측 `contract_signatures`의 서명 시점 파일·본문 해시도 현재 계약과 일치. PDFBox 텍스트 추출로 3쪽, 한글 본문·총금액 50000·특약·수수료 1000bps·`CONTRACT_V3` 표시 확인(글꼴 렌더링 눈 확인은 안 함).
- **권한**: 제3자 계정은 주문 화면 "주문을 찾을 수 없습니다", `GET /api/orders/21`·`/contract`·`/contract/file`·`/funds`가 모두 404. 당사자는 같은 PDF 주소에서 200 `application/pdf`.

발견(이 확인 범위에서 고치지 않음. 3건 모두 계약·정산 영역으로 황준범에게 전달했고 황준범이 처리 예정, 2026-11-06):
1. 계약 서명 알림은 두 번째 서명에서만 생긴다(`ORDER_CONTRACT_SIGNED` 한 건, 마지막 서명자의 상대방에게). 먼저 서명한 쪽의 상대방은 서명 차례라는 알림을 받지 못한다. [계약 기반](order-contract-foundation.md) 설계대로이며, 첫 서명 알림을 추가할지는 결정이 필요하다.
2. 판매 수익 화면 안내가 "구매 적립금과 별도로 관리"인데, V3는 판매자 구매용 지갑에 `SELLER_PAYOUT`으로 지급한다(지갑 화면에도 "판매대금 지급"으로 보임). 정산 화면(황준범) 문구 확인 필요.
3. 결제 대기 전 계약 패널이 계약이 없을 때 오류 형태(alert)로 "아직 작성된 계약이 없거나 접근할 수 없습니다."를 보이고 콘솔에 404가 남는다. 정상 상태 안내로 바꿀지 검토(계약 화면, 황준범).

후속 정책 확정(2026-11-06, 사용자): 위 발견 1의 첫 서명 알림을 추가한다. `feature/first-contract-signature-notification`의 로컬 구현은 미서명 상대방에게만 `ORDER_CONTRACT_SIGNATURE_REQUESTED`를 보내고, 양측 완료 전환·완료 알림은 유지한다. 서명 이력/아웃박스의 원자 처리·반복 및 동시 요청 중복 방지는 [알림 기록](notifications.md#첫-계약-서명-알림--2026-11-06-로컬-후속)을 따른다. 실제 브라우저 재확인은 아직이다.

고친 것: 판매자 수락 안내 "수락하면계약 작성·…"의 공백 누락(JSX 줄바꿈 공백 제거, `OrderDetailPage.tsx`). 프런트 검사 통과, 수정 후 화면 재확인은 안 함.

남은 G 항목: G2 타이머 "진행 중" 패널 화면(시작 가능 시각 48시간 대기 또는 DB 시각 조정 필요), G4 좁은 화면, K4 연결 후 B4·C8. → G2 패널·G4는 8절에서 확인.

## 8. 남은 화면 확인 — 2026-11-06

환경: main `7329be1` 백엔드 `bootRun`(개인 개발 DB, 실행 인자로만 V3 생성 허용) + Vite + Playwright 격리 컨텍스트(구매자 14·판매자 15). 담당표의 G4는 황준범이지만 이번 확인은 정은찬이 했다.

- **G2 타이머 진행 중 패널(주문 23)**: 주문 생성→수락→계약 초안·PDF·양측 서명→결제→1차 납품은 **API 호출**로 진행했다(화면 버튼 거래는 7절). 개인 DB에서 이 주문의 `deliveries.submitted_at` 한 행만 49시간 앞당겼다. 판매자 패널 "자동 구매 확정 타이머 시작 가능" → 화면 버튼 "7일 자동 구매 확정 타이머 시작" → "자동 구매 확정 타이머 진행 중 · 2026. 11. 13. 오후 4:55까지 구매자 응답(구매 확정·수정 요청·취소 요청)이 없으면 자동으로 구매가 확정됩니다". 구매자 패널도 같은 진행 중 안내이며 시작 버튼이 없다. DB: 타이머 `AUTO_CONFIRM` ACTIVE 1행(시작+7일 만료), `order_funds` HELD 50,000원.
- **G4 320px**: 구매자로 주문 23(진행 중 타이머·보관 중 대금)·21(지급 완료)·22(전액 반환)·17(계약 대기)에서 문서 가로 넘침 없음(`scrollWidth`=`clientWidth`, 화면 밖 요소 0). 대금·타이머 패널 문구가 줄바꿈되고 겹치지 않음을 스크린샷으로 확인.
- **불변식**: 4절 조회 0행(주문 18~23).

남은 G 항목: K4 관리자 API·화면 연결 후 B4 실서버·브라우저 확인과 활성화 판정(B4·C8 자동 테스트는 #121). 실제 48시간·7일 시간 경과는 확인하지 않았다(시각 조정으로 대신함).
