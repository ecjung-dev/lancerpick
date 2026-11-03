# 프로젝트 JPA·QueryDSL 통일

2026-11-03. refactor/project-jpa-querydsl. 선행 금액 처리 리팩터링을 포함한다.

## 적용 원칙

사용자 결정에 따라 운영 Java 코드에서 JdbcTemplate·직접 JDBC 조회/쓰기·nativeQuery/createNativeQuery를 제거한다. Entity 저장/변경은 JPA, 조건·조인·집계는 QueryDSL로 통일한다. SQL이 필요한 Flyway와 테스트 fixture·DB 원문 검사에는 해당 운영 규칙을 적용하지 않는다. JPA 내부의 JDBC 드라이버 사용 자체를 없애는 것은 아니다.

## 전환 내용

| 영역 | 변경 |
| --- | --- |
| 주문 생성 | 회원·상품 Entity 조회/잠금, 기존 참여 회원 ID 오름차순 획득 유지 |
| 주문 목록·상세 | QueryDSL 조인·조건·정렬·페이지. 과거 조건은 스냅샷에서 읽고 현재 상품으로 재작성하지 않음 |
| 취소 요청 | Entity 생성·해결·승인 응답 정리와 QueryDSL 조회. 같은 주문 잠금·원자성 유지 |
| 관리자 상품 | QueryDSL 검색/정렬, JPA 잠금·숨김/버전 변경·운영 이력 저장. LIKE의 %, _, ! 일반 문자 검색 유지 |
| 지갑·원장 | JPA 충전 반영/원장 생성, QueryDSL 결제 조인 내역. API의 Instant/UTC 표현 유지 |
| 회원·납품·주문 잠금 | 네이티브 쿼리를 JPQL 및 JPA 잠금으로 전환 |

공유 Product·SellerProfile·Review·ProductModerationLog Entity는 기존 테이블에 매핑하기 위해 추가한다. 상품/판매자/후기 기능 전체의 신규 API를 구현한 것은 아니다. 기존 Entity에는 조회용 getter를 보강하며 공개 setter를 추가하지 않는다. DB 스키마·Flyway·API 필드·업무 정책은 변경하지 않는다.

## 지갑 최초 생성의 예외적 ORM 기능

없는 지갑을 먼저 잠그면 서로 다른 회원 초기화에서도 간격 잠금 교착이 생길 수 있으므로 insert-first 처리를 유지한다. Hibernate HQL의 `insert ... on conflict do nothing`을 사용하며 이는 네이티브 SQL 쿼리가 아니다. 표준 JPQL에 없는 Hibernate 확장임을 명시한다. [공식 HQL 문서](https://docs.hibernate.org/stable/core/querylanguage/html_single/)를 따른다.

MySQL이 중복 행의 no-op도 1건으로 보고하면 신규 생성과 혼동하므로 공통 Hikari의 `useAffectedRows=true`를 유지한다. 신규 1건·중복 0건 반환을 전제로 기존 지급 원장·소유자를 검증하고, 불완전 상태를 자동 지급으로 복구하지 않는다. 이 설정 변경 또는 DB 교체 시 초기화 반복/동시성과 반환 건수를 함께 재검증해야 한다.

## 검증

지갑 초기 지급 20건을 우선 통과했다. 동시 동일/다른 회원, 기존 낮은 잔액 보존, 불완전 원장 거절, 오래된 REPEATABLE_READ 스냅샷을 포함한다. 전체 회귀 검사는 관리자 검색/권한/버전/롤백, 주문 생성/조회/취소/납품/수정, 금액 처리·UTC 원문·동시성을 재실행한다.

ProductionPersistenceBoundaryTests는 운영 소스의 직접 Spring JDBC import·nativeQuery=true·createNativeQuery·DriverManager 연결 재도입을 차단한다. 테스트 SQL과 마이그레이션은 검사 대상에서 제외한다. 최종 실행 결과는 아래에 기록한다.

최종 격리 Docker MySQL 8.4 clean build --offline **490건**, 실패·오류·스킵 0으로 통과했다. 최초 전환 검사에서 무조건 flush하던 직접 상세 조회와 지갑 중복 삽입 반환 건수 차이를 발견했고, 트랜잭션 참여 시 flush 및 실제 변경 건수 설정으로 보완 후 전체를 재실행했다. 초기 지급 20건도 별도로 통과했다. 임시 컨테이너·볼륨은 정리했으며 개인 DB·키는 사용하지 않았다. API·프런트 소스 변경이 없어 프런트 재빌드는 생략했다. 원격 CI·실제 브라우저 성공을 대신하지 않는다.
