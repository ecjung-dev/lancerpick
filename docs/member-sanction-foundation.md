# 회원 제재 공통 기반 — B1/B2

2026-11-09, 황준범. `feature/member-sanction-foundation`의 로컬 구현 기록이다. main 반영·관리자 화면 완료와 구분한다. 정책은 [화면 개편 §11·§16](ui-renewal-member-policy.md)을 따른다.

## 제공 범위

| 항목 | 이번 구현 | 후속 연결 |
| --- | --- | --- |
| B1 제재 조회 | `MemberSanctionQuery.restriction(long memberId)` | 정은찬: 주문·타이머 검사, 박소현: 신규 상품 활동 검사 |
| B2 제재 명령 | `MemberSanctionCommandService.apply` / `revoke`, 관리자 DB 권한·회원 잠금·이력·중복 방지 | 황준범: 회원 관리 API·화면 |
| 보안 잠금 인증 | 로그인 거절, 인증 버전 증가, 기존 HTTP/소켓 인증 재검사 경로 연결 | 실제 브라우저·소켓 폐기 확인은 별도 |
| 타이머 중단 전달 | 커밋 후 호출, 실패/연결 전 미처리 기록, 내부 재시도 | 정은찬: `SecurityLockTimerStopper` 구현체·타이머 재검사 |
| 만료 | 서버 UTC 시각으로 기간 종료 시 제한 없음으로 조회 | 별도 만료 Batch·행 삭제 불필요 |

**관리자 HTTP 경로·화면, 상품/주문의 신규 활동 차단, 실제 타이머 중단·만료 보호는 이 브랜치에 없다.** 연결 전 제재 기능 전체 완료로 표시하거나 일반 운영에 열지 않는다. 비밀번호 기준일(B3), 잠금 공통화(R1), 결제 enum(R2)도 별도다. 자동 취소·환불은 추가하지 않았다.

## B1: 조회 계약

```java
var restriction = memberSanctionQuery.restriction(memberId);
restriction.kind();                // NONE / GENERAL_RESTRICTION / SECURITY_LOCK
restriction.blocksNewActivity();   // 일반 제한 또는 보안 잠금
restriction.securityLocked();
restriction.adminId();             // 현재 제재 적용 관리자. NONE이면 null
restriction.latestSecurityLock();  // 가장 최근 보안 잠금 기록. 없으면 null
```

클래스는 `member/service/MemberSanctionQuery`다. 조회는 회원/주문 잠금을 추가하지 않는다. 호출자가 회원 존재·권한·잠금을 보장한다. 없는 양수 ID의 조회는 NONE이며 유효하지 않은 ID는 내부 호출 오류다. 변경 명령에서는 READ_COMMITTED 트랜잭션의 기존 회원 잠금 뒤 조회하고, 조회 결과만으로 별도 트랜잭션의 명령을 허용하지 않는다. 기존 외부 트랜잭션에 참여할 때 조회 서비스의 isolation 선언이 외부 격리 수준을 바꾸지는 않으므로 REPEATABLE_READ의 오래된 스냅샷을 명령 판단에 사용하지 않는다. 일반 이용 제한은 로그인·기존 거래를 막지 않는다.

현재 유효 기간은 `startsAt <= 서버 시각 < endsAt`, 철회 기록은 제외한다. 둘 다 유효하면 SECURITY_LOCK 우선이다. 같은 종류의 새 제재는 기존 활성 기록을 철회하고 교체하며 다른 종류는 보존한다. 보안 잠금 해제 후 일반 제한이 남아 있으면 일반 제한이 적용된다.

`latestSecurityLock`은 만료·철회 후에도 `sanctionId/adminId/reason/appliedAt/endsAt`을 반환한다. **현재 제한 NONE만 보고 잠금 이전 타이머를 자동 확정하면 안 된다.** 커밋 후 전달 장애·서버 종료 뒤 잠금이 먼저 만료될 수 있다. 타이머 담당은 과거 잠금의 appliedAt과 해당 타이머를 대조해 중단 여부를 재검사한다.

## B2: 변경·트랜잭션 계약

```java
long sanctionId = memberSanctionCommandService.apply(
    adminId, memberId, SanctionKind.SECURITY_LOCK,
    reason, untilUtc, requestKey); // UUID, 재시도 시 동일 키·동일 인자
memberSanctionCommandService.revoke(adminId, memberId, sanctionId, reason);
```

- 관리자 ID는 추후 Controller의 `CurrentMemberId.require(authentication)`에서 받는다. 요청 본문의 관리자 ID를 신뢰하지 않는다. 서비스도 DB ADMIN 권한을 확인한다. 자기 자신·관리자 계정 대상 제재는 거절한다.
- 사유는 정규화 후 1~1,000자, 종료 시각은 잠금 뒤 서버 시각보다 미래다. 모든 시각은 UTC LocalDateTime, 마이크로초 정밀도다.
- 호출자는 외부 트랜잭션 없이 호출한다(`NEVER`). 서비스의 짧은 READ_COMMITTED 트랜잭션에서 회원 ID 오름차순으로 관리자 공유 잠금·대상 회원 배타 잠금을 얻는다. 주문/지갑 잠금은 이 트랜잭션에서 얻지 않는다.
- 제재 이력 저장과 보안 잠금의 인증 버전 증가는 함께 성공·롤백한다. 버전은 만료·철회 때 감소시키지 않는다. 기존 세션은 복구되지 않고 새 로그인이 필요하다.
- `(member_id, request_key)` UNIQUE와 대상 회원 잠금으로 반복·동시 요청을 방어한다. 같은 키·동일 인자는 최초 ID를 반환하고 인증 버전·이벤트를 반복 생성하지 않는다. 다른 인자는 409 `SANCTION_REQUEST_CONFLICT`다.
- 보안 잠금 로그인은 올바른 비밀번호 검증 후 403 `MEMBER_LOCKED`. 기존 HTTP 세션은 기존 인증 버전 검사에서 401, 소켓은 기존 인증 재검사 경로를 사용한다. 이미 진행 중인 명령은 기존 회원 잠금 순서로 직렬화하며 완료 거래를 소급 취소하지 않는다.

## 정은찬 연결: 커밋 후 타이머 중단

`member/service/SecurityLockTimerStopper`의 Bean 구현체 하나를 제공한다.

```java
void stop(SecurityLockApplied lock);
// lock.sanctionId(), adminId(), memberId(), reason(), appliedAt()
```

1. 회원 제재가 실제 커밋된 뒤, 제재 트랜잭션 밖에서 호출된다. 타이머 쪽은 주문마다 별도 트랜잭션을 사용한다. 회원 공유 잠금 → 주문 배타 잠금을 유지한다.
2. `appliedAt` 당시 영향을 받은 타이머만 중단한다. 해제 후 정상적으로 시작한 새 타이머를 늦은 전달로 중단하면 안 된다. 같은 기산점 재시작 금지·만료 후 미처리도 ADMIN_STOP 처리하는 규칙은 유지한다.
3. 모든 대상이 처리됐을 때만 정상 반환한다. 일부 실패는 예외로 알린다. 전달은 중복될 수 있으므로 중단 명령은 멱등이어야 한다.
4. 성공 후 `timer_stop_delivered_at`을 별도 트랜잭션으로 기록한다. 실패·구현체 부재·프로세스 중단은 미처리 상태로 보존한다. 제재 커밋 성공을 되돌리거나 성공 전달로 가장하지 않는다.
5. `SecurityLockDelivery.retryPending(int limit)`는 1~100건 내부 재시도 진입점이다. 자동 스케줄러·관리자 API는 아직 연결하지 않았다. 재시도와 별개로 타이머 시작/만료 처리 자체의 제재·과거 잠금 재검사가 필수다.

전달 로그에는 제재 ID·예외 종류만 남긴다. 비밀번호·세션·제재 사유를 로그에 쓰지 않는다.

## V10 DB

기존 V1~V9는 수정하지 않고 `V10__add_member_sanctions.sql`로 업무 테이블 1개를 추가한다. 누적 업무 32개 + Batch 시스템 9개 = 41개이며 Flyway 이력 테이블은 별도다. 개인 DB는 정상 서버 기동/Flyway 실행 때 적용되며 자동 DDL 생성으로 대체하지 않는다.

| 컬럼 | 의미 |
| --- | --- |
| id / member_id | 제재 ID / 대상 회원 FK |
| kind / reason / applied_by | 종류·사유·적용 관리자 FK |
| request_key | 대상별 중복 방지 UUID |
| starts_at / ends_at | 적용·종료 UTC 시각 |
| revoked_at / revoked_by / revoke_reason | 수동 해제 또는 같은 종류 교체 이력 |
| timer_stop_delivered_at | 타이머 중단 전달 완료 시각. 일반 제한은 NULL |

기간·종류·해제 필드 묶음·전달 시각 CHECK, 대상/관리자 FK와 요청 UNIQUE를 적용한다. 현재 상태 조회 인덱스와 미처리 전달 조회 인덱스를 제공한다. 만료 이력을 삭제하거나 이전 Flyway를 다시 쓰지 않는다.

## 검증

2026-11-09 로컬, main `4064d08` 기반: 격리 Docker MySQL 8.4에서 백엔드 `clean build` 962건 통과(실패·오류·건너뜀 0). V10 Flyway 적용·`ddl-auto=validate` 기동을 포함한다. 패키지 분리 후 전체 재실행 결과이며, 이후 기존 파일의 조건문 줄바꿈·블록 표기만 정리했다.

후속: main `4430cd0`(#129 화면 개편·#130 주문 목록 조회 조건)까지 작업 브랜치에 통합했다. 소스 충돌은 없고 현황·작업표·개편 설계 문서의 추가 기록 충돌 3곳은 양쪽 기록을 보존해 정리했다. 통합 커밋 `94d2947`에서 격리 MySQL 기반 제재·로그인·공개 메인·주문 조회·계약 통합 테스트 85건과 프런트 `test:api` 236건이 통과했다(실패·오류·건너뜀 0). 85건을 전체 재실행으로 표시하거나 앞선 962건과 합산하지 않는다. main 통합 후 프런트 lint/build·실제 브라우저·원격 CI는 이번에 재실행하지 않았다.

신규 제재 통합 9건·전달 단위 2건은 일반 제한/보안 잠금 우선순위·정확한 만료 경계·철회 후 이력 보존, 중복/동시 요청, 관리자 권한·입력·외부 트랜잭션 거절, 인증 버전 실패 시 전체 롤백, 기존 세션 폐기·잠금 로그인 차단·만료 후 새 로그인, 추가 회원 잠금 없는 조회, 커밋 후 호출·실패/구현체 부재 시 미처리 보존·재시도를 확인한다.

관리자 API/화면과 실제 타이머 구현은 미연결이므로 대역으로 커밋 후 호출·실패 보존을 검사했다. 실제 브라우저·소켓 잠금 확인과 U7/U8 전체 완료를 뜻하지 않는다. 개인 DB 변경·외부 결제 호출은 없고 격리 컨테이너는 검사 후 제거했다.
