# 랜서픽 공통 개발 환경 구축 가이드

후속 상태 2026-11-04: 회원 세션 인증·가입 시 지갑 지급·주문·결제·환불·정산에 더해 실제 상품 검색·판매자 프로필 관리·납품/수정/취소 요청 화면이 현재 소스에 있다. 마이페이지 메뉴·거래 URL 상태와 CI/test 프로필은 [통합 검증 문서](account-trade-integration.md)를 따른다. 아래 2026-10-29 표는 당시 기록이다. 실행은 [루트 README](../README.md), 기능 구현 방법은 [백엔드](backend-common-guide.md)·[프런트 가이드](frontend-common-guide.md), 남은 작업은 [현황표](current-status.md)를 따른다.

역할 배정 갱신 2026-10-29: [확정 담당·지원 원칙·업무 비중](work-plan.md#확정-역할-배정)을 따른다. 상세 일정·검토자·배포 담당은 별도 확정한다.

2026-10-29 구조 검토 반영: [변경 기록](review-changes.md). 실제 구현·실행 검증과 구분한다.

팀원 4명이 같은 환경에서 실행하고 첫 화면과 API를 연결하기 위한 실습 안내

작성일 2026년 10월 29일  |  v1.0  |  Windows PowerShell 기준 | Java 25 기준 개정

## 이 문서의 목표

한 저장소의 LancerPickFrontend와 LancerPick을 실행하고 MySQL에 연결한다. 처음 구성한 담당자 외 팀원도 복제 후 동일하게 실행할 수 있어야 한다. 이후 인증을 연결하여 로그인과 보호 API까지 확인한다.

기술 기준은 기존 공통 개발 규칙을 따른다. 아래 폴더명·포트·DB 이름·설정 예시는 구축을 위한 추천안이다. 실제 설치와 실행을 완료했다는 기록은 아니며, 완료 여부는 마지막 확인표에 남긴다.

## 현재 저장소를 실행하는 팀원 안내

공통 환경 후속 반영 2026-10-29: JPA 설정은 공통 properties로 통일했고 CI도 이를 재사용한다. 개인 DB 파일 import는 application-default.properties로 옮겼으며 test 프로필은 TEST_DB_* 전용이다. 루트 .gitignore도 추가했다. 아래 초기 검토 표의 빌드·DB 상태와 구분하며, 테스트 전용 계정 준비와 실행은 [테스트 DB 안내](test-database.md)를 따른다.

2026-10-29 파일 검토 기준으로 프로젝트는 이미 생성되어 있다. 실제 경로는 `LancerPick/`과 `LancerPickFrontend/`이고, 기본 Java 패키지는 `com.example.lancerpick`이다. 신규 팀원은 [루트 README](../README.md)의 복제 후 실행 절차를 우선 따른다. 아래 프로젝트 생성 절차를 기존 저장소에 다시 실행하지 않는다.

현재 로컬 DB 접속은 각자의 MySQL과 `LancerPick/local-application.properties`를 사용한다. 이 개인 파일은 검토하지 않았으며 실제 접속 성공 여부도 확인하지 않았다. 루트 `compose.yaml`·`.env.example`과 Docker 배포 구성은 아직 없다. 아래 Docker DB 및 YAML 설정은 선택적인 신규 구축 예시이며 현재 적용 상태가 아니다. 기존 `application.properties`에 YAML 예시를 중복 추가하지 않는다. CI 구성은 이미 `.github/workflows/ci.yml`에 있으므로 새로 만드는 대신 내용을 검토·수정한다.

| 항목 | 현재 파일에 기록된 값 | 검증 상태 |
| --- | --- | --- |
| 백엔드 | Java 25 / Spring Boot 4.1.2 / Gradle Wrapper 9.8.2 | 설정 확인, 빌드 미실행 |
| 프런트 런타임 | Node 24.22.1 / npm 11.19.1(README 기록) | 설치 상태 미확인 |
| 프런트 의존성 | React·React DOM 19.3.0 / Vite 8.3.5 / TypeScript ~5.9.3 | package.json 확인, 이번 검토에서 설치·빌드 미실행 |
| 라우팅·조회 | React Router 7.18.5 / TanStack Query 5.105.0 | 의존성과 Provider 확인, 업무 API 미연결 |
| DB·CI | MySQL 8.4 / CI 임시 MySQL / Flyway 의존성 | 업무 SQL 없음, 실행·마이그레이션 검증 미완료 |

현재 구조와 설계의 차이는 [프로젝트 구조 검토](project-review.md), 구현 전 결정과 담당 배정은 [작업표](work-plan.md)에 기록한다.

| 구분 | 누가 무엇을 하는가 |
| --- | --- |
| 초기 구축 | 황준범이 프런트 공통 기반을 주담당하고 정은찬이 통합을 조율한다. 각 기능 담당자가 화면·API·DB를 구현하며 별도 배포·환경 운영 담당은 미확정이다. |
| 교차 확인 | 초기 작성자 외 1명이 새 폴더에 복제하여 실행한다. 이후 전원 확인한다. |
| 범위 | 로컬 실행 → DB 연결 → React와 API 연결 → 세션 인증 검증 → CI와 첫 배포 준비 |
| 완료 후 | 기능 담당별 개발을 시작한다. 고도화용 테이블·외부 결제·Redis 등을 미리 추가하지 않는다. |

## 진행 순서

1. 도구 설치와 버전 기록
2. 저장소와 프로젝트 생성 — 초기 생성 참고용
3. 로컬 MySQL 실행 — 현재 방식은 루트 README, Docker는 선택 예시
4. Spring Boot 설정과 DB 변경 관리
5. React 연결과 공통 요청 처리
6. 인증 검증과 자동 검사
7. 팀원 복제 실행과 오류 해결
8. 완료 기록과 첫 배포 연결

명령은 각 단계에 적힌 폴더에서 실행한다. 예시의 비밀번호·주소·계정은 실제 값으로 교체하며, 팀원은 초기 생성 명령을 반복하지 않는다.

## 1 도구 설치와 버전 기록

각자 Git, Eclipse Temurin JDK 25, Node.js 24 LTS를 준비한다. Docker DB 예시를 선택하거나 컨테이너 배포를 준비할 때는 Docker Desktop도 설치한다. Windows에서는 Docker Desktop의 WSL 2 기반 Linux 컨테이너를 사용한다. 필요한 경우 Windows 가상화·WSL 설치 후 재부팅한다.

| 도구 | 기준과 확인 방법 |
| --- | --- |
| Java | JDK 25. java -version과 IDE의 Project SDK·Gradle JVM이 모두 25인지 확인 |
| Node와 npm | Node 24 LTS. Node에 포함된 npm 사용. 각자 설치한 정확한 버전을 맞춤 |
| Spring Boot | 기존 선정값 4.1.2. Java 25 사용. Gradle은 9.1.0 이상 9.x Wrapper를 검증 후 고정 |
| React | react와 react-dom 19.3.0을 동일하게 사용 |
| Vite와 TypeScript | Vite 8.3 계열. TypeScript와 React 플러그인은 호환 버전 선택 후 잠금 파일로 고정 |
| 기타 라이브러리 | React Router 7, TanStack Query 5. MySQL 8.4 LTS와 Flyway 사용 |
| IDE | 백엔드 IntelliJ IDEA, 프런트 VS Code 추천. 도구 선택보다 JDK·실행 설정 일치가 중요 |

```
git --version
java -version
node --version
npm.cmd --version
docker version
docker compose version
```

PowerShell에서 npm.ps1 실행 정책 오류가 나면 npm.cmd를 사용한다. 전역 실행 정책을 바꾸지 않아도 된다. Docker 명령은 Docker Desktop이 실행 중이어야 한다.

### 버전을 고정하는 방법

초기 담당자가 docs/setup-guide.md에 JDK·Node·npm·Docker 버전과 Gradle Wrapper 버전을 기록한다. package-lock.json과 Gradle Wrapper 파일을 커밋한다. MySQL은 검증한 8.4 패치 태그 또는 이미지 digest를 기록하여 팀이 같은 이미지로 맞춘다.

계열로 선정된 패치 버전은 설치 시 확인한다. 지정 버전이 조회되지 않거나 의존성 충돌이 발생하면 강제 설치하지 말고 원인을 확인한 뒤 공통 개발 규칙의 기준 표와 함께 변경한다. npm의 --force나 --legacy-peer-deps로 충돌을 숨기지 않는다.

## 2 저장소와 프로젝트 생성

다음은 초기 생성 참고 절차다. 현재 저장소의 구조는 아래와 같으며 기존 프로젝트에 생성 명령을 덮어 실행하지 않는다.

```
lancerpick/
  LancerPickFrontend/
  LancerPick/
  docs/setup-guide.md
  .github/workflows/ci.yml
  README.md
```

### 백엔드 생성

새 저장소를 처음 생성하는 경우 https://start.spring.io 에서 Gradle Groovy, Java, Spring Boot 4.1.2, Java 25, Jar를 선택한다. 현재 프로젝트의 Group은 com.example, 프로젝트명은 LancerPick, 기본 패키지는 com.example.lancerpick이다. 이 검토에서 이름이나 패키지를 변경하지 않는다. 생성한 ZIP의 프로젝트 내용이 LancerPick 바로 아래 오도록 압축을 푼다.

선택 의존성: Spring Web, Spring Security, Spring Data JPA, Validation, MySQL Driver, Flyway Migration. Boot 4에서는 생성기가 제공하는 스타터 구성을 유지한다. Flyway의 MySQL 지원 모듈도 생성 결과에서 확인하고, 누락 시 Boot가 관리하는 버전으로 추가한다.

LancerPick/build.gradle과 gradle/wrapper/gradle-wrapper.properties를 확인한다. Java toolchain은 25로 맞추고 별도 전역 Gradle 대신 저장소의 gradlew.bat를 사용한다. languageVersion = JavaLanguageVersion.of(25)로 지정하고 Gradle Wrapper는 9.1.0 이상 9.x인지 확인한다. 미리보기 기능은 사용하지 않는다.

### 프런트엔드 생성

저장소 루트에서 초기 담당자만 아래를 실행한다. create-vite의 생성 도구 버전과 실제 Vite 버전은 서로 별개다.

```
npm.cmd create vite@8 LancerPickFrontend -- --template react-ts
cd LancerPickFrontend
npm.cmd install --save-exact react@19.3.0 react-dom@19.3.0
npm.cmd install --save-exact react-router@7 @tanstack/react-query@5
npm.cmd install --save-dev --save-exact vite@8.3
npm.cmd run build
npm.cmd ls react react-dom vite typescript
```

생성된 @vitejs/plugin-react와 @types/react 계열의 호환성도 확인한다. 설치가 끝난 package.json과 package-lock.json을 함께 커밋한다. 이후 팀원은 npm ci로 동일한 의존성을 설치한다.

### 저장소 제외 항목

```
node_modules/
dist/
.gradle/
build/
.idea/
.env
.env.*
!.env.example
*.local
*.log
```

환경변수 예시에는 비밀값을 넣지 않는다. Gradle Wrapper JAR는 제외하지 않는다. 기본 브랜치에 직접 작업하기보다 초기 환경 구성 PR을 만들고 검토 후 병합한다.

## 3 로컬 MySQL 실행

개발 중에는 DB만 Docker로 실행하고 React와 Spring Boot는 PC에서 실행하는 구성을 추천한다. 아래 파일은 저장소 루트에 만든다. 3307 포트를 사용하여 기존 로컬 MySQL과 충돌 가능성을 줄인다.

이 장은 Docker DB를 새로 선택할 때의 예시다. 현재 로컬 MySQL을 사용하는 팀원에게 필수 전환 절차가 아니며, 기존 DB·개인 설정을 삭제하지 않는다.

### 환경변수 파일

.env.example을 아래 형태로 작성하고 .env로 복사한다. .env에는 각자 로컬 비밀번호를 입력한다. 단순 영문·숫자 조합을 사용하면 초기 인용부호 문제를 줄일 수 있다.

```
MYSQL_IMAGE=mysql:8.4
MYSQL_DATABASE=lancerpick
MYSQL_USER=lancerpick
MYSQL_PASSWORD=REPLACE_WITH_LOCAL_PASSWORD
MYSQL_ROOT_PASSWORD=REPLACE_WITH_DIFFERENT_PASSWORD
```

mysql:8.4는 첫 연결 확인용 계열 태그다. 성공 후 MYSQL_IMAGE를 검증한 패치 또는 digest로 고정하여 .env.example에도 동일하게 기록한다.

### compose.yaml

```
services:
  db:
    image: ${MYSQL_IMAGE:?Set MYSQL_IMAGE}
    environment:
      MYSQL_DATABASE: ${MYSQL_DATABASE}
      MYSQL_USER: ${MYSQL_USER}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:?Set MYSQL_PASSWORD}
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:?Set ROOT password}
      TZ: UTC
    ports:
      - "127.0.0.1:3307:3306"
    volumes:
      - mysql_data:/var/lib/mysql
    command: ["--default-time-zone=+00:00"]
volumes:
  mysql_data:
```

### 실행과 접속 확인

```
Copy-Item .env.example .env
# .env 값을 수정한 뒤 실행
docker compose up -d db
docker compose ps
docker compose logs --tail 50 db
docker compose exec db mysql -u lancerpick -p lancerpick
```

비밀번호 입력 후 SELECT 1; 과 SELECT @@session.time_zone; 을 실행한다. 접속 성공을 확인한 뒤 Spring Boot를 시작한다. DB 준비 전에 앱을 실행해 실패했다면 DB 준비 완료 후 다시 시작한다.

docker compose stop으로 멈춘다. 볼륨은 DB 데이터를 보관한다. down -v는 데이터를 삭제하므로 일상적인 종료에 사용하지 않는다. 기존 볼륨에서는 .env 비밀번호만 바꿔도 DB 계정 비밀번호가 바뀌지 않는다.

## 4 Spring Boot와 DB 연결

현재 공통 설정 파일은 `LancerPick/src/main/resources/application.properties`다. 아래 YAML은 설정 항목을 설명하는 예시다. 적용할 경우 기존 properties에서 같은 항목을 관리하거나 설정 형식을 하나로 통일하며, 두 파일에 중복 정의하지 않는다. 비밀번호는 개인 접속 파일 또는 실행 환경변수에서 읽고 배포 설정은 별도로 둔다.

```
server:
  port: 8080
spring:
  datasource:
    url: ${DB_URL}
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  jpa:
    open-in-view: false
    hibernate:
      ddl-auto: validate
    properties:
      hibernate.jdbc.time_zone: UTC
  flyway:
    enabled: true
```

### 환경변수 전달

Compose가 읽는 루트 .env는 PC에서 실행하는 Spring Boot에 자동 전달되지 않는다. IDE의 실행 구성에 다음 세 값을 넣는다. DB_PASSWORD는 .env의 MYSQL_PASSWORD와 같은 값이다. 개인 실행 구성은 커밋하지 않는다.

```
DB_URL=jdbc:mysql://localhost:3307/lancerpick?connectionTimeZone=UTC
DB_USERNAME=lancerpick
DB_PASSWORD=각자 설정한 로컬 비밀번호
```

터미널에서 실행한다면 같은 PowerShell 창에 아래와 같이 환경변수를 설정한다. 비밀번호는 입력 창에서 받아 명령 이력에 남기지 않는다.

```
$env:DB_URL = 'jdbc:mysql://localhost:3307/lancerpick?connectionTimeZone=UTC'
$env:DB_USERNAME = 'lancerpick'
$dbCredential = Get-Credential -UserName lancerpick
$env:DB_PASSWORD = $dbCredential.GetNetworkCredential().Password
cd LancerPick
.\gradlew.bat --version
.\gradlew.bat bootRun
```

### Flyway 적용

실제 ERD에 맞춘 첫 물리 SQL을 검토한 뒤 src/main/resources/db/migration/V1__init.sql에 둔다. 아직 SQL을 작성하지 않았다면 빈 DB 연결만 먼저 확인하고, DB 스키마 구현 완료로 표시하지 않는다. 환경 확인용 가짜 업무 테이블은 만들지 않는다.

Flyway 적용 후 엔티티와 DB 구조가 맞는지 ddl-auto=validate로 확인한다. 공유된 V1 파일은 수정하지 않고 V2 이후 파일로 변경한다. 번호는 담당자끼리 병합 전에 조정한다. 실제 MySQL로 검사하며 H2 통과를 MySQL 검증으로 대체하지 않는다.

MySQL 공개키 조회 관련 오류가 발생하면 먼저 Connector/J와 인증·TLS 설정을 확인한다. 로컬 개발용 우회 옵션을 배포 JDBC URL에 그대로 복사하지 않는다.

## 5 React와 API 연결

LancerPickFrontend/vite.config.ts의 기존 React 플러그인 설정을 유지하고 server 항목을 추가한다. 프런트에서는 localhost:8080 절대 주소 대신 /api 상대 경로를 사용한다.

```
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
```

```
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: 'http://localhost:8080', changeOrigin: true },
    },
  },
})
```

### 첫 연결 확인

백엔드에 로컬 확인용 GET /api/health를 만들고 {"status":"UP"}만 반환하도록 한다. 이 경로는 기존 업무 API가 아닌 환경 확인용 추가안이다. Security에서 이 GET 경로만 공개하고 나머지 보호 규칙은 유지한다.

이 응답은 HTTP 연결 성공만 보여준다. DB 연결 성공은 별도로 SQL 접속과 애플리케이션 DB 로그에서 확인한다. 전체 경로 permitAll이나 CSRF 비활성화로 연결 문제를 우회하지 않는다.

```
# 새 터미널에서 LancerPickFrontend 폴더로 이동
npm.cmd ci
npm.cmd run dev
```

브라우저에서 http://localhost:5173/api/health로 응답을 확인한다. 이후 임시 확인 화면에서 fetch 결과를 표시한다. 확인 후 해당 화면은 실제 상품 목록 화면으로 교체한다.

```
const response = await fetch('/api/health', {
  credentials: 'same-origin',
})
if (!response.ok) throw new Error(`HTTP ${response.status}`)
const result = await response.json()
```

### 공통 프런트 구조

src/features에는 기능별 화면·API를, src/shared에는 공통 UI·요청 함수를 둔다. 라우팅과 QueryClientProvider는 앱 진입부 한곳에서 구성한다. 로그인 상태 조회, 로딩·빈 목록·오류 표시의 예시를 한 화면에서 먼저 완성한다.

공통 요청 함수는 오류 code·401·403, 204의 빈 본문, CSRF 헤더 전달을 처리한다. 위 fetch 예시는 GET 연결 확인용이므로 결제·취소 등의 최종 공통 요청 함수로 사용하지 않는다. VITE_ 환경변수에는 비밀키를 넣지 않는다.

## 6 인증과 자동 검사 연결

### 세션 인증 검증은 별도 작업으로 완료

환경 확인용 GET이 성공해도 인증 구현이 끝난 것은 아니다. 김창재와 React 공통 주담당 황준범이 기존 API 명세서의 인증 계약에 맞춰 아래 흐름을 구현하고 확인한다. Spring Security 기본 생성 비밀번호 화면을 최종 로그인으로 사용하지 않는다.

| 순서 | 확인할 동작 |
| --- | --- |
| 1 | CSRF 토큰 조회와 세션 쿠키 발급. 응답의 headerName과 token을 공통 요청 코드에서 보관 |
| 2 | 토큰을 포함한 로그인. 실제 명세의 입력 형식과 일치하며 세션에 인증 정보 저장 |
| 3 | 로그인 성공 후 토큰 다시 조회. 현재 사용자 API와 보호 API가 같은 세션으로 성공 |
| 4 | 토큰 없는 변경 요청은 거절. 다른 사용자 자원 접근도 거절 |
| 5 | 로그아웃 후 토큰 재조회. 사용자 캐시 제거, 보호 API는 401, 새로고침 시 상태 일치 |

개발 HTTP에서는 Secure 쿠키를 강제하면 쿠키 전달이 막힐 수 있다. 배포 HTTPS에서는 Secure·HttpOnly와 적절한 SameSite를 적용한다. API 인증 실패는 로그인 HTML로 리다이렉트하지 않고 계약에 맞는 JSON과 상태 코드로 반환한다. CSRF 저장소·토큰 지연 로딩·로그인 후 갱신을 함께 검토한다.

### 로컬 검사

```
# LancerPickFrontend에서 실행
npm.cmd ci
npx.cmd tsc -b
npm.cmd run build
```

```
# DB 환경변수가 설정된 LancerPick 터미널에서 실행
.\gradlew.bat clean build
```

초기 생성 템플릿에 없는 test 스크립트를 실행 안내에 넣지 않는다. Vitest와 React Testing Library는 첫 공통 동작 테스트를 작성할 때 설정하고, 스크립트를 추가한 뒤 CI에 연결한다.

### GitHub Actions 구성 순서

PR에서 프런트와 백엔드 검사를 실행한다. 프런트는 고정 Node → npm ci → 타입 검사 → 빌드 순서다. 백엔드는 JDK 25 → MySQL 준비 → 테스트 DB 환경변수 → Gradle build 순서로 구성한다.

CI용 DB는 개발 DB와 분리한다. 테스트는 매 실행 새 DB 또는 격리된 스키마를 사용하며 Flyway를 적용한다. Linux 실행기에서는 gradlew 실행 권한도 확인한다. 비밀번호는 GitHub Secrets 또는 격리된 일회성 테스트 값으로 관리한다.

검사 실패를 숨기려고 테스트를 제외하지 않는다. 데이터베이스가 필요한 contextLoads 테스트라면 먼저 테스트 DB와 접속 환경을 마련한다. 정확한 Actions 버전과 이미지 고정값은 CI 구성 시 공식 문서를 확인하여 기록한다.

### GitHub Actions 구축 및 검증 절차

현재 저장소 루트의 .github/workflows/ci.yml을 검토한다. 새 저장소에서만 해당 파일을 생성한다.

프런트엔드와 백엔드 Job을 구성한다.

main 대상 PR 및 main Push를 실행 조건으로 설정한다.

격리된 CI용 MySQL 데이터베이스를 준비한다.

Gradle Wrapper와 npm 잠금 파일을 사용한다.

CI 구성 변경을 작업 브랜치에 Push하고 PR을 생성한다.

GitHub Actions에서 각 Job의 실행 결과를 확인한다.

실패 시 로그를 확인하고 원인을 수정한 뒤 재검증한다.

정상 실행을 확인한 Job을 GitHub Ruleset의 필수 검사로 지정한다.

PR 승인과 CI 성공 조건이 실제로 병합을 제한하는지 확인한다.

정확한 GitHub Actions 버전, 이미지 버전, 테스트 환경변수와 실행 명령은 검증 후 저장소 설정과 실행 문서에 기록한다.

## 7 팀원이 복제해서 실행하기

현재 저장소의 팀원은 [루트 README](../README.md)에 따라 개인 MySQL 접속 파일을 준비하고 실행한다. 아래 표는 3장의 Docker DB 예시를 선택했을 때의 절차다. Spring Initializr와 create-vite로 프로젝트를 다시 생성하지 않는다.

| 순서 | 작업과 확인 |
| --- | --- |
| 1 | GitHub 저장소 주소로 git clone. README에서 팀 기준 JDK·Node·Docker 버전 확인 |
| 2 | 루트 .env.example을 .env로 복사하고 로컬 비밀번호 입력 |
| 3 | 루트에서 docker compose up -d db. SQL 접속으로 준비 완료 확인 |
| 4 | IDE에 LancerPick을 Gradle 프로젝트로 열고 JDK 25·DB 환경변수 지정 |
| 5 | LancerPick 실행. Flyway·JPA 오류가 없는지 확인 |
| 6 | LancerPickFrontend에서 npm ci 후 npm run dev. localhost:5173 접속 |
| 7 | React → /api 프록시 → Spring 응답 확인. 인증 구현 후 세션 검증 추가 |
| 8 | 로컬 빌드·관련 테스트 실행. 커밋 번호와 결과를 완료표에 기록 |

### 자주 발생하는 문제

| 증상 | 확인 및 해결 |
| --- | --- |
| java 버전 불일치 | 터미널 Java, IDE SDK, Gradle JVM을 각각 확인한다. IDE만 바꾸면 터미널은 그대로일 수 있다. |
| DB 연결 거부 | Docker 실행·DB 로그·3307 포트·준비 완료 여부를 확인한다. 컨테이너 내부 포트는 3306이다. |
| DB Access denied | DB 사용자와 비밀번호를 확인한다. 기존 볼륨은 .env 변경으로 계정이 갱신되지 않는다. |
| Flyway checksum 오류 | 공유된 변경 파일을 임의 수정했는지 확인한다. 이력을 지우거나 repair부터 실행하지 않는다. |
| API에서 401 또는 403 | 세션·CSRF·권한을 순서대로 확인한다. CORS 문제로 단정하지 않는다. |
| 프록시 연결 실패 | 8080 백엔드 실행과 Vite proxy를 확인한다. 프런트 변경 후 서버를 재시작한다. |
| npm ci 실패 | Node·npm 버전 및 package.json과 lock 파일의 일치 여부를 확인한다. 임의로 lock 파일을 삭제하지 않는다. |

에러 공유 시 실행한 명령, 폴더, 도구 버전, 최초 오류 메시지를 남긴다. 비밀번호·쿠키·토큰은 지운다. 해결 방법은 README의 오류 해결 항목에 짧게 추가한다.

## 8 완료 기록과 다음 작업

### 공통 환경 완료표

아래 표는 실제 실행 후 작성한다. 작성자 PC만 성공하면 공통 환경 완료로 보지 않는다. React 연결 완료와 인증 검증 완료를 구분하여 기록한다.

| 검증 항목 | 결과와 근거 |
| --- | --- |
| 버전·커밋 기준 | JDK / Node / npm / Gradle / DB 이미지 / 커밋 SHA |
| 실행 재현 | 팀원 1 ___  팀원 2 ___  팀원 3 ___  팀원 4 ___ |
| DB 연결 | SQL 접속, UTC 확인, Flyway 적용 상태 |
| 프런트 연결 | React 화면 → API 응답 표시 |
| 인증 연결 | 로그인 / 보호 API / CSRF 거절 / 로그아웃 |
| 빌드·자동 검사 | 로컬 빌드 결과 / PR 검사 링크 |
| 미완료 | 문제 / 담당자 / 해결 목표일 |

### 저장소에 남길 자료

README에는 시작 방법과 자주 쓰는 명령을 둔다. docs/setup-guide.md에는 정확한 버전·필요한 환경변수·오류 해결을 기록한다. .env.example, compose.yaml, Gradle Wrapper, package-lock.json을 함께 관리한다. 실제 비밀값은 저장하지 않는다.

### 이후 첫 배포로 연결

로컬 확인 후 기능 개발과 병행하여 작은 흐름을 먼저 배포한다. Nginx가 React 빌드 결과와 /api를 같은 도메인으로 제공하도록 구성하고, React 경로의 새로고침과 API 경로의 분리를 확인한다.

컨테이너에서 백엔드를 실행하면 DB 주소는 localhost:3307이 아니라 Compose 서비스명 db:3306이다. HTTPS·세션 쿠키·DB 영속 저장·접근 제한·로그를 확인한다. 배포 서버·도메인·실제 비밀값은 별도로 정하고 배포 안내를 작성한다. Docker의 빌드용 JDK와 실행용 Java는 모두 25 기준으로 고정한다. GitHub Actions의 Java 설정도 25로 맞춘다.

고도화 준비는 기능별 경계와 설정 분리로 확보한다. 외부 결제·파일 저장·알림 구현은 해당 기능을 선택한 시점에 추가한다. 지금은 재현 가능한 실행과 최초 통합이 우선이다.

### 기준 문서와 공식 참고 자료

팀 기준: 랜서픽 공통 개발 규칙 v1.0 및 MVP 기획 명세서 v1.0. 공식 문서 확인일: 2026년 10월 29일. 설치·보안 패치·세부 API는 실제 구축 시 다시 확인한다.

Spring Boot 요구사항  https://docs.spring.io/spring-boot/system-requirements.html | Gradle 호환표 https://docs.gradle.org/current/userguide/compatibility.html

DB 초기화  https://docs.spring.io/spring-boot/how-to/data-initialization.html

Spring Security CSRF  https://docs.spring.io/spring-security/reference/servlet/exploits/csrf.html

React 19.3  https://react.dev/blog/2026/09/09/react-19-3

Vite 시작 안내  https://vite.dev/guide/

Compose 환경변수  https://docs.docker.com/compose/how-tos/environment-variables/variable-interpolation/
