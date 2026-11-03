# 공개 상품 검색 API

작성일: 2026-11-03. 담당: 박소현. 브랜치: `codex/product-search`.

## 구현 범위

API 명세 1·3·9·10절에 따라 `GET /api/products`를 구현했다. 로그인 없이 조회하며 `keyword`, `categoryId`, `page`, `size`를 받는다. React 메인 검색도 실제 상품·카테고리 API로 연결했다. 상품 등록·상세·주문 진입 기능과 구분한다.

- 서버 QueryDSL 조건에서 판매 중(`ON_SALE`)이고 관리자에게 숨겨지지 않은 상품만 조회한다.
- keyword는 공통 TextNormalizer로 정규화하고 최대 100 코드 포인트를 허용한다. 생략·정규화 후 빈 값은 제한 없음이다.
- 제목 또는 설명의 포함 검색이며, 카테고리 조건과는 AND로 결합한다. `%`, `_`, `!`, 역슬래시 등 사용자 입력은 바인딩하며 LIKE 와일드카드로 실행하지 않는다.
- categoryId는 생략 가능하지만 제출하면 양의 BIGINT 10진 문자열이어야 한다. 형식·범위 오류는 400 VALIDATION_FAILED, 존재하지 않는 정상 ID는 빈 결과다.
- page 기본 0, size 기본 20·허용 범위 1~100. 오프셋은 long으로 계산한다.
- created_at DESC, id DESC 정렬. 범위 밖 페이지는 빈 items와 같은 필터의 전체 건수·페이지 수를 반환한다.
- 공통 PageResponse를 사용하고 상품·카테고리·판매자 ID는 문자열로 반환한다. 판매자 이메일·비밀번호·내부 숨김 상태는 응답하지 않는다.

## 구조와 연결

ProductSearchController → 읽기 전용 ProductSearchService → ProductSearchQueries(QueryDSL/JPA) → 기존 Product·Category·Member 조인이다. 최신 main의 [JPA·QueryDSL 통일 기준](project-persistence.md)에 맞춰 운영 코드의 직접 JDBC를 제거했다. 목록에 필요한 열만 선택하고 관리자 전용 응답이나 쓰기 경로를 공개 API에 재사용하지 않는다. CategoryResponse와 공유 Product Entity는 재사용하며 테이블·마이그레이션을 추가하지 않는다. 범위 밖 페이지는 Service에서 집계 후 빈 목록을 반환해 JPA offset의 int 변환 오류를 방지한다.

Service와 Queries는 demo에서 제외한다. 실제 DB가 없는 공통 HTTP 테스트에만 검색 Service·Queries mock을 추가했다. Security 공개 경로는 기존 `/api/products` 설정을 사용하고 CSRF·보호 API 정책을 변경하지 않는다.

## 검증과 재현

- ProductSearchControllerTests 15건: 기본 페이지·공개 접근·빈 결과·잘못된 페이지/크기/카테고리·Unicode 공백·이모지 길이 경계·큰 오프셋·필터 집계.
- ProductSearchIntegrationTests 7건: 실제 MySQL 제목/설명 검색·카테고리 결합·숨김/중지 제외·HTTP 응답·생성 시각/ID 정렬·페이지 경계·특수문자 일반 검색.
- `@IntegrationTest`와 TEST_DB_* 전용 DB를 사용한다. 임의 고유 이름의 SQL fixture는 테스트 트랜잭션으로 롤백하며 기존 데이터를 일괄 삭제하지 않는다.
- 로컬 임시 MySQL은 8.0.46이다. 개인 개발 DB와 다른 포트·새 lancerpick_ci·임시 계정을 사용한다. 팀 기준 MySQL 8.4 및 원격 CI 결과와 구분한다.
- 2026-11-03 실행: 기존 V1/V2·Hibernate validate를 포함한 `gradlew.bat build --no-daemon` 성공. 백엔드 전체 510건(상품 검색 22건 포함), 실패·오류·건너뜀 0. 임시 DB를 build 경로에 두어 clean은 사용하지 않았다. 종료 후 임시 서버와 자격 정보 파일을 정리했다. 프런트 검사·실제 브라우저 검색·원격 CI 결과는 아니다.

테스트 DB 설정은 [공통 안내](test-database.md)를 따른다. 백엔드 폴더에서:

```powershell
.\gradlew.bat test --tests "com.example.lancerpick.product.*"
.\gradlew.bat build
```

## 남은 작업

React 메인은 `/api/products`와 `/api/categories`를 사용한다. 조회 키는 `['products', search]`, `['categories']`로 예시 캐시와 분리했다. 검색 조건은 URL에 유지하고 카테고리 변경 시 첫 페이지로 돌아간다. 범위 밖 페이지는 서버의 실제 전체 페이지 수와 첫 페이지 복귀 버튼을 표시한다. 오류를 예시 데이터로 대체하지 않는다. 상품 카드는 상세 API 대신 목록 응답의 요약을 표시한다.

2026-11-03 프런트 검증: 타입 검사 포함 build·lint·test:api 23건 통과(추가 4건 포함). 실제 기능 API 모듈을 Vite로 읽어 공개 경로·검색어 인코딩·문자열 ID·페이지/취소 신호·빈 응답·서버 오류 전파를 확인했다. 이는 네트워크 대역 검사이며 아래 실제 브라우저 검증과 구분한다. 의존성 추가·잠금 파일 변경은 없다.

## 실제 브라우저 검증 후속

2026-11-03 개인 DB와 분리한 새 임시 MySQL 8.0.46의 lancerpick_ci에 V1/V2를 적용했다. 별도 백엔드(18080)·Vite(15173)와 in-app 브라우저를 연결하고 테스트 전용 카테고리 2개·공개 상품 7개·숨김 1개·판매 중지 1개를 사용했다. 개발 DB·실제 카테고리 초기 데이터는 변경하지 않았다.

- 공개 상품 7개가 첫 페이지 4개·둘째 페이지 3개로 조회되고 이전/다음 버튼이 경계에서 비활성화된다.
- 제목 '로고' 검색은 공개 5개만 반환하고 숨김·중지 상품은 제외한다. 설명에만 있는 '브랜드' 검색은 2개를 반환한다.
- 검색어와 디자인 카테고리를 함께 선택하면 1개이며, 브라우저 뒤로가기로 카테고리 선택 전 결과 2개가 복원된다.
- 둘째 페이지에서 개발 카테고리를 선택하면 page가 초기화되고 해당 카테고리 2개만 표시된다.
- `%_!` 검색은 해당 문자를 가진 상품 1개만 표시하고 새로고침해도 URL·검색어·결과가 유지된다.
- 상품 요약에서 가격·기간·판매자가 표시되며 실제 상세/주문 미구현 범위를 안내한다.
- 존재하지 않는 검색어는 빈 결과, page=999는 페이지 범위 밖 안내·실제 총 페이지 수·첫 페이지 복귀 버튼을 표시한다. 두 빈 상태의 안내 문구를 구분하도록 보완했다.
- 임시 백엔드 종료 시 상품·카테고리 오류와 재조회 버튼이 표시된다. 서버 복구 후 각 버튼으로 정상 재조회한다. 오류를 빈 성공·예시 상품으로 대체하지 않는다.
- 문구 수정 후 타입 검사 포함 build·lint·test:api 23건을 다시 통과했다. 임시 백엔드·Vite·MySQL을 종료하고 임시 자격 정보 파일을 제거했다.

main 화면에는 DB를 사용하는 기본 백엔드가 필요하며 demo 프로필의 예시 API로 자동 전환하지 않는다. 팀 기준 MySQL 8.4·최신 CI·리뷰 승인 확인은 남아 있다. 초기 브라우저 검증 단계에서는 커밋·Push·PR 병합을 수행하지 않았으며, 아래 후속 검증과 구분한다.

## 최신 main 반영 후 Push 전 검증

2026-11-03 커밋 준비 중 main의 `29afd0c`까지 JPA·QueryDSL 통일 변경이 추가되어 반영했다. 공통 HTTP 테스트 충돌은 검색 Service/Queries와 팀원의 정산 Repository 모의 빈을 모두 보존해 해결했다. ProductSearchQueries도 공유 QProduct·QCategory·QMember를 사용하는 QueryDSL 스칼라 조회로 전환했다. 운영 코드에 직접 JDBC/네이티브 SQL을 다시 추가하지 않는다. 테스트 fixture SQL은 기존 규칙대로 유지한다.

집계보다 큰 offset은 Service에서 바로 빈 목록을 반환한다. 기본 빈 결과·큰 페이지의 HTTP 검사도 DB 목록 호출을 건너뛰는 계약으로 보완했다. 검색어 정규화·특수문자 처리·공개 상품 조건·응답 모양은 유지한다.

개인 DB와 분리한 새 임시 MySQL **8.0.46**에서 V1/V2·Hibernate validate를 포함한 `gradlew.bat build --no-daemon`을 재실행했다. 전체 **512건**(상품 검색 22건·운영 영속성 경계 검사 포함), 실패·오류·건너뜀 0이다. 임시 DB는 종료하고 자격 정보 파일을 제거했다. 프런트는 이 후속 변경에서 수정하지 않았으며 앞선 build·lint·23건 결과를 따른다. 앞선 브라우저 확인은 JDBC 구현 당시 결과이며, QueryDSL 전환 후 실제 MySQL HTTP·검색 통합 검사는 이번 전체 빌드에서 재실행했다. 실제 브라우저 클릭을 다시 실행한 것으로 기록하지 않는다.

원격 MySQL 8.4 CI와 작성자 외 1명 이상 승인은 Push 후 최신 PR에서 확인한다. main에 직접 Push하거나 PR을 병합하지 않는다.
