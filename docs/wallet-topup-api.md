# 토스 테스트 충전 신청·본인 조회 API

후속 구현: [승인·지급·재확인 API](wallet-topup-approval.md)와 [React 충전 화면](wallet-topup-frontend.md)을 추가했다. 아래 신청 단계 기록은 유지하며, 실제 테스트 키·로그인 포함 전체 결제 흐름은 아직 검증 전이다.

작성일: 2026-11-02. 담당: 황준범. 브랜치: `feature/wallet-topup-api`.

## 1. 구현 범위

기존 V2 충전 스키마를 사용해 신청 저장·본인 조회를 구현한다. Flyway 변경은 없다. 기반 스키마 커밋은 `0bda86a`이며, main 병합 전 해당 작업 브랜치에서 분기했다. 스키마 PR을 먼저 병합한 뒤 이 API 작업을 통합한다. 신청 단계 이후 승인·지급·본인 재확인은 위 후속 문서로 구현했으며 실제 로그인·충전 UI·실제 테스트키 연동은 별도다.

## 2. 호출 계약

| 메서드·경로 | 입력·권한 | 응답 |
| --- | --- | --- |
| POST /api/me/wallet/topups | 인증+CSRF, JSON amount: 원 단위 정수 1~1000000 | 201, Location: 생성된 조회 경로, WalletTopupResponse |
| GET /api/me/wallet/topups/{id} | 인증, 양수 long ID | 200, WalletTopupResponse |

```json
{"amount":50000}
```

응답 필드: `id`(문자열), `tossOrderId`(서버 생성), `amount`, `currency=KRW`, `isTest=true`, `status`, `createdAt`(UTC ISO 8601).

생성 상태는 항상 READY다. memberId·status·tossOrderId·paymentKey·approvalRequestKey를 요청으로 받아 적용하지 않는다. 응답에도 회원 ID·paymentKey·내부 승인 멱등키·오류 원문은 넣지 않는다. 성공 응답은 Cache-Control: no-store다. 금액은 DTO에서 BigDecimal로 받아 소수 자릿수를 검증한 뒤 longValueExact로 변환하며 Long 역직렬화 과정의 소수 절삭을 피한다.

이 단계의 POST는 요청마다 서로 다른 충전 신청을 생성한다. 같은 금액으로 다시 호출해도 자동 지급·외부 승인은 없으며, 준비 API의 멱등 재요청 기능은 아직 없다. 프런트는 공통 정책대로 변경 요청을 자동 재시도하지 않는다. 네트워크 오류가 나도 생성되지 않았다고 단정하지 않는다. 실제 승인·지급은 향후 선택된 한 신청의 고정 주문번호·멱등키와 DB 제약을 사용한다.

## 3. 보안·트랜잭션 경계

- Controller는 기존 CurrentMemberId로 검증된 principal의 memberId를 얻는다. 실제 인증 구현을 추가하거나 고정 회원 ID를 사용하지 않는다.
- create는 하나의 짧은 DB 트랜잭션에서 입력·지갑 존재 확인 후 서버 주문번호와 승인 멱등키를 생성하고 READY 행을 저장한다.
- 조회는 readOnly 트랜잭션에서 `(id, memberId)` 조건을 사용한다. 타인 건과 없는 건은 동일한 404로 응답한다.
- 지갑이 없어도 자동 생성·가입 지급을 호출하지 않는다. 신청 저장은 지갑 잔액·원장·상품 주문을 변경하지 않는다.
- 외부 통신은 없다. 이후 승인 단계의 PROCESSING·토스 호출·지급 트랜잭션 분리는 [스키마 문서](wallet-topup-schema.md#6-후속-호출트랜잭션-계약)를 따른다.
- !demo Controller·Service다. DB 없는 demo에는 등록하지 않는다. 실제 로그인/로그아웃 연동과 세션 만료의 종단 간 검증은 남아 있다.

## 4. 오류

| 조건 | 결과 |
| --- | --- |
| 미인증 GET, 유효한 CSRF를 가진 미인증 POST | 401 AUTHENTICATION_REQUIRED |
| CSRF 누락·불일치 POST | 403 CSRF_INVALID, 미인증 여부보다 먼저 차단될 수 있음 |
| 누락·음수·0·상한 초과·소수·형식 오류 금액 | 400 VALIDATION_FAILED |
| 조회 ID 형식·범위 오류 | 400 VALIDATION_FAILED |
| 없는 지갑의 생성 요청, 타인/없는 충전 조회 | 404 RESOURCE_NOT_FOUND |

## 5. 검증

WalletTopupApiIntegrationTests는 격리 MySQL에서 실제 Security 필터→Controller→Service→Repository를 검사한다. 인증 principal은 테스트에서만 주입하며 실제 로그인 검증은 아니다. 생성/본인 조회·타인 차단·금액·CSRF·서버 생성 식별자·응답 비밀값 제외·잔액/원장 무변경을 확인한다. 일반 테스트와 함께 실행하고 결과는 작업표에 기록한다. 외부 테스트 키와 토스 통신은 사용하지 않는다.

2026-11-02 격리 MySQL 8.4에서 전체 clean build --offline 118건을 통과했다(신규 API 16건). 전용 컨테이너·볼륨 정리 완료. 후속 작업과 진행률 추정은 [담당 진행 현황](wallet-payment-progress.md)을 따른다. 커밋·push·PR·원격 CI는 별도다.
