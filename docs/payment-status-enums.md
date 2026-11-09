# 결제·보관금 상태 enum — R2

2026-11-09, 황준범. main `6cf5667`(#135)에서 분기한 `refactor/payment-funds-status`의 로컬 구현 기록이다. 정은찬의 [리팩토링 제안 §14.2](ui-renewal-member-policy.md#142-결제보관-상태-enum-전환-r2)을 구현하며 주문 호출/조회 영향은 정은찬 검토 대상이다. push·PR·main 반영은 별도다.

## 내부 타입·외부 계약

| 엔티티 | 내부 타입 | 기존 DB 값·길이 |
| --- | --- | --- |
| Payment.status / status() / getStatus() | `PaymentStatus` | PAID, REFUNDED / VARCHAR(10) |
| OrderFunds.status / getStatus() | `OrderFundsStatus` | HELD, RELEASED, REFUNDED / VARCHAR(16) |

두 필드는 `@Enumerated(EnumType.STRING)` + `@JdbcTypeCode(SqlTypes.VARCHAR)`를 사용한다. DB의 기존 CHECK·NOT NULL·컬럼 길이·원문 값을 유지하고 ordinal이나 DB native ENUM으로 바꾸지 않는다. 알 수 없는 값을 정상 enum으로 보정하는 Converter/기본값은 없다. 새 enum 상수를 추가하려면 해당 DB CHECK·상태 정책을 별도 검토해야 한다.

- 팩터리·환불/보관 해제 전이는 enum 상수, 비교는 `==`/`!=`다. PaymentRepository.markRefunded, OrderFundsReader, 기존 주문 결제·취소·확정과 FullFundingService의 V3 보관·지급·반환·자동 확정에 적용했다.
- QueryDSL 생성 경로는 `EnumPath<PaymentStatus>` / `EnumPath<OrderFundsStatus>`다. 관리자 금융 조회의 상태 조건도 enum 비교로 구성한다.
- 관리자 조회 입력의 status는 기존 String 경계를 유지한다. 서비스에서 enum 이름을 엄격히 검사하고 저장소에서는 검증된 값을 enum으로 변환한다. 알 수 없는 값/다른 대소문자는 기존 400 `VALIDATION_FAILED`다.
- 주문 상세 결제, V3 대금 패널, 관리자 결제/보관금 DTO는 기존 String 필드를 유지하고 조립 시 `.name()`으로 변환한다. JSON·프런트 API·`NOT_PAID`(미결제 조회용 가상 상태) 계약은 그대로다. NOT_PAID를 DB enum에 추가하지 않는다.

금액·수수료·원장·멱등 키·환불/정산 횟수·잠금 순서·트랜잭션 경계·정책 버전·외부 토스 상태는 변경하지 않았다. 인증·채팅·회원 제재·알림 API에도 새 기능을 추가하지 않는다.

## DB·기존 데이터 검증

기존 Flyway V1~V10은 수정하지 않았고 새 마이그레이션도 추가하지 않는다. 실제 매핑/읽기·쓰기를 MySQL에서 검증한 결과를 아래에 기록한다. DB의 `ddl-auto=validate`를 유지하며 `update`로 컬럼을 바꾸지 않는다.

FullFundingIntegrationTests의 보강은 enum 팩터리로 만든 새 행만 확인하는 것과 구분한다.

1. SQL로 기존 형식의 PAID 결제·HELD 보관금을 넣고 영속성 컨텍스트를 비운 후 JPA enum으로 읽는다.
2. 환불 전이를 JPA로 저장·다시 읽어 두 enum이 REFUNDED인지, 원문 DB도 REFUNDED인지 확인한다.
3. 과거 REFUNDED 결제를 SQL로 넣어 enum으로 읽고 반복 환불이 거절되는지 확인한다.
4. RELEASED 보관금도 저장·재조회하고, information_schema에서 상태 컬럼이 여전히 VARCHAR(10)/VARCHAR(16)인지 확인한다.

이 검사는 격리 DB에 기존 형식의 데이터를 재현한 호환 검사다. 사용자의 개인 DB 전체 내용을 검사·변경했다는 뜻은 아니다.

## 검증 결과

2026-11-09, main `6cf5667` 기반 변경을 격리 Docker MySQL 8.4에서 `clean build`로 검사했다. **989건 통과, 실패·오류·건너뜀 0**. V1~V10 Flyway 적용·Hibernate `ddl-auto=validate`, 기존 형식의 VARCHAR 행 읽기·환불 저장·컬럼 타입 확인을 포함한다. 기존 금융·관리자 HTTP 검사는 JSON 상태 문자열, 결제·반복 결제, 환불·중복 반환, 취소/확정 경쟁, 수동/자동 확정, 지급/정산·실패 롤백을 확인한다. 실제 브라우저·외부 결제 제공자·원격 CI는 이번 검사 범위가 아니다. 검사 후 임시 컨테이너는 제거했다.

프런트 소스·API JSON 필드가 그대로이므로 프런트 검사는 이번 작업에서 반복하지 않는다. 검증 도구는 Git 제외 `testDocument/`에 두고 개인 설정·키는 커밋하지 않는다.

후속: main `b7a91e2`(#136 판매 수익 기간 조회)까지 통합했다(`139084f`). 소스 충돌은 없고 문서 3곳은 양쪽 기록을 보존해 해결했다. 격리 MySQL에서 기간 조회·기존 구매 확정·V3 지급·VARCHAR 호환·반복 환불 검사를 포함한 53건이 통과했다(실패·오류·건너뜀 0). 앞선 전체 989건과 합산하지 않는다. 통합 후 전체 clean build·프런트 검사는 반복하지 않았으며 원격 CI는 별도로 확인한다.
