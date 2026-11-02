# 랜서픽 LancerPick

고정 가격 프리랜서 서비스의 주문·결제·납품·구매 확정·판매 수익을 관리하는 4인 팀 프로젝트입니다. 2026-11-02 기준 공통 HTTP·Security 기반과 메모리 예시 상품 조회·화면이 구현되어 있습니다. 실제 회원 인증·상품 DB·주문·결제 등 업무 기능은 후속 구현 대상입니다.

## 팀 역할

| 담당자 | 역할 | 핵심 책임 |
| --- | --- | --- |
| 정은찬 | 주문·거래 / 조장 | 주문·납품·수정·취소·구매 확정·통합 조율 |
| 황준범 | 결제·정산 / React 공통 | 적립금·결제·환불·정산·공통 프런트·API 연결 방식 |
| 김창재 | 회원·인증·관리자 | 회원가입·로그인·권한·관리자 상품 운영 |
| 박소현 | 판매자·상품·후기 | 판매자·상품·검색·후기 |

각자 자기 기능의 화면·API·DB·테스트를 책임지고, 조장과 React 공통 담당자가 어려운 설계·로직을 지원합니다. [확정 배정과 예상 업무 비중](docs/work-plan.md#확정-역할-배정)을 참고합니다.

## 저장소 구조

```text
lancerpick/
├─ LancerPick/           # Spring Boot 백엔드
├─ LancerPickFrontend/   # React 프런트엔드
├─ docs/                 # 기획·API·ERD·개발 안내
├─ .github/
│  └─ workflows/
│     └─ ci.yml          # 자동 빌드·검사
├─ .gitignore            # 공통 Git 제외 규칙
└─ README.md             # 프로젝트 소개·실행 안내
```

## 준비 사항

토스 테스트 충전 저장 확장: [Flyway V2와 충전 스키마](docs/wallet-topup-schema.md)를 추가했습니다. V1 기본 16개 테이블에 충전 신청 테이블 1개가 추가됩니다. 일반 백엔드 실행 시 V2가 적용될 수 있으므로 대상 DB·적용 이력을 확인합니다. 충전 API·토스 승인·실제 지급은 후속 구현입니다.

Windows PowerShell 기준입니다. 명령의 시작 위치는 저장소 루트입니다. 저장소를 다른 경로에 복제해도 폴더 구조는 동일합니다.

| 도구 | 팀 기준 |
| --- | --- |
| Java | Eclipse Temurin JDK 25 |
| 백엔드 | Spring Boot 4.1.2, 저장소의 Gradle Wrapper 사용 |
| Node.js / npm | 24.22.1 / 11.19.1 |
| DB | 각자의 로컬 MySQL 8.4 |

```powershell
git --version
java -version
node --version
npm.cmd --version
```

팀원은 저장소를 복제해서 사용합니다. Spring Initializr나 Vite 생성 명령을 다시 실행하지 않습니다.

## 백엔드 실행

### 1. 로컬 MySQL 준비

MySQL을 실행하고 `lancerpick` 데이터베이스와 접속 가능한 개인 개발용 계정을 준비합니다. 이미 만들었다면 다시 생성하지 않습니다. MySQL 클라이언트에서 DB를 생성할 때의 예시는 다음과 같습니다.

```sql
CREATE DATABASE IF NOT EXISTS lancerpick CHARACTER SET utf8mb4;
```

DB 계정에는 해당 데이터베이스에서 개발 및 Flyway 마이그레이션에 필요한 권한이 있어야 합니다. 16개 업무 테이블의 최초 SQL은 [Flyway V1](LancerPick/src/main/resources/db/migration/V1__init.sql) 한 곳에서 관리합니다. 2026-11-02 실행용 경로로 이동했고, MySQL 8.0.46 테스트 DB에서 V1 적용·clean build 성공을 사용자 실행 결과로 확인했습니다. 기준 MySQL 8.4 재현과 업무 엔티티 매핑은 아직 미검증입니다. [검증 상세](docs/physical-db.md#2026-11-02-사용자-실행-검증-결과)를 참고합니다. 이제 일반 실행이나 DB 통합 테스트 시 Flyway가 연결된 DB에 테이블 생성을 시도하므로 먼저 대상 DB와 적용 이력을 확인합니다. demo 실행은 DB를 사용하지 않습니다.

### 2. 개인 접속 정보 작성

저장소 루트에서 실행합니다.

```powershell
cd .\LancerPick
# 최초 한 번만 복사합니다. 기존 개인 설정 파일은 덮어쓰지 않습니다.
Copy-Item local-application.properties.example local-application.properties
```

`LancerPick/local-application.properties`를 열어 실제 값으로 수정합니다.

```properties
DB_URL=jdbc:mysql://localhost:3306/lancerpick?connectionTimeZone=UTC
DB_USERNAME=본인의_DB계정
DB_PASSWORD=본인의_DB비밀번호
```

포트·DB 이름은 본인의 환경에 맞춥니다. Docker로 호스트 3307 포트를 사용한다면 URL도 3307로 바꿉니다. 개인 파일은 Git에서 제외되며 예시 파일만 공유합니다. 공통 `application.properties`는 `${DB_URL}`, `${DB_USERNAME}`, `${DB_PASSWORD}`로 참조합니다.

### 3. Spring Boot 시작

`LancerPick` 폴더에서 실행합니다. 첫 실행에는 Gradle과 의존성 다운로드가 필요합니다.

```powershell
.\gradlew.bat --version
.\gradlew.bat bootRun
```

기본 주소는 `http://localhost:8080`입니다. 터미널의 애플리케이션 시작 로그를 확인합니다. 종료는 `Ctrl+C`입니다.

IntelliJ에서는 `LancerPick`을 Gradle 프로젝트로 열고 Project SDK와 Gradle JVM을 모두 25로 지정합니다. `LancerPickApplication`을 실행하며 Working directory는 `build.gradle`이 있는 `LancerPick` 폴더로 설정합니다. 개인 설정의 상대 경로는 이 폴더를 기준으로 읽습니다.

현재 메모리 예시 조회 `/api/demo/categories`, `/api/demo/products`와 CSRF 토큰 조회 `/api/auth/csrf`, 공통 오류 응답은 구현되어 있습니다. formLogin·httpBasic·기본 logout은 비활성화되어 있고 실제 회원 인증·세션 로그인·업무 API는 미구현입니다. 401/403·CSRF 검사 통과를 실제 로그인 구현 완료로 해석하지 않습니다. 실제 DB 연결 결과는 별도로 기록합니다.

DB 없이 예시 화면만 확인하려면 이 폴더에서 선택적으로 실행합니다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=demo"
```

`demo`는 DB·JPA·Flyway 자동 설정과 개인 DB 파일 import를 사용하지 않으며 127.0.0.1에서만 수신합니다. DB 검증·운영 배포용 프로필이 아닙니다. 기본 실행은 개인 DB 설정이 필요합니다. [실행 방식 구분](docs/test-database.md#1-설정-구분)을 참고합니다.

## 프런트엔드 실행

백엔드 터미널을 유지하고 **새 터미널**을 엽니다. 저장소 루트에서 실행합니다.

```powershell
cd .\LancerPickFrontend
npm.cmd ci
npm.cmd run dev
```

브라우저에서 `http://localhost:5173`에 접속합니다. 종료는 해당 터미널에서 `Ctrl+C`입니다. `npm ci`는 최초 실행 또는 잠금 파일의 의존성이 변경됐을 때 실행하고, 이후에는 `npm run dev`로 시작하면 됩니다.

PowerShell에서 `npm.ps1` 실행 정책 오류가 없다면 `npm.cmd` 대신 `npm`을 사용해도 됩니다. 5173 포트가 사용 중이면 기존 Vite 서버를 종료한 뒤 다시 실행합니다.

현재 `/`는 서버의 메모리 예시 상품을 조회하며 검색·카테고리·페이지·상품 미리보기를 제공합니다. `/orders`, `/login`, `/signup`은 안내 화면입니다. 상품 화면의 조회에는 백엔드가 필요하며, 연결 실패를 프런트 샘플로 자동 대체하지 않습니다. 실제 상품 DB 조회·상세·주문은 아직 미구현입니다.

## 프런트와 백엔드 연결

```text
브라우저 → localhost:5173 → /api 요청 프록시 → localhost:8080
```

프런트에서는 `/api/...` 상대 경로로 요청합니다. 개발 프록시는 `LancerPickFrontend/vite.config.ts`에 설정되어 있습니다. 공통 요청 함수는 세션 쿠키·CSRF 헤더·204·오류 처리를 제공합니다. 실제 로그인·로그아웃 후 인증 상태 및 캐시 연동과 업무 API 연결은 후속 구현 대상입니다. 백엔드 포트를 바꾸면 [API_PROXY_TARGET 설정](docs/frontend-common-guide.md#2-3-백엔드-포트가-8081일-때만)을 따릅니다.

## 빌드와 검사

프런트 폴더에서 실행합니다.

```powershell
npm.cmd run test:api
npm.cmd run typecheck
npm.cmd run lint
npm.cmd run build
```

아래 Gradle 명령은 백엔드 폴더에서 실행합니다. 공통 HTTP·Security·정규화 검사는 DB 없이 실행할 수 있습니다. 개인 DB 파일을 읽지 않는 테스트 구성을 사용하며 실제 DB 연결·회원 로그인·업무 기능 검증을 대신하지 않습니다.

```powershell
.\gradlew.bat test --tests "com.example.lancerpick.common.*" --tests "com.example.lancerpick.support.TestDatabaseInitializerTests"
```

전체 빌드는 아래 테스트 전용 DB 설정을 준비한 뒤 백엔드 폴더에서 실행합니다.

```powershell
.\gradlew.bat clean build
```

백엔드 통합 테스트는 `@IntegrationTest`와 `test` 프로필을 사용합니다. 개발용 개인 접속 파일을 읽지 않고 `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`로 테스트 전용 MySQL에 접속합니다. 테스트 전용 DB·계정 준비와 PowerShell 실행 방법은 [테스트 DB 안내](docs/test-database.md)를 따릅니다. 테스트용 URL을 생략하면 `localhost:3306/lancerpick_test`를 사용하며 계정·비밀번호는 반드시 별도로 지정합니다. 일반 실행의 `DB_*`로 대체하지 않습니다.

공통 JPA 설정은 OSIV 비활성화, 스키마 검증(`validate`), JDBC 시각 UTC입니다. Flyway는 활성화하며 테이블은 SQL로 관리합니다. V1 적용은 앞서 링크한 검증 기록의 범위로 확인했지만 업무 엔티티·인증·거래 기능 구현 완료를 의미하지 않습니다.

## GitHub Actions CI

저장소 루트의 `.github/workflows/ci.yml`이 `main` 대상 PR 생성·수정과 `main` push 시 자동 실행됩니다. GitHub Actions 탭에서 수동 실행도 가능합니다. 프런트와 백엔드는 별도 작업으로 병렬 검사합니다.

| 작업 | 검사 내용 |
| --- | --- |
| Frontend checks | 고정 Node 버전, npm ci, 타입 검사, 린트, 빌드 |
| Backend checks | JDK 25, 임시 MySQL 8.4, Gradle 빌드·테스트, JPA 스키마 검증 |

CI는 실행마다 `lancerpick_ci` DB를 만들고 종료 시 폐기합니다. 워크플로는 `TEST_DB_*`를 전달하며 JPA 설정은 백엔드 공통 파일을 사용합니다. 워크플로의 `ci_only_*` 값은 격리된 테스트 전용 값이며 실제 접속 정보가 아닙니다. 개인 설정 파일이나 로컬 DB를 사용하지 않고 별도 Secrets 등록도 필요하지 않습니다. JDBC의 공개키 조회 허용·TLS 비활성화 옵션은 CI 임시 DB 전용입니다.

테이블을 추가할 때는 엔티티와 Flyway SQL을 함께 반영해야 합니다. CI는 테스트를 생략하거나 스키마를 자동 생성해 검사를 우회하지 않습니다. 프런트에는 `test:api` 자동 테스트가 있으나 현재 CI는 타입·린트·빌드만 실행합니다. `test:api`와 docs SQL 검사 스크립트는 현재 CI에 연결되어 있지 않습니다. CI 성공을 이 검사들의 실행이나 검토용 16개 테이블의 적용 성공으로 해석하지 않습니다. 이번 문서 갱신에서는 CI 설정을 변경하지 않습니다.

이 구성은 자동 검사만 수행합니다. 실제 GitHub 실행 결과는 push 또는 PR 후 확인해야 합니다. PR 병합 시 두 검사를 필수로 강제하려면 저장소에서 브랜치 보호 또는 규칙을 별도로 설정합니다.

## 자주 발생하는 문제

| 증상 | 확인 사항 |
| --- | --- |
| Java 버전 불일치 | 터미널 Java, IntelliJ SDK, Gradle JVM이 모두 25인지 확인 |
| DB 연결 실패 | MySQL 실행 상태, 포트, DB 존재 여부, 계정·권한·비밀번호 확인 |
| DB 설정값을 찾을 수 없음 | 개인 파일 위치와 실행 Working directory 확인 |
| `npm.ps1` 실행 차단 | `npm.cmd`로 실행 |
| 5173 포트 사용 중 | 기존 프런트 개발 서버 종료 |
| `/api` 연결 거부 | 8080 백엔드가 실행 중인지 확인 |

세부 구조는 [백엔드 README](LancerPick/README.md)와 [프런트엔드 README](LancerPickFrontend/README.md)를 참고합니다.

기능 구현 방법은 [백엔드 공통 가이드](docs/backend-common-guide.md)와 [프런트 공통 가이드](docs/frontend-common-guide.md), 개발 착수 순서·남은 결정은 [작업표](docs/work-plan.md#5-개발-착수-순서와-연결-계약)를 참고합니다.
