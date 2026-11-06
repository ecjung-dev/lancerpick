# 주문별 1:1 저장형 채팅

후속 로컬: 저장형 커밋 `8e92e28` 이후 [WebSocket/STOMP·재접속 복구·소켓 세션 폐기](order-chat-realtime.md)를 연결했다. 아래는 저장형 1차 단계의 범위와 검증 이력이며 ‘소켓 미포함/후속’은 당시 기준이다.

2026-11-05, `feature/order-chat-storage`. 금융 기능이 반영된 main `03b5687`에서 시작한 후속 구현이다. [4인 설계](advanced-design-four-person.md)의 J1 중 저장·조회·화면 단계이며 **WebSocket/STOMP·실시간 전달·Redis·읽음 표시·첨부·메시지 수정/삭제는 포함하지 않는다**. 기존 HTTP 세션·CSRF·JPA/QueryDSL을 사용한다.

## 확정 정책

- 주문 한 건이 대화방 한 개다. 기존 주문도 사용할 수 있고 별도 방 생성은 없다.
- 구매자·판매자만 조회/작성한다. 관리자 권한만으로 타인의 대화 원문을 열람하거나 전송하지 못한다. 타인/없는 주문은 404다.
- 사용자 확인(2026-11-05): **종료 후에는 조회만 허용**한다. 작성 가능 상태는 REQUESTED·AWAITING_CONTRACT·AWAITING_PAYMENT·IN_PROGRESS·AWAITING_CONFIRMATION·REVISION_IN_PROGRESS·과거 V2의 AWAITING_BALANCE다. COMPLETED·REJECTED·CANCELLED·TERMINATED 및 알 수 없는 상태는 새 메시지를 차단한다.
- 본문은 공통 문자열 정리(양끝 Unicode 공백 제거·CRLF/CR→LF) 후 1~2,000 코드 포인트다. HTML을 실행하거나 링크의 외부 내용을 수집하지 않는다.
- 채팅으로 주문 가격·조건·상태·납기·계약 버전·거래 updatedAt을 변경하지 않는다. 별점/거래 이력이나 상대방 알림을 메시지마다 생성하지 않는다.
- 종료 직전 저장됐으나 응답만 유실된 메시지는 **같은 발신자·같은 키·같은 내용**으로 종료 후 재요청해도 기존 메시지를 반환한다. 종료 후 새 메시지를 허용하는 것은 아니다.

## API

기존 보호 API 규칙을 따르며 서버가 `CurrentMemberId.require()`로 발신자를 결정한다. POST는 CSRF가 필요하다. 응답은 `Cache-Control: no-store`다.

| API | 입력·응답 |
| --- | --- |
| `POST /api/orders/{orderId}/chat/messages` | `{"clientMessageId":"UUID","content":"본문"}`. 최초 201, 같은 요청 재전송 200. 메시지 DTO 반환 |
| `GET /api/orders/{orderId}/chat/messages` | 최신 메시지 조회. 기본 limit=50, 1~100 |
| 같은 GET + `beforeSequence=순번` | 해당 순번 미만의 과거 메시지 조회 |
| 같은 GET + `afterSequence=순번` | 해당 순번 초과의 누락 메시지 복구. 처음부터 복구할 때 0 허용 |

두 커서는 동시에 받지 않는다. before는 양수, after는 0 이상이며 Long 범위로 검증한다. UUID는 소문자 표준형으로 정리한다. senderId·sequence·시각은 서버 값이며 클라이언트가 정할 수 없다.

메시지 응답 필드: `id`, `orderId`, `sequence`, `senderId`, `clientMessageId`, `content`, `createdAt`. ID/순번은 **JSON 문자열**, 시각은 UTC ISO 8601이다. 목록은 `items`, `direction`(BEFORE/AFTER), `nextCursor`(추가 데이터가 없으면 null), `hasMore`, `lastSequence`, `canSend`를 반환한다. 두 방향 모두 items는 순번 오름차순이다. lastSequence는 해당 조회 스냅샷에서 확인한 주문의 마지막 순번이며 nextCursor와 역할이 다르다.

| 오류 | 의미 |
| --- | --- |
| 400 VALIDATION_FAILED | 잘못된 본문·UUID·커서·limit |
| 401 AUTHENTICATION_REQUIRED | 세션 없음/폐기 |
| 403 CSRF_INVALID | 변경 요청 CSRF 검증 실패 |
| 404 RESOURCE_NOT_FOUND | 타인/없는 주문 |
| 409 CHAT_CLOSED | 종료 주문에 새 메시지 작성 |
| 409 IDEMPOTENCY_KEY_REUSED | 같은 발신자의 키를 다른 정리된 본문에 재사용 |

## DB·트랜잭션

기존 V5 `order_chat_messages`·V6 `orders.chat_sequence`를 사용하며 migration·테이블·컬럼 추가는 없다. 쓰기에는 JPA, 커서 조회에는 QueryDSL을 사용한다.

전송은 READ_COMMITTED에서 **실행 회원 공유 잠금 → 주문 배타 잠금 → 기존 키 확인 → 상태 확인 → chat_sequence 증가·메시지 저장**을 한 트랜잭션으로 처리한다. 주문의 순번을 사용하며 전역 메시지 AUTO_INCREMENT를 커밋 순서로 가정하지 않는다. 같은 주문의 전송과 거래 상태 변경은 같은 주문 잠금으로 직렬화된다. 기존 UNIQUE(order_id,sequence)·UNIQUE(order_id,sender_id,client_message_id)도 유지한다. 같은 UUID라도 발신자가 다르면 각자의 요청이다.

조회는 REPEATABLE_READ에서 당사자 여부·마지막 순번·페이지를 같은 스냅샷으로 읽는다. 과거는 DESC로 limit+1개를 읽고 선택된 페이지를 뒤집으며, 복구는 ASC로 읽는다. 순번 인덱스로 조회하고 전체 개수/offset 조회는 하지 않는다.

메시지 저장 실패/상위 트랜잭션 실패 시 순번도 롤백된다. 이 경계에는 소켓·메일·외부 통신이 없다. 요청/응답 DTO의 toString은 본문을 숨긴다.

## 프런트 연결과 확인 방법

주문 상세 `/orders/{id}`에 ‘주문 대화’를 표시한다. 구매자·판매자를 서로 다른 브라우저/시크릿 창에 로그인해 같은 주문을 열면 된다. 종료 주문에는 작성창이 없고 내역만 보인다. 현재는 **대화 새로고침·화면 재진입으로 새 메시지를 확인**하며 실시간 수신을 구현한 것으로 표현하지 않는다.

기능 파일은 `features/order-chat/types.ts`·`api.ts`·`queries.ts`·`validation.ts`·`messages.ts`·`components`로 나눴다. 공통 HTTP/CSRF·Button·AsyncState를 사용하고 본문은 React 일반 텍스트로 표시한다. 과거 페이지를 순번으로 병합/중복 제거하며 BIGINT를 Number로 바꾸지 않는다.

메시지 조회 키에 현재 회원 ID를 포함한다. 계정 전환 시 공통 accountCache가 조회 취소/삭제, 컴포넌트 key가 입력 상태를 초기화한다. 전송 전후 서버의 회원을 다시 확인하고 변경 응답을 개인 캐시에 직접 삽입하지 않는다. 배경 세션 재확인으로 같은 계정의 입력을 지우지 않으며, 조회/권한 실패 시 이전 대화는 숨긴다.

POST를 자동 재전송하지 않는다. 결과 불명확 시 같은 화면에서 본문/UUID를 유지하고 편집을 잠근다. 사용자가 ‘대화 새로고침’으로 저장된 키를 확인하거나 **동일 키·동일 내용**으로 재전송한다. 브라우저 전체 새로고침·탭 종료 시 초안/키는 사라지므로 서버에 저장된 내역부터 확인한다. 브라우저 저장소에 대화 초안을 보관하지 않는다.

## 검증·다음 단계

격리 MySQL에서 채팅 통합 7건 통과: 본인/상대 조회·발신자 변조 무시, CSRF/익명/타인·관리자 차단, 동시 중복 전송·양측 순번, 카운터/메시지 롤백, 종료 상태·종료 후 기존 요청 재확인, 양방향 커서, Unicode 한도. 프런트 신규 8건은 큰 ID/커서·CSRF·재전송 키·HTML 이스케이프·종료 화면·권한 실패 숨김·계정 변경 시 조회 중단/캐시 제거를 검사했다.

최종 전체 결과(2026-11-05, main `03b5687` 기반 로컬 소스): 격리 MySQL 8.4에서 **백엔드 clean build 793건 통과(실패·오류·건너뜀 0)**, **프런트 180건·lint·TypeScript 포함 build 통과**. 기본 프로필의 DB 없는 HTTP 검사도 포함한다. 기존 500kB 초과 프런트 청크 경고는 남아 있다. 실제 브라우저 두 계정 대화 확인과 원격 CI는 아직이다. 개발 DB·키를 사용하지 않았고 임시 검사 컨테이너는 정리했다.

J1의 다음 단계는 같은 서비스를 재사용하는 WebSocket/STOMP 전송·구독 권한, 커밋 후 전달, 순번 기반 누락 복구다. C2의 세션 만료/비밀번호 변경/계정 전환 시 서버 소켓 종료도 함께 연결한다. J1 전체 완료·실시간 채팅 완료로 표시하지 않는다.
