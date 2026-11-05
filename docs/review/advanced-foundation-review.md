# 고도화 공통 기반 검토 결과

> 후속 상태(2026-11-05): 이 리뷰는 main `c24f37d` 당시 기록으로 보존한다. 문서·정책 DTO·허용 행동·기존 명령 차단·계약 시각 검사는 main `1e10d78`에 보완 반영됐다. 항목별 처리와 남은 활성화 조건은 [정책 경계 기록](../advanced-policy-boundary.md), V5/V6 적용 범위는 [스키마 기록](../advanced-schema-foundation.md)을 따른다. 아래 발견 사항 전체를 현재 미해결 결함으로 해석하지 않는다.

작성일: 2026-11-05. 검토 대상: main `c24f37d`(PR "feat: 고도화 금융·계약 공통 기반과 팀 개발 기준 추가", 변경 범위 `e44d411..c24f37d`).

## 검토 방법과 범위

- 1차: Claude Code가 V3/V4 마이그레이션, `contract` 패키지, `ContractPaymentPolicy`, `Order`·`Payment`·`OrderPaymentService` 변경, 고도화 문서를 정적으로 읽고 보완 사항을 도출했다.
- 2차: Codex가 같은 커밋(작업 트리 변경 없음)에서 항목별로 정적 교차 검증했다(`git diff --check` 포함).
- **실행하지 않은 검증**: 빌드·테스트·DB 실행·브라우저 확인은 하지 않았다. 문서에 기록된 651건 통과는 PR 작성 당시 결과이며 이번 검토에서 재실행하지 않았다.

결론: 현재 `CONTRACT_V2` 주문을 생성하는 경로가 없으므로 **지금 운영 동작을 깨는 문제는 없다.** 다만 문서 정합성 문제와, 계약 주문을 활성화하기 전에 반드시 해결해야 할 항목이 있다.

경로 약어: `J` = `LancerPick/src/main/java/com/example/lancerpick`, `R` = `LancerPick/src/main/resources`.

## 1. 항목별 판정

| # | 보완 사항 | Codex 판정 | 심각도 | 근거 |
| --- | --- | --- | --- | --- |
| 1 | 작업표는 "고도화 내부 알림"을 김창재에게, 4인 설계는 알림·아웃박스를 정은찬에게 배정 | CONFIRMED | 문서 | `docs/work-plan.md:15,24`, `docs/advanced-design-four-person.md:28,117` |
| 2 | `erd.md`·`api-spec.md`·`current-status.md`에 V3/V4와 `CONTRACT_PAYMENT_NOT_READY`(409) 미반영 | CONFIRMED | 문서 | `docs/erd.md:3`, `docs/current-status.md:22`, `docs/api-spec.md:281–283` |
| 3 | DB 설계 문서의 "아직 Flyway에 추가하지 않음" 문구가 낡았고 적용 순서가 실제(V3 금융 → V4 계약)와 다름 | CONFIRMED | 문서 | `docs/advanced-db-design.md:3,9,155` |
| 4 | `OrderAllowedActions`가 정책 버전과 무관하게 `AWAITING_PAYMENT`에서 `PAY`를 반환 → V2 주문에서 버튼 노출 후 409 | CONFIRMED | 활성화 전 필수 | `J/order/service/OrderAllowedActions.java:11–22` |
| 5 | 정책 버전 검사가 결제 서비스에만 있음. 취소는 전액 환불, 확정·정산은 단일 전액 결제를 가정 | PARTIAL(계약 서비스 자체는 정책 검사함) | 활성화 전 필수 | `OrderCancellationService.java:108–129`, `OrderConfirmationService.java:72–89`, `OrderDeliveryService.java:61–89` |
| 6 | 계약 서비스가 `AWAITING_PAYMENT`를 계약 대기 상태로 임시 사용(설계는 `AWAITING_CONTRACT`) | CONFIRMED(문서에 임시 연결로 명시) | 활성화 전 필수 | `OrderContractService.java:143–146`, `docs/advanced-db-design.md:151` |
| 7 | 주문 상세·목록 DTO에 `policyVersion` 없음 → 프런트가 계약 주문을 구분 불가 | CONFIRMED | 활성화 전 필수 | `J/order/dto/OrderDetailResponse.java:7–15`, `OrderSummaryResponse.java:5–6` |
| 8 | `Order.policyVersion`이 `LEGACY_V1` 기본값뿐이고 V2 생성 경로가 없음. 정책 문자열이 여러 곳에 리터럴로 흩어짐 | CONFIRMED | 생성 경로: 활성화 전 필수 / 문자열 중복: 경미 | `J/order/entity/Order.java:50`, `Payment.java:19`, `OrderPaymentService.java:72`, `OrderContractService.java:143` |
| 9 | `prepare`의 `members.findById(...).orElseThrow()`가 500으로 이어질 수 있음 | PARTIAL(FK·HTTP 미연결로 실제 발생은 입증 안 됨) | 경미 | `OrderContractService.java:83–84`, `ApiExceptionHandler.java:49–53` |
| 10 | `FAILED` 전이가 없고 `startGeneration` 호출처가 없어 계약이 `DRAFT`에 머묾 | CONFIRMED(렌더러 미연결은 문서에 명시) | 활성화 전 필수 | `OrderContract.java:134–166`, `OrderContractService.java:108–110` |
| 11 | 4인 설계 §7.6의 `contract_confirmations`/`contract_notices`가 실제 `contract_signatures`/`order_histories` 통합과 다름 | PARTIAL(4인 문서가 DB 설계 우선을 명시) | 문서 | `docs/advanced-design-four-person.md:182–183` |

## 2. Codex가 추가로 찾은 사항

| 사항 | 판정 | 심각도 | 근거 |
| --- | --- | --- | --- |
| 주문당 결제 1건 UNIQUE가 남아 있어 선금·잔금 두 행 저장 불가 | CONFIRMED(의도적 유보로 문서 명시) | 활성화 전 필수 | `R/db/migration/V1__init.sql:266`, V3 `:26–27`, `docs/advanced-finance-foundation.md:14` |
| `OrderContract` 상태 전이가 null·역행 시각을 검사하지 않지만 V4 CHECK는 거부 → 잘못된 내부 호출 시 저장 단계 오류 | CONFIRMED | 경미 | `OrderContract.java:134–137,148–166,169–173`, V4 `:21,32–33` |
| 4인 설계의 9,999원 분할 예시가 최소 계약 금액 10,000원과 충돌 | CONFIRMED | 문서 | `docs/advanced-design-four-person.md:205,240`, `ContractPaymentPolicy.java:11–12` |
| 렌더러 완료 반영 시 주문 잠금·상태 재검사가 아직 없음(호출자 책임 주석만 존재) | 조건부 | 렌더러 연결 시 필수 | `OrderContract.java:140–159` |
| 계약 해시 무결성 | 결함 미확인. 실제 PDF·메타데이터 일치 검증은 미구현 범위 | 활성화 전 검증 | `OrderContractService.java:96,172–178`, `docs/order-contract-foundation.md:23` |

## 3. 문제없음으로 확인된 항목

- `ContractPaymentPolicy` 금액: 총액 100,000원 → 선금 50,000, 구매자 반환 35,000, 보상 15,000, 수수료 150, 판매자 순수익 14,850. 설계 문서와 일치(`ContractPaymentPolicy.java:23–36`, `advanced-design-four-person.md:220`).
- 표준 계약서 `standard-v1.txt`: 분할·보상·취소 정책과 MVP 재납품 기한 규칙(`mvp-spec.md:151,153`)에 부합.
- 잠금 순서: 계약 서비스가 회원 공유 잠금(PESSIMISTIC_READ) → 주문 쓰기 잠금(PESSIMISTIC_WRITE) 순서를 지킨다(`OrderContractService.java:136–142`, `OrderRepository.java:13–20`).

## 4. 처리 순서 제안

| 시점 | 대상 | 담당 |
| --- | --- | --- |
| 지금 | 1·2·3·11번, 9,999원 예시 문서 정정 | 정은찬(문서 PR), 영향 담당자 확인 |
| 황준범과 합의 | V5 이후 마이그레이션 순서, 결제 회차 UNIQUE 교체 시점, PDF 렌더러 실패·재시도·완료 반영 잠금 책임(10번·렌더러 항목) | 황준범·정은찬 |
| 계약 주문 생성 API를 열 때 같은 PR에서 | 4·5·6·7·8번, 결제 회차 UNIQUE 교체, 9번 정리, 엔티티 시각 검증 보강 | 정은찬(주문), 황준범(결제·DDL) |
| 활성화 직전 | 실제 PDF·파일 해시 일치, 취소·렌더러 완료 경합을 실제 DB 동시성 테스트로 확인 | 황준범·정은찬 |
