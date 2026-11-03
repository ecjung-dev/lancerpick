# 랜서픽 MVP 물리 DB 설계

구현 대조 2026-11-03: 실행 원본은 Flyway V1·V2이며 누적 17개 테이블이다. 본문의 컬럼 사전·이미지·정적 검사 기준은 V1 기본 16개를 설명한다. V1/V2·현재 Entity validate를 포함한 MySQL 8.4 검증 결과는 [현황표](current-status.md)를 따른다. 과거 작성일의 매핑 미검증 설명과 구분하며, 이미 적용한 V1을 수정하지 않는다.

2026-11-02 V2 확장: 사용자 요청으로 [토스 충전 신청 저장 구조](wallet-topup-schema.md)와 새 Flyway V2·JPA 매핑을 추가한다. 아래 컬럼 사전·ERD 이미지·정적 검사 결과는 V1 MVP 기본 구조이며 V2 누적 구조와 구분한다. 기존 V1은 변경하지 않는다.

작성일: 2026-10-29. 기존 docs의 Markdown 문서 13개를 검토해 작성한 MySQL 8.4 기준 설계안이다. 기존 업무 정책과 이번에 선택한 물리 설계 기준을 구분한다. 팀 리뷰·실제 MySQL 적용·JPA 매핑 검증 완료를 뜻하지 않는다.

2026-10-30 계약 보완: 사용자 요청으로 아래 입력 길이·정규화·상품 생성 기본값을 [API 명세](api-spec.md)의 구현 기준으로 채택했다. 이전 분리 시점에 미확정이었던 제안과 구분하며, 실제 테이블 생성·팀원 리뷰·매핑 검증 완료를 뜻하지 않는다. 자료형·인덱스·그 밖의 설계 선택은 기존 검토 상태를 유지한다.

후속 검토 반영: 조건 원문 7개 컬럼을 order_snapshots로 분리하여 총 16개 테이블·120개 컬럼·23개 FK로 갱신했다. 가격·소요일·진행 상태는 orders에 유지한다. 문자열 길이·초기값 등의 기존 제안은 이번 분리로 새롭게 업무 정책으로 확정하지 않는다.

## 1. 결과물과 적용 범위

- [ERD PNG 원본](images/lancerpick-physical-erd.png) · [확대용 SVG](images/lancerpick-physical-erd.svg): DDL에서 생성한 16개 테이블·120개 컬럼·23개 FK 관계도. PK·FK·복합 UNIQUE 묶음·NULL 여부를 표시한다.

- [DDL 원본](../LancerPick/src/main/resources/db/migration/V1__init.sql): 기존 논리 ERD의 16개 테이블, 자료형·NULL·기본값·PK·FK·UNIQUE·CHECK·인덱스.
- [읽기 전용 스키마 확인 SQL](sql/inspect-physical-schema.sql): 별도 테스트 DB에 적용한 뒤 구조를 확인할 때 사용.
- [정적 검사 도구](scripts/check_physical_schema.py): 테이블·컬럼·FK·인덱스·안전한 파일 구성을 점검한다. MySQL 실행 검증을 대신하지 않는다.

2026-11-02 최초 적용 준비: DDL을 `LancerPick/src/main/resources/db/migration/V1__init.sql`로 이동했다. docs에는 중복 SQL을 두지 않고 검사·ERD 생성 도구도 이 파일을 읽는다. 이후 MySQL 8.0.46 테스트 DB의 V1 적용·전체 빌드 성공을 사용자 실행 결과로 확인했다. [검증 상세](#2026-11-02-사용자-실행-검증-결과)를 참고하고 후속 8.4.13 초기 적용 결과와 엔티티 매핑·업무 검증은 구분한다. 일반 실행·DB 통합 테스트에서 마이그레이션을 시도하므로 연결 대상과 기존 이력을 먼저 확인한다. 엔티티·서비스·업무 API는 이번 이동에 포함하지 않는다.

V1은 기본 구조를 유지하며 채팅·알림·소셜 로그인·외부 충전·파일·출금 테이블을 포함하지 않는다. 외부 충전의 후속 추가는 위 V2 확장 문서를 따른다. 카테고리 실제 목록과 관리자·시연 회원 데이터는 임의로 만들지 않는다.

## 2. 문서에서 유지한 정책

| 근거 | 반영 내용 |
| --- | --- |
| [MVP](mvp-spec.md) | 가격 1~100만원, 소요일 1~30일, 초기 지급 100만원, 수정 1회, 전액 환불, 구매 확정 시 정산, 수수료 10% 버림 |
| [ERD](erd.md) | 16개 테이블·기존 컬럼 이름·관계, 주문 스냅샷·이력 보존, 원장·정산 분리 |
| [API](api-spec.md) | 문자열 ID, 주문·취소 요청 키, 상품 version, 납품 1·2회차, 별점 1~5, 선택 값 NULL |
| [화면 흐름](screen-flow.md) | 현재 상품과 주문 조건 구분, 취소 대기 행동 제한, 최초·수정 납기 분리 |
| [개발 규칙](development-rules.md) | 단일 트랜잭션, 주문→지갑 잠금, Flyway, UTC, MySQL 기반 검증 |
| [확장 계획](expansion-plan.md) | 현재 payments는 주문 결제 전용, 미사용 고도화 구조 선제 도입 금지 |
| [Git 협업](git-workflow.md) | PR·교차 검토·CI 후 병합, 공유 적용된 Flyway 파일 수정 금지 |
| [환경 구축](setup-guide.md)·[테스트 DB](test-database.md) | 개인 DB와 테스트 DB 구분, ddl-auto=validate, MySQL 8.4 기준 |
| [문서 안내](README.md)·[구조 검토](project-review.md)·[변경 기록](review-changes.md)·[작업표](work-plan.md) | 과거 설계·검토 기록과 현재 상태 구분, 역할별 공동 검토·미확정 사항 유지 |

기존에 확정되지 않았던 문자열 길이·자료형·인덱스·초기 기본값은 아래의 새 설계 선택이다. 이전부터 합의된 원문 정책이라고 표현하지 않는다.

## 3. 이번 설계 선택

### 공통 저장 기준

| 항목 | 선택 | 이유·매핑 |
| --- | --- | --- |
| 엔진 | InnoDB | 트랜잭션·FK·행 잠금 |
| 문자셋 | utf8mb4 | 한글·이모지 저장 |
| 일반 문자열 collation | utf8mb4_0900_ai_ci | 상품 검색 등의 대소문자·악센트 비구분 비교, 원문 저장값은 보존 |
| 이메일 | VARCHAR(254), utf8mb4_0900_as_cs | 서버 정규화 후 동일 문자열 고유성 보장, 악센트 차이를 임의로 합치지 않음 |
| ID | signed BIGINT, 독립 PK는 AUTO_INCREMENT | Java Long, JSON에서는 문자열. seller_profiles·wallets는 회원 ID 공유 PK, order_snapshots는 주문 ID 공유 PK |
| 금액·잔액 | signed BIGINT | Java Long, 결제 변동액 음수 허용, 중간 계산도 long 이상 |
| 기간·순번·별점·bps | INT | Java Integer로 일관되게 매핑 |
| 숨김 여부 | BOOLEAN | MySQL TINYINT(1), Java Boolean, 0·1 CHECK |
| 상태·행동 코드 | VARCHAR + CHECK, utf8mb4_0900_as_cs | 대소문자·후행 공백을 구분. JPA 순번 저장·MySQL ENUM 사용 안 함 |
| UUID 요청 키 | CHAR(36), ascii_bin | UUID 파싱 후 소문자 하이픈 표준형으로 저장·비교 |
| 원장 이벤트 키 | VARCHAR(100), ascii_bin | 서버가 규칙에 따라 생성, 사용자 입력으로 받지 않음 |
| 시각 | DATETIME(6) | UTC 규약, 마이크로초 정밀도, nullable 사건 시각 구분 |
| 긴 설명·조건 | TEXT + CHAR_LENGTH CHECK | utf8mb4 VARCHAR 여러 개로 인한 행 크기 부담을 줄이고 최대 문자 수 제한 |

DB 시각 자동 기본값·ON UPDATE CURRENT_TIMESTAMP·트리거는 사용하지 않는다. 서비스의 같은 UTC Clock에서 사건 시각을 정하고 마이크로초로 절삭한 값을 상태·이력·결제에 공유한다. JPA는 UTC LocalDateTime으로 매핑하고 API 경계에서 UTC OffsetDateTime/ISO 8601로 변환한다. DATETIME은 시간대를 저장하거나 자동 변환하지 않으므로 이 규약과 JDBC UTC 설정을 함께 유지한다. 납기는 절삭한 paid_at/requested_at에서 `Duration.ofDays`로 계산한다.

DB CHECK의 NULL 처리에 기대 필수값이 빠지지 않도록 필수 컬럼은 NOT NULL로 선언했다. 사건 시각은 상태별 NULL·NOT NULL 조합을 별도로 검사한다. FK에 연쇄 변경 동작을 지정하지 않아 기본 제한 동작으로 이력의 연쇄 삭제를 막는다. 거래 기록 자체의 직접 UPDATE/DELETE 금지는 애플리케이션·운영 권한 정책도 필요하다.

JPA 구현에서는 EnumType.STRING만 지정하고 DB가 VARCHAR일 것이라고 가정하지 않는다. Hibernate의 MySQL enum 매핑과 구분하기 위해 `@Enumerated(EnumType.STRING)`과 `@JdbcTypeCode(SqlTypes.VARCHAR)` 및 해당 `@Column(length=...)`를 함께 명시하고 validate로 확인한다. TEXT는 명시적인 TEXT 컬럼 매핑을 사용한다. 요청 키는 String CHAR(36) 매핑으로 두고 서버에서 UUID를 파싱·정규화한다. 공유 PK인 seller_profiles·wallets·order_snapshots에는 별도 IDENTITY를 붙이지 않는다. 실제 엔티티는 이 작업에서 생성하지 않았다.

### 입력 길이와 정규화

아래 길이는 2026-10-30 API 구현 기준으로 채택했다. 엔티티·DTO·화면이 만들어질 때 동일하게 반영한다. 숫자는 코드 포인트 수 상한이며 이전부터 합의된 정책으로 소급하지 않는다. 필수값·공백·생략·null·비밀번호 예외의 최신 계약은 [API 명세 10절](api-spec.md#10-오류와-입력-기준)을 따른다.

| 입력 | 필수·길이 |
| --- | --- |
| 이메일 | 필수, 3~254. 서버에서 앞뒤 공백 제거·Locale.ROOT 소문자화·이메일 형식 검증 |
| 표시 이름 | 필수 1~30, 중복 허용 |
| 판매자 소개 / 전문 분야 | 필수 1~2000 / 1~100 |
| 카테고리명 / 상품명 | 필수 1~50 / 1~100 |
| 상품 설명 / 제공 범위 / 준비사항 | 필수 1~10000 / 1~3000 / 1~3000. 준비사항이 없다면 설명 문구로 표현 |
| 요구사항 / 납품 설명 | 필수 1~5000 / 1~5000 |
| 수정·취소·거절·운영 사유 | 1~1000. 업무상 필수 사유는 필수, history.reason·응답 사유는 선택 |
| 후기 글 | 선택, 입력 시 1~2000 |
| 포트폴리오 URL | 선택, 입력 시 1~2048, HTTPS로 통일하는 신규 기준안 |
| 결과물 URL | 필수 1~2048, 기존 HTTPS 정책 유지 |
| 거래 규칙 원문 | 서버 생성, 필수 1~10000. 게시한 규칙 전체가 범위에 드는지 확인 |

일반 사용자 문자열은 앞뒤 공백을 제거하고 본문 줄바꿈은 LF로 통일한다. 선택 문자열의 공백·빈 문자열은 NULL로 정규화한다. 본문 내부 공백·줄바꿈은 유지한다. 비밀번호 원문은 trim·소문자화하지 않으며 길이 정책은 인코더 선정과 함께 별도 확정한다. hash의 255는 인코딩 결과 저장 한도이지 비밀번호 원문 길이가 아니다.

길이 비교는 Unicode 코드 포인트 수로 통일한다. MySQL CHAR_LENGTH, Java codePointCount, 프런트 `[...value].length`를 기준으로 한다. Java String.length·기본 @Size와 HTML maxlength만으로는 이모지를 같은 기준으로 셀 수 없으므로 서버 검증을 별도로 구현한다. 결합 문자·이모지 한 묶음이 사용자 눈의 한 글자와 다를 수 있음을 UI 안내에 반영한다. TEXT는 utf8mb4 기준 최대 10000 코드 포인트를 수용할 수 있다.

UUID와 사용자 입력의 비교는 정규화된 문자열 기준이며, 주문 스냅샷도 같은 길이·정규화 정책을 따른다. URL은 서버 URI 파서로 https 스킴·호스트 존재·userinfo 부재를 검증하고 서버에서 접속하지 않는다. DB는 URL 길이만 검사한다.

### 기본값

가입 role은 USER, 상품 sale_status는 ON_SALE·is_hidden은 false·version은 0, 주문은 REQUESTED, 취소 요청은 PENDING이다. 상품 즉시 판매 시작은 이번 설계 선택이며 초안/심사 상태를 추가하지 않는다. 가입 원자 트랜잭션은 지갑을 0으로 생성한 뒤 100만원 지급과 INITIAL_GRANT 내역을 함께 반영한다. 지갑 기본값 0은 초기 지급을 생략해도 된다는 뜻이 아니다.

결제 시 서버가 1000bps를 고정한다. DB는 장래 과거 비율 보존을 위해 0~10000bps 범위만 보장하고 임의의 수수료율을 클라이언트에서 받지 않는다. 정산은 payments의 고정 비율을 복사한다.

## 4. 핵심 제약과 서비스의 책임

orders는 REQUESTED·REJECTED에서 수락·결제 시각을 허용하지 않으며, AWAITING_PAYMENT에서는 수락 시각을 필수로 하고 결제 시각은 허용하지 않는다. 결제 후 진행·검수·수정·완료 상태에는 수락·조건 확인·결제·납기를 필수로 한다. CANCELLED는 수락 전·수락 후 미결제·결제 후 취소를 모두 허용하고 기존 수락·결제 기록은 보존한다.

수락≥생성, 조건 확인≥수락, 결제≥조건 확인, 최초 납기>결제, 완료≥결제, 취소≥이미 발생한 수락·결제 시각을 검사한다. updated_at도 이미 기록된 사건 시각보다 빠를 수 없다. 최초 납기의 정확한 소요일 덧셈과 다른 테이블의 사건 연결은 서비스에서 검증한다.

TEXT 길이는 `CHAR_LENGTH(원문) BETWEEN 1 AND 상한`으로 실제 저장 길이를 제한하고 `CHAR_LENGTH(TRIM(원문)) > 0`으로 공백뿐인 값을 별도 거절한다. 선택인 reviews.content는 NULL을 계속 허용한다. 서버의 trim·줄바꿈 정규화 정책과 별개로 뒤 공백을 붙여 DB 길이 검사를 우회하지 못하게 한다.

| 정책 | DB 보장 | 서비스·트랜잭션에서 보장 |
| --- | --- | --- |
| 회원 이메일 중복 | 정규화 문자열 UNIQUE·NOT NULL | 이메일 형식·정규화·동시 중복 오류의 409 변환 |
| 주문 중복 | (buyer_id, request_key) UNIQUE | 키 조회 우선·원입력 비교·다른 입력 409 |
| 취소 요청 중복 | (order_id, requested_by, request_key) UNIQUE | 주문 잠금 아래 PENDING 최대 1건, 같은 키 기존 결과 반환 |
| 납품 2회차 제한 | (order_id, sequence_no) UNIQUE, 순번 1·2 | 요청 회차와 현재 상태·판매자·대기 취소 검사 |
| 수정 1회 | order_id UNIQUE, delivery_id UNIQUE | 대상 납품이 같은 주문의 최초 납품인지 검증 |
| 결제·정산·후기 | 각각 order_id UNIQUE | 결제 자격·완료 상태·작성자 권한·원결제 일치 |
| 적립금 중복 | event_key UNIQUE, (payment_id,type) UNIQUE | 회원별 고정 초기 지급 키, 지갑 잠금, 원장합·잔액 일치 |
| 테스트 지급·결제·환불 | 원장 유형별 부호·범위·참조 NULL 규칙 | 주문 구매자와 지갑 소유자 일치·전액 환불·결제액 일치 |
| 정산 금액 | fee=floor(gross*bps/10000), seller+fee=gross | 원결제·완료 주문·정산 중복과 원자성 |
| 주문·스냅샷 | orders에 가격·기간 범위, order_snapshots에 공유 PK·FK·원문 필수값 | 주문·스냅샷·최초 이력 원자 생성, 상품 잠금·버전·판매자 일치·원문 불변 |
| 취소·완료 배타성 | 상태별 종료 시각·허용 상태 CHECK | 주문→지갑 잠금과 상태 재검사, 환불·정산 동시 성립 방지 |
| 숨김·판매 중지 분리 | 두 컬럼·허용값·version | 관리자 권한·동일 상태 무이력·판매자 변경과 버전 충돌 |

PENDING 최대 한 건을 위해 `UNIQUE(order_id,status)`를 만들지 않는다. 이 제약은 과거 REJECTED·WITHDRAWN 이력의 반복도 막기 때문이다. 원래 설계대로 주문 잠금 아래 검사한다. 이를 위한 생성 컬럼·추가 테이블은 도입하지 않는다.

FK는 각 참조 대상의 존재만 보장한다. products.seller_id는 기존 ERD처럼 members를 참조하고 판매자 프로필 보유는 서비스에서 확인한다. orders.seller_id와 원상품 판매자 일치, 수정 대상 납품의 동일 주문 여부, 요청·이력 actor 권한, 관리자 role은 같은 트랜잭션에서 확인한다. 단순 FK만으로 이 관계가 보장된다고 표현하지 않는다.

원장 키는 `INITIAL_GRANT:{memberId}`, `PAYMENT:{paymentId}`, `REFUND:{paymentId}`로 고정한다. (payment_id,type) UNIQUE에서 NULL끼리는 중복 제한이 되지 않으므로 초기 지급 1회는 고정 event_key 규칙과 회원·지갑·원장 원자 생성에 의존한다. 조회용 orderId는 wallet_entries.payment_id → payments.order_id로 가져온다. 정산·후기의 판매자·상품·작성자도 orders를 통해 조회하며 중복 컬럼을 추가하지 않는다.

## 5. 상태와 이력 코드

주문·결제·취소 요청 상태는 기존 API 명세의 문자열을 그대로 사용한다. 주문 이력 action의 실제 영문 코드는 이번 물리 설계에서 선택했다.

| action | 의미 |
| --- | --- |
| CREATED | 주문 신청, from_status=NULL·to_status=REQUESTED |
| ACCEPTED / REJECTED | 판매자 수락 / 거절 |
| PAID | 테스트 적립금 결제 |
| DELIVERED | 최초·수정 납품, 회차는 deliveries에서 확인 |
| REVISION_REQUESTED | 수정 요청 |
| CANCELLATION_REQUESTED | 취소 요청, 주문 상태 유지 |
| CANCELLATION_APPROVED / CANCELLATION_REJECTED / CANCELLATION_WITHDRAWN | 취소 요청 승인 / 거절 / 철회 |
| CANCELLED | 주문 직접 취소 또는 승인 후 취소 확정 |
| COMPLETED | 구매 확정·정산 |

취소 요청 해결과 주문 취소가 함께 일어나면 두 사건 이력을 같은 트랜잭션에 남긴다. 현재 상태가 유지되는 사건은 from_status=to_status다. 동일 요청 재호출에는 새 사건을 만들지 않는다. 이력 action은 API의 allowedActions 명령 코드와 다른 개념이다. 예를 들어 명령 ACCEPT의 완료 이력은 ACCEPTED다.

## 6. 조회와 인덱스

| 조회 | 우선 사용할 인덱스·조인 |
| --- | --- |
| 공개 상품 최신순 | products(is_hidden,sale_status,created_at DESC,id DESC) |
| 카테고리별 공개 상품 | products(category_id,is_hidden,sale_status,created_at DESC,id DESC) |
| 내 상품 | products(seller_id,created_at DESC,id DESC) |
| 구매/판매 주문 최신순 | orders(buyer_id 또는 seller_id,created_at DESC,id DESC), status는 우선 잔여 필터 |
| 상품별 주문·후기 | orders(product_id,created_at DESC,id DESC) → reviews의 order_id UNIQUE 조인 |
| 주문 상품명·조건 조회 | orders → order_snapshots(order_id) 공유 PK 조인. 상품명은 스냅샷에서 조회 |
| 주문 이력 | order_histories(order_id,created_at DESC,id DESC) |
| 납품·수정 | deliveries(order_id,sequence_no)·revision_requests(order_id) UNIQUE |
| 대기 취소 요청 | cancellation_requests(order_id,status,requested_at DESC,id DESC) |
| 지갑 내역 | wallet_entries(member_id,created_at DESC,id DESC) |
| 판매 수익 | orders(seller_id,...) → settlements(order_id) UNIQUE, 정산 생성순 정렬은 실행 계획 확인 |
| 관리자 운영 이력 | product_moderation_logs(product_id,created_at DESC,id DESC) |

FK에 필요한 인덱스도 DDL에서 명시했다. 상품 키워드는 제목·설명 LIKE 검색으로 시작하며 B-tree가 `%키워드%`를 해결한다고 가정하지 않는다. FULLTEXT·캐시·중복 집계 컬럼은 도입하지 않는다. 공개 후기 생성순, 판매자 정산 생성순, 관리자 복합 필터에는 조인·정렬 비용이 남으므로 실제 데이터의 EXPLAIN ANALYZE로 확인한다. 위 인덱스는 API 조회 형태에 따른 초기 설계이며 성능 측정 완료를 뜻하지 않는다.

가격·정산·잔액 API는 원 단위 정수를 유지한다. 집계 SUM은 MySQL에서 넓은 정밀도로 계산하고 Java에서 정확 변환한다. JSON 숫자 응답은 JavaScript 안전 정수 범위인지 확인하며 그 범위를 넘는 운영 규모를 지원하려면 별도 API 금액 표현 계약을 먼저 정한다.

## 7. 적용과 검증

1. 팀원이 보완된 API 계약과 이력 코드·JPA 매핑을 리뷰한다. 상품 PATCH 상세는 API 3절을 따르고, 실제 카테고리 목록·비밀번호 원문 정책은 별도로 결정한다.
2. 실제 MySQL에서 VERSION(), sql_mode, @@session.time_zone을 확인한다. Workbench 연결명 MySQL80만으로 실제 서버 버전을 단정하지 않는다. 기준은 MySQL 8.4이며 8.0을 쓰면 최소 8.0.16 이상의 CHECK 집행과 차이를 따로 검증한다.
3. 기존 업무 테이블이 없는 별도 테스트 스키마에 먼저 적용한다. DDL을 수동 실행하는 것과 Flyway 이력 등록은 다르다. 같은 DB에 수동 생성 후 V1을 다시 적용하거나 baseline/repair로 우회하지 않는다.
4. V1은 실행용 경로에 있고 로컬 8.0.46 테스트 DB의 적용 결과는 아래 기록으로 확인했다. 다른 환경에서는 빈 격리 테스트 DB와 환경변수를 확인한 뒤 Flyway로 최초 적용하고 실제 결과를 검증한다. 같은 DB에 수동 생성 후 다시 V1을 실행하지 않는다. docs에 복사본을 유지하지 않으며, 기존 업무 데이터나 적용 이력이 있으면 별도 마이그레이션 계획을 세운다.
5. 실제 Flyway 적용 후 16개 테이블과 flyway_schema_history를 확인한다. 현재 V1은 스키마 기반으로 먼저 적용했으며 업무 엔티티는 후속 기능 PR에서 매핑하고 Hibernate validate로 확인한다. 이후 스키마 변경이 필요한 기능은 해당 마이그레이션과 엔티티를 함께 검토한다. 엔티티가 없을 때 시작 테스트만 성공한 것은 16개 매핑 검증이 아니다.
6. 아래 실패·동시성 시나리오를 검증하고 PR에 실제 결과를 기록한다. 공유 적용된 V1은 이후 수정하지 않고 V2 이상을 추가한다.

| 시나리오 | 기대 결과 |
| --- | --- |
| 중복 이메일·동일 주문 키·동일 취소 키 | UNIQUE 거부, 서비스는 계약에 맞는 재조회·충돌 처리 |
| 0원·100만원 초과·기간 0/31·별점 0/6 | CHECK 거부 |
| 없는 FK·다른 주문 납품에 대한 수정 요청 | FK 또는 서비스 관계 검증 거부 |
| 같은 회차 납품·두 번째 수정·두 번째 후기·정산 | UNIQUE 또는 상태 검사 거부 |
| 잘못된 상태·음수 잔액·유형별 잘못된 원장 부호 | CHECK 거부 |
| 완료/취소 상태와 종료 시각 불일치 | CHECK 거부 |
| 해결된 취소 요청에 해결자·시각 누락 | CHECK 거부 |
| 정산 합계·버림 계산 불일치 | CHECK 거부 |
| 취소 요청 해결 후 같은 키 재전송·환불 후 재결제 | 기존 해결 결과 또는 정해진 409, 재반영 없음 |
| 최초 납품 지연 재전송·수정 후 1회차 재요청 | 2회차 미생성, 기존 데이터 불변 |
| 동시 지갑 사용·취소와 확정·취소 요청과 납품 | 주문→지갑 잠금 아래 유효한 전환만 반영 |
| 이모지·한글의 길이 경계·NULL·빈 문자열 | DTO·화면·DB 문자 수와 정규화 일치 |
| 결제·환불·정산 처리 중 실패 | 상태·금액·이력 전체 롤백 |

### 검증 상태

후속 재검토: 16개 테이블·120개 컬럼·23개 FK·74개 CHECK의 정적 검사를 통과했고, 컬럼 사전과 PNG·SVG를 SQL에서 다시 생성했다. SQL에 있는 orders·TEXT CHECK 식을 추출해 [조건식 논리 검사](scripts/test_schema_checks.py) 4개를 실행했다. 정상 상태·취소 3경로, 모순된 상태, 역전된 시각, TEXT 11개 컬럼의 한글·이모지·길이 경계·뒤 공백 우회를 확인했다.

이 논리 검사는 메모리 SQLite의 SQL 3값 논리로 문자열 비교와 길이 식만 평가한다. MySQL 테이블 생성·자료형·collation·FK·CHECK 집행·Flyway·동시성 검증이 아니며 이를 대체하지 않는다. `python docs/scripts/check_physical_schema.py`와 `python docs/scripts/test_schema_checks.py`로 실행한다.

아래는 최초 정적 검토 당시 기록이며, 2026-11-02 후속 DB 결과는 다음 절에 별도로 기록한다. 설계·정적 구조 검사와 실제 DB 실행 검증은 구분한다. 당시 작업에서는 개인 접속 파일·비밀번호를 읽지 않았고 실제 DB에 DDL을 실행하지 않았다. 로컬 MySQL 클라이언트와 Docker 명령은 사용할 수 없었으며, 파서 검사는 MySQL 실행 성공으로 표현하지 않는다. 앞서 사용자가 보고한 기존 clean build 성공은 이번 신규 스키마 적용 성공과 다르다.

### 공식 근거

CHECK의 허용 표현·NULL·다른 테이블 참조 제한은 [MySQL CHECK 문서](https://dev.mysql.com/doc/refman/8.4/en/create-table-check-constraints.html), FK의 키·제한 동작은 [MySQL FK 문서](https://dev.mysql.com/doc/refman/8.4/en/create-table-foreign-keys.html), DATETIME 정밀도·시간대 특성은 [MySQL 시각 문서](https://dev.mysql.com/doc/refman/8.4/en/datetime.html)를 확인했다. TEXT·VARCHAR 행 크기 선택은 [InnoDB 제한](https://dev.mysql.com/doc/refman/8.4/en/innodb-limits.html)을 참고했다.

### 2026-11-02 최초 적용 준비 기록

실행용 경로 이동은 완료했으며 SQL 본문의 테이블·제약은 변경하지 않았다. 사용자 Workbench 확인 기준 로컬 서버는 MySQL 8.0.46으로 문서·CI 기준 8.4와 다르다. lancerpick_test에는 업무 테이블이 없고 flyway_schema_history의 적용 이력은 비어 있었다. Workbench 세션 시간대를 +00:00으로 설정하고 대상 DB를 lancerpick_test로 선택했다. 이는 사용자 화면·응답으로 확인한 사전 점검이며 실제 Flyway 적용 성공은 아니다. Workbench의 USE·세션 설정은 Spring JDBC 연결에 자동 전달되지 않으므로 TEST_DB_URL·전용 계정과 UTC 옵션을 별도로 확인한다. 8.0.46 실행 결과와 8.4 검증 결과는 구분해서 기록한다.

### 2026-11-02 사용자 실행 검증 결과

검증 실행자는 사용자(정은찬)이며, 아래 결과는 이 대화의 Workbench 화면·오류 출력·빌드 성공 회신을 근거로 한다. 에이전트가 해당 MySQL에 직접 접속한 결과가 아니다. 서버는 MySQL 8.0.46, 대상은 전용 lancerpick_test이며 문서·CI 기준 MySQL 8.4와 구분한다.

| 항목 | 실행·확인 | 결과 |
| --- | --- | --- |
| 최초 적용 | LancerPickApplicationTests 선택 실행으로 Flyway 적용 후 이력 조회 | version=1, description=init, success=1 |
| 생성 개수 | information_schema 조회 | 업무 테이블 16, FK 23, CHECK 74 |
| CHECK 대표 사례 | categories의 display_order=-1 삽입 | 오류 3819, ck_categories_order 위반 |
| FK 대표 사례 | members.id=-1 없음 확인 후 해당 member_id로 wallets 삽입 | 오류 1452, fk_wallets_member 위반 |
| UNIQUE 대표 사례 | 같은 categories.name 두 번 삽입 | 두 번째 요청 오류 1062, uk_categories_name 위반 |
| 초기 롤백 시도 | 중복 검사 후 ROLLBACK·조회 | 검증용 행이 남아 실패 원인은 확정하지 않음; 해당 ID·이름 조건으로 검증용 행 삭제 후 정리 확인 |
| 독립 롤백 재검증 | 같은 연결에서 START TRANSACTION → 정상 INSERT → ROLLBACK → 조회를 한 단계씩 실행 | 명령 성공과 사용자 회신상 검증용 행 없음 확인 |
| 전체 빌드 | TEST_DB_* 설정한 터미널에서 gradlew.bat clean build | 사용자 BUILD SUCCESSFUL 회신 |
| 자격 정보 정리 | TEST_DB_PASSWORD 제거·자격 정보 변수 초기화 | 사용자 완료 회신; 비밀번호는 기록하지 않음 |

CHECK·FK·UNIQUE는 위 대표 사례만 동작 검증했으며 모든 제약·인덱스 정의를 전수 검증한 것은 아니다. ROLLBACK의 0 rows affected 자체가 성공 증거는 아니며 마지막 SELECT의 행 부재 확인을 근거로 한다. AUTO_INCREMENT 번호는 실패·롤백 뒤 연속적이지 않을 수 있으므로 데이터 정리를 위해 ID를 초기화하지 않는다.

위 빌드는 업무 엔티티 추가 전 실행이므로 16개 테이블의 Hibernate 매핑 검증으로 보지 않는다. 이후 8.4.13 초기 적용 결과는 다음 절에 기록하며, 원격 CI·다른 팀원 재현·전체 제약 경계·주문/결제/환불 동시성·업무 롤백은 남아 있다. V1 상단의 execution validation pending 주석은 적용 준비 시점에 작성된 기록이며 현재 검증 상태는 이 절을 따른다. 이미 로컬에 적용한 V1 파일은 체크섬 보존을 위해 이후 주석까지 포함해 수정하지 않고, 필요한 변경은 후속 마이그레이션으로 처리한다.

### 2026-11-02 MySQL 8.4 전환 후 사용자 실행 결과

기존 8.0 서버와 별도로 설치한 MySQL 8.4.13, 포트 3307에서 사용자가 실행한 결과다. 비밀번호·개인 접속 파일은 기록하거나 읽지 않았다.

| 대상 | 사용자 확인 결과 |
| --- | --- |
| 서버 | SELECT VERSION(), @@port 결과 8.4.13 / 3307 |
| 테스트 DB lancerpick_test | LancerPickApplicationTests 성공, Flyway version=1 / init / success=1, 업무 테이블 16개 |
| 전체 빌드 | TEST_DB_*로 새 서버를 지정한 상태에서 clean build 성공 |
| 개발 DB lancerpick | DB_*로 새 서버를 지정한 bootRun에서 Started LancerPickApplication·8080 시작, Flyway version=1 / init / success=1 |

이 실행은 새 Order 엔티티 작성 전 결과다. 이후 추가한 엔티티의 매핑·저장 성공으로 해석하지 않는다. 8.0에서 확인한 FK 23·CHECK 74 및 대표 제약 검사를 8.4에서 다시 수행했다고 기록하지 않는다. 개발 DB의 테이블 개수는 별도로 회신받지 않았으며, 다른 팀원 PC·원격 CI도 이번 결과 범위에 포함하지 않는다. V1 SQL은 변경하지 않았다.

### 2026-11-02 주문 엔티티 저장 후속 검증

에이전트가 개인 설정·기존 개발/테스트 서버와 분리한 임시 MySQL 8.4.13(127.0.0.1:13317, lancerpick_ci)에서 기존 V1·V2를 적용했다. Order·OrderSnapshot·OrderHistory를 포함한 현재 엔티티의 시작 시 스키마 검증과 전체 clean build 119개 테스트가 통과했다. 업무 테이블 전체의 엔티티 구현을 뜻하지 않는다.

주문 저장 통합 테스트 6개로 저장 후 별도 트랜잭션 재조회, 상품 변경 후 주문 조건 보존, 스냅샷/이력 FK 실패 시 선행 저장 롤백, 구매자별 요청 키 UNIQUE, 스냅샷 공유 PK 중복 거절을 확인했다. 상세 범위와 제한은 [작업표](work-plan.md)의 주문 저장 Repository·MySQL 통합 검증 절을 따른다. 저장은 테스트에서 트랜잭션으로 조합했으며 실제 주문 서비스·보호 API·멱등 응답·동시성·전원 재현·원격 CI는 검증하지 않았다. V1·V2 SQL과 ERD는 수정하지 않았다.

### 2026-11-02 주문 생성 서비스·UTC 원문 후속 검증

[주문 생성 내부 서비스](order-creation.md)가 주문·스냅샷·최초 이력의 단일 트랜잭션 저장과 키 비교·동시 생성을 구현했다. 격리 MySQL 8.4.13의 기존 V1/V2에서 신규 통합 테스트 16개와 전체 clean build 135개가 통과했다. 실패 롤백은 실제 서비스에서 검증하며 이력 실패는 저장 경계에서 실제 FK 위반 SQL을 유도했다. 보호 API·실제 회원/상품 쓰기·결제 연동·원격 CI는 별도다.

기존 LocalDateTime 매핑의 저장/재조회 왕복만으로는 발견되지 않던 9시간 DATETIME 이동을 서울 JVM에서 재현했다. Order·OrderHistory 시각을 JDBC LOCAL_DATE_TIME 직접 매핑으로 보완하고 UTC Clock의 Instant를 변환한다. 서울·로스앤젤레스 주문 검사와 SQL DATE_FORMAT 원문 조회로 보존을 확인한다. 과거 생성 데이터의 시각 보정은 자동 실행하지 않는다. 기존 119개 결과는 당시 저장 경로 검사이며 UTC 원문 검증으로 확대하지 않는다. SQL·컬럼·ERD는 변경하지 않았다.

### 주문 생성 교차 구매 잠금 후속

구매자만 잠근 채 주문을 삽입하면 교차 구매에서 seller_id FK의 상대 회원 공유 잠금과 충돌할 수 있다. 격리 MySQL 두 연결의 SQL 재현에서 ERROR 1213을 확인했다. 참여 회원 ID를 작은 순서로 개별 잠금하고 상품 잠금 후 사전 판매자와 일치하는지 재검사하도록 보완했다. 실제 서비스 교차 구매 5회와 판매자 조회 변경 거절을 회귀 검사하며 상세 실행 결과는 [작업표](work-plan.md)를 따른다. 이 수정은 잠금 코드 변경으로 V1·V2 및 DB 정의는 그대로다.

## 8. 테이블별 컬럼 사전

아래는 DDL 원본에서 추출한 컬럼 정의다. CHECK·UNIQUE·FK·인덱스의 정확한 구문은 DDL 원본을 따른다. DDL 변경 시 컬럼 사전도 함께 갱신한다.

<!-- COLUMN_DICTIONARY -->

### members

| 컬럼 | 자료형 | NULL | 기본값·생성 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 불가 | AUTO_INCREMENT | 회원 ID |
| `email` | `VARCHAR(254)` | 불가 | 없음 | 정규화한 로그인 이메일 |
| `password_hash` | `VARCHAR(255)` | 불가 | 없음 | 인코더가 생성한 비밀번호 해시 |
| `display_name` | `VARCHAR(30)` | 불가 | 없음 | 표시 이름, 중복 허용 |
| `role` | `VARCHAR(10)` | 불가 | 'USER' | USER 또는 ADMIN |
| `created_at` | `DATETIME(6)` | 불가 | 없음 | 가입 시각 UTC |
| `updated_at` | `DATETIME(6)` | 불가 | 없음 | 수정 시각 UTC |

### seller_profiles

| 컬럼 | 자료형 | NULL | 기본값·생성 | 의미 |
| --- | --- | --- | --- | --- |
| `member_id` | `BIGINT` | 불가 | 없음 | 판매자 회원 ID, 공유 PK |
| `introduction` | `TEXT` | 불가 | 없음 | 판매자 소개, 최대 2000자 |
| `specialty` | `VARCHAR(100)` | 불가 | 없음 | 전문 분야 |
| `portfolio_url` | `VARCHAR(2048)` | 허용 | 없음 | 선택 포트폴리오 HTTPS URL |
| `created_at` | `DATETIME(6)` | 불가 | 없음 | 생성 시각 UTC |
| `updated_at` | `DATETIME(6)` | 불가 | 없음 | 수정 시각 UTC |

### categories

| 컬럼 | 자료형 | NULL | 기본값·생성 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 불가 | AUTO_INCREMENT | 카테고리 ID |
| `name` | `VARCHAR(50)` | 불가 | 없음 | 고정 분류명 |
| `display_order` | `INT` | 불가 | 0 | 화면 표시 순서 |

### products

| 컬럼 | 자료형 | NULL | 기본값·생성 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 불가 | AUTO_INCREMENT | 상품 ID |
| `seller_id` | `BIGINT` | 불가 | 없음 | 판매자 회원 ID |
| `category_id` | `BIGINT` | 불가 | 없음 | 카테고리 ID |
| `title` | `VARCHAR(100)` | 불가 | 없음 | 상품명 |
| `description` | `TEXT` | 불가 | 없음 | 상세 설명, 최대 10000자 |
| `scope_description` | `TEXT` | 불가 | 없음 | 제공 및 미제공 범위, 최대 3000자 |
| `preparation_description` | `TEXT` | 불가 | 없음 | 구매자 준비사항, 최대 3000자 |
| `price` | `BIGINT` | 불가 | 없음 | 현재 가격 원 |
| `duration_days` | `INT` | 불가 | 없음 | 소요일, 1일은 24시간 |
| `sale_status` | `VARCHAR(10)` | 불가 | 'ON_SALE' | 판매 상태 |
| `is_hidden` | `BOOLEAN` | 불가 | FALSE | 관리자 숨김 |
| `version` | `BIGINT` | 불가 | 0 | JPA 상품 버전 |
| `created_at` | `DATETIME(6)` | 불가 | 없음 | 생성 시각 UTC |
| `updated_at` | `DATETIME(6)` | 불가 | 없음 | 수정 시각 UTC |

### orders

| 컬럼 | 자료형 | NULL | 기본값·생성 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 불가 | AUTO_INCREMENT | 주문 ID |
| `buyer_id` | `BIGINT` | 불가 | 없음 | 구매자 회원 ID |
| `seller_id` | `BIGINT` | 불가 | 없음 | 주문 당시 판매자 회원 ID |
| `product_id` | `BIGINT` | 불가 | 없음 | 원상품 ID |
| `request_key` | `CHAR(36)` | 불가 | 없음 | 정규화 UUID 요청 키 |
| `status` | `VARCHAR(32)` | 불가 | 'REQUESTED' | 주문 상태 |
| `price_amount` | `BIGINT` | 불가 | 없음 | 주문 가격 원 |
| `duration_days_snapshot` | `INT` | 불가 | 없음 | 주문 소요일 |
| `accepted_at` | `DATETIME(6)` | 허용 | 없음 | 수락 시각 UTC |
| `buyer_confirmed_terms_at` | `DATETIME(6)` | 허용 | 없음 | 결제 전 조건 확인 시각 UTC |
| `paid_at` | `DATETIME(6)` | 허용 | 없음 | 결제 시각 UTC, 환불 후 보존 |
| `due_at` | `DATETIME(6)` | 허용 | 없음 | 최초 납기 UTC |
| `completed_at` | `DATETIME(6)` | 허용 | 없음 | 구매 확정 시각 UTC |
| `cancelled_at` | `DATETIME(6)` | 허용 | 없음 | 취소 시각 UTC |
| `created_at` | `DATETIME(6)` | 불가 | 없음 | 생성 시각 UTC |
| `updated_at` | `DATETIME(6)` | 불가 | 없음 | 수정 시각 UTC |

### order_snapshots

| 컬럼 | 자료형 | NULL | 기본값·생성 | 의미 |
| --- | --- | --- | --- | --- |
| `order_id` | `BIGINT` | 불가 | 없음 | 주문 ID, 공유 PK |
| `product_version_snapshot` | `BIGINT` | 불가 | 없음 | 확인한 상품 버전 |
| `product_title_snapshot` | `VARCHAR(100)` | 불가 | 없음 | 주문 당시 상품명 |
| `description_snapshot` | `TEXT` | 불가 | 없음 | 주문 당시 상세 설명 |
| `scope_snapshot` | `TEXT` | 불가 | 없음 | 주문 당시 제공 범위 |
| `preparation_snapshot` | `TEXT` | 불가 | 없음 | 주문 당시 준비사항 |
| `terms_snapshot` | `TEXT` | 불가 | 없음 | 주문 당시 거래 규칙 원문, 최대 10000자 |
| `requirements` | `TEXT` | 불가 | 없음 | 구매자 요구사항, 최대 5000자 |

### order_histories

| 컬럼 | 자료형 | NULL | 기본값·생성 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 불가 | AUTO_INCREMENT | 주문 이력 ID |
| `order_id` | `BIGINT` | 불가 | 없음 | 주문 ID |
| `actor_id` | `BIGINT` | 불가 | 없음 | 행동한 회원 ID |
| `action` | `VARCHAR(40)` | 불가 | 없음 | 업무 행동 코드 |
| `from_status` | `VARCHAR(32)` | 허용 | 없음 | 이전 주문 상태, 최초 생성은 NULL |
| `to_status` | `VARCHAR(32)` | 불가 | 없음 | 이후 주문 상태 |
| `reason` | `VARCHAR(1000)` | 허용 | 없음 | 선택 사유 |
| `created_at` | `DATETIME(6)` | 불가 | 없음 | 기록 시각 UTC |

### deliveries

| 컬럼 | 자료형 | NULL | 기본값·생성 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 불가 | AUTO_INCREMENT | 납품 ID |
| `order_id` | `BIGINT` | 불가 | 없음 | 주문 ID |
| `sequence_no` | `INT` | 불가 | 없음 | 최초 1, 재납품 2 |
| `description` | `TEXT` | 불가 | 없음 | 납품 설명, 최대 5000자 |
| `result_url` | `VARCHAR(2048)` | 불가 | 없음 | HTTPS 결과물 URL |
| `submitted_at` | `DATETIME(6)` | 불가 | 없음 | 제출 시각 UTC |

### revision_requests

| 컬럼 | 자료형 | NULL | 기본값·생성 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 불가 | AUTO_INCREMENT | 수정 요청 ID |
| `order_id` | `BIGINT` | 불가 | 없음 | 주문당 한 번 |
| `delivery_id` | `BIGINT` | 불가 | 없음 | 대상 최초 납품 ID |
| `reason` | `VARCHAR(1000)` | 불가 | 없음 | 수정 사유 |
| `requested_at` | `DATETIME(6)` | 불가 | 없음 | 요청 시각 UTC |
| `due_at` | `DATETIME(6)` | 불가 | 없음 | 재납품 기한 UTC |

### cancellation_requests

| 컬럼 | 자료형 | NULL | 기본값·생성 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 불가 | AUTO_INCREMENT | 취소 요청 ID |
| `order_id` | `BIGINT` | 불가 | 없음 | 주문 ID |
| `requested_by` | `BIGINT` | 불가 | 없음 | 요청 구매자 회원 ID |
| `request_key` | `CHAR(36)` | 불가 | 없음 | 정규화 UUID 요청 키 |
| `reason` | `VARCHAR(1000)` | 불가 | 없음 | 취소 요청 사유 |
| `status` | `VARCHAR(16)` | 불가 | 'PENDING' | 취소 요청 상태 |
| `requested_at` | `DATETIME(6)` | 불가 | 없음 | 요청 시각 UTC |
| `resolved_at` | `DATETIME(6)` | 허용 | 없음 | 해결 시각 UTC |
| `resolved_by` | `BIGINT` | 허용 | 없음 | 해결한 회원 ID |
| `response_reason` | `VARCHAR(1000)` | 허용 | 없음 | 선택 응답 사유 |

### wallets

| 컬럼 | 자료형 | NULL | 기본값·생성 | 의미 |
| --- | --- | --- | --- | --- |
| `member_id` | `BIGINT` | 불가 | 없음 | 회원 ID, 지갑 공유 PK |
| `balance` | `BIGINT` | 불가 | 0 | 구매용 테스트 적립금 잔액 원 |
| `updated_at` | `DATETIME(6)` | 불가 | 없음 | 최종 변동 시각 UTC |

### payments

| 컬럼 | 자료형 | NULL | 기본값·생성 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 불가 | AUTO_INCREMENT | 결제 ID |
| `order_id` | `BIGINT` | 불가 | 없음 | 주문당 한 건 |
| `amount` | `BIGINT` | 불가 | 없음 | 실제 결제액 원 |
| `fee_rate_bps` | `INT` | 불가 | 1000 | 결제 당시 수수료율, MVP 1000bps |
| `status` | `VARCHAR(10)` | 불가 | 'PAID' | 결제 상태 |
| `paid_at` | `DATETIME(6)` | 불가 | 없음 | 결제 시각 UTC |
| `refunded_at` | `DATETIME(6)` | 허용 | 없음 | 전액 환불 시각 UTC |

### wallet_entries

| 컬럼 | 자료형 | NULL | 기본값·생성 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 불가 | AUTO_INCREMENT | 적립금 내역 ID |
| `member_id` | `BIGINT` | 불가 | 없음 | 지갑 소유자 회원 ID |
| `payment_id` | `BIGINT` | 허용 | 없음 | 결제 ID, 초기 지급은 NULL |
| `type` | `VARCHAR(16)` | 불가 | 없음 | 초기 지급, 결제, 환불 |
| `amount_delta` | `BIGINT` | 불가 | 없음 | 변동액 원, 결제는 음수 |
| `balance_after` | `BIGINT` | 불가 | 없음 | 변동 직후 잔액 원 |
| `event_key` | `VARCHAR(100)` | 불가 | 없음 | 서버가 고정 규칙으로 생성한 중복 방지 키 |
| `created_at` | `DATETIME(6)` | 불가 | 없음 | 변동 시각 UTC |

### settlements

| 컬럼 | 자료형 | NULL | 기본값·생성 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 불가 | AUTO_INCREMENT | 정산 ID |
| `order_id` | `BIGINT` | 불가 | 없음 | 주문당 한 건 |
| `gross_amount` | `BIGINT` | 불가 | 없음 | 결제액 원 |
| `fee_rate_bps` | `INT` | 불가 | 없음 | 원결제에 고정한 수수료율 |
| `fee_amount` | `BIGINT` | 불가 | 없음 | 원 미만 버림 수수료 원 |
| `seller_amount` | `BIGINT` | 불가 | 없음 | 판매 수익 원 |
| `created_at` | `DATETIME(6)` | 불가 | 없음 | 구매 확정 정산 시각 UTC |

### reviews

| 컬럼 | 자료형 | NULL | 기본값·생성 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 불가 | AUTO_INCREMENT | 후기 ID |
| `order_id` | `BIGINT` | 불가 | 없음 | 주문당 한 건 |
| `rating` | `INT` | 불가 | 없음 | 별점 1~5 |
| `content` | `TEXT` | 허용 | 없음 | 선택 후기 글, 최대 2000자 |
| `created_at` | `DATETIME(6)` | 불가 | 없음 | 작성 시각 UTC |

### product_moderation_logs

| 컬럼 | 자료형 | NULL | 기본값·생성 | 의미 |
| --- | --- | --- | --- | --- |
| `id` | `BIGINT` | 불가 | AUTO_INCREMENT | 상품 운영 이력 ID |
| `product_id` | `BIGINT` | 불가 | 없음 | 상품 ID |
| `admin_id` | `BIGINT` | 불가 | 없음 | 실행 관리자 회원 ID |
| `action` | `VARCHAR(10)` | 불가 | 없음 | 숨김 또는 복구 |
| `reason` | `VARCHAR(1000)` | 불가 | 없음 | 운영 사유 |
| `created_at` | `DATETIME(6)` | 불가 | 없음 | 처리 시각 UTC |
