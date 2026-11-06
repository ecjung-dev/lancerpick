# 구매 확정·판매 수익 정산

2026-11-06 화면 안내 후속(`fix/trade-finance-guidance`, 로컬): 판매 수익 합계는 완료 거래의 누적 집계다. 새 전자계약(CONTRACT_V3) 수익은 수수료 제외 후 구매 적립금으로 지급되고, 기존 LEGACY_V1 수익은 별도 기록으로 유지된다고 안내한다. 전부 구매 적립금과 별도로 관리된다는 기존 화면 문구를 수정했다. 지급/수수료 계산·조회 API·잔액·DB는 변경하지 않는다.

2026-11-05 신규 정책: CONTRACT_V3는 [결제 금액 보관·판매자 적립금 지급·자동 구매 확정](full-funding-policy.md)을 따른다. V7은 main `2fec099`에 반영돼 업무 테이블 30개와 새 모드를 제공한다. 아래 선금/잔금·보상·V1~V6/29개 설명은 해당 버전의 이력이다. 신규 V3 금액 명령·스케줄러의 완료를 뜻하지 않는다.

> 최신 수동 확인(2026-11-04): 사용자가 실제 화면의 일반 거래 완료·정산, 취소·환불·잔액 반환과 기본 충전 실패 흐름 확인을 완료했다. 아래 날짜별 미확인·후속 표기는 당시 기록이며, 현재 남은 확인과 보류 사항은 [사용자 수동 검증 현황](manual-trade-verification.md)을 따른다.

> 문서 읽기 기준(2026-11-04): 아래 날짜·브랜치별 구현 및 검증 설명은 당시 기록이다. 현재 반영 여부·남은 작업은 [현황표](current-status.md), 실행 설정은 [루트 README](../README.md), API 계약은 [API 명세](api-spec.md)를 따른다. 과거의 미구현·병합 전 표현과 검사 수를 현재 상태로 확대하지 않는다.

후속 2026-11-04: 마이페이지에서 판매 수익 화면으로 직접 이동할 수 있다. 구매 확정·환불 이후 실제 HTTP/React 렌더링과 전체 회귀 범위는 [거래 통합 검증](account-trade-integration.md)을 따른다. 과거 실행 건수와 최신 합본 검증을 구분한다.

저장소 후속 변경: [JPA·QueryDSL 전환](finance-persistence.md)에 따라 정산 생성은 Entity/JpaRepository, 수익 조회는 QueryDSL로 변경했다. API·수수료·동시성 정책은 유지한다.

2026-11-03. 황준범. feature/order-settlement. 선행 결제·직접 취소/환불 브랜치를 포함한다.

## 구매 확정

POST /api/orders/{id}/confirm. 인증 구매자·CSRF 필요, 본문 없음. 검수 대기 AWAITING_CONFIRMATION이며 대기 취소 요청이 없는 주문만 가능하다. 판매자는 403, 비참여자/없는 주문은 동일 404다. 수정 중·취소/거절 등은 409 ORDER_STATE_CONFLICT, 대기 취소 요청은 409 CANCELLATION_PENDING이다.

실행자 회원 FOR SHARE → 참여자 주문 FOR UPDATE 순서로 잠그고 원결제 PAID·주문 금액 일치·결제 시각 존재를 검사한다. READ_COMMITTED 트랜잭션에서 COMPLETED·완료/갱신 시각·정산 한 건·COMPLETED 이력·응답 조립까지 함께 처리한다. 원결제의 fee_rate_bps를 복사하고 원 단위 정수 나눗셈으로 수수료를 버림 계산한다. 999원·1000bps는 수수료 99원, 판매 수익 900원이다. 구매 지갑을 변경하지 않는다.

완료 재호출은 원결제와 기존 정산 금액/비율 일치를 확인해 현재 주문을 반환하며 이력·시각·정산을 재생성하지 않는다. 상태/결제/정산 모순은 DATA_INTEGRITY_ERROR로 처리한다. order_id UNIQUE를 유지하고 새 테이블이나 Flyway 변경은 없다. 직접 취소와 구매 확정이 같은 주문 잠금을 사용하므로 취소·환불과 완료·정산이 동시에 확정되지 않는다.

## 본인 판매 수익 조회

- GET /api/me/settlements?page=0&size=20: 본인 판매 주문의 정산만 조회. 최대 100, created_at 내림차순·동일 시각 ID 내림차순. 공통 PageResponse이며 범위를 벗어난 페이지는 빈 items다.
- GET /api/me/settlements/summary: completedCount,grossAmount,feeAmount,sellerAmount. 없는 내역은 모두 0.

두 API는 인증 principal 회원 ID만 사용하며 200·no-store다. ID는 문자열, 금액은 정수, 시각은 UTC ISO 8601이다. SQL SUM은 정확한 정수로 변환하며 JSON 안전 정수 범위를 초과하면 조용히 반올림하지 않고 실패한다. REPEATABLE_READ 읽기 트랜잭션으로 목록·건수를 조회한다. 각 HTTP 요청 사이의 전체 스냅샷까지 공유하지는 않는다.

## 화면

/mypage/settlements에서 실제 조회 API를 사용해 합계·내역·페이지 이동·로딩·재조회·401 로그인 안내를 표시한다. 마이페이지에 링크를 추가했고 기존 흑백 스타일·공통 apiRequest·TanStack Query·CSS Modules를 사용한다. wallet 하위 쿼리 키와 gcTime=0을 사용해 기존 계정 전환 캐시 정리에 참여한다. 실제 로그인에서 캐시 정리 함수를 호출하는 연결은 인증 담당자가 완료해야 한다.

판매 수익은 구매 적립금과 별도이며 출금 기능은 제공하지 않는다고 화면에 안내한다. 예시 수익을 실제 응답처럼 표시하지 않는다. 현재 로그인 기능이 없으면 정상 경로에서 로그인 안내가 나온다. 구매 확정 버튼을 포함한 주문 상세 화면은 주문 담당자 연결 범위이며 이번에는 판매 수익 화면을 추가했다.

## 검증과 제한

OrderConfirmationIntegrationTests는 기존 결제/취소 21건을 재사용하고 구매 확정·수수료 버림·중복 정산·취소와 확정 경쟁·실제 FK 실패 롤백·본인 조회/페이지·권한을 추가 검사한다. 선택 실행 28건 통과. 테스트 principal·상태 fixture를 사용하므로 실제 로그인·브라우저 전체 거래 성공을 의미하지 않는다. 프런트 lint·build·기존 test:api 15건을 검증하며 최종 전체 검사 결과는 아래 기록한다.

격리 Docker MySQL 8.4에서 최종 전체 clean build --offline **410건**이 실패·오류·스킵 없이 통과했다. 개인 DB·시크릿은 사용하지 않았고 임시 컨테이너/볼륨은 정리했다. 프런트는 최종 CSS Modules 적용 후 lint·build를 통과했으며 공통 test:api 15건도 통과했다. 정산 화면의 실제 로그인/브라우저 조작 검증은 아직 하지 않았다. 원격 CI·다른 팀원 PC에서의 성공까지 대신하지 않는다.
