# 랜서픽 실제 프로젝트 구조 검토

> 문서 읽기 기준(2026-11-04): 아래 날짜·브랜치별 구현 및 검증 설명은 당시 기록이다. 현재 반영 여부·남은 작업은 [현황표](current-status.md), 실행 설정은 [루트 README](../README.md), API 계약은 [API 명세](api-spec.md)를 따른다. 과거의 미구현·병합 전 표현과 검사 수를 현재 상태로 확대하지 않는다.

현재 대조 2026-11-04: 최신 구현·검증 범위는 [현황표](current-status.md)를 따른다. JPA·QueryDSL, 가입/로그인·상품 상세·주문 작성과 거래 화면이 main에 반영됐다. 아래 초기 구조 표의 빈 패키지·미구현·DB 미검증 표기는 2026-10-29 기록이다. 당시 표를 현재 구현 상태나 운영 브라우저·배포 검증 완료로 해석하지 않는다.

후속 상태 2026-11-03: 회원가입·세션 로그인·내 정보, 공통 기반·메모리 예시 상품·카테고리 DB 조회·지갑, 주문 생성/조회/수락/납품/수정과 결제·직접 취소/환불·확정/정산 및 구매자 취소 요청 API가 구현되었다. 거래 금융 화면 기반과 실제 로그인 기반 브라우저 통합·신규 화면의 완료는 구분한다. 아래 2026-10-29 표는 당시 기록이며 최신 진척은 [작업표](work-plan.md)와 각 기능 문서를 따른다.

설계 후속 변경 2026-10-29: `orders`의 조건 원문을 `order_snapshots`로 분리하여 현재 논리 설계는 총 16개 테이블이다. 아래 본문의 15개 표기는 당시 검토 기준이며 스키마 구현 완료를 뜻하지 않는다.

역할 배정 갱신 2026-10-29: [확정 담당·지원 원칙·업무 비중](work-plan.md#확정-역할-배정)을 따른다. 상세 일정·검토자·배포 담당은 별도 확정한다.

후속 변경 2026-10-29: 아래 본문은 문서 검토 당시의 상태다. 이후 공통 JPA 설정·test 프로필·전용 TEST_DB_*·접속 전 DB 검사·루트 .gitignore를 반영했다. 실제 DB·원격 CI 연결 검증은 별도로 필요하다. 현재 실행 방법은 [공통 환경 안내](test-database.md)를 따른다.

검토일: 2026-10-29. 승인된 소스·설정·문서를 읽은 정적 검토다. 소스와 실행 설정은 변경하지 않았다. 빌드·서버·DB·브라우저·GitHub Actions 실행 및 저장소 보호 설정은 확인하지 않았다. 개인 접속 파일·.env·인증서·키·.git 내부·의존성 및 빌드 산출물은 읽지 않았다.

## 1. 확인한 구조

```text
lancerpick/
  README.md
  docs/
  .github/workflows/ci.yml
  LancerPick/
    build.gradle
    settings.gradle
    gradle/wrapper/gradle-wrapper.properties
    src/main/java/com/example/lancerpick/
      LancerPickApplication.java
      auth/ member/ seller/ product/ order/
      wallet/ payment/ settlement/ review/ admin/
      common/config/ common/security/ common/exception/
    src/main/resources/application.properties
    src/test/java/com/example/lancerpick/LancerPickApplicationTests.java
  LancerPickFrontend/
    package.json
    package-lock.json
    vite.config.ts
    src/
      app/router.tsx
      app/providers.tsx
      features/auth/ features/products/ features/orders/
      shared/api/client.ts
      shared/components/
      main.tsx
      index.css
```

package-lock.json과 Gradle Wrapper JAR는 존재 여부만 확인했다. 하위 프로젝트에서 AGENTS.md는 발견되지 않았다. 기능 패키지의 대부분은 `.gitkeep`으로 보존한 빈 디렉터리다.

## 2. 구조 판단

| 영역 | 실제 상태 | 판단 |
| --- | --- | --- |
| 저장소 | 백엔드·프런트 단일 저장소 | 문서의 단일 저장소 원칙과 일치 |
| 백엔드 | 기능별 패키지, 기능 안의 계층 디렉터리 | 현 단계에서 구조 재편 필요 없음. 빈 계층마다 클래스를 의무적으로 만들 필요 없음 |
| 엔티티 책임 | README에 Member·Category·주문 하위 엔티티 소속 정의 | 중복 엔티티를 만들지 않도록 구현 시 유지 |
| 프런트 | app·features·shared, CSS Modules | 기능별 화면과 공통 처리를 나누는 방향과 일치 |
| 라우팅 | 상품·거래·로그인 안내 화면과 없는 경로 안내 | 경로 골격만 구현. 로그인·접근 통제·업무 화면은 미구현 |
| 서버 데이터 | QueryClientProvider, mutations.retry=false | 변경 자동 재시도 금지 기준을 반영. 실제 조회·갱신·캐시 정리는 미구현 |
| 연결 | Vite의 /api → localhost:8080 프록시 | 개발 연결 설정 있음. 공통 fetch와 서버 API가 없어 실제 연결 완료는 아님 |
| CI | 프런트 타입·린트·빌드, JDK 25·임시 MySQL·Gradle build | 워크플로 파일 존재. 원격 실행 성공·필수 병합 검사 강제 여부는 미확인 |

## 3. 구현 전에 처리할 항목

### 우선: 로컬과 CI의 JPA 설정 기준 맞추기

근거: [로컬 공통 설정](../LancerPick/src/main/resources/application.properties), [CI 설정](../.github/workflows/ci.yml).

로컬 공통 파일에는 DB 접속값 참조만 있고 `spring.jpa.open-in-view=false`, `spring.jpa.hibernate.ddl-auto=validate`, `spring.jpa.properties.hibernate.jdbc.time_zone=UTC`가 명시되어 있지 않다. CI는 같은 항목을 환경변수로 지정한다. 문서의 공통 기준이 로컬에서도 적용되도록 정리해야 한다. 개인 파일의 추가 설정은 열어보지 않았으므로 현재 로컬의 최종 유효 설정까지 단정하지 않는다.

후속 검증: 개인 설정에 의존하지 않는 공통 기준, 엔티티·V1 적용 후 스키마 일치, API 서비스에서 필요한 조회를 완료하는 방식 확인.

### 우선: 테스트 DB 격리

근거: [현재 테스트](../LancerPick/src/test/java/com/example/lancerpick/LancerPickApplicationTests.java), [루트 README](../README.md).

현재 테스트는 별도 프로필 없이 `@SpringBootTest`로 전체 컨텍스트를 로드한다. 로컬 기본 경로로 실행하면 개발용 DB 접속 설정을 사용한다. CI의 임시 DB는 분리되어 있지만 로컬 테스트 격리 구성은 없다. 금액·초기 지급·상태 변경 테스트를 추가하기 전에 테스트 전용 DB와 실행 설정을 준비해야 한다.

현재 테스트 본문은 비어 있으며 업무 엔티티와 마이그레이션도 없다. 이 테스트가 통과하더라도 15개 테이블·결제·환불·동시성 검증을 통과한 것으로 해석할 수 없다.

### 우선: 최초 스키마·인증·공통 요청의 연결

근거: [리소스 설정](../LancerPick/src/main/resources/application.properties), [공통 요청 골격](../LancerPickFrontend/src/shared/api/client.ts), [로그인 안내 화면](../LancerPickFrontend/src/features/auth/pages/LoginPage.tsx).

Flyway 의존성은 있지만 `src/main/resources/db/migration`과 업무 SQL이 없다. Security 설정·컨트롤러·업무 서비스도 아직 없다. 프런트의 client.ts·기능별 api.ts·types.ts는 주석과 `export {}`만 있다. 이는 고장난 완성 기능이 아니라 구현 전 상태다. 최초 구현에서는 회원·지갑 생성, 로그인·CSRF·보호 API, 공통 오류·인증 캐시 처리를 작은 흐름으로 연결한다.

### 다음: 저장소 전체의 제외 규칙

근거: 루트 파일 목록과 [백엔드 제외 규칙](../LancerPick/.gitignore), [프런트 제외 규칙](../LancerPickFrontend/.gitignore).

루트 `.gitignore`가 없고, 루트 `.idea/` 디렉터리는 존재한다. 백엔드 제외 규칙은 개인 properties를 다루지만 `.env` 계열은 명시하지 않는다. 하위 제외 규칙만으로 루트의 개인 IDE 파일이나 환경변수 파일까지 관리한다고 가정하지 말고 루트 정책을 마련해야 한다. 실제 추적·커밋 여부나 비밀정보 유출은 확인하지 않았으며 유출로 판단하지 않는다.

### 다음: 테스트와 재현성을 기능 진척에 맞춰 강화

CI의 MySQL 이미지는 `mysql:8.4` 계열 태그이고 JDK는 `25` 계열이다. 재현 기준으로 삼을 패치·이미지 식별자는 초기 검증 후 기록한다. 현재 CI에서 빈 스키마와 시작 테스트만 성공하는 것과 실제 Flyway 변경 검증을 구분한다. 기능이 생기면 중복·동시성·롤백 테스트를 같은 PR에 추가한다.

QueryDSL·Lombok·DevTools도 build.gradle에 포함되어 있다. 의존성 존재만으로 사용 필요성이나 호환성 검증이 끝난 것은 아니다. 기존 의존성을 이번 검토에서 제거하지 않았으며 첫 빌드·실제 조회 구현 시 사용 범위와 검증 결과를 기록한다.

## 4. 문서에 반영한 차이

- 예시 `backend/`, `frontend/` 경로를 실제 `LancerPick/`, `LancerPickFrontend/`로 정리했다.
- 초기 생성 참고 절차와 현재 저장소 복제 후 실행 절차를 구분했다.
- 현재 MySQL·개인 properties 방식과 선택적인 Docker·YAML 예시를 구분했다.
- 구성된 CI 파일과 미확인 원격 실행 결과를 구분했다.
- API 세부 계약의 미확정 사항과 정은찬·황준범 호출 경계 제안을 작업표로 연결했다.

기본 패키지 `com.example.lancerpick`은 애플리케이션과 테스트에서 일치한다. 이전 가이드의 `com.lancerpick` 제안과 다르다는 이유만으로 패키지 이동을 요구하지 않는다. 배포 Dockerfile·Compose·Nginx 설정은 없으며 실제 배포 준비 완료로 표시하지 않는다.

## 5. 후속 검증 순서 제안

1. 팀원별 도구·Wrapper·의존성 설치와 빌드를 확인한다.
2. 공통 JPA 설정·테스트 DB·V1을 정리하고 실제 MySQL에서 검증한다.
3. 세션·CSRF·보호 API·공통 fetch를 연결한다.
4. 상품 등록부터 구매 확정·정산까지 거래 한 건을 화면에서 완료한다.
5. 취소·환불·수정·권한·중복·동시 요청·롤백을 검증한다.
6. CI의 실제 실행과 병합 제한, HTTPS 배포 및 다른 팀원 재현을 확인한다.

실제 담당 배정과 결과는 [작업표](work-plan.md)에 기록한다. 이번 문서 검토에서 위 검증을 실행한 것은 아니다.
