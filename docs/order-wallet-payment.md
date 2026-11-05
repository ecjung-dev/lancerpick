# 주문 적립금 결제

2026-11-05 신규 정책: CONTRACT_V3는 [전액 보관·판매자 적립금 지급·자동 구매 확정](full-funding-policy.md)을 따른다. V7은 main `2fec099`에 반영돼 업무 테이블 30개와 새 모드를 제공한다. 아래 선금/잔금·보상·V1~V6/29개 설명은 해당 버전의 이력이다. 신규 V3 금액 명령·스케줄러의 완료를 뜻하지 않는다.

> 문서 읽기 기준(2026-11-04): 아래 날짜·브랜치별 구현 및 검증 설명은 당시 기록이다. 현재 반영 여부·남은 작업은 [현황표](current-status.md), 실행 설정은 [루트 README](../README.md), API 계약은 [API 명세](api-spec.md)를 따른다. 과거의 미구현·병합 전 표현과 검사 수를 현재 상태로 확대하지 않는다.

저장소 후속 변경: [JPA·QueryDSL 전환](finance-persistence.md)에 따라 Payment Entity·JpaRepository로 전환했다. 아래 최초 구현의 JDBC 설명은 변경 전 기록이며 현재 저장소 구조는 후속 문서를 따른다. API·잠금·금액 정책은 유지한다.

작성일: 2026-11-03. 담당: 황준범. 브랜치: feature/order-wallet-payment.

## API와 연결

POST /api/orders/{id}/payment. 인증 구매자와 CSRF가 필요하다. 본문은 `{"agreedToTerms":true}`이며 문자열 "true"·숫자·false·누락은 거절한다. 금액·회원 ID·수수료는 요청에서 받지 않는다. 성공은 200 OrderDetail·Cache-Control: no-store다. 실제 로그인과 결제 화면 연결은 별도다.

OrderPaymentController는 인증 ID·입력을 확인하고 OrderPaymentService를 호출한다. 서비스의 READ_COMMITTED 트랜잭션 안에서 실행자 회원 FOR SHARE → 참여자 주문 FOR UPDATE → 구매자 확인 → 기존 결제 확인 → 구매자 지갑 FOR UPDATE 순서로 처리한다. 기존 주문 명령의 회원→주문 순서와 호환되며, 서로 다른 주문도 같은 지갑 잠금으로 잔액을 보호한다.

최초 결제는 AWAITING_PAYMENT에서만 가능하다. 저장된 orders.price_amount로 결제하며 payments(PAID,1000bps), 지갑 차감, PAYMENT 원장, 조건 확인/결제/납기 시각, IN_PROGRESS 상태, PAID 이력을 함께 반영한다. 조회 응답 조립까지 실패하면 전체 롤백한다. 원장 키는 PAYMENT:{paymentId}이고 결제 order_id·원장 event_key/(payment_id,type)의 기존 UNIQUE를 유지한다.

결제 전 동의는 이 요청에서 서버 시각으로 기록한다. 납기는 결제 시각 + 주문 소요일×24시간이다. UTC 마이크로초 시각을 공유하고 JDBC의 LocalDateTime 직접 바인딩·기존 JPA LOCAL_DATE_TIME 매핑을 사용한다. PaymentRepository는 기존 조회 전용 SQL 저장소들과 같은 JdbcTemplate 방식이며 서비스 트랜잭션에 참여한다. 새 테이블·마이그레이션은 추가하지 않는다.

## 반복 요청·오류

- PAID이고 진행·검수·수정·완료 상태이면 원결제·원장 일치를 확인해 현재 주문을 반환한다. 추가 차감·이력·시각 변경은 없다.
- REFUNDED는 409 PAYMENT_ALREADY_REFUNDED다. 최초 결제의 잘못된 주문 상태는 409 ORDER_STATE_CONFLICT다.
- 잔액 부족은 409 INSUFFICIENT_BALANCE다. 지갑·결제·원장·주문 변경을 확정하지 않는다.
- 결제 후 상태인데 원결제가 없거나 금액/원장이 모순되면 500 DATA_INTEGRITY_ERROR다. 자동 복구·재차감하지 않는다.
- 판매자는 403, 비참여자·없는 주문은 동일 404다. 인증·CSRF·입력 오류는 공통 정책을 따른다.

상품 숨김/판매 중지는 기존 주문의 결제를 차단하는 조건으로 추가하지 않는다. 토스 충전과 별개인 구매 적립금 결제이며 외부 통신·토스 결제 취소를 수행하지 않는다. 구매 확정·취소·환불·판매 수익 정산은 후속 기능이다.

## 검증

OrderPaymentIntegrationTests에서 실제 Security→HTTP→서비스→격리 MySQL로 정상/반복·권한·CSRF·정확한 boolean·잔액 부족·동시 결제·원장/결제 개수·SQL UTC 원문·납기·환불 후 재결제·완료 재호출·모순 기록을 검사한다. 이력 저장 경계에서 실제 FK 위반을 발생시켜 잔액·원장·결제·주문 롤백도 확인한다. 테스트 인증은 실제 로그인 구현과 구분한다. 실행 결과는 검증 완료 후 기록한다.

2026-11-03 격리 Docker MySQL 8.4에서 결제 전용 13건 및 전체 clean build --offline 361건이 실패·오류·스킵 없이 통과했다. 개인 DB·키를 사용하지 않았고 임시 컨테이너·볼륨은 정리했다. 프런트 변경은 없으며 실제 로그인·화면·원격 CI 결과는 이 로컬 검증에 포함하지 않는다. 취소/환불 API가 아직 없으므로 해당 상태의 반복 결제 검사는 DB fixture로 재현했고, 실제 취소 명령과의 동시성은 취소 기능 연결 후 추가해야 한다.
