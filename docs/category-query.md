# 카테고리 공개 조회

> 문서 읽기 기준(2026-11-04): 아래 날짜·브랜치별 구현 및 검증 설명은 당시 기록이다. 현재 반영 여부·남은 작업은 [현황표](current-status.md), 실행 설정은 [루트 README](../README.md), API 계약은 [API 명세](api-spec.md)를 따른다. 과거의 미구현·병합 전 표현과 검사 수를 현재 상태로 확대하지 않는다.

연결 상태 2026-11-04: 실제 상품·카테고리 공개 API가 현재 메인 화면에 연결되었다. demo API를 호출한다는 과거 설명과 구분한다. 현재 상태와 검증은 [현황표](current-status.md)·[거래 통합](account-trade-integration.md)을 따른다.

작성일: 2026-11-02. 담당: 박소현. 작업 브랜치: `codex/product-list`.

## 구현 범위

`GET /api/categories`가 기존 V1의 `categories`를 조회한다. 로그인 없이 접근할 수 있으며 응답은 `id`(문자열), `name`, `displayOrder`의 배열이다. `display_order ASC, id ASC`로 정렬하며 데이터가 없으면 `200 []`를 반환한다. 서버 오류는 공통 오류 응답을 사용하고 빈 목록으로 숨기지 않는다.

- Entity·Repository·DTO·읽기 전용 Service·Controller를 product 패키지에 둔다.
- Entity를 직접 반환하지 않고 서비스 트랜잭션 안에서 DTO로 변환한다.
- 실제 DB를 사용하지 않는 demo 프로필에서는 이 Controller·Service를 등록하지 않는다. 기존 `/api/demo/categories`는 별도 예시다.
- DB 없는 공통 HTTP 검사에서는 CategoryService만 mock으로 대체하고 기존 지갑·충전·주문 서비스 mock을 보존한다.
- 기존 V1/V2, Security 정책, 실제 카테고리 초기 데이터는 변경하지 않는다. 카테고리 등록·편집 API도 추가하지 않는다.

## 검증

`CategoryControllerTests` 3건은 공개 접근·응답 필드·큰 ID의 문자열 보존·Unicode 이름·빈 목록·내부 오류 비노출을 검사한다. Service는 mock이며 실제 DB 검사를 대신하지 않는다.

`CategoryIntegrationTests` 1건은 공통 `@IntegrationTest`와 TEST_DB_* 전용 MySQL을 사용한다. SQL로 넣은 테스트 분류 세 개의 JPA 매핑·표시 순서/ID 정렬·DTO 변환·비로그인 HTTP 응답을 확인한다. 테스트 fixture는 트랜잭션 롤백하며 기존 카테고리 삭제나 실제 초기 데이터 확정은 하지 않는다.

2026-11-02 에이전트 실행 결과:

- 개인 MySQL과 분리한 임시 MySQL **8.0.46**, 루프백 별도 포트, 새 `lancerpick_ci`와 임시 전용 계정 사용.
- 기존 Flyway V1/V2 적용 및 Hibernate 기본 validate를 포함한 `gradlew.bat build --no-daemon` 성공.
- 전체 **192건**, 실패·오류·건너뜀 0. 카테고리 검사 4건 포함.
- 빌드 경로에 임시 DB를 두어 `clean`은 사용하지 않았다. 개인 접속 파일·개발 DB는 사용하지 않았다.
- 프런트 `npm ci` 후 `npm run build`(타입 검사 포함)·`npm run lint`·`npm run test:api` 15건 통과. 프런트 소스·의존성 정의·잠금 파일은 수정하지 않았다.
- 검증 후 임시 MySQL을 종료하고 임시 자격 정보 파일을 제거했다. build 안의 데이터 디렉터리는 Git 제외 대상이다.

팀 기준 MySQL **8.4**의 최신 PR CI 결과는 Push 후 별도로 확인해야 한다. 위 로컬 결과를 원격 CI 성공이나 실제 상품 검색·회원 인증 검증으로 표현하지 않는다.

## 제외·남은 범위

상품 검색·상품 목록 API·판매자 프로필·상품 등록/수정·후기는 이번 변경에 포함하지 않는다. React 메인은 아직 demo API를 사용한다. 실제 상품 API와 일관되게 전환할 때 카테고리 API·조회 키·화면을 함께 검토해야 하며, 이 PR만으로 상품 화면 연결 완료를 표시하지 않는다.

실제 카테고리 목록·초기 데이터 방식은 팀 결정 후 별도로 반영한다. 현재 변경은 카테고리 조회 API 단위이며 작업표의 '카테고리 조회·상품 검색' 전체 완료가 아니다. Push·최신 CI·작성자 외 1명 이상 승인 확인 후 Squash merge한다.

## 재실행

테스트 전용 DB 설정은 [테스트 DB 안내](test-database.md)를 따른다. 백엔드 폴더에서:

```powershell
.\gradlew.bat test --tests "com.example.lancerpick.product.*"
.\gradlew.bat build
```

첫 명령에도 MySQL 통합 검사가 포함된다. 일반 개발 DB의 DB_*로 대체하지 않는다.
