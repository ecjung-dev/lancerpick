# 관리자 회원 조회·제재 관리

2026-11-09 황준범, `feature/admin-member-management`, 분기 main `c0009f2`(#148). 기존 V10 제재 기반과 U7 주문 차단·U8 타이머 연결을 관리자 HTTP/화면에 연결했다. 로컬 구현 기록이며 push·PR·main 병합과 구분한다. 새 마이그레이션은 없다.

## 범위와 정책

| 상태 | 허용·차단 |
| --- | --- |
| 일반 이용 제한 | 로그인·기존 거래 처리는 허용. 신규 주문·상품 등록·판매자 수락은 차단 |
| 보안 잠금 | 로그인·기존 인증 세션 차단. 기존 타이머 중단 연결을 호출하고 진행 중 거래는 관리자 확인 |
| 기간 만료 | 서버 현재 시각으로 즉시 제한 해제. 만료 갱신 Batch나 이력 삭제 없음. 폐기된 세션·중단된 타이머는 복원하지 않음 |

서로 다른 종류는 공존하며 대표 상태는 `SECURITY_LOCK > GENERAL_RESTRICTION > NONE`이다. 같은 종류를 다시 적용하면 활성 제재를 해제하고 새 기록을 만든다. 주문 자동 취소·환불·정산 회수는 하지 않는다. 본인·관리자 계정에는 적용/해제를 허용하지 않는다. 역할 부여·회원 삭제·잔액 조정은 범위 밖이다.

## API 계약

모든 경로는 `/api/admin/members` 하위이며 세션 ADMIN 권한과 현재 DB 관리자 역할을 검사한다. POST는 기존 CSRF 보호를 유지한다. 성공 응답은 `Cache-Control: no-store`, 오류는 공통 API 오류 형식이다.

| 메서드·경로 | 입력 | 성공 |
| --- | --- | --- |
| GET `/` | `q`, `restriction`, `page=0`, `size=20` | 200 PageResponse 회원 목록 |
| GET `/{memberId}` | 회원 ID | 200 회원 상세·서버 시각·활성 제재 |
| GET `/{memberId}/sanctions` | `page=0`, `size=20` | 200 PageResponse 전체 제재 이력 |
| POST `/{memberId}/sanctions` | 아래 적용 JSON | 200 `{memberId, sanctionId}`. 신규/동일 요청 재전송 모두 200 |
| POST `/{memberId}/sanctions/{sanctionId}/revoke` | `{ "reason": "처리 사유" }` | 204 |

```json
{
  "kind": "GENERAL_RESTRICTION",
  "reason": "운영 기준 위반 확인",
  "endsAt": "2026-11-16T06:00:00Z",
  "requestKey": "11111111-2222-4333-8444-555555555555"
}
```

- `q`는 앞뒤 공백 제거 후 100 코드 포인트 이하. 숫자로만 구성되면 회원 번호 정확 일치(0·범위 초과는 빈 목록), 나머지는 이메일·표시 이름 부분 일치다. `%`, `_`, `!`는 와일드카드로 해석하지 않는다. 최신 가입·ID 내림차순이다.
- `restriction`은 생략 또는 `NONE`, `GENERAL_RESTRICTION`, `SECURITY_LOCK`. 공존 시 대표 상태로 필터링한다. page는 0 이상, size는 1~100. 전체 이력은 적용 시각·ID 내림차순이다.
- ID는 JSON 문자열, 시각은 UTC 오프셋 ISO 형식이다. 입력 종료 시각은 오프셋 필수이며 UTC 변환 후 DB 연도 범위 1000~9999와 실제 미래 시각을 검사한다. 사유는 공통 정규화 후 1~1000 코드 포인트다. kind/reason/endsAt/requestKey의 숫자·불리언 강제 문자열 변환은 허용하지 않는다.
- 적용 UUID는 표준 8-4-4-4-12 형식이다. 동일 회원·키·관리자·조건은 기존 ID를 반환하며 이미 만료/해제돼도 다시 적용하지 않는다. 같은 키의 조건이 다르면 409 `SANCTION_REQUEST_CONFLICT`다.
- 해제는 제재 ID를 대상으로 한다. 반복 호출은 최초 해제 사유·시각을 보존한다. 이전 제재 해제가 새 교체 제재를 해제하지 않는다. 자연 만료 기록에 대한 API 해제는 기존 내부 서비스대로 이력을 남길 수 있고 화면은 현재 활성 제재만 선택지로 제공한다.
- 인증 없음 401 `AUTHENTICATION_REQUIRED`, 권한 없음 403 `FORBIDDEN`, CSRF 실패 403 `CSRF_INVALID`, 잘못된 입력 400 `VALIDATION_FAILED`, 없는 회원/타 회원 제재 404 `RESOURCE_NOT_FOUND`다. 기존 새 거래 차단 403 `MEMBER_RESTRICTED`·보안 잠금 로그인 403 `MEMBER_LOCKED`는 유지한다.

목록 필드: `id, email, displayName, role, restriction, createdAt`. 상세는 `member, emailVerifiedAt, serverTime, canManage, activeSanctions`다. 제재 필드는 `id, kind, state, reason, appliedBy, startsAt, endsAt, revokedAt, revokedBy, revokeReason, timerStopDeliveredAt`. state는 `ACTIVE/EXPIRED/REVOKED/SCHEDULED`이며 SCHEDULED는 미래 시작 데이터 조회용으로 신규 예약 적용 기능은 없다. 비밀번호·해시·인증 버전·요청 키·비밀번호 권고 기준일은 조회하지 않는다.

## 트랜잭션과 다른 담당 영역

- `AdminMemberQueries`는 JPA/QueryDSL 스칼라 조회다. `AdminMemberService`의 읽기 전용 REPEATABLE_READ 안에서 요청별 서버 시각과 DB 스냅샷을 고정한다. 조회에 회원 쓰기 잠금을 추가하지 않는다.
- Controller는 기존 `MemberSanctionCommandService`에 위임한다. 이 서비스의 `NEVER` 경계를 유지해 외부 트랜잭션으로 감싸지 않는다. 기존 회원 ID 순서 잠금·대상 배타 잠금 아래 제재 기록/인증 버전을 함께 커밋하고, 커밋 뒤 타이머 중단을 전달한다.
- 보안 잠금 전달 미완료는 상세 경고로 표시한다. 기존 U8 시작/만료 재검사를 유지하며 `SecurityLockDelivery.retryPending`의 자동 스케줄러 연결은 아직 별도 후속이다. 이번 화면에 재시도 API를 임의 추가하지 않았다.
- **박소현 영역 영향:** `ProductManagementService.create`의 기존 회원 배타 잠금 뒤 `MemberSanctionQuery`를 검사해 제한 중 상품 등록을 403으로 차단했다. 상품 수정·판매 중지와 기존 거래는 바꾸지 않았다. 주문 생성/수락 차단은 기존 정은찬 구현을 재사용한다.
- 김창재의 인증 필터·로그인과 정은찬의 타이머 연결을 재사용하며 기존 Flyway·테이블·Security 경로 설정은 변경하지 않는다.

## 화면과 요청 복구

관리자 메뉴의 **회원 관리** 또는 `/admin/members`에서 검색·상태 필터·페이지·상세로 접근한다. 상세는 현재 상태·제재 적용/해제·전체 이력을 제공한다. `datetime-local` 입력은 기기 시간대와 관계없이 한국 시간으로 해석한다. 실제 허용 여부는 서버가 재검증한다.

조회는 30초 갱신·포커스 재조회·no-store이며 계정 전환 시 공통 캐시 정리를 따른다. 401/403이면 이전 상세·처리 입력을 숨긴다. 적용 전 영향 확인 체크가 필요하고 전송 중 재클릭을 막는다.

결과가 불명확하면 전체 원래 요청과 UUID를 계정별 QueryClient 메모리에 보존한다. 화면을 나갔다 돌아와도 **같은 요청으로 결과 확인**을 사용하며 새 UUID를 만들지 않는다. 재시도 거절만으로 최초 요청 실패를 단정하지 않는다. 계정 변경/로그아웃 때 정리하며 브라우저 새로고침·종료를 넘는 영속 보관은 제공하지 않는다. 성공 뒤 목록·상세·이력을 재조회한다.

## 검증과 한계

- 격리 Docker MySQL 8.4 관련 백엔드 119건 통과. 새 관리자 HTTP 통합 9건에서 실제 로그인/기존 세션 차단, 일반 제한 후 상품 등록 차단, 만료·교체·중복/동시 요청, 권한·CSRF·입력·응답 비밀정보 제외를 확인했다.
- 프런트 typecheck·lint·test:api **292건**·build 통과. 기존 번들 500kB 경고는 남는다.
- Edge 응답 대역 브라우저: 검색/뒤로 이동, 확인 체크·연속 클릭, 저장 후 응답 503·경로 이동·동일 키 재시도, 두 제재 공존·선택 해제, 정확한 만료 경계, 입력 초기화, 관리자 대상 보호, 403 후 캐시 숨김, 320px 가로 넘침 없음을 확인했다.
- 브라우저 검사는 API 대역이며 실제 서버·브라우저·소켓 통합 확인이나 원격 CI 통과를 뜻하지 않는다. 사용자 개인 DB에 제재를 생성하지 않았다.

격리 Docker MySQL 8.4 전체 백엔드 `clean build`는 **1,030건 통과**(실패·오류·건너뜀 0)했다. 이 실행 뒤 해제 DTO를 기존 문의 DTO에서 회원 제재 전용 `AdminMemberSanctionRequest.Revoke`로 분리하고 오래된 주석을 정정했다. 최종 소스의 관리자·회원 제재·상품·주문·타이머·로그인·공개 메인 관련 **119건 재검사도 통과**(실패·오류·건너뜀 0)했다. 검사 수를 합산하지 않는다. 검사에 사용한 임시 MySQL 컨테이너는 종료 후 제거했다.

기동 시 새 Controller를 읽도록 백엔드를 재시작한다. 관리자 계정으로 로그인한 뒤 `http://localhost:5173/admin/members`에서 확인할 수 있다.
