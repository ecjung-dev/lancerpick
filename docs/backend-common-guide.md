# 백엔드 공통 개발 가이드

2026-11-04. 현재 구현 기준. 업무는 [MVP 명세](mvp-spec.md), 응답 계약은 [API 명세](api-spec.md), 데이터 구조는 [물리 DB 설계](physical-db.md), 검증 환경은 [테스트 DB 안내](test-database.md)를 따른다.

## 패키지와 책임

com.example.lancerpick 아래 auth·member·seller·product·order·wallet·payment·settlement·admin 등 기능별 패키지를 사용한다. 기능 안에서 controller·dto·service·repository·entity를 구분한다. 외부 제공자 통신은 wallet/gateway에 둔다.

Controller는 입력·인증 정보 수신과 응답 상태를 처리한다. Service는 업무 규칙·권한·트랜잭션을 관리한다. Repository는 데이터 접근·잠금을 담당한다. Entity는 저장 상태와 상태 변경 규칙을 표현한다. API 응답은 dto에 정의하고 Entity나 Service 내부 응답 타입을 직접 노출하지 않는다.

정산 조회는 settlement/service/SettlementQueryService와 settlement/dto의 SettlementResponse·SettlementSummaryResponse를 사용한다. 내부 처리 결과 record와 Repository 조회 projection은 API 응답 DTO와 역할이 다르다.

주문 채팅도 `chat/controller`·`service`·`dto`·`repository`·`entity`를 구분한다. 소켓 설정은 `chat/config`, 커밋 이벤트는 `chat/event`, handshake·프레임/세션 보안은 `chat/realtime`에 둔다. HTTP·소켓 Controller 모두 `OrderChatService`의 같은 저장·권한 검증을 재사용한다. [실시간 연결 계약](order-chat-realtime.md)을 따른다.

## 데이터 접근·변경

Entity 저장·변경은 JPA, 검색·조인·집계는 QueryDSL을 사용한다. 운영 코드에 JdbcTemplate·직접 JDBC·네이티브 SQL을 추가하지 않는다. Flyway와 테스트 fixture·DB 원문 검증 SQL은 별도로 관리한다. 지갑 최초 생성의 기존 HQL 충돌 처리와 useAffectedRows 설정은 함께 유지한다.

기존에 적용한 Flyway 마이그레이션을 수정하지 않고 후속 버전을 추가한다. 서버 시작과 검사에서 ddl-auto=validate로 매핑을 확인한다. ID는 JSON 문자열, 금액은 정수 원, API 시각은 UTC ISO 8601 기준이다. MySQL DATETIME과 LocalDateTime의 UTC 보관 규칙을 유지한다.

## 인증·입력·오류

보호 Controller는 CurrentMemberId.require(authentication)로 회원 ID를 얻는다. 클라이언트 회원 ID를 권한 근거로 사용하지 않는다. 로그인 principal은 AuthenticatedMember 계약을 구현한다. 관리자 경로는 Security에서 ADMIN을 확인하고 변경 서비스는 현재 DB 권한도 확인한다.

DTO는 형식, 서비스는 범위·정규화·소유권·상태를 검증한다. 공통 BusinessException·ApiExceptionHandler·SecurityErrorWriter의 code·message·fieldErrors·traceId 형식을 사용한다. 비밀번호·세션·토스 시크릿·요청 본문을 로그로 남기지 않는다.

## 거래와 트랜잭션

업무를 시작하는 서비스가 전체 DB 트랜잭션을 관리한다. 주문·지갑·결제·환불·정산·원장·이력의 함께 성공해야 하는 변경은 같은 트랜잭션에 참여한다. 현재 주문 명령은 실행 회원 공유 잠금, 주문 잠금, 필요 시 구매자 지갑 잠금 순서로 처리한다. 주문 생성은 관련 회원 ID 오름차순 잠금과 상품 버전을 확인한다. 변경 시 [주문 생성](order-creation.md) 및 금융 문서의 순서를 대조한다.

외부 토스 승인 조율은 WalletTopupApprovalService의 NEVER 경계와 TopupApprovalTransactions를 사용한다. 외부 네트워크 호출을 DB 잠금 안에 넣지 않는다. 승인 결과 불명확 상태의 재확인은 같은 승인 요청을 무조건 반복하는 방식으로 바꾸지 않는다.

상태 변경·금액 검증·이력 기록은 한 줄에 압축하지 않는다. 조건과 실패 사유를 읽을 수 있게 작성한다. 기능 변경과 포맷 정리를 검토 가능한 범위로 구분한다.

## 실행·검증

실행은 [설정 안내](setup-guide.md)를 따른다. 개인 DB·시크릿은 Git에서 제외된 설정으로 관리한다. 테스트는 TEST_DB_*를 사용하고 개발 DB와 분리한다. test 프로필은 토스 외부 호출을 비활성화한다.

전체 검사는 백엔드 디렉터리에서 Gradle Wrapper의 clean build를 실행한다. 상태·금액 기능은 실제 MySQL에서 중복·동시 요청·롤백·타인 접근을 검증한다. 외부 결제 제공자는 대역·로컬 HTTP 서버로 검증하고 실제 토스 테스트 결제와 구분한다. 실행한 검사 수·실패·남은 수동 검증을 PR에 기록한다. CI 보고서는 Actions의 backend-test-reports에서 확인한다.
