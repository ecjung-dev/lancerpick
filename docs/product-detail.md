# 공개 상품 상세 조회

> 문서 읽기 기준(2026-11-04): 아래 날짜·브랜치별 구현 및 검증 설명은 당시 기록이다. 현재 반영 여부·남은 작업은 [현황표](current-status.md), 실행 설정은 [루트 README](../README.md), API 계약은 [API 명세](api-spec.md)를 따른다. 과거의 미구현·병합 전 표현과 검사 수를 현재 상태로 확대하지 않는다.

현재 상태(2026-11-04): 공개 상품 상세 API·화면은 main 670b99b에, 상품 상세에서 주문 작성으로 이어지는 화면은 main ff71fda(PR #51)에 반영됐다. 실제 브라우저·모바일 동선과 운영 로그인부터 주문 생성까지의 통합 검증은 별도다. 아래 테스트 수와 커밋·푸시·병합 전 표현은 이 기능을 개발하던 당시 기록이다.

2026-11-04 박소현 담당. `main` 8dffe84에서 `codex/product-detail`을 생성했다. 판매자 프로필 병합과 최신 main 반영을 확인하고 작업했다.

## 구현

- GET /api/products/{id}: 로그인 없이 공개·판매 중 상품만 조회한다. 기존 목록과 같은 ON_SALE·is_hidden=false 조건을 QueryDSL 조인에 적용한다.
- 잘못된 양수 BIGINT ID는 400 VALIDATION_FAILED, 미존재·숨김·판매 중지 상품은 동일한 404 RESOURCE_NOT_FOUND다.
- ProductSummary에 description, scopeDescription, preparationDescription, version, saleStatus, createdAt, updatedAt을 추가한 DTO를 반환한다. ID는 문자열, 시각은 UTC offset을 명시한다. 판매자의 이메일·비밀번호나 isHidden은 공개하지 않는다. 성공 응답은 no-store다.
- React /products/:id 경로와 목록 카드 이동을 연결한다. 설명·작업 범위·준비사항, 가격·작업 기간·판매자·카테고리·한국 시각을 표시한다. 검색 결과에서 진입하면 목록 복귀 링크에 검색 조건·페이지를 유지한다.
- 로딩·404·서버 오류를 구분한다. 상세 캐시를 장기 보존하지 않고 진입·포커스에서 서버 상태를 재확인한다. 설명을 HTML로 해석하지 않고 React 텍스트로 표시한다.
- DB·Flyway·Security·의존성 변경 없음. 운영 데이터 접근은 기존 JPA Entity와 QueryDSL만 사용한다.

## 검증

- 상품·공통 백엔드 선택 테스트 52건 통과, 실패·오류 0. 전체 백엔드 테스트 실행 결과는 아니다.
- 개인 DB와 분리한 임시 MySQL 8.0.46/lancerpick_ci에서 기존 V1/V2와 매핑을 적용했다. 상세 실제 조인, 응답 계약·UTC·민감 필드 미노출 및 숨김·중지·미존재 차단을 확인했다. 테스트 fixture는 롤백하고 임시 MySQL은 종료했다.
- 프런트 build(타입 검사 포함), lint, 전체 tests/*.test.mjs 47건 통과. 문자열 BIGINT·취소·쿠키 전달, 잘못된 ID의 요청 차단, HTTP 오류 전파를 확인했다.
- React 서버 렌더링 검사로 직접 상세 URL, 상세 내용·가격, HTML escaping, UTC→KST, 목록 복귀 검색 조건을 확인했다. 실제 브라우저 클릭·새로고침·모바일 시각 검증과 구분한다.

## 남은 범위

이 작업 당시 실제 브라우저·모바일 검증, MySQL 8.4 원격 CI, 팀원 검토·병합은 별도였다. 상품 등록·수정·판매 상태 관리·후기 및 주문 작성 화면 연결은 당시 상품 상세 PR 범위에 포함하지 않았다. 주문 작성 연결은 이후 별도 PR #51로 병합됐다.

## Commit·Push 전 최종 검증

사용자 요청으로 main의 de364e3(PR #45·#46)을 충돌 없이 fast-forward 반영했다. 기존 상품 상세 변경을 보존했다. 개인 DB와 분리한 임시 MySQL 8.0.46에서 `gradlew.bat build --no-daemon` 전체 **584건**, 실패·오류·건너뜀 0을 확인했다. V1/V2와 기본 validate를 유지하고 검사 후 임시 MySQL을 종료했다.

당시 main 기준 프런트 build(타입 검사 포함)·lint·전체 테스트 **54건** 통과. 가상 데이터로 상세 URL·원문 escaping·KST·목록 조건 복귀, 404·500 안내·재시도 버튼과 외부 목록 복귀 주소 차단을 렌더링 검사했다. API 검사는 잘못된 ID 요청 차단·큰 문자열 ID·쿠키·취소·HTTP 오류를 확인한다. 이 검사에서 브라우저 직접 클릭과 모바일 검사는 수행하지 않았다. 커밋·Push는 사용자가 승인했으며 당시 원격 CI·리뷰·병합은 별도였다.
