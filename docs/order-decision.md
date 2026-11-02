# 주문 수락·거절 API

작성일: 2026-11-03. 담당: 정은찬. 실제 로그인·React 화면·주문 결제는 별도다.

## 계약

| 경로 | 입력 | 결과 |
| --- | --- | --- |
| POST /api/orders/{id}/accept | 본문 없음 | REQUESTED → AWAITING_PAYMENT |
| POST /api/orders/{id}/reject | reason 문자열 필수 | REQUESTED → REJECTED |

두 API 모두 인증 principal 회원 ID와 CSRF를 사용하고 판매자만 실행할 수 있다. 주문의 구매자는 403 FORBIDDEN, 비참여자와 없는 주문은 동일 404 RESOURCE_NOT_FOUND다. ID 형식·범위 오류는 400이다. 관리자 역할만으로 판매자 권한을 우회하지 않는다.

거절 사유는 일반 문자열 정책으로 앞뒤 Unicode 공백 제거·줄바꿈 통일 후 1~1000 코드 포인트를 허용한다. 숫자를 문자열로 자동 변환하지 않는다. 입력 오류는 400 VALIDATION_FAILED다. 수락은 accepted_at·updated_at, 거절은 updated_at만 갱신한다. 거절을 직접 취소로 기록하지 않으며 accepted_at·cancelled_at·paid_at·납기는 새로 설정하지 않는다.

성공은 200 OrderDetail과 Cache-Control: no-store다. ACCEPTED/REJECTED 이력의 actor는 인증 판매자, fromStatus는 REQUESTED, toStatus는 실제 전환 결과다. 수락 사유는 null, 거절 사유는 정규화한 원문이다. 시각은 서버 Clock의 Instant를 UTC·마이크로초로 변환해 사용한다.

이미 수락·거절했거나 다른 상태이면 동일 명령 재전송도 409 ORDER_STATE_CONFLICT다. 생성 요청의 멱등 200과 구분한다. 재시도 때 시각·이력을 덮어쓰지 않는다. 상품이 숨김·판매 중지되었어도 기존 주문의 결정은 가능하다.

## 트랜잭션·잠금

OrderDecisionService의 READ_COMMITTED 트랜잭션에서 실행자 회원 FOR SHARE → 참여자 조건의 주문 FOR UPDATE → 판매자·상태 확인 → 상태/시각·이력 flush → 상세 응답 조립을 수행한다. 상태·이력·응답 조립 중 예외가 발생하면 전체 변경을 롤백한다. 이력의 actor FK가 참조하는 회원 공유 잠금을 주문보다 먼저 확보해, 회원→주문 순서의 생성 재전송과 잠금 순서가 엇갈리지 않도록 했다. 생성 서비스의 회원 ID 오름차순 잠금, 향후 주문→지갑 순서는 변경하지 않았다.

수락/수락·거절/거절·수락/거절 경쟁은 주문 잠금으로 직렬화해 한 요청만 성공한다. Clock이 DB 시각보다 과거이거나 MySQL 범위를 벗어나면 변경하지 않고 실패한다. 실제 결제·취소 등 미구현 명령과의 경쟁, 다중 서버 부하·모든 조합의 데드락 부재를 검증했다는 의미는 아니다.

## 검증 범위

OrderDecisionApiIntegrationTests에서 실제 필터·HTTP·서비스·MySQL을 연결하고 로그인만 테스트 principal로 제공한다. 정상 전환·UTC SQL 원문·거절 사유 경계·권한/CSRF·중복·동시 경쟁·실제 이력 FK 오류 시 롤백과 재시도·생성 재전송과의 잠금 호환을 검사한다. 프런트·스키마·V1/V2는 변경하지 않는다. 최종 테스트 개수와 실행 결과는 [작업표](work-plan.md)를 따른다.
