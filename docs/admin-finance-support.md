# 관리자 금융 조회·재처리 연결 준비 (K3/K6)

2026-11-06, 황준범. main `a1f7fda`(#114)에서 분기한 `feature/admin-finance-support`의 내부 연결 계약이다. 관리자 API·화면·감사 이력은 김창재 담당이며, 이번 변경은 조회 서비스·DTO·재처리 후보 판단을 제공한다. 새 테이블·Flyway·REST 경로·화면은 추가하지 않는다.

## 창재님이 연결할 서비스

빈: `admin/service/AdminFinanceQueryService`. 모든 public 진입에서 전달받은 회원의 **현재 DB 역할**을 검사한다. 존재하지 않는 회원·잘못된 인증 ID는 401 `AUTHENTICATION_REQUIRED`, 일반 회원은 403 `FORBIDDEN`이다. 대상 조회 전에 권한을 확인한다. 인증 시점의 세션 역할만으로 조회를 허용하지 않는다.

| 메서드 | 반환 | 용도 |
| --- | --- | --- |
| `topups(adminId, TopupFilter, page, size)` | `PageResponse<AdminFinanceResponses.Topup>` | 회원 ID·충전 상태로 충전 목록 조회 |
| `topup(adminId, topupId)` | `Topup` | 충전 상태·시도 수·다음 시각·처리권 만료와 후보 조건 |
| `payments(adminId, PaymentFilter, page, size)` | `PageResponse<PaymentRecord>` | 주문·구매자·판매자·결제 상태·정책별 결제/환불 목록 |
| `orderFinance(adminId, orderId)` | `OrderFinance` | 주문 금액·결제/환불·보관금·정산·종료 분배를 함께 조회 |
| `recoveryJobs(adminId, page, size)` | `PageResponse<RecoveryJob>` | 충전 복구 JobInstance별 최신 실행과 집계 |
| `recoveryJob(adminId, executionId)` | `RecoveryJob` | 특정 실행의 집계·재시작 후보 판단 |

조건 타입은 `AdminFinanceQueries.TopupFilter(memberId, status)`와 `PaymentFilter(orderId, buyerId, sellerId, status, policyVersion)`다. 필터 자체가 null이면 전체 조건이다. 회원·주문 ID 필터는 양수만 허용하고, 결제 상태는 `PAID`/`REFUNDED`, 정책은 `TradePolicyVersion`, 충전 상태는 `WalletTopupStatus`를 사용한다. 문자열 상태를 임의로 대소문자 변환하지 않는다.

페이지는 0부터, 크기는 1~100이다. 충전은 생성 시각·ID 내림차순, 결제는 결제 시각·ID 내림차순, Job은 인스턴스 최신 순이다. Job 목록의 `totalElements`는 실행 수가 아닌 인스턴스 수이며, 이전 실행은 상세 메서드로 조회한다. 잘못된 조건은 400 `VALIDATION_FAILED`, 없는 상세 대상 또는 다른 종류 Job의 실행 ID는 404 `RESOURCE_NOT_FOUND`다.

## 응답·개인정보·시간

DTO는 `admin/dto/AdminFinanceResponses`에 있다. ID는 문자열, 금액은 정수 원, 시각은 UTC ISO 8601이다. 아직 결제하지 않은 주문이나 기존 LEGACY_V1 주문도 조회할 수 있다. 보관금·정산·종료 기록이 없으면 null로 반환한다. 기록 누락을 0원 정산이나 정상 상태로 재해석하지 않는다.

결제 키·승인 요청 키·처리권 토큰·회원 이메일/비밀번호·외부 응답은 조회 응답에 포함하지 않는다. 마지막 충전 오류는 알려진 내부 코드만 반환하고, 알 수 없는 저장 값은 `RESULT_REQUIRES_REVIEW`로 바꾼다. Batch 파라미터·컨텍스트·예외/스택 원문은 DTO로 반환하지 않는다.

Batch `scanRequestedAt`은 허용된 `requestedAtMillis` 파라미터를 UTC로 변환한 **원래 스캔 요청 시각**이다. 재시작 실행의 실제 시작 시각으로 표시하면 안 된다. 프레임워크의 시간대 없는 `LocalDateTime` 메타데이터를 UTC라고 가정하지 않는다. 결과 집계는 `completed`, `failed`, `deferred`, `exhausted`, `stale`만 읽으며 `creditedCount`는 Step의 지급 완료 건수다. Job `COMPLETED`는 모든 충전의 지급 완료를 의미하지 않는다.

프로세스 종료 등으로 인스턴스만 있고 실행이 없으면 `status=NO_EXECUTION`, `executionId=null`, 재시작 불가로 반환한다. 조회 전체가 실패하거나 이 인스턴스를 정상 완료로 표시하지 않는다.

## 후보 판단의 의미

`Decision(eligible, reason)`은 **조회 순간의 내부 조건**이다. 실행 권한·처리 예약·감사 이력을 대신하지 않는다. 이 값이 true여도 이번 변경에 관리자 실행 API는 없다. 실제 실행 시 권한·대상·상태·처리권을 다시 검사해야 한다.

### 개별 충전

- 완료/실패는 `TERMINAL_STATE`, READY·결제 키/처리 시작 시각 누락은 `PAYMENT_NOT_IDENTIFIED`다.
- 유효 처리권은 `LEASE_ACTIVE`, 처리 시작 후 최소 대기 전은 `TOO_EARLY`, 다음 시각 전은 `RETRY_DELAY`다. 만료·대기 시각과 정확히 같아지면 해당 대기 사유는 해제된다.
- 토스 설정이 비활성이면 `PROVIDER_DISABLED`다. 설정 확인만 수행하며 조회 서비스에서 외부 API를 호출하지 않는다.
- `automaticRecovery`는 위 조건 외에 복구 설정 활성과 자동 시도 상한도 검사한다(`RECOVERY_DISABLED`·`AUTOMATIC_LIMIT_REACHED`).
- `manualRecheck`는 자동 횟수 상한이나 Batch 등록 여부로 막지 않는다. 다만 관리자 후보 안내에서는 처리권·최소 대기·백오프를 존중한다. 기존 구매자 본인 재확인 API의 허용 규칙은 변경하지 않는다.
- 이 조회는 처리권을 확보/해제하거나 시도 수를 초기화하지 않는다. 실제 관리자 단건 재확인은 아직 미연결이다.

### Batch 재시작

고정 Job 이름 `walletTopupRecovery`만 조회한다. 같은 인스턴스의 **최신 실행**이 `FAILED` 또는 `STOPPED`이고, 복구 설정·토스 설정·허용 파라미터가 유효할 때만 후보가 된다. 실행 중은 `JOB_RUNNING`, 이전 실행은 `NEWER_EXECUTION_EXISTS`, 잘못된 파라미터는 `INVALID_JOB_PARAMETERS`, 나머지 상태는 `JOB_NOT_RESTARTABLE`이다.

`STARTED`·`UNKNOWN`·`ABANDONED`를 임의로 실패로 바꾸거나 강제 재시작하지 않는다. 프로세스 종료 확인과 이력 복구는 [Batch 운영 경계](wallet-topup-recovery-batch.md#내부-실행재시작-계약)를 따른다. 기존 `TopupRecoveryJobService.start()` / `restart(executionId)`는 내부 조율용 그대로이며 관리자 API를 직접 붙인 상태가 아니다.

## K6 실제 실행을 연결할 때

현재 감사 이력은 `product_moderation_logs`의 상품 숨김/복구 전용이다. 금융 작업을 여기에 끼워 넣거나 원장/Batch 컨텍스트를 관리자 감사 이력으로 사용하지 않는다. 공통 관리자 작업 이력 설계·API·화면은 김창재, 필요한 DDL은 황준범과 맞춘다.

1. Controller는 세션의 `CurrentMemberId.require(authentication)`에서 관리자 ID를 얻고 `/api/admin/**` 권한·변경 요청 CSRF·`Cache-Control: no-store`를 적용한다. 요청 본문/쿼리의 관리자 ID를 신뢰하지 않는다.
2. 변경 서비스는 DB 권한, 사유, 대상과 중복 요청을 재검사하고 **감사 요청 기록을 먼저 커밋**한다. 실제 저장 계약과 중복 키는 관리자 영역과 확정해야 한다.
3. 외부 조회/Batch 시작은 감사 기록 트랜잭션 밖에서 실행한다. `TopupRecoveryJobService`의 `NEVER`, 실제 충전 claim → 트랜잭션 밖 조회 → 지급 적용 경계를 깨지 않는다.
4. 결과 이력을 별도 커밋한다. 조회·실행 결과가 불명확하면 성공/실패를 단정하지 않는다. 잔액 직접 수정·실패 상태 강제 완료·시도 수 초기화로 복구하지 않는다.

관리자 단건 재확인은 인증된 구매자용 API에 타인의 ID를 넣어 호출하는 방식으로 연결하지 않는다. 관리자 권한·사유·감사 기록을 갖춘 명령 경계가 준비된 후 기존 지급 트랜잭션과 처리권 검증을 재사용한다. 이번에는 읽기 후보와 실제 실행을 구분해 제공한다.

## 검증

PR #117 충돌 해결 후속(2026-11-06): main `7329be1`(#118 세션 만료 헤더·캐시 정리)을 통합했다. `current-status.md` 상단의 금융 준비 절과 세션 정리 절을 모두 보존했다. 백엔드 소스는 직전 `3869391`과 동일하며 해당 커밋의 원격 CI 성공을 확인했다. 병합 후 프런트 전체 223건·lint·TypeScript 포함 build 통과. 최신 커밋의 원격 CI는 별도다. 화면 안내 수정 `b9ac35c`는 별도 브랜치에 보존하고 이 PR에 포함하지 않는다.

PR #117 생성 직후 main `15358e0`(#116 브라우저 확인·수락 안내 공백 수정)도 반영했다. J5의 main 반영 표기와 브라우저 기록을 보존했다. 백엔드 소스는 #115 통합 후의 `0b9140f`와 동일하므로 백엔드 선택 검사를 반복하지 않았고, 최종 프런트 219건·lint·TypeScript 포함 build는 다시 통과했다. #116에서 발견한 판매 수익 안내·정상 계약 미작성 alert·첫 서명 알림 정책은 이 PR에서 변경하지 않는다.

공유 전 main `f82c49d`(#115 공통 이벤트·오류 계약)을 병합했다. 관리자 금융 소스는 `e8431ef`와 동일하게 유지했고 현황 문서의 두 절을 모두 보존했다. 병합 후 격리 MySQL에서 관리자 금융 선택 검사 **19건**, 프런트 전체 **219건**·lint·TypeScript 포함 build가 통과했다. 백엔드 전체는 이 병합 후 다시 실행하지 않았으며 아래 894건은 병합 전 결과다. 원격 CI는 PR에서 별도로 확인한다. 프런트에는 기존 500kB 이상 청크 경고가 남아 있다.

병합 전 격리 MySQL 8.4에서 백엔드 전체 `clean build` **894건**이 실패·오류·건너뜀 없이 통과했다. 신규 관리자 금융 통합 18건·설정 분리 단위 1건이 포함된 결과다. 권한 차단·DB 역할 변경·필터/페이징·연결 금액과 누락 기록·자격 증명 비노출·처리권 만료/백오프/상한·Batch 실행 집계/이전 실행/실행 없는 인스턴스/잘못된 파라미터를 검사했다. 이전 선행 선택 17건과 합산하지 않는다. 이 실행 당시에는 프런트 검사를 재실행하지 않았다. 실제 관리자 화면·재처리 실행은 별도다.

테스트 회원은 `support/TestMembers`의 인증된 가입 증명과 실제 `MemberService.signup`으로 만든다. 조회 테스트의 결제·보관·정산·종료 상태 조합은 fixture이며 거래 명령 자체의 검증과 구분한다. 개인 DB·실제 토스·SMTP·관리자 화면은 사용하지 않는다.
