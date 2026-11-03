# 관리자 상품 조회·숨김·복구 API

2026-11-03 저장소 후속 변경: [프로젝트 JPA·QueryDSL 통일](project-persistence.md)에 따라 현재 운영 구현에서 JdbcTemplate·네이티브 SQL을 제거했다. 아래 이전 SQL 구현 설명·실행 결과는 당시 기록으로 보존하며 현재 구조와 최종 검증 범위는 후속 문서를 따른다.

작성일: 2026-11-03. 담당: 김창재. PR #32. 계약은 [API 명세 9절](api-spec.md#9-관리자-상품-운영-김창재)을 따른다. 관리자 React 화면은 별도다.

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
6. `is_hidden`, `version = version + 1`, `updated_at`을 변경하고 `product_moderation_logs`(HIDE/RESTORE, 정리한 reason, admin_id)를 같은 트랜잭션에서 저장한다. UPDATE에도 `AND version = ?` 조건을 함께 건다.

Product 엔티티는 상품 담당 영역이라 중복 정의하지 않았다. 주문 생성과 같은 방식으로 JdbcTemplate를 써서 `products`를 조회하고 잠근다. 상품 수정과 판매 상태 변경도 같은 상품 행 잠금과 version 검사를 사용해야 관리자 숨김과 동시에 실행돼도 안전하다(API 11절 시나리오). 새 테이블과 Flyway 변경은 없다.

## 검증 범위

AdminProductApiIntegrationTests 39건(파라미터 포함)은 실제 Security 필터, HTTP, Service, MySQL을 연결해 검사한다. 로그인만 테스트 principal로 대체한다. 검사 대상은 다음과 같다.

- 권한: 익명 401, 일반 회원 403, DB 권한이 해제된 관리자 403, CSRF 없음 403
- 목록: 응답 필드와 정렬, 목록에 상세 설명이 없음, 필터 AND 결합, `%`·`_` 이스케이프, 범위 밖 page
- 입력 오류: 잘못된 쿼리·ID·본문 400, 없는 상품 404
- 숨김·복구: 숨김 성공(판매 상태 유지, 정리한 사유, admin_id), 같은 상태 재전송(오래된 버전도 200, 이력 추가 없음), 버전 충돌 409, 복구 후 이력이 최신순, reason 1000/1001자 경계

origin/main(PR #31 포함)을 병합한 뒤 전체 `gradlew test`가 통과했다.

판매자 상품 수정과 관리자 숨김의 실제 동시 실행 테스트는 상품 쓰기 API가 병합된 뒤 추가한다. keyword 비교는 `products` 컬럼 콜레이션(utf8mb4_0900_ai_ci)을 따르므로 대소문자와 악센트를 구분하지 않는다.
