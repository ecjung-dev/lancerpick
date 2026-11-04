# 지갑 생성·최초 테스트 적립금 지급

> 문서 읽기 기준(2026-11-04): 아래 날짜·브랜치별 구현 및 검증 설명은 당시 기록이다. 현재 반영 여부·남은 작업은 [현황표](current-status.md), 실행 설정은 [루트 README](../README.md), API 계약은 [API 명세](api-spec.md)를 따른다. 과거의 미구현·병합 전 표현과 검사 수를 현재 상태로 확대하지 않는다.

현재 연결 2026-11-03: `MemberService.signup()`이 가입 트랜잭션에서 `WalletInitializationService.initializeForNewMember(Long memberId)`를 호출한다. 회원·지갑·최초 지급 내역은 함께 저장/롤백한다. 과거 제안인 `createInitialWallet(memberId, now)`가 현재 호출 계약은 아니다. 실제 가입·로그인 경유 검사는 [지갑 세션 연결](wallet-session-integration.md)·[세션 거래 검사](session-trade-integration.md)를 따른다.

2026-11-03 저장소 후속 변경: [프로젝트 JPA·QueryDSL 통일](project-persistence.md)에 따라 현재 운영 구현에서 JdbcTemplate·네이티브 SQL을 제거했다. 아래 이전 SQL 구현 설명·실행 결과는 당시 기록으로 보존하며 현재 구조와 최종 검증 범위는 후속 문서를 따른다.

작성일: 2026-11-02. 담당: 황준범. 브랜치: `feature/wallet-initial-grant`.

## 1. 구현 범위

기존 V1의 `wallets`, `wallet_entries`에 Entity·Repository를 매핑하고 회원가입에서 호출할 내부 초기화 서비스를 구현했다. 테이블 추가·V1 수정·공개 API 추가는 하지 않았다.

- 회원당 지갑 한 개, 최초 테스트 적립금 1,000,000원 한 번 지급.
- 최초 내역은 `INITIAL_GRANT`, `payment_id=NULL`, `amount_delta=1000000`, `balance_after=1000000`.
- 고정 이벤트 키는 `INITIAL_GRANT:{memberId}`.
- 지갑·원장에는 같은 UTC 시각을 사용하고 마이크로초로 절삭한다.
- `Wallet.updatedAt`·`WalletEntry.createdAt`은 `LOCAL_DATE_TIME` JDBC 매핑으로 UTC 원문을 보존한다. 네이티브 지갑 INSERT에는 UTC로 변환한 `Instant`를 전달한다. `LocalDateTime` 파라미터를 그대로 바인딩하면 JVM 시간대에 따라 DB 원문이 이동할 수 있다.
- 기존 정상 지급 건을 재호출해도 현재 잔액·시각·기존 내역을 변경하지 않는다.

이 작업만으로 회원가입·로그인·잔액 조회 화면·주문 결제·환불·정산·외부 충전이 완성되는 것은 아니다. 실제 회원 Entity와 회원가입 서비스는 회원 담당자가 구현한다.

## 2. 회원가입에서 호출할 계약

호출 대상은 다음 메서드다.

```java
walletInitializationService.initializeForNewMember(savedMemberId);
```

회원가입 서비스에서 **회원 저장 후 ID를 확정하고, 같은 트랜잭션 안에서 Spring이 주입한 서비스 빈을 호출**한다. 호출자의 회원 저장과 지갑·지급 내역은 함께 커밋·롤백한다.

| 항목 | 구현 계약 |
| --- | --- |
| 입력 | 서버가 저장한 회원 ID(long). 금액·이벤트 키는 클라이언트 입력으로 받지 않음 |
| 반환 | void. 최초 생성·정상 재호출 모두 정상 반환 |
| 트랜잭션 | `Propagation.MANDATORY`. 호출자의 트랜잭션이 없으면 처리 전 실패 |
| 회원 없음 | 404 `RESOURCE_NOT_FOUND` |
| ID가 0 이하 | 400 `VALIDATION_FAILED` |
| 기존 지갑의 최초 지급 근거 누락·불일치 | 500 `DATA_INTEGRITY_ERROR`. 자동 보정·재지급하지 않음 |
| 원장 INSERT 등 DB 처리 실패 | 예외 전파. 호출자가 숨기지 않고 전체 롤백 |

상위 회원가입의 중복 이메일·가입 요청 계약을 대신하는 기능은 아니다. 회원 담당자와 호출 위치·실패 전파를 함께 검토해야 한다. 현재 인증 principal 형식은 이 내부 서비스의 입력에 영향을 주지 않으며, 사용자 입력의 memberId를 그대로 신뢰하는 공개 엔드포인트는 만들지 않는다.

## 3. 구현 파일

기본 경로는 `LancerPick/src/main/java/com/example/lancerpick/`이다.

| 파일 | 책임 |
| --- | --- |
| wallet/entity/Wallet.java | 공유 PK `member_id`, 잔액, 갱신 시각 매핑 |
| wallet/entity/WalletEntry.java | 원장 매핑·초기 지급 기록 생성·기존 지급 근거 확인 |
| wallet/entity/WalletEntryType.java | 기존 V1의 INITIAL_GRANT·PAYMENT·REFUND 코드 |
| wallet/repository/WalletRepository.java | 회원 행 잠금·지갑 생성 시도·기존 지갑 잠금 조회 |
| wallet/repository/WalletEntryRepository.java | 이벤트 키로 기존 지급 근거 잠금 조회 |
| wallet/service/WalletInitializationService.java | 지급 정책·트랜잭션 참여·중복과 불완전 상태 검사 |

Member·Payment Entity를 중복 정의하지 않고 외래키 ID 컬럼을 매핑했다. 외래키 존재는 기존 MySQL 제약으로 확인한다. `WalletEntry`에는 수정·삭제용 도메인 메서드가 없고 공개 쓰기 API도 제공하지 않는다. Repository 자체를 DB 변경 권한 통제 장치로 표현하지는 않는다.

## 4. 잠금·동시 요청 설계

초기 지갑은 아직 행이 없으므로 지갑 행 잠금만으로 생성 경쟁을 직렬화할 수 없다. 이 초기화에서는 **회원 행을 먼저 잠가 같은 회원의 생성 시도를 직렬화**한다. 주문 결제·환불의 `주문 → 구매자 지갑` 규칙을 변경하는 것이 아니며, 결제 과정에서 초기화 서비스를 호출하지 않는다.

```text
상위 회원가입 트랜잭션
 → 회원 PK 잠금·존재 확인
 → 공유 PK로 지갑 INSERT 시도
    ├─ 신규 1건: 최초 원장 INSERT
    └─ 기존 0건: 실제 지갑·최초 지급 내역을 잠금 조회해 확인
 → 상위 처리까지 성공하면 커밋
```

지갑 생성은 MySQL `INSERT IGNORE`의 반환 건수로 구분한다. ID는 존재·양수 여부를 확인하고, 금액은 서버 고정값, 시각은 UTC·마이크로초·MySQL 범위로 검증한다. 0건을 정상 지급으로 간주하지 않고 실제 지갑과 올바른 원장 존재를 확인한다. 그 밖의 반환값·불완전 상태는 실패한다. 기존 지갑의 잔액이나 updated_at을 덮어쓰는 upsert는 사용하지 않는다.

잠금 조회를 사용해 호출자가 앞서 일반 조회로 만든 REPEATABLE READ 스냅샷이 있더라도 다른 요청이 커밋한 지급 근거를 확인한다. PK·이벤트 UNIQUE 제약은 마지막 중복 방어선으로 유지한다.

### 테스트 중 발견한 교착 상태와 수정

첫 구현은 회원 잠금 후 없는 지갑을 `FOR UPDATE`로 조회했다. 같은 회원의 요청은 통과했지만 **서로 다른 회원을 동시에 초기화하는 실제 MySQL 테스트에서 간격 잠금에 의한 교착 상태**가 발생했다.

없는 행에 잠금 조회를 먼저 하지 않고 지갑 INSERT를 먼저 시도하도록 바꿨다. 이후 같은 회원·서로 다른 회원의 동시 초기화 검사를 각각 5회 반복한다. 이는 이 경로의 검증이며 모든 거래·잠금 조합에서 데드락이 없다는 보장은 아니다.

## 5. 테스트 실행과 결과

테스트 클래스는 `WalletInitializationIntegrationTests`다. 개발용 DB와 다른 `lancerpick_test` 또는 `lancerpick_ci`를 사용해야 한다. 설정은 [테스트 DB 안내](test-database.md)를 따른다.

```powershell
cd C:\project\LancerPick
.\gradlew.bat test --tests "com.example.lancerpick.wallet.WalletInitializationIntegrationTests"
```

통합 테스트는 클래스 전체를 하나의 자동 롤백 트랜잭션으로 감싸지 않는다. 각 요청을 독립 트랜잭션으로 커밋하고 저장된 결과를 확인한다. 테스트용 회원은 SQL fixture로 만들며 실제 회원가입 구현 완료를 뜻하지 않는다.

| 검사 | 확인할 결과 |
| --- | --- |
| 최초 지급·JPA 매핑 | 회원·지갑·원장 각 1건, 100만 원, FK·타입·UTC 시각 일치 |
| 독립 호출 | 외부 트랜잭션 없으면 지급 전 거절 |
| 재호출 | 원장 ID·금액·시각 불변, 지급 1회 |
| 잔액 변경 후 재호출 | 낮아진 현재 잔액을 다시 100만 원으로 덮어쓰지 않음; 결제 동작 검증은 아님 |
| 같은 회원 동시 4요청 × 5회 | 지갑·원장 1개, 잔액·내역 합계 100만 원 |
| 서로 다른 회원 동시 4요청 × 5회 | 회원마다 지갑·원장 1개, 불필요한 초기화 교착 방지 |
| 기존 스냅샷 후 다른 요청의 지급 커밋 | 최신 지급 근거를 확인하고 추가 지급 없음 |
| 실제 원장 UNIQUE 실패 | 같은 트랜잭션의 새 회원·지갑·원장 모두 롤백 |
| 초기화 후 가입 후속 실패 | 새 회원·지갑·원장 모두 롤백 |
| 불완전 지갑·다른 회원의 원장 | DATA_INTEGRITY_ERROR, 자동 보정 없음 |
| 없는 회원·잘못된 ID | 신규 금액·내역 없음 |

검증 환경은 기존 개인 DB와 분리한 임시 Docker MySQL 8.4.13, REPEATABLE READ다. 기존 Flyway V1 적용과 지갑·원장 두 Entity의 Hibernate validate를 확인했다. 나머지 14개 테이블의 업무 Entity 매핑 검증으로 확대해 표현하지 않는다.

위 검증은 초기 지급 구현 당시의 범위다. 후속 회원가입·초기 지급 UTC SQL 원문 재검증과 최신 실행 결과는 [작업표](work-plan.md)를 따른다. 실제 회원 인증·외부 제공자 호출·원격 CI·다른 팀원 환경 검증과는 구분한다.

DB 없는 메모리 예시(`demo`)에서는 초기화 서비스 빈을 만들지 않는다. DB를 제외하는 기본 프로필 HTTP 테스트에서는 이 서비스를 모의 빈으로 대체하고, 실제 서비스 동작은 위 MySQL 통합 테스트에서 확인한다.
