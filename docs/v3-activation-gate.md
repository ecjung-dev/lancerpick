# V3 신규 생성 활성화 게이트(G1) 시나리오

2026-11-06, 정은찬·황준범(G1). main `9f9aa8f`(#105) 기준. `lancerpick.order.full-funding-creation-enabled`(기본 false)를 켜서 신규 주문을 CONTRACT_V3로 만들기 전에 확인할 시나리오·판정 기준·현재 근거를 정리한다. 정책은 [결제 금액 보관 정책](full-funding-policy.md), 금액 처리는 [금액 처리 구현](full-funding-payment.md), 타이머는 [자동 구매 확정](auto-confirm-timer.md), 알림은 [V3 알림](v3-notices.md)을 따른다.

이 문서는 확인 목록이다. 아래 "현재 근거"는 해당 테스트가 존재한다는 뜻이며, G1 판정은 같은 커밋에서 다시 실행한 결과로만 한다.

## 2026-11-09 재판정 준비 — 활성화 승인 아님

main `44c7857`에는 K4 관리자 화면(#132)·운영 조회/타이머 화면(#133), U7 신규 주문/판매자 수락 차단(#140), 보안 잠금 타이머(#139), 채팅 화면(#142)이 반영돼 있다. 과거의 ‘K4 화면 미연결’ 보류 사유는 해소됐지만 과거 커밋의 통과 결과로 현재 활성화를 승인하지 않는다.

- 미결제 V3 취소·거절 주문의 대금 안내는 #129에서 `결제 없이 종료된 거래입니다.`로 수정됐고 `order-funds.test.mjs`에 회귀 검사가 있다. 후보 커밋의 실제 화면에서도 재확인한다.
- 이용 제한 판매자의 수락 버튼이 남는 문제는 서버 403 차단과 별개인 표시 결함이다. `fix/restricted-order-actions`에서 상세 `allowedActions`의 `ACCEPT`만 제한 상태에 따라 제외하도록 로컬 구현·관련 검증을 마쳤다. main 병합 후 후보 SHA를 고정한다. 일반 제한 중 거절·기존 거래 처리와 만료/해제 후 수락은 유지한다. 화면은 조회 당시 정보이므로 이후 제재 변경과 경합하면 기존 서버 잠금 아래 검사가 최종 기준이다.
- 후보 확정 후 백엔드 `clean build`와 프런트 네 가지 검사를 같은 SHA에서 실행한다. 실서버/브라우저 B4(관리자 승인·거절·타이머 해제)·F4(on→off 후 기존 V3 완료), 필수 시나리오와 4절 불변식을 함께 확인한다. 이번 준비 검사는 최종 후보 검사를 대체하지 않는다.
- 정은찬은 B4/F4·불변식 조회·판정 기록을, 황준범은 대금 안내/수락 버튼 보완 확인·결과 공동 검토·활성화 PR 리뷰를 맡는다. 이 기록 자체로 다른 담당자의 검증을 수행 완료했다고 표시하지 않는다.
- 결과를 6절에 남겨 공동 통과 판정한 뒤에만 설정 기본값과 문서로 한정한 활성화 PR을 올린다. 현재 기본값 `false`를 유지한다. 구체적인 판정일은 수정 병합과 검사 일정에 따라 확정한다.

준비 검증(위 브랜치, main `44c7857` 기반): 격리 MySQL 8.4에서 주문 조회·생성·수락 및 기본 컨텍스트 관련 백엔드 102건 통과(실패·오류·건너뜀 0). 기존/V2/V3 제한 중 수락 제외·거절 유지·구매자 행동 유지·만료 경계/해제 후 복구와 기존 거래 처리를 검사했다. 프런트 대금 패널 회귀 5건 통과. DB·프런트 운영 코드·기본 스위치 변경 없음. 전체 clean build·프런트 네 가지 검사·실서버/브라우저 B4/F4·실데이터 불변식 조회를 수행한 최종 게이트 결과는 아니다.

후속(2026-11-09): #144가 main에 병합된 뒤 후보 SHA를 **main `f28b0d7`(#143)** 로 고정하고, 같은 커밋에서 위 검사를 모두 다시 했다([12절](#12-후보-sha-f28b0d7-같은-커밋-재확인--2026-11-09)). 이어 #145(B4 발견 보완, 공통 오류 처리·관리자 화면 코드 변경)가 main에 들어와 후보를 **main `60b7b7f`(#145)** 로 다시 고정하고 같은 커밋에서 전체를 재확인했다([13절](#13-최종-후보-sha-60b7b7f-같은-커밋-재확인--2026-11-09)). 정은찬 확인 범위의 필수 항목은 통과했다. 활성화 판정은 황준범 공동 검토 뒤 6절에 확정하며, 그 전까지 기본값 `false`를 유지한다.

## 1. 판정 규칙

- **같은 소스**: 활성화 후보 커밋 하나에서 백엔드 `clean build`, 프런트 `typecheck`·`lint`·`test:api`·`build`, 실서버·브라우저 확인을 모두 한다. 이전 커밋의 결과를 합산하지 않는다.
- **세 가지 증거를 구분**해 기록한다: 자동 테스트(로컬 `lancerpick_test`/CI), 실서버+실제 DB(개인 DB에서 스위치를 켠 실행), 브라우저(두 계정 실제 로그인).
- **필수 조건**: 아래 표의 필수(●) 항목이 모두 통과하고, 4절 금액 불변식 조회 결과가 0행이며, 열린 결함이 없을 것.
- **켜지 않는 사유**: K4 미연결이면 결제 후 판매자 무응답 취소를 처리할 운영 경로가 없으므로 활성화하지 않는다. 주문 쪽 서비스는 #121, 관리자 API·화면은 #132로 연결됐고 B4 실서버·브라우저 확인을 마쳤다([9절](#9-b4-관리자-승인거절타이머-해제f4-확인--2026-11-09)).
- 판정 기록은 이 문서 6절과 [현재 현황](current-status.md)에 남긴다.

## 2. 시나리오

표기: ● 필수, ○ 권장. 근거 클래스는 `LancerPick/src/test/java/com/example/lancerpick/` 기준.

### A. 정상 흐름

| ID | 시나리오 | 기대 결과 | 현재 근거 | 남은 확인 |
| --- | --- | --- | --- | --- |
| A1 ● | 스위치 on 상태에서 HTTP 주문 생성 → 수락 → 초안 → PDF → 양측 서명 → 결제 → 납품 → 구매 확정 | 정책 V3 고정, 결제액만 차감·HELD, 확정 시 판매자 +90%·수수료 10%·완료, 이력·알림 순서 | `V3ActivationGateIntegrationTests`.switchOnHttpTradeRuns…(실제 설정 on, 전 단계 HTTP·이력·아웃박스 순서·대금 조회). 실서버 API 실행 기록: [V3 알림](v3-notices.md#실서버브라우저-확인--2026-11-06-main-9f9aa8f) | 화면 버튼으로 하는 전체 거래(G1 행) |
| A2 ● | 수정 요청 1회 → 재납품 → 확정 | 재납품 기한=요청 시각+소요일, 두 번째 수정 요청 거절 | `ContractV3FulfillmentApiIntegrationTests.v3DeliveryRevisionAndRedelivery…`, `ReviewTimerIntegrationTests.revisionNotifiesRedeliveryDeadline…`. 브라우저 [10절](#10-같은-커밋-화면-버튼-전체-거래스케줄러-확인--2026-11-09)(주문 37) | — |
| A3 ● | 납품 후 48시간 → 판매자 타이머 시작 → 7일 만료 → 자동 확정 | SYSTEM 완료·AUTO_CONFIRMED closure·수수료 동일·양측 알림·후기 가능 | `ReviewTimerIntegrationTests.sellerStartsAfter48Hours…`, `concurrentExpiryRunsPaySellerOnlyOnce`. 실서버 스케줄러·후기 등록 [10절](#10-같은-커밋-화면-버튼-전체-거래스케줄러-확인--2026-11-09)(주문 39) | — |
| A4 ○ | 만료 24시간 전 구매자 사전 안내 | 구매자에게 1회, 판매자 없음 | `autoConfirmNoticeIsSentOnceToBuyer…`, `…NotSentForEndedOrExpiredTimers`. 실서버 알림 목록 [10절](#10-같은-커밋-화면-버튼-전체-거래스케줄러-확인--2026-11-09)(주문 39) | — |
| A5 ● | 결제 시 납기 통지 | `DEADLINE_NOTIFIED` 1건·`orders.due_at`과 일치 | `paymentNotifiesDeliveryDeadlineOnce…`. 거래 이력 화면 [10절](#10-같은-커밋-화면-버튼-전체-거래스케줄러-확인--2026-11-09) | — |

### B. 취소·반환

| ID | 시나리오 | 기대 결과 | 현재 근거 | 남은 확인 |
| --- | --- | --- | --- | --- |
| B1 ● | 결제 전 취소(수락 전·계약 대기·서명 후) | 금액 이동 없음, `ORDER_CANCELLED` | `V3ActivationGateIntegrationTests`.cancellationBeforePaymentMovesNoMoney…(V3 수락 전·계약 대기·서명 후 구매자/판매자), V2 기준 `OrderContractIntegrationTests` | — |
| B2 ● | 결제 후 구매자 취소 요청 → 판매자 승인 | 보관금 전액 반환·REFUNDED·수익/수수료 0·`ORDER_FUNDS_REFUNDED` | `FullFundingTradeIntegrationTests.approvedCancellationRefundsOnce…`, `manualConfirmationAndApprovedRefundUseV3FundsWording`. 브라우저 [10절](#10-같은-커밋-화면-버튼-전체-거래스케줄러-확인--2026-11-09)(주문 38) | — |
| B3 ● | 취소 요청 거절·철회 후 재개 | 타이머 종료, 해결 시각이 새 기산점 | `cancellationRequestCancelsTimerAndRejectionRestartsReviewWindow` | — |
| B4 ● | 판매자 무응답 → 운영 문의 → 관리자 승인 취소(K4) | 전액 반환·`ADMIN_CANCELLATION`(`inquiry_id`)·문의 종료 한 트랜잭션 | 서비스 통합 테스트(`AdminCancellationReviewIntegrationTests`, 승인·거절·재전송·실패 롤백, main 반영 #121). 실서버·브라우저 [9절](#9-b4-관리자-승인거절타이머-해제f4-확인--2026-11-09)(주문 34 승인·35 거절) | — |
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
| E4 ● | 계약 PDF 다운로드는 당사자만, 변조 파일은 503 | 바이트 노출 없음 | `ContractDocumentIntegrationTests.finishedDocument…OnlyToParties`, `missingAndTamperedFiles…`. 실제 다운로드·해시 대조 [10절](#10-같은-커밋-화면-버튼-전체-거래스케줄러-확인--2026-11-09)(주문 37~39) | — |
| E5 ○ | 계정 전환 시 대금·주문 캐시 정리 | 이전 계정 금액 미표시 | 프런트 `test:api`(order-funds queryKey) | 브라우저 두 계정 |

### F. 호환·회귀

| ID | 시나리오 | 기대 결과 | 현재 근거 |
| --- | --- | --- | --- |
| F1 ● | 스위치 on 전후 기존 LEGACY_V1 주문 처리 | 정책 값 불변, 기존 결제·환불·정산 그대로 | `replayKeepsPolicyFixedAtCreationEvenAfterSwitch`, `LegacyPaymentCompatibilityTests`, 기존 주문 통합 테스트 |
| F2 ● | CONTRACT_V2 기존 주문 | 결제 후 명령 계속 차단, 계약 원문·해시 보존 | `v2PaidOrderStaysBlocked`, `OrderContractIntegrationTests.replacementPreservesOldSignature…`, `OrderCreationIntegrationTests.fullFundingOrderFreezesNewTerms…`(기존 V1/V2 원문 보존) |
| F3 ● | 금액 하한·상한 | 10,000원 미만 생성·확정·결제 거절 | `contractOrderRejectsTotalBelowMinimum…`, `contractOrderAcceptsMinimumBoundary`, DB CHECK |
| F4 ● | 스위치를 다시 끄면 | 신규만 LEGACY_V1, 이미 만든 V3 주문은 끝까지 처리 | 정책 고정 검사(F1)로 간접 확인. 실서버 on→off 후 V3 주문 35 확정·신규 주문 36 LEGACY_V1([9절](#9-b4-관리자-승인거절타이머-해제f4-확인--2026-11-09)) |

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
| 2026-11-09 | main `f3c56be`(#140) | 로컬 `lancerpick_test` 백엔드 `clean build` 1002건 통과(실패·오류·건너뜀 0). 프런트 typecheck·lint·`test:api` 272건·build 통과 | 같은 커밋에서 [B4·F4](#9-b4-관리자-승인거절타이머-해제f4-확인--2026-11-09)(주문 34~36)와 [G1 전체 화면 거래·스케줄러](#10-같은-커밋-화면-버튼-전체-거래스케줄러-확인--2026-11-09)(주문 37~39: G1 A1·A2·B2, G2 자동 확정·사전 안내, G3, G5·E4, E1, G4 320px). 이전 커밋 결과는 합산하지 않음 | 결제된 V3 주문 12건 전체 0행 | `f3c56be` 기준 필수(●) 항목 전체 같은 커밋 근거 확인([11절 대조표](#11-필수-항목-같은-커밋-근거-대조--2026-11-09)), 열린 결함 없음. 단 이후 #142·#144로 코드가 바뀌어 최종 판정이 아님: #144 이후 고정할 후보 SHA에서 같은 검사·필수 시나리오 재확인 필요. 권장 E5(계정 전환 캐시 브라우저)는 미확인 |
| 2026-11-09 | **후보** main `f28b0d7`(#143, #141·#142·#144 포함) | 로컬 `lancerpick_test` 백엔드 `clean build` 1009건 통과(실패·오류·건너뜀 0, 16:09~16:12 KST). 프런트 `npm ci`·typecheck·lint·`test:api` 274건·build 통과 | 같은 커밋에서 [12절](#12-후보-sha-f28b0d7-같은-커밋-재확인--2026-11-09): G1 A1·A2(40)·B2(41), G2 사전 안내·자동 확정·후기(42), B4 승인(43)·거절·타이머 해제(44), F4 off 후 44 확정·신규 45 LEGACY_V1, E1 제3자 404, G5·E4 해시 10/10·쪽별 렌더링, #129 대금 안내, #144 제한 판매자 수락 버튼, 320px | 결제된 V3 주문 17건 전체 0행, 아웃박스 217건 모두 `COMPLETED` | `f28b0d7` 기준 필수(●) 항목 충족·열린 결함 없음. 단 이후 #145로 운영 코드가 바뀌어 최종 후보가 아님(다음 행) |
| 2026-11-09 | **최종 후보** main `60b7b7f`(#145) | 로컬 `lancerpick_test` 백엔드 `clean build` 1011건 통과(실패·오류·건너뜀 0, 16:34~16:37 KST). 프런트 `npm ci`·typecheck·lint·`test:api` 274건·build 통과. 실행 트리는 `60b7b7f`와 운영·테스트 코드가 같은 병합 커밋(문서만 다름) | 같은 코드에서 [13절](#13-최종-후보-sha-60b7b7f-같은-커밋-재확인--2026-11-09): G1 A1·A2(47)·B2(48), G2 사전 안내·자동 확정·후기(49), B4 승인(50)·거절·타이머 해제(51), F4 off 후 51 확정·신규 52 LEGACY_V1, E1 제3자 404, G5·E4 해시 10/10·쪽별 렌더링, #129, #144(53), #145 관리자 상태 라벨·PDF 406, 320px | 결제된 V3 주문 22건 전체 0행, 아웃박스 278건 모두 `COMPLETED` | 정은찬 확인: 필수(●) 항목 전체 같은 커밋 근거 충족, 열린 결함 없음. **황준범 공동 검토 대기**(통과 판정 후 기본값 변경 PR). 권장 E5는 미확인 |

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

후속 수정(2026-11-06, `fix/trade-finance-guidance`, 로컬): 위 발견 2·3의 판매 수익 지급 안내와 정상 계약 미작성 alert 표시를 보완했다. 기존 거래 수익의 별도 기록과 새 계약의 구매 적립금 지급을 구분하고, 생성 이력·기존 데이터·계약 필수 상태·오류 종류로 미작성 안내와 실제 실패를 구분한다. 이 화면 수정은 API/DB·서명 알림을 변경하지 않는다. 초기 프런트 전체 222건·lint·TypeScript 포함 build 통과(신규 회귀 3건). SSR 자동 검사이며 실제 브라우저 재확인은 하지 않았다. 발견 1의 첫 서명 알림 추가는 이후 별도 PR #123으로 main에 반영됐다.

후속 정책 확정(2026-11-06, 사용자): 위 발견 1의 첫 서명 알림을 추가한다. `feature/first-contract-signature-notification`의 로컬 구현은 미서명 상대방에게만 `ORDER_CONTRACT_SIGNATURE_REQUESTED`를 보내고, 양측 완료 전환·완료 알림은 유지한다. 서명 이력/아웃박스의 원자 처리·반복 및 동시 요청 중복 방지는 [알림 기록](notifications.md#첫-계약-서명-알림--2026-11-06-로컬-후속)을 따른다. 실제 브라우저 재확인은 아직이다.

고친 것: 판매자 수락 안내 "수락하면계약 작성·…"의 공백 누락(JSX 줄바꿈 공백 제거, `OrderDetailPage.tsx`). 프런트 검사 통과, 수정 후 화면 재확인은 안 함.

남은 G 항목: G2 타이머 "진행 중" 패널 화면(시작 가능 시각 48시간 대기 또는 DB 시각 조정 필요), G4 좁은 화면, K4 연결 후 B4·C8. → G2 패널·G4는 8절에서 확인.

## 8. 남은 화면 확인 — 2026-11-06

환경: main `7329be1` 백엔드 `bootRun`(개인 개발 DB, 실행 인자로만 V3 생성 허용) + Vite + Playwright 격리 컨텍스트(구매자 14·판매자 15). 담당표의 G4는 황준범이지만 이번 확인은 정은찬이 했다.

- **G2 타이머 진행 중 패널(주문 23)**: 주문 생성→수락→계약 초안·PDF·양측 서명→결제→1차 납품은 **API 호출**로 진행했다(화면 버튼 거래는 7절). 개인 DB에서 이 주문의 `deliveries.submitted_at` 한 행만 49시간 앞당겼다. 판매자 패널 "자동 구매 확정 타이머 시작 가능" → 화면 버튼 "7일 자동 구매 확정 타이머 시작" → "자동 구매 확정 타이머 진행 중 · 2026. 11. 13. 오후 4:55까지 구매자 응답(구매 확정·수정 요청·취소 요청)이 없으면 자동으로 구매가 확정됩니다". 구매자 패널도 같은 진행 중 안내이며 시작 버튼이 없다. DB: 타이머 `AUTO_CONFIRM` ACTIVE 1행(시작+7일 만료), `order_funds` HELD 50,000원.
- **G4 320px**: 구매자로 주문 23(진행 중 타이머·보관 중 대금)·21(지급 완료)·22(전액 반환)·17(계약 대기)에서 문서 가로 넘침 없음(`scrollWidth`=`clientWidth`, 화면 밖 요소 0). 대금·타이머 패널 문구가 줄바꿈되고 겹치지 않음을 스크린샷으로 확인.
- **불변식**: 4절 조회 0행(주문 18~23).

남은 G 항목: K4 관리자 API·화면 연결 후 B4 실서버·브라우저 확인과 활성화 판정(B4·C8 자동 테스트는 #121). 실제 48시간·7일 시간 경과는 확인하지 않았다(시각 조정으로 대신함).

## 9. B4 관리자 승인·거절·타이머 해제·F4 확인 — 2026-11-09

환경: main `f3c56be`(#140) 백엔드 `bootRun`(개인 개발 DB, 실행 중 V10 적용, 실행 인자로만 `--lancerpick.order.full-funding-creation-enabled=true`) + Vite + Playwright(Chromium) 격리 컨텍스트 3개(구매자 14·판매자 15·관리자 12). 관리자는 [관리자 상품 운영](admin-product-moderation.md)의 로컬 절차대로 개인 DB의 테스트 계정 12를 ADMIN으로 바꿨다. 주문 34·35의 생성→수락→계약 초안·PDF·양측 서명→결제→1차 납품→구매자 취소 요청은 **API 호출**로 준비했고(이 단계의 화면 확인은 같은 커밋의 [10절](#10-같은-커밋-화면-버튼-전체-거래스케줄러-확인--2026-11-09)), 아래 단계는 모두 **화면 버튼**으로 진행했다. 명령 버튼은 렌더 후 DOM click 3회로 눌러 POST 수를 셌다.

- **구매자 검토 요청**: 주문 상세 취소 요청 아래 "관리자 검토 요청". 빈 내용 제출은 요청 없음, 내용 입력 후 3회 클릭 → POST 1건(`/cancellation-requests/{id}/review-inquiries`), "검토 대기" 표시. 판매자 화면에는 문의 영역이 없다(구매자 문의).
- **관리자 목록·상세**: `/admin/inquiries`에 문의 2건(검토 대기·취소 검토·주문 번호·신청자). 상세에 주문 요약·대기 중 취소 요청·처리 사유 입력. 사유 없이 거절 → 요청 없음·"사유를 입력해 주세요.", 승인 버튼은 되돌릴 수 없음 확인 체크 전 비활성.
- **B4 승인(주문 34)**: 3회 클릭 → POST 1건. 문의 "처리 완료(관리자 #12)", 주문 취소됨. 양측 주문 상세 대금 패널 "구매자 전액 반환 완료 · 반환 50,000원 · 수수료 0원", 취소 요청 "승인됨·처리 사유", 이력 "취소 요청 승인 · 관리자", "주문 취소 · 관리자". 양측 알림 "보관된 대금이 구매자에게 전액 반환되었습니다."
- **B4 거절(주문 35)**: 3회 클릭 → POST 1건. 대금 "주문 대금 보관 중 50,000원" 유지, 취소 요청 "거절됨", 이력 "취소 요청 거절 · 관리자". 타이머 패널 기산점이 거절 시각(15:31)+48시간(11/11 15:31)으로 표시. 양측 알림 "관리자가 운영 문의를 검토해 취소 요청을 거절했습니다."
- **타이머 해제(주문 35)**: 개인 DB에서 이 주문의 `deliveries.submitted_at`과 취소 요청 8의 `requested_at`·`resolved_at`만 49시간 앞당김 → 판매자 "7일 자동 구매 확정 타이머 시작" 3회 클릭 → POST 1건, 진행 중. `/admin/review-timers`에 진행 중 2건(기존 주문 23 포함). 사유 없이 해제 → 요청 없음, 사유 입력 후 3회 클릭 → POST 1건, 목록에서 사라짐. 양측 패널 "관리자가 타이머를 해제함", 이력 "관리자 타이머 해제 · 관리자", 양측 알림.
- **F4 스위치 on→off**: 스위치 없이 서버 재시작 후 구매자가 V3 주문 35를 화면에서 구매 확정(3회 클릭 → POST 1건) → "판매자 지급 완료 · 수수료 5,000원 · 지급 45,000원". 같은 상태에서 API로 만든 새 주문 36은 `LEGACY_V1`(바로 취소).
- **DB 대조**: 34 `REFUNDED`/closure `ADMIN_CANCELLATION`(inquiry 1)·`REFUND` 50,000원, 35 `RELEASED`/`COMPLETED` 수수료 5,000원·`SELLER_PAYOUT` 45,000원, 취소 요청 7 `APPROVED`·8 `REJECTED`(둘 다 `ADMIN`, 처리자 12), 문의 1·2 `CLOSED`(담당 12), 타이머 `CANCELLED`/`ADMIN_STOP`(12). 아웃박스 128건 모두 `COMPLETED`. 4절 불변식 조회 0행(결제된 V3 주문 9건 전체).
- **화면 공통**: 관리자 목록·상세 2건·타이머 화면과 주문 34·35에서 콘솔 오류 없음, 1280px·320px 가로 넘침 없음. 결제 없이 취소된 V3 주문 31·33의 대금 패널은 "결제 없이 종료된 거래입니다."(#129에서 수정된 표시).

발견(이 확인 범위에서 고치지 않음, 판정 필수 항목 아님):
1. 관리자 문의 상세의 주문 상태가 `AWAITING_CONFIRMATION`·`CANCELLED` 코드 그대로 보인다(관리자 화면, 김창재).
2. 계약 PDF 주소에 `Accept: application/json`만 보내면 406이 아니라 500 `INTERNAL_ERROR`로 응답한다(`HttpMediaTypeNotAcceptableException`이 공통 처리기에서 미처리). 브라우저 다운로드에는 영향 없음(공통 오류 처리, 김창재).

후속(2026-11-09, 김창재, `fix/b4-admin-findings`): 1은 관리자 문의 상세·금융 조회의 주문 상태를 `orderStatusLabel`로 표시해 보완했다. 2는 `ApiExceptionHandler`가 406 `NOT_ACCEPTABLE`(및 같은 원인의 405·415)을 공통 오류로 응답하도록 보완했다([API 10절](api-spec.md#10-오류와-입력-기준)). 판정 필수 항목이 아니며, 최종 후보 SHA 재확인 범위는 위 재판정 준비 절을 따른다.

개인 DB 잔여: 주문 33(V3 계약 대기 중 취소, 준비 스크립트 중단분)·34(관리자 승인 취소)·35(구매 확정)·36(LEGACY_V1 취소), 회원 12 ADMIN.

## 10. 같은 커밋 화면 버튼 전체 거래·스케줄러 확인 — 2026-11-09

9절 판정 기록이 B4·F4만 같은 커밋에서 확인하고 G1 전체 화면 거래는 7절(2026-11-06, main `f82c49d`)에 기대고 있어, 1절 "같은 소스" 규칙대로 main `f3c56be`(#140)에서 다시 확인했다(코드 리뷰 지적 반영). 환경은 9절과 같고(개인 DB `bootRun`, 실행 인자로만 V3 생성 허용, Vite, Playwright 격리 컨텍스트 구매자 14·판매자 15·제3자 12), **API 호출 없이 모든 단계를 화면 버튼**으로 진행했다(제3자 API 응답 확인만 브라우저 fetch). 확인 전 계정 12는 USER로 되돌렸다.

- **G1 A1·A2 정상 거래(주문 37)**: 상품 상세 → 주문 요청 → 판매자 "주문 수락" → 특약 입력·"특약으로 계약 초안 저장" → "PDF 생성" → 판매자 PDF 다운로드(다운로드 전 동의·서명 비활성)·동의·서명(1/2) → 구매자 PDF 다운로드·서명(2/2, 결제 대기) → 결제 동의·"적립금으로 결제"(3회 클릭 → POST 1건, 보관금 50,000원) → "1차 납품 제출" → 수정 사유 입력·"수정 요청"(수정 진행 중, 수정 기한 표시) → "2차 납품 제출" → 2차 납품 후 구매자 화면에 수정 요청 폼 없음 → 구매 확정. 대금 "판매자 지급 완료 · 수수료 5,000원 · 지급 45,000원".
- **G1 B2 결제 후 합의 취소(주문 38)**: 같은 계약 절차(특약 없음)·결제 → 구매자 "취소 요청 제출" → 판매자 환불 동의 체크(체크 전 승인 비활성) → "승인 및 환불". 대금 "구매자 전액 반환 완료 · 반환 50,000원 · 수수료 0원".
- **G2·A3·A4 실서버 스케줄러(주문 39)**: 같은 화면 절차로 결제·1차 납품. 개인 DB에서 이 주문의 납품 시각만 49시간 앞당긴 뒤 판매자 "7일 자동 구매 확정 타이머 시작"(양측 진행 중 패널). 이어 타이머의 `basis_at`·`eligible_at`·`started_at`·`expires_at`과 납품 시각을 함께 145시간 앞당겨(만료 23시간 전, `ck_timer_window` 유지) 스케줄러가 구매자에게만 `ORDER_AUTO_CONFIRM_SOON` 1건 생성("24시간 안에 구매가 자동으로 확정됩니다…"). 다시 24시간 앞당겨 만료가 지나자 다음 주기에 타이머 `EXPIRED`/`TIMEOUT`, 주문 구매 확정, closure `AUTO_CONFIRMED` 수수료 5,000원, `SELLER_PAYOUT` 45,000원, 양측 `ORDER_AUTO_CONFIRMED`("검수 응답 기한이 지나 구매가 자동으로 확정되었습니다…"). 양측 패널 "자동 구매 확정 완료", 이력 "자동 구매 확정 · 시스템". 구매자 "후기 등록"이 동작해 후기 1건 저장(폼을 채우지 않고 눌러 기본 별점 5점·내용 없음으로 저장됨).
- **G3**: 세 주문 이력에 "납기 통지 · 시스템", 알림 문구(보관·지급·반환·자동 확정·사전 안내·수정 기한) 확인.
- **G5·E4 PDF**: 세 주문 모두 양측이 화면에서 받은 PDF의 SHA-256이 서로 같고 `order_contracts.file_hash`와 일치, 양측 `contract_signatures`의 서명 시점 파일·본문 해시도 현재 계약과 일치(6/6). PDFBox 텍스트 추출로 주문 37 PDF 3쪽, `CONTRACT_V3`·총액 50000·"G1 특약"·수수료·구매자·판매자 포함 확인. 표시 확인은 11절.
- **지갑·판매 수익 화면**: 구매자 잔액 650,000 → 550,000원(결제 3건·#38 결제 환불), 판매자 1,225,000 → 1,315,000원("판매대금 지급" #37·#39 각 45,000원), 판매 수익 화면에 #37·#39 결제액·수수료·수익.
- **E1 권한**: 제3자(계정 12, USER) 주문 화면 "주문을 찾을 수 없습니다.", `GET /api/orders/37`·`/contract`·`/contract/file`·`/funds`·`/api/orders/39/review-timer` 모두 404.
- **DB·화면 공통**: 4절 불변식 0행(결제된 V3 주문 12건 전체), 아웃박스 160건 모두 `COMPLETED`. 주문 37·38·39·지갑·판매 수익 화면 1280px·320px 가로 넘침 없음, 콘솔 오류는 제3자 화면의 예상 404만.

확인하지 않은 것: 권장(○) E5 계정 전환 캐시 정리 브라우저 확인, 실제 48시간·7일 경과(DB 시각 조정으로 대신함). 개인 DB 잔여: 주문 37·39 구매 확정(39 후기 1건), 38 취소 반환.

## 11. 필수 항목 같은 커밋 근거 대조 — 2026-11-09

판정 근거가 리뷰 지적마다 하나씩 보완되지 않도록, 2절의 필수(●) 항목 전체를 후보 커밋 main `f3c56be` 근거와 한 번에 대조했다. 자동 테스트 근거는 같은 커밋의 백엔드 `clean build`(1002건, 2026-11-09 15:25~15:28 KST)에서 아래 클래스가 실제 실행된 결과(`build/test-results`)로 확인했다: `V3ActivationGateIntegrationTests` 7·`FullFundingTradeIntegrationTests` 14·`ReviewTimerIntegrationTests` 22·`ContractV3FulfillmentApiIntegrationTests` 4·`OrderContractIntegrationTests` 20·`ContractDocumentIntegrationTests` 16·`AdminCancellationReviewIntegrationTests` 9·`LegacyPaymentCompatibilityTests` 1·`OrderCreationIntegrationTests` 32건, 모두 실패·오류·건너뜀 0.

**G5 PDF 표시**: 같은 커밋에서 화면으로 받은 주문 37·38·39 PDF를 PDFBox `PDFRenderer`(100 DPI)로 쪽마다 이미지로 만들어 눈으로 확인했다. 세 PDF 모두 3쪽, 글꼴은 NanumGothic 부분 집합 **내장**. 주문 37 전 쪽에서 제목·항목명·당사자 이름·서비스명 등 한글이 깨짐·`[U+코드]` 대체·잘림 없이 표시되고, 총금액 `50000`, 소요일 `3`, 구매자 요구사항, 표준 본문, `[특약]` "G1 특약: 결과물은 PNG와 원본 파일로 함께 제공합니다.", 정상 수수료 `1000`(10%), 대금 처리 "전액 보관", `CONTRACT_V3`가 보인다. 특약 없는 주문 38은 `[특약]` 제목 아래가 빈 줄로 표시된다(결함 아님, 관찰).

| 항목 | 같은 커밋 근거 | 종류 |
| --- | --- | --- |
| A1 | `V3ActivationGateIntegrationTests` + 10절 주문 37 | 자동·브라우저 |
| A2 | `ContractV3FulfillmentApiIntegrationTests`·`ReviewTimerIntegrationTests` + 10절 주문 37 | 자동·브라우저 |
| A3 | `ReviewTimerIntegrationTests` + 10절 주문 39(실서버 스케줄러, DB 시각 조정) | 자동·실서버·브라우저 |
| A5 | `ReviewTimerIntegrationTests` + 10절 이력 "납기 통지 · 시스템" | 자동·브라우저 |
| B1 | `V3ActivationGateIntegrationTests`(수락 전·계약 대기·서명 후) | 자동 |
| B2 | `FullFundingTradeIntegrationTests` + 10절 주문 38 | 자동·브라우저 |
| B3 | `ReviewTimerIntegrationTests` + 9절 거절 후 기산점 표시(주문 35) | 자동·브라우저 |
| B4 | `AdminCancellationReviewIntegrationTests` + 9절 주문 34·35 | 자동·실서버·브라우저 |
| B5 | `FullFundingTradeIntegrationTests` | 자동 |
| C1~C7 | `FullFundingTradeIntegrationTests`·`ReviewTimerIntegrationTests`·`V3ActivationGateIntegrationTests`(실제 커밋·동시 스레드) | 자동 |
| D1~D5 | `FullFundingTradeIntegrationTests`·`ReviewTimerIntegrationTests`(실패 주입·롤백) | 자동 |
| E1 | `FullFundingTradeIntegrationTests`·`ReviewTimerIntegrationTests` + 10절 제3자 화면·API 404 | 자동·브라우저 |
| E2·E3 | `FullFundingTradeIntegrationTests`·`OrderContractIntegrationTests` | 자동 |
| E4 | `ContractDocumentIntegrationTests` + 10절 다운로드 해시 6/6 | 자동·브라우저 |
| F1 | `OrderCreationIntegrationTests`·`LegacyPaymentCompatibilityTests` | 자동 |
| F2 | `ContractV3FulfillmentApiIntegrationTests`·`OrderContractIntegrationTests`·`OrderCreationIntegrationTests` | 자동 |
| F3 | `OrderCreationIntegrationTests` | 자동 |
| F4 | 9절 스위치 off 재시작 후 V3 확정(35)·신규 LEGACY_V1(36) | 실서버·브라우저 |
| G1 | 10절 주문 37·38(지갑 원장·판매 수익 화면 포함) | 실서버·브라우저 |
| G2 | 10절 주문 39(사전 안내·자동 확정·아웃박스 → 알림) | 실서버·브라우저 |
| G3 | 10절 이력·알림 문구 | 브라우저 |
| G5 | 10절 해시 + 이 절 렌더링 확인 | 브라우저·파일 |
| 4절 불변식 | 결제된 V3 주문 12건 전체 0행 | 실서버 DB |

권장(○) 항목: A4·G4 확인(10절), C8 자동(`AdminCancellationReviewIntegrationTests`), E5 계정 전환 캐시 브라우저는 미확인(프런트 `test:api` 자동 근거만). 실제 48시간·7일 경과는 DB 시각 조정으로 대신했다.

## 12. 후보 SHA `f28b0d7` 같은 커밋 재확인 — 2026-11-09

9~11절은 `f3c56be` 기준이고, 이후 #142(주문 채팅창)·#144(제한 판매자 `ACCEPT` 제외)로 코드가 바뀌었다. 그래서 #144 이후 main `f28b0d7`(#143)을 후보로 고정하고, 1절 "같은 소스" 규칙대로 같은 커밋에서 자동 검사와 실서버·브라우저 확인을 모두 다시 했다. 이전 커밋 결과는 합산하지 않는다.

**자동 검사**: 로컬 `lancerpick_test`(3307)에서 백엔드 `clean build` 1009건 통과(실패·오류·건너뜀 0). 11절 대조표의 근거 클래스가 같은 실행에서 모두 실패 0으로 돌았다: `V3ActivationGateIntegrationTests` 7·`FullFundingTradeIntegrationTests` 14·`ReviewTimerIntegrationTests` 22·`ContractV3FulfillmentApiIntegrationTests` 4·`OrderContractIntegrationTests` 20·`ContractDocumentIntegrationTests` 16·`AdminCancellationReviewIntegrationTests` 9·`LegacyPaymentCompatibilityTests` 1·`OrderCreationIntegrationTests` 32. 프런트는 `npm ci` → typecheck·lint·`test:api` 274건·build 통과.

**환경**: 개인 DB `bootRun`(V10, 마이그레이션 없음. 실행 인자로만 `--lancerpick.order.full-funding-creation-enabled=true`) + Vite + Playwright(Chrome headless) 격리 컨텍스트(구매자 14·판매자 15·제3자/관리자 12). 모든 거래 단계는 **화면 버튼**으로 진행했다. 명령 버튼은 렌더 후 DOM click 3회로 누르고 POST 수를 셌다. 관리자 확인 동안만 계정 12를 ADMIN으로 바꾸고 끝난 뒤 USER로 되돌렸다.

- **G1 A1·A2(주문 40)**: 상품 상세 → 주문 요청(`CONTRACT_V3`) → 수락 → 특약 계약 초안 → PDF 생성 → 판매자 다운로드(다운로드 전 서명 비활성)·서명 1/2 → 구매자 다운로드·서명 2/2 → 결제(동의 전 비활성, POST 1건) → 1차 납품 → 수정 요청 → 2차 납품(이후 수정 요청 폼 없음) → 구매 확정. 대금 "판매자 지급 완료 · 수수료 5,000원 · 지급 45,000원", 이력 "납기 통지". 수정 요청은 DB `revision_requests` 1행으로 확인했다(스크립트의 요청 주소 패턴이 단수형 `/revision-request`와 달라 POST 수를 세지 못함).
- **G1 B2(주문 41)**: 같은 계약 절차·결제 → 구매자 취소 요청 → 판매자 환불 동의(동의 전 비활성) → 승인 및 환불. 대금 "구매자 전액 반환 완료".
- **G2·A3·A4(주문 42)**: 결제·1차 납품 → 납품 시각 49시간 앞당김 → 판매자 타이머 시작 → 타이머 시각과 납품 시각을 145시간 앞당김(만료 23시간 전). 실서버 스케줄러가 구매자(14)에게만 `ORDER_AUTO_CONFIRM_SOON` 1건 생성. 다시 24시간 앞당기자 타이머 `EXPIRED`/`TIMEOUT`, 주문 `COMPLETED`, closure `AUTO_CONFIRMED` 수수료 5,000원, 양측 `ORDER_AUTO_CONFIRMED`. 양측 패널 "자동 구매 확정 완료", 이력 "자동 구매 확정 · 시스템", 구매자 알림 목록에 사전 안내·자동 확정 문구. 후기 등록 POST 1건으로 후기 1건 저장(별점 5·내용 없음, 화면 "구매 후기" 영역에 표시).
- **B4 승인(주문 43)·거절(주문 44)**: 결제·납품·취소 요청 뒤 구매자 "관리자 검토 요청"(빈 내용은 전송 없음, POST 1건). 판매자 화면에는 검토 요청 영역이 없다. `/admin/inquiries/{id}`에서 확인 체크 전 승인 비활성, 사유 없이 거절하면 전송 없음, 승인·거절 각 POST 1건. 43 전액 반환·이력 "관리자", 44 보관 유지·"거절됨".
- **타이머 해제(주문 44)**: 납품 시각과 취소 요청 시각을 49시간 앞당김 → 판매자 타이머 시작 → `/admin/review-timers` 해제(사유 없으면 전송 없음, POST 1건) → 목록에서 사라짐, 구매자 패널 "관리자가 타이머를 해제". 관리자 타이머 화면 320px 넘침 없음.
- **F4**: 스위치 없이 재시작 후 V3 주문 44를 구매자 화면에서 확정(수수료 5,000·지급 45,000), 새 주문 45는 `LEGACY_V1`(구매자 직접 취소로 정리).
- **E1**: 제3자(계정 12, USER) 주문 40 화면 "주문을 찾을 수 없습니다", `/api/orders/40`·`/contract`·`/contract/file`·`/funds`·`/review-timer` 모두 404.
- **G5·E4 PDF**: 주문 40~44 모두 양측이 화면에서 받은 PDF의 SHA-256이 같고 `order_contracts.file_hash`와 일치, 양측 `contract_signatures`의 서명 시점 파일·본문 해시도 일치(10/10). PDFBox로 5개 모두 3쪽, NanumGothic 부분 집합 내장, 대체 문자 없음, `CONTRACT_V3`·`50000` 포함. 주문 40은 쪽별 렌더링(100 DPI)을 눈으로 확인: 한글 항목·당사자·서비스명, 총금액 50000, 소요일 3, 요구사항, 표준 본문, `[특약]` "G1 특약: 결과물은 PNG와 원본 파일로 함께 제공합니다.", 수수료 1000, "전액 보관", `CONTRACT_V3`.
- **#129**: 결제 없이 취소된 V3 주문 33의 대금 패널 "결제 없이 종료된 거래입니다."
- **#144**: 새 주문 46(스위치 off라 LEGACY_V1, 수락 버튼 판정은 정책 공통) 생성 후 개인 DB에 판매자 15 일반 이용 제한 행을 직접 추가했다. 판매자 상세에 "주문 수락" 버튼 없음, 거절 폼 유지, 수락 직접 요청은 403 `MEMBER_RESTRICTED`. 제재 철회 후 다시 조회하면 수락 버튼 복구. 주문은 판매자 거절로 정리했다. 관리자 제재 API·화면이 없어 DB로 대신했다.
- **DB·화면 공통**: 4절 불변식 0행(결제된 V3 주문 17건 전체), 아웃박스 217건 모두 `COMPLETED`. 취소 요청 10(판매자 승인)·11(관리자 승인)·12(관리자 거절)의 처리 주체와 처리자, 문의 3·4 `CLOSED`(담당 12), 타이머 42 `EXPIRED`/`TIMEOUT`·44 `CANCELLED`/`ADMIN_STOP`(12)가 기대와 같다. 잔액은 구매자 550,000 → 400,000원(결제 5건, 41·43 반환), 판매자 1,315,000 → 1,450,000원(40·42·44 각 45,000원). 판매 수익 화면에 40·42·44 표시. 주문 40~44 상세·지갑·판매 수익 1280px·320px 가로 넘침 없음. 콘솔·HTTP 오류는 로그인 전 `/api/me` 401, 계약 작성 전 `/contract` 404(정상 미작성 상태), #144 확인의 의도한 403뿐이다.

확인하지 않은 것: 권장(○) E5 계정 전환 캐시 브라우저 확인, 실제 48시간·7일 경과(DB 시각 조정으로 대신함). 9절 발견 2건(관리자 문의 상세 상태 코드 표시, PDF `Accept: application/json` 500)은 이번 범위에서 다시 보지 않았고 판정 필수 항목이 아니다. 개인 DB 잔여: 주문 40·42·44 구매 확정(42 후기 1건), 41·43 취소 반환, 45 취소, 46 거절, 제재 1 철회됨.

이 절의 결과는 `f28b0d7` 기준이다. 이후 #145가 운영 코드를 바꿔 최종 후보는 13절 `60b7b7f`다.

## 13. 최종 후보 SHA `60b7b7f` 같은 커밋 재확인 — 2026-11-09

12절 확인 뒤 main에 #145(김창재, 9절 발견 2건 보완)가 들어왔다. `ApiExceptionHandler`가 406·405·415를 공통 오류로 응답하게 바뀌었고, 관리자 문의 상세·금융 조회는 주문 상태를 한글 라벨로 표시한다. 운영 코드가 바뀌었으므로 1절 "같은 소스" 규칙대로 후보를 **main `60b7b7f`** 로 다시 고정하고 12절 확인 전체를 같은 코드에서 다시 했다. 실행 트리는 작업 브랜치에 main을 병합한 커밋으로, `60b7b7f`와 운영·테스트 코드가 같고 문서만 다르다(`git diff 60b7b7f HEAD -- LancerPick LancerPickFrontend` 비어 있음).

**자동 검사**: 로컬 `lancerpick_test`에서 백엔드 `clean build` 1011건 통과(실패·오류·건너뜀 0, #145 신규 2건 포함). 프런트 `npm ci` → typecheck·lint·`test:api` 274건·build 통과.

**실서버·브라우저**: 환경과 방법은 12절과 같다(개인 DB `bootRun`, 실행 인자로만 V3 생성 허용, Vite, Playwright Chrome 격리 컨텍스트, 화면 버튼, 3회 클릭 POST 수 계산, 관리자 확인 동안만 계정 12 ADMIN).

- **G1 A1·A2(주문 47)**: 특약 계약·양측 서명·결제·1차 납품·수정 요청·2차 납품·구매 확정. 수정 요청도 3회 클릭에 POST 1건이었다(12절 스크립트의 주소 패턴을 고침). 대금 "수수료 5,000원 · 지급 45,000원", 이력 "납기 통지".
- **G1 B2(주문 48)**: 결제 후 취소 요청 → 판매자 승인 및 환불 → "구매자 전액 반환 완료".
- **G2·A3·A4(주문 49)**: 12절과 같은 시각 조정으로 구매자에게만 `ORDER_AUTO_CONFIRM_SOON` 1건, 만료 뒤 `EXPIRED`/`TIMEOUT`·`AUTO_CONFIRMED` 수수료 5,000원·양측 `ORDER_AUTO_CONFIRMED`. 양측 패널·이력 "자동 구매 확정 · 시스템", 알림 문구, 후기 등록 POST 1건과 저장된 후기("별점 5 / 5") 표시.
- **B4(주문 50 승인·51 거절과 타이머 해제)**: 검토 요청·관리자 승인/거절·타이머 해제 각 POST 1건, 빈 입력은 전송 없음, 확인 체크 전 승인 비활성. #145대로 관리자 문의 상세에 주문 상태 코드(`AWAITING_CONFIRMATION` 등)가 보이지 않는다.
- **F4**: 스위치 없이 재시작 후 V3 주문 51 구매 확정(수수료·지급 표시), 새 주문 52는 `LEGACY_V1`(직접 취소로 정리).
- **E1**: 제3자 주문 47 화면 없음, 주문·계약·PDF·대금·타이머 API 모두 404. #145대로 당사자가 PDF 주소에 `Accept: application/json`만 보내면 406 `NOT_ACCEPTABLE`(이전 500).
- **G5·E4**: 주문 47~51 양측 다운로드 PDF 해시가 같고 DB `file_hash`·양측 서명 시점 해시와 일치(10/10). 5개 모두 3쪽, NanumGothic 내장, 대체 문자 없음. 주문 47 쪽별 렌더링에서 한글·총금액 50000·특약·수수료 1000·"전액 보관"·`CONTRACT_V3` 확인.
- **#129·#144**: 미결제 종료 V3 주문 33 "결제 없이 종료된 거래입니다." 새 주문 53에서 판매자 15 일반 이용 제한(DB 제재 행 2) 동안 수락 버튼 없음·거절 폼 유지·직접 수락 403 `MEMBER_RESTRICTED`, 철회 후 수락 버튼 복구, 판매자 거절로 정리.
- **DB·화면 공통**: 4절 불변식 0행(결제된 V3 주문 22건 전체), 아웃박스 278건 모두 `COMPLETED`. 취소 요청 처리 주체(판매자 15, 관리자 12), 문의 `CLOSED`(담당 12), 타이머 49 `EXPIRED`·51 `ADMIN_STOP`(12), 수정 요청 1행·후기 1행이 기대와 같다. 잔액은 구매자 400,000 → 250,000원, 판매자 1,450,000 → 1,585,000원. 주문 47~51 상세·지갑·판매 수익·관리자 타이머 화면 1280px·320px 가로 넘침 없음. 콘솔·HTTP 오류는 로그인 전 `/api/me` 401, 계약 작성 전 `/contract` 404, 의도한 406·403뿐이다.

확인하지 않은 것: 권장(○) E5 계정 전환 캐시 브라우저 확인, 실제 48시간·7일 경과(DB 시각 조정으로 대신함). 개인 DB 잔여: 주문 47·49·51 구매 확정(49 후기 1건), 48·50 취소 반환, 52 취소, 53 거절, 제재 2 철회됨, 회원 12 USER.
