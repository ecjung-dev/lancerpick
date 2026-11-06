# 미확정 충전 복구 Batch (J5)

2026-11-06, 황준범. main 반영(#114, 작업 브랜치 `feature/wallet-topup-recovery-batch`). 실제 토스 조회·관리자 재처리 화면 확인과 구분한다.

## 처리 범위

사용자가 결제창을 닫았거나 승인 후 통신/지급 저장이 실패해 남은 `PROCESSING`·`UNKNOWN` 충전을 조회하고 복구한다. 서버가 이미 저장한 `payment_key`로 **결제 조회만** 호출한다. 승인 요청을 다시 보내거나 충전을 새로 만들지 않는다. `READY` 신청은 대상이 아니다.

기존 `TopupApprovalTransactions.apply`가 주문번호·키·금액·KRW·테스트 여부·승인 시각·제공자 상태를 다시 검증한다. 확인된 `DONE`에서만 충전 완료·잔액 증가·원장 기록이 함께 커밋된다. `ABORTED`·`EXPIRED`·`CANCELED`는 지급 없이 실패로 끝낸다. 불명확한 응답은 미확정으로 유지한다. 주문 환불·출금·실제 현금 반환은 이 작업의 범위가 아니다.

## 실행 설정

기본값은 다음과 같다. 기존 팀원에게 새 개인 설정이나 Redis 설치를 요구하지 않는다.

| 설정 | 기본값 | 의미 |
| --- | --- | --- |
| `TOPUP_RECOVERY_ENABLED` | `false` | 복구 서비스·Batch Job 등록 |
| `TOPUP_RECOVERY_SCHEDULING_ENABLED` | `false` | 등록된 복구 Job의 주기 실행 |
| `lancerpick.wallet.topup-recovery.stale-seconds` | 120초 | 처리 시작 후 자동 조회까지 최소 대기 |
| `lancerpick.wallet.topup-recovery.lease-seconds` | 180초 | 한 작업자가 확보하는 처리권 유효 시간 |
| `lancerpick.wallet.topup-recovery.max-attempts` | 8회 | 자동 조회 처리권 확보 최대 횟수 |
| `lancerpick.wallet.topup-recovery.retry-seconds` | 60초 | 재시도 간격의 시작값 |
| `lancerpick.wallet.topup-recovery.delay-ms` | 60000ms | 전용 스케줄러의 초기/완료 후 대기 |

자동 실행할 환경에서만 Git 제외 개인 설정 또는 환경변수에 앞의 두 값을 `true`로 넣고 서버를 재시작한다. 기존 토스 테스트 키와 `TOSS_TOPUP_ENABLED=true`도 필요하다. 토스가 비활성 상태면 처리권/잔액을 변경하기 전에 실행이 실패한다. 키 원문은 문서·Job 파라미터에 넣지 않는다. `demo` 프로필에는 복구 기능을 등록하지 않는다.

`spring.batch.job.enabled=false`로 애플리케이션 시작 시 Job 자동 실행을 막고, `spring.batch.jdbc.initialize-schema=never`로 프레임워크의 자동 DDL을 막는다. 위 기능 설정과 관계없이 Flyway V9는 시스템 테이블을 적용한다.

## 트랜잭션·동시성

1. QueryDSL로 처리 시작 시각이 오래되고, 재시도 시각이 지났으며, 처리권이 없거나 만료된 ID를 찾는다. 실행 시작의 최대 ID를 상한으로 고정하고 ID 오름차순으로 조회한다.
2. 짧은 JPA 트랜잭션에서 해당 충전을 쓰기 잠금하고 조건을 재검사한다. UUID 처리권·만료 시각·시도 수를 기록한 뒤 커밋한다.
3. `TopupRecoveryService.recover`의 `NOT_SUPPORTED` 경계가 Step 트랜잭션을 일시 중단한다. 외부 토스 조회 시 활성 업무 트랜잭션이나 충전·지갑 잠금이 없다.
4. 새 JPA 트랜잭션에서 충전을 다시 잠그고 같은 처리권·유효 시간·미확정 상태인지 검사한다. 유효하면 기존 지급 트랜잭션을 재사용한다. 충전 → 지갑 잠금 순서와 원장 UNIQUE를 유지한다.
5. 수동 재확인이 먼저 완료했거나 새 작업자가 처리권을 얻었다면 예전 작업자의 늦은 응답은 `STALE`로 끝내고 금액/상태를 덮어쓰지 않는다.

Step 자체는 기본 트랜잭션으로 실행 컨텍스트와 체크포인트를 저장한다. 업무 변경은 그 Step 트랜잭션과 별도로 커밋되므로, 지급 직후 체크포인트 저장 전에 실패해도 재실행 시 기존 상태/원장으로 중복 지급을 막는다. Step 트랜잭션의 JDBC 연결은 외부 조회 동안에도 풀에 예약될 수 있다. 단일 전용 스케줄러에서 동기 실행하고 HTTP 대기 시간을 제한하며, 여러 병렬 worker로 확장하는 설정은 넣지 않았다. 조회 전체를 하나의 주문·지갑 트랜잭션으로 묶지 않는다.

자동 재시도는 기본 60·120·240·480·960·1920·3600초 간격이다. 실패 항목은 다음 시각을 기록하고 이후 ID를 계속 처리한다. 상한에 도달하면 자동 대상에서 제외하며 지급 실패로 단정하지 않는다. 처리 중 프로세스가 종료되면 상태가 `PROCESSING`으로 남을 수도 있다. 기존 본인 재확인 API는 횟수 상한 이후에도 사용할 수 있다.

## 내부 실행·재시작 계약

| 호출 | 동작 |
| --- | --- |
| `TopupRecoveryJobService.start()` | 새 서버 UUID와 시작 시각으로 `walletTopupRecovery` 실행 |
| `TopupRecoveryJobService.restart(executionId)` | 같은 Job의 실패/중지 실행을 Batch 규칙에 따라 체크포인트부터 재시작 |

현재 호출은 동기식이다. 주기 실행은 `topupRecoveryScheduler` 전용 스레드 하나를 사용한다. 이 Bean은 `defaultCandidate=false`로 등록하고 복구 작업에서 이름으로 지정하므로 기존 알림·타이머의 기본 스케줄러를 가져가지 않는다. 내부 Job 실행에는 회원 세션을 전달하지 않는다. 관리자 HTTP API·권한 검사·사유 입력·운영 감사 이력(K6)은 아직 추가하지 않았으며, 일반 사용자 경로에 이 서비스를 공개하면 안 된다.

Job 파라미터는 `run.id`와 `requestedAtMillis` 두 개만 허용한다. Step 컨텍스트에는 `highestId`·`lastId`와 `completed`·`failed`·`deferred`·`exhausted`·`stale` 집계만 업무 값으로 저장한다. 프레임워크의 기본 체크포인트 항목도 함께 저장된다. 결제 키·처리권 토큰·외부 응답 원문은 저장하지 않는다. 업무 예외는 고정 오류 코드로 바꿔 Batch 실패 메타데이터에 제공자 원문이 들어가지 않도록 한다.

Job의 `COMPLETED`는 이번 스캔이 끝났다는 뜻이다. 모든 미확정 충전이 지급됐다는 뜻이 아니므로 상태와 결과별 집계를 함께 확인한다. Step `writeCount`는 지급 완료 수, `filterCount`는 나머지 처리 결과 수다.

프로세스 강제 종료로 메타데이터가 `STARTED`에 남으면 바로 `restart`할 수 없다. 새 Job 실행은 만료된 처리권을 다시 확보해 업무 복구를 계속할 수 있다. 예전 실행 이력의 복구는 작업자 종료를 운영자가 확인한 후 Spring Batch `JobOperator.recover`로 실패 상태를 정리하는 별도 운영 절차다. 실행 중인 작업을 자동으로 종료된 것으로 간주하거나 강제 복구하는 API는 제공하지 않는다.

## DB·라이브러리

Boot 4.1.2가 관리하는 `spring-boot-starter-batch-jdbc` / Spring Batch 6.0.6을 사용한다. V9는 해당 버전의 공식 MySQL 스키마를 가져온 **9개 `BATCH_*` 시스템 테이블**이다. main `6d2dac5` 병합 시 관심 상품이 이미 V8을 사용해, 아직 공유하지 않은 복구 스키마만 V8에서 V9로 옮겼다. main에 반영된 V1~V8은 변경하지 않는다. 업무 테이블은 V8까지 31개이며 시스템 테이블을 더하면 40개다(`flyway_schema_history` 제외).

V5에 이미 있는 `wallet_topups`의 재시도 수·다음 시각·처리권·만료 컬럼과 인덱스를 사용한다. 별도 복구 시도 업무 테이블은 만들지 않는다. 업무 데이터 접근은 JPA/QueryDSL을 유지한다. JDBC는 Spring Batch 프레임워크의 실행 메타데이터 저장 구현에 한정되며, 업무 Repository에 JdbcTemplate·직접 SQL을 추가하지 않았다.

## 검증

### 최신 main 통합 — 2026-11-06

main `6d2dac5`(#113까지)을 로컬 복구 작업에 병합했다. `current-status.md`의 복구/Redis 기록과 회원·지급 경합 보완 기록을 모두 보존했다. main V8 관심 상품 SQL은 유지하고 미공유 Batch SQL만 내용 변경 없이 V9로 옮겼다. 기존 지갑·복구 Java 코드는 로컬 `5691b21`과 동일하다.

새 격리 MySQL에서 Flyway V1~V9가 모두 성공했고, 업무 31개 + Batch 시스템 9개 = 40개 테이블을 확인했다(`flyway_schema_history` 제외). 병합 후 백엔드 전체 `clean build` **875건**이 실패·오류·건너뜀 없이 통과했다. 프런트 API 테스트 **218건**·lint·TypeScript 포함 build도 통과했다. Vite의 500kB 이상 청크 경고는 남아 있으며 빌드 실패는 아니다. 개인 개발 DB·실제 토스·메일 서버에 접속하지 않았다.

### 병합 전 구현 검사

격리 MySQL 8.4에서 백엔드 전체 `clean build` **839건(신규 복구 통합 16건 포함)**이 실패·오류·건너뜀 없이 통과했다. 이후 스케줄러 구성 보완에 대해 별도 설정 검사 **4건**이 통과했고, 해당 보완을 포함한 복구 통합·설정 선택 검사 **20건**도 새 격리 MySQL에서 실패·오류·건너뜀 없이 통과했다. 스케줄러 보완 이후 전체 검사를 다시 실행하지 않았으며, 전체 검사 수와 선택 검사 수를 합산한 전체 실행 결과로 표현하지 않는다.

테스트는 외부 토스 대역과 테스트 전용 계정을 사용하며 개인 개발 DB·실제 토스 API를 사용하지 않는다. 신규 통합 검사는 중복/동시 실행, 수동 재확인 충돌, 처리권 만료와 오래된 응답, 재시도 상한, 제공자 불일치, DB 제약 실패 롤백, 체크포인트 재시작과 비밀정보 미저장을 다룬다. 설정 검사는 기본 비활성·명시적 활성화·demo 제외·기존 기본 스케줄러 유지 경로를 확인한다. 원격 CI와 실제 토스 연동·운영 스케줄 실행은 이번 자동 검사에 포함하지 않았다.

## 공식 근거

- [Spring Boot Batch 자동 구성·시작 실행 제어](https://docs.spring.io/spring-boot/reference/io/spring-batch.html)
- [Tasklet Step의 트랜잭션](https://docs.spring.io/spring-batch/reference/step/tasklet.html)
- [JobRepository와 메타데이터](https://docs.spring.io/spring-batch/reference/job/configuring-repository.html)
- [JobOperator 재시작·복구 API](https://docs.spring.io/spring-batch/reference/api/org/springframework/batch/core/launch/JobOperator.html)

V9 스키마의 원본 경로는 `spring-batch-core-6.0.6.jar!/org/springframework/batch/core/schema-mysql.sql`이다.
