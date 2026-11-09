# 관리자 계약 PDF 운영 조회 — K6 내부 연결 준비

2026-11-09, 황준범. 기준 main `de0b72f`(#124), 브랜치 `feature/admin-contract-pdf-inspection`. 관리자 API·화면·실제 재처리 실행은 연결 담당 작업 이후에 진행한다.

## 제공 범위

`AdminContractPdfQueryService.inspect(long adminId, long orderId, Integer version)` → `AdminContractPdfResponse`.

- `version=null`이면 현재 계약, 지정하면 해당 버전을 조회한다. 현재 DB의 관리자 역할을 먼저 확인한다.
- 주문/계약 ID는 문자열, 시각은 UTC다. 버전·상태·현재 버전 여부·생성/변경 시각·조회 시각·파일 메타데이터 유무·생성 후보 판단을 반환한다.
- 파일 메타데이터 유무는 LOCAL 백엔드·키·양수 크기/상한·32바이트 해시의 DB 필드 조합만 검사한다. 실제 파일 존재·바이트·해시 일치를 확인했다는 뜻이 아니다.
- 계약 본문·특약·원문 해시·파일 경로·파일 해시·생성 처리권 키·회원 정보를 반환하지 않는다.
- JPA 읽기 전용 REPEATABLE_READ 트랜잭션이다. 파일 I/O·외부 요청·생성·삭제·금융 변경·처리권 획득·감사 이력 저장은 없다. 새 DDL·REST API·화면은 없다.

## 생성 후보 판단

후보는 조회 시점 정보이며 **관리자 실행 권한이나 예약이 아니다**. 기존 당사자 PDF 생성 규칙과 동일한 주문 상태·기한을 기준으로 하며 `contracts.generation-timeout`(기본 2분, 0초 초과~30분 이하)을 사용한다.

| 조건 | 결과 |
| --- | --- |
| 이전/교체된 버전 | `NOT_CURRENT_VERSION` |
| 수락 전·결제 후·종료 주문 등 생성 불가 상태 | `ORDER_NOT_WRITABLE` |
| 대기 중 취소 요청 | `CANCELLATION_PENDING` |
| 계약 변경 시각이 서버 시각 이후 | `CLOCK_BEFORE_UPDATE` |
| DRAFT | `INITIAL_GENERATION` 후보 |
| FAILED | `RETRY_FAILED` 후보 |
| GENERATING, 변경 시각+기한 전 | `GENERATION_ACTIVE` |
| GENERATING, 변경 시각+기한 도달/초과 | `RETRY_EXPIRED` 후보 |
| READY·SIGNED | `DOCUMENT_READY`·`DOCUMENT_SIGNED` (재생성 후보 아님) |
| 지원하지 않는 상태 | `UNSUPPORTED_STATE` |

여러 조건이 겹치면 표의 위쪽 차단 조건을 먼저 적용한다. 결과의 `eligible=true`일 때만 kind가 후보 종류이며 reason은 null이다. false이면 kind는 NONE이고 reason이 있다. 이 판단에는 파일 유실 복구나 서명 문서 재생성 정책을 추가하지 않았다.

## 오류와 후속 연결

- 현재 회원 없음/로그인 ID 없음: 401 `AUTHENTICATION_REQUIRED`. 일반 회원: 403 `FORBIDDEN`.
- 관리자 권한 확인 후 잘못된 주문 ID·버전: 400 `VALIDATION_FAILED`.
- 없는 주문·계약·버전 또는 LEGACY_V1 주문: 404 `RESOURCE_NOT_FOUND`.
- 향후 관리자 Controller는 세션에서 얻은 관리자 ID로 호출하고 관리자 접근 통제·`Cache-Control: no-store`를 적용한다. 요청의 관리자 ID를 신뢰하지 않는다.
- 재처리 실행은 김창재의 관리자 사유·감사 이력 계약과 맞춘 후 별도 명령 서비스로 연결한다. 기존 구매자/판매자 ID를 대신 넣어 당사자 API를 호출하지 않는다. 실행 시 관리자 권한·주문 잠금·현재 계약 버전·취소 요청·시각·처리권을 다시 검사해야 한다.

## API·화면 연결 — 2026-11-09 (김창재)

`GET /api/admin/orders/{orderId}/contract-pdf?version=`(no-store)로 이 서비스를 그대로 호출하고, `/admin/finance/orders/:id` 화면의 "계약 PDF" 칸에 버전·상태·파일 정보 유무·생성 후보 판단을 조회 전용으로 보여 준다. 재생성 실행 버튼은 없다. [API 9-3절](api-spec.md#9-3-관리자-금융계약-pdf-운영-조회-김창재).

## 검증

후속 실제 DB 확인(2026-11-09): Docker 기동 후 격리 MySQL 8.4에서 기존 계약 통합 17건·신규 PDF 운영 조회 통합 3건·단위 12건·기본 공개 메인 8건, 합계 40건 통과(실패·오류·건너뜀 0). 실제 JPA 역할 조회, 일반 회원 차단·관리자 승격 후 조회·강등 후 차단, 현재/이전 버전 선택, 없는 버전, JSON 민감정보 비노출을 확인했다. 조회 전후 계약 상태·생성 키·파일 경로·변경 시각·서명·아웃박스 이력이 동일함을 대조했다. DB에 저장된 GENERATING의 기한 직전/정확한 경계에서는 테스트용 고정 시각을 사용해 후보만 달라지고 처리권·상태가 바뀌지 않음을 확인했다. 개인 개발 DB와 실제 PDF 파일에는 접근하지 않았다. 전체 백엔드·실서버·브라우저·원격 CI 검증은 별도다.

초기 Docker 기동 전 기록: 신규 `AdminContractPdfQueryServiceTests` 12건과 DB 미사용 `DefaultCatalogApiTests` 8건, 합계 20건 및 백엔드 소스·테스트 전체 컴파일 통과. 당시 Docker가 꺼져 DB 검사는 미실행이었다. 이 결과는 위 후속 40건과 합산하지 않는다. 프런트 코드는 변경하지 않았다.
