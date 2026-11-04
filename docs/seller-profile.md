# 판매자 프로필 관리

> 문서 읽기 기준(2026-11-04): 아래 날짜·브랜치별 구현 및 검증 설명은 당시 기록이다. 현재 반영 여부·남은 작업은 [현황표](current-status.md), 실행 설정은 [루트 README](../README.md), API 계약은 [API 명세](api-spec.md)를 따른다. 과거의 미구현·병합 전 표현과 검사 수를 현재 상태로 확대하지 않는다.

작성일: 2026-11-03. 담당: 박소현. 브랜치: `codex/seller-profile`.

## 구현 범위

API 명세 3·10절을 따라 기존 seller_profiles Entity를 확장하고 JPA Repository·요청/응답 DTO·입력 검사·Service·Controller를 연결했다. 공유 회원 PK를 프로필 PK로 사용하며 새 테이블·Flyway 변경·프로필 삭제 API는 추가하지 않는다.

| API | 권한·결과 |
| --- | --- |
| GET /api/sellers/{id} | 공개 조회. 잘못된 ID는 400, 미등록 프로필은 404 |
| GET /api/me/seller-profile | 인증된 본인. 미등록 404 |
| PUT /api/me/seller-profile | 인증·CSRF 필요. 최초 생성 201, 수정 200 |

응답은 id(문자열), displayName, introduction, specialty, portfolioUrl이다. 표시 이름은 회원에서 조회하며 이메일·비밀번호·잔액을 공개하지 않는다. 회원 번호는 요청 본문·쿼리·헤더가 아닌 공통 CurrentMemberId의 인증 principal에서 얻는다. 실제 로그인 기능은 기존 인증 담당자의 구현을 사용하며 테스트 인증을 운영 코드에 넣지 않는다.

## 입력·저장 규칙

- introduction 필수 1~2000, specialty 필수 1~100 코드 포인트. 공통 TextNormalizer로 CRLF/CR을 LF로 통일하고 Unicode 앞뒤 공백을 제거한다.
- portfolioUrl은 선택, 최대 2048 코드 포인트. HTTPS·host 존재·userinfo 부재를 URI 파서로 확인한다. 서버에서 외부 주소에 접속하지 않는다.
- PUT은 전체 교체다. 소개·전문 분야를 매번 제출하고, 링크 생략·null·공백은 기존 링크를 NULL로 지운다.
- 조회는 읽기 전용 트랜잭션, 쓰기는 READ_COMMITTED 단일 트랜잭션이다. 회원 행을 먼저 PESSIMISTIC_WRITE로 잠가 같은 회원의 최초 생성·수정 경쟁을 직렬화한다.
- 신규 공유 PK Entity는 EntityManager.persist로 등록하고 기존 관리 Entity는 도메인 메서드로 수정한다. 일반 setter나 직접 JDBC 운영 코드는 추가하지 않는다.
- 공통 Clock.instant를 UTC LocalDateTime으로 변환하고 마이크로초로 절삭한다. DATETIME은 LOCAL_DATE_TIME JDBC 매핑으로 저장한다. 수정 시 created_at은 보존한다.
- demo에서는 Controller·Service를 등록하지 않는다. DB 없는 기본 HTTP 테스트에 SellerProfileService 모의 빈만 추가하고 기존 테스트 설정을 유지했다.

## 검증

- SellerProfileInputTests 18건: 필수/선택값·Unicode 정규화·이모지 길이 경계·URL 형식/길이·userinfo 차단.
- SellerProfileControllerTests 6건: 공개 응답·미인증 차단·CSRF·인증 회원 ID 사용·201/200. Service는 mock이다.
- SellerProfileIntegrationTests 12건: 실제 MySQL 저장·별도 트랜잭션 재조회·HTTP 생성/수정·누락 입력 무변경·링크 삭제·초기 시각 보존·UTC 원문·실패 롤백·4개 동시 최초 등록·ID 오류·없는 회원 차단.

통합 검사는 TEST_DB_* 전용 lancerpick_ci와 임시 MySQL 8.0.46을 사용한다. fixture로 만든 회원·프로필만 정리하며 개발 DB를 사용하지 않는다. HTTP 통합 테스트의 인증 principal은 테스트 대역이므로 실제 브라우저 로그인부터의 종단 간 검증과 구분한다. 팀 기준 MySQL 8.4·원격 CI 성공을 대신하지 않는다.

2026-11-03 에이전트 실행 결과: 프로필 전용 36건과 기존 기능을 포함한 백엔드 전체 **548건**, 실패·오류·건너뜀 0. V1/V2·기본 Hibernate validate를 포함한 `gradlew.bat build --no-daemon` 성공. build 경로의 임시 DB를 사용해 clean은 생략했다. 검사 후 임시 MySQL을 종료하고 자격 정보 파일을 제거했다. 운영 Java에는 JPA만 사용하며 직접 JDBC/네이티브 쿼리 금지 검사도 통과했다.

백엔드 폴더에서 [테스트 DB 설정](test-database.md) 후 실행한다.

```powershell
.\gradlew.bat test --tests "com.example.lancerpick.seller.*"
.\gradlew.bat build
```

아래 초기 백엔드 검사 당시 프런트 관리 화면은 미구현이었으며, 이후 프런트 후속 구현 절의 결과와 구분한다. 초기 검사 단계에서는 커밋·Push·PR 병합을 하지 않았다.

## main 병합 후 Push 전 확인

2026-11-03 main의 `7efafbd`까지 작업 브랜치에 반영했다. docs/README 충돌은 프로필 문서 링크와 팀원의 세션 거래·지갑 연결 문서를 모두 보존해 해결했다. 프로필 변경 기준 커밋은 `34b8c66`, 병합 기록은 `03e9617`이다.

개인 DB와 분리한 새 임시 MySQL 8.0.46에서 V1/V2·기본 validate를 포함해 전체 build를 다시 실행했다. **568건**, 실패·오류·건너뜀 0이다. 프런트 build(타입 검사 포함)·lint·test:api 23건 및 별도 wallet-session 2건, 합계 25건도 통과했다. 이 백엔드 검사 단계에서는 프런트 소스를 변경하지 않았으며 팀원의 main 변경을 반영한 회귀 검사다. 임시 MySQL을 종료하고 자격 정보 파일을 제거했다.

MySQL 8.4 원격 CI·팀원 리뷰 승인은 Push 후 최신 PR에서 확인한다. 실제 프로필 화면과 브라우저 로그인부터의 연동 완료를 의미하지 않으며 PR 병합은 별도로 진행한다.

## 프런트 후속 구현

2026-11-03 같은 작업 브랜치에서 `/mypage/seller-profile` 화면을 추가했다. 마이페이지 메뉴에 진입 링크를 두고 공통 레이아웃·Input·Button·AsyncState와 TanStack Query를 재사용한다. 백엔드와 함께 기존 PR에서 검토할 프런트 후속 변경이다. PR 병합은 별도로 진행한다.

- `/api/me`로 서버 세션 확인 후 계정별 프로필을 조회한다. 미인증이면 폼을 숨기고 로그인 안내를 제공한다.
- 본인 조회의 404 RESOURCE_NOT_FOUND만 미등록으로 처리한다. 서버·네트워크 오류를 신규 등록으로 숨기지 않는다.
- 소개·전문 분야·선택 링크, 코드 포인트 글자 수·필드 오류·저장 중 비활성화·성공 안내를 제공한다. PUT에는 세 필드를 모두 보내고 빈 링크는 null로 제출한다.
- 공통 apiRequest로 쿠키·CSRF·오류를 처리하며 변경 요청을 자동 재시도하지 않는다. 저장 전 서버 계정을 다시 확인하고 다른 계정이면 제출을 중단한다.
- 계정별 조회 키, gcTime=0, 취소·제거 정리, 세션 만료 시 폼 숨김을 적용한다. 세션 재확인 중에는 이전 계정 폼을 숨기고, 같은 계정 확인 후에는 입력을 유지한다.
- 저장 후 서버 응답으로 폼을 갱신한다. 사용자가 외부 포트폴리오 링크를 입력해도 앱이 해당 주소를 자동 호출하지 않는다.

프런트 build(타입 검사 포함)·lint·test:api **28건** 통과. 추가 API/검증 검사 5건은 실제 경로·문자열 ID·CSRF PUT·404/오류 구분·전체 교체·문자 수/URL 경계를 확인한다. 의존성 추가와 잠금 파일 변경은 없다.

브라우저는 개인 DB와 분리한 새 임시 MySQL **8.0.46**, 백엔드 18080·Vite 15173을 사용했다. 운영 로그인 화면은 아직 준비 중이므로 Git 제외 경로의 임시 테스트 도구에서 테스트 계정 A/B를 실제 회원가입·로그인 API로 인증했다. 일반 앱의 인증·Security 코드를 우회하거나 수정하지 않았다.

브라우저 확인: 비로그인 안내, 미등록 폼, 필수값·HTTP 링크 오류, 최초 등록·서버 정규화 반영, 수정·링크 삭제·새로고침 유지, 다른 계정으로 재진입 시 이전 데이터 미노출, 로그아웃 후 폼 숨김. 프로필 등록 화면을 캡처했다. 이것은 임시 도구를 통한 실제 세션 검사이며 운영 로그인/회원가입 화면·전역 인증 메뉴 전체 완료를 뜻하지 않는다.

검증 후 임시 백엔드·Vite·MySQL을 종료하고 임시 자격 정보·테스트 로그인 도구를 제거했다. 평소 사용하는 개발 DB·8080 서버·5173 프런트 설정은 변경하지 않았다.

실제 운영 로그인 화면에서의 진입·로그아웃 흐름 통합은 인증/React 공통 담당자와 후속 확인한다. MySQL 8.4 최신 CI·팀원 리뷰·PR 병합도 별도다.
