# 결제·정산의 JPA·QueryDSL 전환

> 문서 읽기 기준(2026-11-04): 아래 날짜·브랜치별 구현 및 검증 설명은 당시 기록이다. 현재 반영 여부·남은 작업은 [현황표](current-status.md), 실행 설정은 [루트 README](../README.md), API 계약은 [API 명세](api-spec.md)를 따른다. 과거의 미구현·병합 전 표현과 검사 수를 현재 상태로 확대하지 않는다.

2026-11-03 저장소 후속 변경: [프로젝트 JPA·QueryDSL 통일](project-persistence.md)에 따라 현재 운영 구현에서 JdbcTemplate·네이티브 SQL을 제거했다. 아래 이전 SQL 구현 설명·실행 결과는 당시 기록으로 보존하며 현재 구조와 최종 검증 범위는 후속 문서를 따른다.

2026-11-03. refactor/finance-jpa-querydsl. 기존 V1/V2·API·금액/상태 정책은 변경하지 않는다.

## 변경 이유

초기 결제·정산은 기존 주문 조회의 JdbcTemplate 패턴을 따라 구현했다. 기능 테스트는 통과했지만 JPA·QueryDSL을 선택한 팀의 저장소 구조와 일관성이 부족했고 서비스에 SQL이 섞였다. JDBC가 필요해서 선택한 구조가 아니므로, 쓰기는 JPA와 Entity 상태 변경으로, 수익 조회는 QueryDSL로 정리한다.

## 구조

- Payment Entity + PaymentRepository(JpaRepository): 원결제 생성·조회, Entity 환불 전환·변경 감지. 기존 호출부와의 연결을 유지하는 insert/markRefunded 기본 메서드는 JPA에 위임하며 SQL을 실행하지 않는다.
- Settlement Entity + SettlementRepository: 정산 1건 생성·재호출 시 기존 금액 비교. 수수료 버림 계산은 Entity 팩터리에서도 검증한다.
- CancellationRequest Entity/Repository: 직접 취소 경로의 대기 요청 조회·승인. 승인 이력도 기존 OrderHistory 팩터리와 JPA로 저장한다.
- SettlementQueryRepository: QSettlement·QOrder로 본인 주문 조인, 건수·목록·정렬·정산 합계를 구성한다. QueryDSL의 SUM은 BigDecimal로 받아 기존 정확 변환·JSON 안전 정수 검사를 유지한다.
- OrderCancellationService·OrderConfirmationService·SettlementQueryService의 JdbcTemplate/직접 SQL을 제거한다.

Entity는 기존 컬럼에 매핑하고 UTC LocalDateTime에는 LOCAL_DATE_TIME을 적용한다. 새 마이그레이션은 없다. 주문→지갑 잠금, order_id/event_key UNIQUE, 원자성·반복 요청 계약은 유지한다. 상세 응답의 JDBC 조회와 후속 취소 요청 조회 전에 JPA 변경을 flush해 DB에서 같은 결과를 읽도록 한다.

## 남겨둔 범위

주문 담당자의 CancellationRequestQueries·OrderDetailQueries·OrderListQueries·OrderCreationQueries는 이번에 전면 변경하지 않는다. 취소 요청 승인 경로가 JPA 직접 취소를 호출한 뒤 기존 JDBC 조회로 결과를 확인하는 흐름도 회귀 검사에 포함한다. 추후 해당 담당자와 별도 합의해 바꿀 수 있다.

지갑 최초 생성의 INSERT IGNORE·회원 잠금 및 충전의 기존 JPA 네이티브 쿼리는 유지한다. 이를 JdbcTemplate 제거와 같은 작업으로 취급하지 않는다. 테스트의 JdbcTemplate fixture/DB 원문 검사도 유지한다.

## 검증

기존 실제 MySQL 결제·환불·정산·취소 요청 통합 테스트를 전체 재실행한다. QueryDSL의 복수 정산 합계, 동일 시각 ID 내림차순 페이지, 타인 데이터 제외, 없는 회원의 0원 합계를 보강했다. 기존 동시 잔액 사용·취소/확정 충돌·중복 지급/정산·실제 FK 실패 롤백·UTC SQL 원문 검사도 유지한다. 최종 실행 결과는 아래 기록한다.

최종 격리 MySQL 8.4 clean build --offline **489건**, 실패·오류·스킵 0으로 통과했다. 최초 전환 검사에서는 승인 이력 팩터리가 CANCELLATION_APPROVED를 허용하지 않아 실패했고, 기존 SQL과 동일한 승인 이력을 JPA로 생성하도록 보완한 뒤 전체를 재실행했다. QueryDSL 보강 검사는 최종 실행에 포함됐다. 임시 컨테이너·볼륨은 정리했고 개인 DB·키는 사용하지 않았다. 프런트·API 형식·DB 스키마 변경이 없어 프런트 재빌드는 하지 않았다. 실제 브라우저/원격 CI 검증을 뜻하지 않는다.
