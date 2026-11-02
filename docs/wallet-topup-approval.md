# 토스 테스트 충전 승인·지급·본인 재확인

후속 화면: [React 충전·결과 화면](wallet-topup-frontend.md)을 추가했다. 아래 서버 단계 기록과 구분하며 실제 키·인증 포함 결제 검증은 여전히 대기 중이다.

작성일: 2026-11-02. 담당: 황준범. 브랜치: `feature/wallet-topup-api`.

## 1. 범위

기존 V2와 신청/조회 API에 서버 승인·적립금 반영·본인 재확인을 추가한다. Flyway 변경은 없다. 실제 토스 테스트 결제창을 통한 연동 실행은 아직 하지 않았다. 검증은 외부 API 대역·실제 Security/DB·커밋/롤백을 사용한다. 로그인 구현·React 결제 UI·웹훅·자동 복구 스케줄러·충전 취소는 포함하지 않는다.

실제 제공자 호출은 기본 비활성화다. 개인 설정에 아래 값과 기존 테스트 시크릿을 준비하고 서버를 재시작해야 활성화된다. 실제 키를 문서·Git·로그에 기록하지 않는다.

```properties
TOSS_TOPUP_ENABLED=true
TOSS_SECRET_KEY=CHANGE_ME
```

기본값은 false이며 비활성화 상태의 새 승인 요청은 503 PAYMENT_PROVIDER_UNAVAILABLE로 차단하고 READY를 유지한다. 활성화 시 test_gsk_ 또는 test_sk_ 키만 허용하고 live 키는 거부한다. demo에서는 관련 Bean을 등록하지 않는다. 테스트에 개인 키나 외부 통신을 요구하지 않는다.

## 2. 공개 API 계약

| 호출 | 입력 | 정상 결과 |
| --- | --- | --- |
| POST /api/me/wallet/topups/{id}/confirm | 인증+CSRF; paymentKey,orderId,amount | 완료/종료 상태 200, PROCESSING/UNKNOWN은 202 |
| POST /api/me/wallet/topups/{id}/reconcile | 인증+CSRF; 본문 없음 | 저장된 paymentKey로 외부 조회 후 반영. 처리 중 202, 완료/종료 200 |
| GET /api/me/wallet/topups/{id} | 인증 | DB 조회만. 외부 승인·조회·지급 없음 |

```json
{"paymentKey":"SDK에서 받은 결제 식별자","orderId":"서버가 생성한 충전 주문번호","amount":50000}
```

요청 amount는 정수 1~100만원이며 DTO에서 소수 절삭을 차단한다. 식별자 길이/형식을 검증하고, DB에 저장된 본인 신청·주문번호·금액과 다시 비교한다. 최초 승인 시도에서 paymentKey를 신청과 묶고 이후 변경하지 않는다. 다른 신청에서 같은 paymentKey 사용은 UNIQUE로 차단하고 409로 응답한다.

응답은 기존 WalletTopupResponse다. no-store를 적용하고 내부 멱등키·paymentKey·provider 원문·시크릿은 반환하지 않는다. 200 자체를 충전 성공으로 판단하지 말고 status=COMPLETED를 확인해야 한다. FAILED도 상태 조회 결과로 200을 반환할 수 있다. 202는 성공/실패 미확정이며 새 충전을 자동 생성하거나 승인을 자동 재전송하지 않는다.

## 3. 트랜잭션 경계

```text
WalletTopupApprovalService (Propagation.NEVER)
  → prepare: 본인 충전 행 잠금·금액 검사·키 연결·PROCESSING, DB 커밋
  → 토스 승인 호출: DB 트랜잭션 밖
  → apply: 충전 행 → 지갑 잠금
      외부 결과 검증
      잔액 증가 + TOPUP 원장 + COMPLETED, 같은 DB 커밋
```

DB 메서드는 별도 Spring Bean인 TopupApprovalTransactions에서 호출하므로 self-invocation 문제를 피한다. 조율 서비스의 NEVER는 호출자 트랜잭션 안에서 외부 통신하는 실수를 차단한다. 실제 HTTP 호출은 상위 가입/주문 트랜잭션 안에서 실행하지 않는다.

충전·지갑 잠금 후 현재 잔액을 읽고 Math.addExact로 합산한다. TOPUP:{id} 이벤트 키·topup_id UNIQUE·같은 회원/금액 FK로 중복/잘못된 지급을 방어한다. 없는 원장의 FOR UPDATE는 사용하지 않아 다른 지갑끼리 원장 간격 잠금에 걸리지 않게 한다. 완료 재호출은 기존 원장의 근거까지 확인한 뒤 현재 결과를 반환한다.

지급 시각은 Instant를 native 쿼리에 전달해 UTC DATETIME을 저장한다. 충전 완료·지갑 갱신·원장 생성의 물리 UTC 시각을 테스트에서 비교한다. 기존 초기 지급용 LocalDateTime API는 이번에 변경하지 않는다.

## 4. 외부 호출·결과 검증

RestClient와 JDK HttpClient를 사용한다. 주소는 https://api.tosspayments.com 고정, redirect 금지, connect 5초/read 65초다. Basic 인증은 서버 시크릿+빈 비밀번호로 구성하며 승인 요청에 저장된 approval_request_key를 Idempotency-Key로 전달한다. 애플리케이션에서 자동 승인 재시도하지 않는다.

제공자 응답의 paymentKey·orderId·totalAmount·currency를 저장값과 비교한다. DONE일 때 취소 가능 잔액도 전액과 같고 승인 시각이 유효해야 지급한다. DONE 외 상태에서는 지급하지 않는다. 일치하는 ABORTED/EXPIRED/CANCELED는 FAILED, 진행 중·입금 대기·부분 취소 등은 UNKNOWN으로 남긴다. 비동기 입금 수단을 정상 지원한다는 뜻은 아니다.

gsk 키는 orderId 결제 조회를 지원하지 않으므로 재확인은 paymentKey 조회를 사용한다. 원문 HTTP 오류·인증 헤더·응답 카드 정보는 로그/응답에 전달하지 않는다. 지급 실패 로그에는 내부 충전 ID와 예외 타입만 남긴다.

근거: [토스 코어 API](https://docs.tosspayments.com/reference), [인증·멱등 헤더](https://docs.tosspayments.com/reference/using-api/authorization).

## 5. 불명확 결과·복구

- 외부 응답 유실·HTTP 오류는 UNKNOWN으로 남긴다. HTTP 오류만으로 결제 실패를 확정하지 않는다.
- 외부 성공 후 잔액/원장/완료 중 실패하면 지급 트랜잭션을 롤백하고 UNKNOWN/CREDIT_PENDING을 별도 저장한다.
- 재확인 POST는 승인 POST를 반복하지 않고 저장된 paymentKey로 외부 조회한다. 검증된 DONE을 같은 멱등 지급 경로로 반영한다.
- 같은 신청의 동시 confirm은 최초 한 요청만 외부 승인한다. 다른 요청은 PROCESSING/UNKNOWN 또는 이미 완료된 결과를 반환한다.
- 늦은 UNKNOWN 갱신은 COMPLETED/FAILED를 되돌리지 않는다. 이미 완료됐다면 반복 조회/반영으로 추가 지급하지 않는다.
- DB 자체 장애로 UNKNOWN 저장도 실패하면 5xx가 날 수 있다. 앞서 커밋한 PROCESSING·식별자는 복구 근거로 남으며, 장애 해소 후 재확인한다.

현재 복구는 본인 재확인 API로 실행한다. 서버가 승인 호출 전에 종료됐거나 조회가 계속 불명확한 건을 자동 재승인/실패 처리하는 기능은 없다. 자동 주기 검사·운영자 목록·웹훅·모든 외부 장애의 자동 해결은 후속 범위다. 충전 완료 후 외부에서 따로 취소한 건의 잔액 회수도 이번 범위가 아니다.

## 6. 검사

WalletTopupApprovalIntegrationTests는 실제 DB 커밋·잠금과 대역 Gateway를 사용한다. 외부 호출 시 트랜잭션이 없고 PROCESSING이 이미 커밋됐는지 확인한다. 같은 충전·같은 지갑·서로 다른 지갑의 동시 요청, provider 타임아웃 후 조회 복구, 내부 저장 중 실제 CHECK/FK 실패 후 전체 롤백·재확인, 결제 키 재사용·결과 불일치·미완료 상태를 검사한다.

HttpTossPaymentGatewayTests는 네트워크 대신 MockRestServiceServer로 승인 JSON·인증/멱등 헤더·paymentKey 조회·원문 오류 차단·live 키 거부를 확인한다. 실제 테스트키 결제와 인증 화면부터의 종단 간 검증은 아니다. 실행 결과는 검증 후 작업표에 기록한다.

## 7. 2026-11-02 실행 결과

- 격리 MySQL 8.4에서 V1/V2·기본 validate로 전체 clean build --offline 148건 통과. 승인 통합 25건, 신청 API 17건, HTTP 클라이언트 4건 및 기존 검사를 포함한다.
- 같은 충전 동시 승인과 서로 다른 회원 4명의 동시 지급은 각각 3회 반복 통과했다. 같은 지갑의 두 충전도 합산 잔액을 확인했다.
- 실제 MySQL CHECK/FK 오류를 지급 원장 INSERT 경계에 주입해 잔액 증가·COMPLETED가 함께 롤백되는 것과 재조회 후 1회 지급 복구를 확인했다. 검증 환경의 SUPER 권한이나 보안 설정 완화 없이 실행한다.
- 결과의 주문번호·키·금액·통화·취소 가능 잔액·승인 시각 불일치, 타인/변경 조건·키 중복, 진행 중·입금 대기·부분 취소·종료 상태, 기본 비활성화 시 READY 유지도 확인했다.
- 전체 빌드 후 조회 URI를 상수 템플릿+식별자 변수로 구성하도록 보완하고 클라이언트 5건을 선택 재실행해 통과했다. '/'·'?'가 경로/쿼리가 아닌 식별자 값으로 인코딩됨을 확인했다. 이 추가 1건을 포함한 전체 149건을 재실행한 기록은 아니다.
- 외부 제공자는 대역이며 실제 토스 테스트 시크릿·인터넷 결제를 사용하지 않았다. 전용 DB 컨테이너·볼륨은 정리했다. 실제 로그인·SDK·키 연동·자동 복구·원격 CI는 후속이다.
