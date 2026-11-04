# 관리자 상품 조회·숨김·복구 API

> 문서 읽기 기준(2026-11-04): 아래 날짜·브랜치별 구현 및 검증 설명은 당시 기록이다. 현재 반영 여부·남은 작업은 [현황표](current-status.md), 실행 설정은 [루트 README](../README.md), API 계약은 [API 명세](api-spec.md)를 따른다. 과거의 미구현·병합 전 표현과 검사 수를 현재 상태로 확대하지 않는다.

2026-11-04 갱신: [프로젝트 JPA·QueryDSL 통일](project-persistence.md)(황준범)으로 저장소 구현이 JdbcTemplate에서 QueryDSL·JPA 엔티티로 바뀌었다. Service의 처리 순서와 API 계약은 그대로이며, 아래 설명은 현재 구조 기준으로 고쳤다. 전환 후 같은 테스트 39건을 다시 실행해 통과를 확인했다.

작성일: 2026-11-03. 담당: 김창재. PR #32. 계약은 [API 명세 9절](api-spec.md#9-관리자-상품-운영-김창재)을 따른다. 관리자 화면은 아래 '관리자 화면' 절을 따른다.

## 계약

| 경로 | 입력 | 결과 |
| --- | --- | --- |
| GET /api/admin/products | keyword, isHidden, saleStatus, page, size | AdminProductSummary 목록 |
| GET /api/admin/products/{id} | 없음 | 관리자 ProductDetail(isHidden 포함) |
| GET /api/admin/products/{id}/moderation-logs | page, size | 운영 이력, 최신순 |
| PATCH /api/admin/products/{id}/visibility | hidden, reason, expectedVersion + CSRF | 관리자 ProductDetail |

`/api/admin/**`는 기존 SecurityConfig에서 ROLE_ADMIN만 허용한다. 익명은 401, 일반 회원은 403이다. 모든 성공 응답은 `Cache-Control: no-store`다. 숨김 변경은 판매 상태, 거래, 지갑을 바꾸지 않는다.

## 입력 기준

- keyword: 공백을 정리한 뒤 빈 값이면 조건을 걸지 않는다. 최대 100 코드 포인트다. 제목 또는 설명에서 부분 문자열로 검색한다. `%`, `_`, 이스케이프 문자(`!`)는 `ESCAPE '!'`로 글자 그대로 검색하고, 값은 항상 바인딩한다.
- isHidden은 `true` 또는 `false`, saleStatus는 `ON_SALE` 또는 `STOPPED`만 허용한다. 빈 값, 대문자 변형, 다른 문자열은 400 VALIDATION_FAILED다. 필터는 AND로 결합한다.
- 정렬은 created_at 내림차순, 같은 시각이면 id 내림차순이다. 범위를 벗어난 page는 items=[]이고, 집계는 같은 필터의 전체 결과를 기준으로 한다.
- PATCH 본문: hidden은 JSON boolean, expectedVersion은 0 이상의 JSON 정수, reason은 JSON 문자열이다. reason은 정리 후 1~1000 코드 포인트를 허용한다. `"true"`, `"0"`, 1, 0.5, null, 필드 누락은 400이다.
- 상품 ID는 양의 BIGINT 범위의 10진 문자열만 허용하고, 그 외는 400이다. 없는 상품은 404 RESOURCE_NOT_FOUND다.

## 숨김·복구 처리 순서

AdminProductService는 READ_COMMITTED 트랜잭션에서 다음 순서로 처리한다.

1. 입력 검증
2. 실행자의 현재 DB role 확인. 세션의 ROLE_ADMIN과 별개로 다시 확인하며, ADMIN이 아니면 403이고 회원이 없으면 401이다.
3. `products` 행 `FOR UPDATE` 잠금
4. 이미 요청한 hidden 상태이면 버전과 관계없이 현재 상세를 반환한다. 버전과 이력은 바꾸지 않는다.
5. expectedVersion이 다르면 409 PRODUCT_VERSION_CONFLICT. 상태와 이력은 바꾸지 않는다.
6. `Product.moderate(hidden, expectedVersion, at)`가 버전을 다시 확인한 뒤 `hidden`, `version + 1`, `updatedAt`을 바꾸고, `ProductModerationLog`(HIDE/RESTORE, 정리한 reason, admin_id)를 `persist`한다. 둘 다 같은 트랜잭션에서 flush된다. 처리 시각이 기존 updated_at보다 과거면 변경하지 않고 실패한다.

`AdminProductQueries`는 QueryDSL로 `Product`·`Category`·`Member`를 조인해 목록과 상세를 만든다. 잠금은 `LockModeType.PESSIMISTIC_WRITE`(MySQL `FOR UPDATE`)이고, keyword는 `like(pattern, '!')`로 이스케이프 문자를 지정한다. 범위를 벗어난 page는 목록 조회 없이 빈 배열을 돌려준다. 상품 수정과 판매 상태 변경도 같은 상품 행 잠금과 version 검사를 사용해야 관리자 숨김과 동시에 실행돼도 안전하다(API 11절 시나리오). 새 테이블과 Flyway 변경은 없다.

## 검증 범위

AdminProductApiIntegrationTests 39건(파라미터 포함)은 실제 Security 필터, HTTP, Service, MySQL을 연결해 검사한다. 로그인만 테스트 principal로 대체한다. 검사 대상은 다음과 같다.

- 권한: 익명 401, 일반 회원 403, DB 권한이 해제된 관리자 403, CSRF 없음 403
- 목록: 응답 필드와 정렬, 목록에 상세 설명이 없음, 필터 AND 결합, `%`·`_` 이스케이프, 범위 밖 page
- 입력 오류: 잘못된 쿼리·ID·본문 400, 없는 상품 404
- 숨김·복구: 숨김 성공(판매 상태 유지, 정리한 사유, admin_id), 같은 상태 재전송(오래된 버전도 200, 이력 추가 없음), 버전 충돌 409, 복구 후 이력이 최신순, reason 1000/1001자 경계

origin/main(PR #31 포함)을 병합한 뒤 전체 `gradlew test`가 통과했다.

판매자 상품 수정과 관리자 숨김의 실제 동시 실행 테스트는 상품 쓰기 API가 병합된 뒤 추가한다. keyword 비교는 `products` 컬럼 콜레이션(utf8mb4_0900_ai_ci)을 따르므로 대소문자와 악센트를 구분하지 않는다.

## 관리자 화면 (2026-11-04, `feature/admin-product-ui`)

| 경로 | 내용 |
| --- | --- |
| `/admin/products` | 검색어·노출(전체/공개/숨김)·판매 상태(전체/판매 중/판매 중지) 필터와 페이지를 URL에 보관한다. 허용하지 않는 값은 "전체"로 읽어 400 요청을 보내지 않는다. 검색어는 정리 후 100자를 넘으면 요청하지 않고 안내한다. 숨김과 판매 중지를 서로 다른 표시로 구분한다 |
| `/admin/products/:id` | 상세·숨기기/복구하기(사유 필수)·운영 이력(최신순, 페이지)을 보여준다. 목록에서 들어오면 같은 필터의 목록으로 돌아가며, 돌아갈 주소는 관리자 목록 경로만 허용한다 |

- 파일: `features/admin/`의 `types`·`api`·`validation`·`access`·`queries`와 `pages/`. 공통 `apiRequest`·`AsyncState`·`usePageSearchParam`·`normalizeText`·`formatDateTime`을 재사용한다.
- 권한: 헤더의 "관리자" 링크는 `GET /api/me`의 role이 ADMIN일 때만 보이지만 편의 표시일 뿐이다. 서버 응답 401이면 로그인 안내(`?next=`로 복귀), 403 `FORBIDDEN`이면 "접근 권한이 없어요"를 보여주고 상품 내용은 표시하지 않는다. CSRF 403은 권한 문제로 보지 않는다.
- 캐시: `['admin', ...]` 아래에 두고 매번 최신으로 조회한다(staleTime·gcTime 0). 로그인·로그아웃 때 `resetAccountState()`가 `['auth']` 외 캐시를 지우므로 다른 계정에 남지 않는다.
- 숨김·복구 요청: 마지막으로 본 `version`을 `expectedVersion`으로 보낸다. 처리 중 버튼을 막고 자동 재시도하지 않는다. 성공하면 상세를 응답으로 바꾸고 목록·이력을 다시 조회한다. 409 `PRODUCT_VERSION_CONFLICT`는 입력한 사유를 남긴 채 최신 상품을 다시 불러와 안내한다. 통신 오류·5xx는 서버 처리 여부가 불명확하므로 실패로 단정하지 않고 상품·이력을 다시 조회한다. 화면을 떠난 뒤 도착한 응답은 화면 상태를 바꾸지 않는다.
- 자체 검수 보완(2026-11-04): 다른 관리자가 먼저 같은 상태로 바꾼 경우 서버는 이력 없이 현재 상태를 200으로 돌려주므로, 성공 안내는 "내가 바꿨다"가 아니라 현재 상태(숨김/공개)로 표시하고 운영 이력을 다시 불러온다. 상세 재조회만 실패하면 마지막 상품과 입력 중인 사유를 유지한 채 "다시 불러오기"를 보여준다. 빠른 두 번 클릭은 ref로 한 번 더 막고, 목록의 선택 상자를 바꿀 때 입력해 둔 검색어도 함께 적용한다.
- 검증: `tests/admin.test.mjs`(API 요청 형식·잘못된 ID 차단·PATCH 본문과 재시도 없음, URL 필터·사유 규칙, 401/403 구분, 목록 URL 복원·숨김/판매 중지 구분, 접근 거부 시 데이터 미표시, 100자 초과 미요청, 상세 숨김/복구 버튼·텍스트 이스케이프·이력, 잘못된/없는 ID, 재조회 실패 시 화면 유지, 돌아갈 주소 제한, 헤더 관리자 링크). 실행 결과와 브라우저 확인 여부는 PR에 기록한다.
- 로컬 관리자 계정(2026-11-04 팀 확인): 회원가입은 항상 USER이므로, 로컬 개발 DB에서 테스트용으로 가입한 계정 1개만 `UPDATE members SET role = 'ADMIN', updated_at = UTC_TIMESTAMP(6) WHERE email = '...'`로 바꾼다. 테스트 DB(`lancerpick_test`)에는 하지 않는다. role은 로그인 시 세션에 담기므로 바꾼 뒤 다시 로그인한다.
- 브라우저 확인(로컬): 관리자 계정의 헤더 링크·목록 조회(빈 목록)·이전/다음 비활성, 일반 계정의 링크 미표시와 `/admin/products` 직접 접근 시 접근 권한 없음 안내를 확인했다.
- 실제 데이터 브라우저 확인(2026-11-04, main `16bb8c5`, 로컬 개발 DB, 사용자 수동 확인): [수동 테스트 데이터 SQL](sql/seed-manual-trade-demo.sql)로 상품 4개를 만든 뒤 관리자 계정으로 확인했다.
  - 목록: 4개 모두 공개·판매 중·버전 0으로 표시.
  - 숨기기(`[테스트 B] 상세페이지 디자인`, 사유 "테스트 숨김"): 배지 숨김, 판매 상태는 판매 중 유지, 버전 0→1, 운영 이력 1줄(숨김·사유·관리자 ID), 버튼이 복구하기로 바뀜. 비로그인 공개 목록이 3개로 줄어 해당 상품만 빠짐.
  - 복구(사유 "테스트 복구"): 운영 이력 2줄(최신순 복구→숨김), 버튼이 숨기기로 바뀜. 공개 목록이 다시 4개.
- 남은 확인: 판매자의 판매 재개가 이미 적용된 관리자 숨김을 풀지 않는지, 두 관리자 동시 처리·판매자 수정과 관리자 숨김의 동시 실행 화면 확인. 공개 상세 차단과 기존 주문 유지의 대표 경로는 아래 정은찬 사용자 확인으로 갱신한다. 자동 테스트·백엔드 통합 테스트 결과와 사용자 수동 확인은 합산하지 않는다.

## 공개 상세 차단·기존 주문 유지 사용자 확인 — 2026-11-04

정은찬은 판매 재개 후 관리자 숨김을 적용한 상품의 공개 상세가 조회 차단되는 것을 확인했다(HTTP 상태코드 직접 측정 아님). 기존 주문 #3은 조회·수락·999원 결제·취소 요청·판매자 승인·환불이 가능했고 지갑 +999원과 최종 잔액 999,001원을 전달했다. [수동 검증 기록](manual-trade-verification.md)을 따른다. 이는 이미 숨긴 상품을 판매자가 재개했을 때 숨김이 유지되는지의 역순 시나리오나 동시 처리 검증을 뜻하지 않는다. 앞선 남은 확인 중 공개 상세 차단은 이 결과로 후속 확인됐다.
