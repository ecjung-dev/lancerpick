# 잔액·적립금 내역 조회

2026-11-05 신규 정책: CONTRACT_V3는 [전액 보관·판매자 적립금 지급·자동 구매 확정](full-funding-policy.md)을 따른다. V7은 main `2fec099`에 반영돼 업무 테이블 30개와 새 모드를 제공한다. 아래 선금/잔금·보상·V1~V6/29개 설명은 해당 버전의 이력이다. 신규 V3 금액 명령·스케줄러의 완료를 뜻하지 않는다.

> 문서 읽기 기준(2026-11-04): 아래 날짜·브랜치별 구현 및 검증 설명은 당시 기록이다. 현재 반영 여부·남은 작업은 [현황표](current-status.md), 실행 설정은 [루트 README](../README.md), API 계약은 [API 명세](api-spec.md)를 따른다. 과거의 미구현·병합 전 표현과 검사 수를 현재 상태로 확대하지 않는다.

현재 상태 2026-11-03: 가입 시 최초 지급·세션 로그인·내 정보 API가 구현되었다. 현재 로컬의 `/mypage`·`/mypage/wallet`은 세션 확인 후 실제 잔액·내역을 조회한다. [지갑 세션 연결](wallet-session-integration.md)과 [현황표](current-status.md)를 우선 확인한다. 아래 단계별 검증 기록은 당시 범위다.

2026-11-03 저장소 후속 변경: [프로젝트 JPA·QueryDSL 통일](project-persistence.md)에 따라 현재 운영 구현에서 JdbcTemplate·네이티브 SQL을 제거했다. 아래 이전 SQL 구현 설명·실행 결과는 당시 기록으로 보존하며 현재 구조와 최종 검증 범위는 후속 문서를 따른다.

V2 저장 확장 후속: [충전 신청 스키마](wallet-topup-schema.md)에서 원장 TOPUP 유형을 추가했다. 기존 조회 응답 필드는 유지하고 해당 유형의 orderId는 null이다. [승인·지급 서비스](wallet-topup-approval.md)와 화면은 구현되어 있으며 실제 토스 테스트 카드 검증은 별도다. 아래 검증 건수는 각 실행 당시 기록이다.

작성일: 2026-11-02. 담당: 황준범. 현재 작업 브랜치: `feature/wallet-initial-grant`.

## 1. 현재 범위

[API 명세 8절](api-spec.md#8-지갑정산-담당-황준범-후기-담당-박소현)에 맞는 조회 DTO와 내부 `WalletQueryService`, 내역 조회 Repository를 준비했다. 초기 지급과 같은 지갑 기반 작업으로 현재 브랜치에서 이어서 작성했다.

**조회 Controller·인증 회원 ID 계약·마이페이지 세션 연결까지 구현했다.** 아래 경로는 인증이 필요한 실제 Controller다. 같은 origin의 유효한 로그인 세션으로 조회하며, 로그인·가입 폼과 전역 인증 상태 연결은 아직 남아 있다. demo 프로필에서는 지갑 Controller·Service를 등록하지 않는다.

| 보호 경로 | 내부 서비스 | 응답 |
| --- | --- | --- |
| GET /api/me/wallet | getBalance(authenticatedMemberId) | balance, currency=KRW, isTest=true |
| GET /api/me/wallet/entries?page=0&size=20 | getEntries(authenticatedMemberId,page,size) | PageResponse&lt;WalletEntryResponse&gt; |

조회 서비스는 readOnly 트랜잭션을 사용한다. 조회 중 지갑을 생성하거나 최초 적립금을 지급하지 않는다. 잔액 조회 결과는 표시용이며 결제 처리 시에는 지갑 잠금 후 다시 검증해야 한다.

## 2. 기능별 파일

기본 경로는 `LancerPick/src/main/java/com/example/lancerpick/`이다.

| 파일 | 책임 |
| --- | --- |
| wallet/dto/WalletBalanceResponse.java | 현재 테스트 잔액·통화·테스트 여부 응답 |
| wallet/dto/WalletEntryResponse.java | 내역 ID·유형·증감액·처리 후 잔액·주문 ID·UTC 시각 |
| wallet/repository/WalletEntryView.java | 필요한 컬럼만 받는 내부 조회 projection |
| wallet/repository/WalletEntryRepository.java | 회원별 내역 수·페이지 조회, payments LEFT JOIN |
| wallet/service/WalletQueryService.java | 입력 확인·지갑 확인·내역 DTO 변환·페이지 응답 |
| wallet/controller/WalletController.java | 인증된 본인 잔액·내역 GET, 페이지 입력 검증, Cache-Control: no-store |
| common/security/AuthenticatedMember.java | 인증 principal이 제공할 최소 회원 ID 인터페이스 |
| common/security/CurrentMemberId.java | 인증 상태·principal 타입·양수 ID 확인, 미충족 시 401 |

Entity와 내부 projection을 API로 직접 반환하지 않는다. `memberId`, `paymentId`, `eventKey`는 내역 응답에 포함하지 않는다.

## 3. 저장 구조와 응답 규칙

기존 V1을 그대로 사용하며 컬럼·테이블·마이그레이션을 추가하지 않았다.

```text
wallet_entries.member_id → 조회 대상 지갑 필터
wallet_entries.payment_id → payments.id → payments.order_id
```

초기 지급은 payment_id가 NULL이므로 LEFT JOIN하고 응답의 orderId도 NULL이다. 결제·환불 내역의 주문 ID는 조인 결과로 가져오며 원장에 중복 저장하지 않는다. Payment Entity를 임의로 추가하지 않았다.

ID는 문자열로 변환한다. BIGINT 값이 JavaScript 안전 정수 범위를 넘더라도 주문 ID를 숫자로 변환해 전달하지 않는다. 금액은 기존 계약의 원 단위 정수다. DB의 UTC DATETIME은 네이티브 조회에서 JDBC UTC 기준 Instant로 받고 응답 경계에서 UTC OffsetDateTime으로 변환한다.

첫 검증에서는 네이티브 Timestamp를 LocalDateTime으로 투영할 때 JVM 기본 시간대가 섞여 9시간 이동하는 문제를 재현했다. Instant 투영으로 수정하고 UTC·Asia/Seoul·America/Los_Angeles 기본 시간대에서 같은 응답 시각을 확인한다. ORM Entity의 LocalDateTime 매핑과 네이티브 projection 변환을 동일하다고 가정하지 않는다.

정렬은 `created_at DESC, id DESC`로 고정한다. page는 0부터, size는 1~100이다. Controller는 생략 기본값 page=0, size=20을 적용하고 형식·범위를 검증한다. 내부 서비스도 두 값을 확인한다.

범위를 벗어난 페이지는 items=[]로 반환하고 전체 건수와 전체 페이지 수는 유지한다. 내역이 없으면 totalElements=0, totalPages=0이다. 조회 오프셋은 long으로 계산·전달해 큰 page에서 int 곱셈·변환 오류를 피한다.

같은 읽기 서비스 호출은 하나의 readOnly 트랜잭션에서 실행된다. 페이지별 별도 요청 사이에 데이터가 추가되면 목록이 달라질 수 있으며, 여러 페이지를 하나의 고정된 스냅샷으로 보장하는 기능은 아니다.

## 4. 인증 담당자와 연결할 부분

지갑 쪽에서 사용할 최소 계약을 코드로 준비했다. 인증 담당자는 비밀번호·세션 또는 JWT 검증을 완료한 뒤 `AuthenticatedMember`를 구현한 principal을 `Authentication`에 저장하면 된다. 실제 인증 객체 클래스명·추가 필드는 담당자가 정하며, 인증 서비스 자체를 이번 작업으로 구현한 것은 아니다.

```java
// 형태 설명용 예시. 실제 로그인 구현 클래스가 아님.
public record LoginMember(Long memberId) implements AuthenticatedMember {}
```

1. WalletController는 Spring Security가 제공한 Authentication을 CurrentMemberId에 전달한다.
2. 인증 여부·principal 인터페이스·양수 회원 ID가 확인되면 그 ID만 WalletQueryService에 전달한다.
3. principal이 없거나 계약에 맞지 않으면 401이다. username을 숫자로 파싱하거나 요청의 memberId/X-Member-Id를 신뢰하지 않는다.
4. Spring Security의 기존 보호 정책을 유지한다. 테스트 전용 인증 주입을 일반 실행에 추가하지 않았다.
5. 실제 로그인·로그아웃·세션 만료 및 본인 조회를 통합 검증하고, React 로그인 상태와 연결하는 작업은 남아 있다.

현재 HTTP 테스트는 테스트에서 구성한 인증 principal이 정확한 서비스 인자로 전달되는지, 다른 회원 ID를 요청에 넣어도 바뀌지 않는지 확인한다. DB 필터 분리는 별도 MySQL 테스트의 결과다. 실제 로그인 성공부터 DB 응답까지의 전체 흐름 검증을 완료한 것은 아니다. 가입 호출은 [최초 지급 계약](wallet-initialization.md#2-회원가입에서-호출할-계약)을 따른다.

## 5. 오류와 없는 지갑

| 조건 | 내부 처리 |
| --- | --- |
| 회원 ID가 0 이하 | 400 VALIDATION_FAILED |
| page가 음수, size가 1~100 밖 | 400 VALIDATION_FAILED |
| 조회 대상 지갑 없음 | 404 RESOURCE_NOT_FOUND |
| 유효한 지갑의 내역 0건 | 정상 빈 목록, 자동 지급 없음 |

지갑이 없는 기존 회원을 보정하는 기능은 아니다. 조회 오류를 잡아 초기 지급 서비스를 호출하거나, 실패를 잔액 0원으로 바꾸지 않는다.

## 6. 검증

`WalletQueryIntegrationTests`를 격리된 MySQL의 기존 V1에 대해 실행한다. 개인 DB를 사용하지 않는다. 전용 TEST_DB_* 환경 준비는 [테스트 DB 안내](test-database.md)를 따른다.

```powershell
cd C:\project\LancerPick
.\gradlew.bat test --tests "com.example.lancerpick.wallet.WalletQueryIntegrationTests"
```

검사는 테스트 잔액 JSON 형태, 초기 지급 orderId=NULL, UTC 시각, 내부 필드 제외, 결제·환불의 주문 조인, 문자열 BIGINT ID, 회원별 결과·집계 분리, 정렬·페이지, 매우 큰 page, 빈 결과, 잘못된 입력, 없는 지갑의 무변경을 대상으로 한다.

테스트의 주문·결제는 관계 조회를 위한 SQL fixture다. 주문·결제 쓰기 Service의 구현·검증 결과로 사용하지 않는다. 실행 결과는 [작업표](work-plan.md)에 따로 기록한다.

## 7. 마이페이지 화면 준비

기존 AppLayout의 로고·검색·회원 메뉴·푸터와 흑백 스타일을 재사용한다. 회원 메뉴는 인증 담당자가 로그인 상태에 맞춰 연결할 예정이며 현재 로그인된 것처럼 바꾸지 않았다.

- `/mypage`, `/mypage/wallet`: 인증·API 연결 준비 안내와 미리보기 링크. 지갑 API를 호출하지 않는다.
- `/mypage/wallet/preview`: 별도 예시 데이터로 잔액·지급/결제/전액 환불 내역·페이지 이동을 확인한다. 실제 계정 정보가 아니라는 안내를 표시한다.
- `src/features/wallet/api.ts`: 공통 apiRequest를 통해 본인 잔액·내역을 요청한다. 회원 ID를 전송하지 않는다.
- `queries.ts`: AbortSignal 전달, 계정 캐시 구분, 페이지별 캐시를 담당한다.
- `cache.ts`: clearWalletCache(QueryClient)로 진행 중인 지갑 쿼리를 취소한 뒤 모든 계정의 지갑 캐시를 제거한다. 인증 담당자가 실제 로그아웃·계정 전환 흐름에서 호출해야 한다.
- `pages/WalletPage.tsx`: 준비 화면, 예시 미리보기, 실제 조회용 ConnectedWalletPage 및 공통 표시 컴포넌트.

프런트 실행 후 `http://localhost:5173/mypage/wallet/preview`로 확인한다. 별도 백엔드나 DB는 미리보기에 필요하지 않다. 예시 데이터는 API 오류를 숨기는 대체값으로 사용하지 않는다. 미구현 구매·판매·판매 수익·내 정보 메뉴는 이동 링크 없이 준비 중으로 표시한다.

실제 인증 상태가 준비되면 라우트의 WalletPage를 인증 보호 화면으로 바꾸고, 확인된 로그인 세션의 캐시 구분 값을 전달해 `<ConnectedWalletPage key={sessionKey} sessionKey={sessionKey} />`를 렌더링한다. key는 계정 변경 시 페이지 상태도 초기화한다. sessionKey는 실제 세션 쿠키·토큰이 아닌 캐시 구분 값이며 서버로 보내지 않는다. 미인증·세션 만료 시 해당 화면을 해제하고 로그인 안내로 전환한다. 어느 한 지갑 조회라도 401이면 화면은 잔액·내역을 숨기고 로그인 안내를 표시한다. 로그아웃·계정 전환 시 `await clearWalletCache(queryClient)`를 호출한다. 단순히 enabled=false로 바꾸는 것만으로 캐시가 삭제되지는 않는다.

현재 실제 로그인부터 본인 DB 잔액 표시까지의 통합 검증은 미완료다. 충전·외부 결제·판매 수익 화면은 이번 작업에 포함하지 않는다.

2026-11-02 화면 검증: 프런트 build(TypeScript 포함)·lint·기존 공통 API 테스트 7건 통과. 임시 Vite 서버에서 Edge headless로 예시 내역의 이전/다음·마지막 페이지 버튼 비활성화·390/320px 가로 넘침 없음·기본 진입 시 예시 잔액 미노출·준비/미리보기 화면의 API 요청 없음·페이지 오류 없음을 확인했다. 데스크톱·모바일 캡처를 육안 점검했다. 실제 지갑 HTTP API 통합 테스트를 의미하지 않는다.

## 8. Controller 추가 후 검증

2026-11-02 `WalletControllerTests` 15건과 기존 공통 테스트 18건, 합계 33건을 DB 없이 선택 실행해 통과했다. 미인증 401·알 수 없는 principal/잘못된 ID 차단·본인 ID 전달·다른 ID 쿼리/헤더 무시·페이지 기본값/형식/범위·큰 문자열 ID/UTC JSON·응답 no-store·지갑 없음 404를 확인했다. 조회 서비스는 mock이며 실제 로그인·DB 결합 테스트가 아니다. 이전 전체 MySQL 54건 실행 이후 이번에는 DB를 다시 실행하지 않았다.

```powershell
cd C:\project\LancerPick
.\gradlew.bat test --tests "com.example.lancerpick.wallet.WalletControllerTests" --tests "com.example.lancerpick.common.*"
```

프런트 lint·build와 test:api 8건을 통과했다. 추가한 wallet-cache 테스트는 진행 중인 조회 취소·모든 계정 지갑 캐시 제거·공개 상품 캐시 유지 검증이다. 실제 로그아웃 흐름 연결 검증과 구분한다.

### Push 전 전체 빌드 확인

2026-11-02 위 선택 검사 이후, 개인 DB와 분리한 새 임시 MySQL 8.4 컨테이너에서 기존 V1 적용과 `clean build --offline`을 다시 실행했다. 전체 69건(공통 18·앱 컨텍스트 1·테스트 DB 보호 5·Controller 15·초기 지급 20·조회 10)이 실패 없이 통과했다. 임시 컨테이너·볼륨은 정리했다. 실제 로그인 구현과 프런트 인증 흐름은 여전히 통합 전이다. 원격 CI 결과는 별도로 확인한다.
