# 랜서픽 LancerPick

고정 가격 프리랜서 서비스의 주문·결제·납품·구매 확정·판매 수익을 관리하는 4인 팀 프로젝트입니다. 현재는 개발환경과 패키지 골격을 구성하는 단계입니다.

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

DB 계정에는 해당 데이터베이스에서 개발 및 Flyway 마이그레이션에 필요한 권한이 있어야 합니다. 업무 테이블은 아직 작성하지 않았으며, 이후 검토된 Flyway SQL로 관리합니다.

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

현재 업무 API와 로그인은 미구현입니다. Spring Security 기본 로그인 화면이나 401/403 응답은 업무 기능 구현 완료를 의미하지 않습니다. 백엔드 실행·DB 연결 검증 결과는 실제 실행 후 별도로 기록합니다.

## 프런트엔드 실행

백엔드 터미널을 유지하고 **새 터미널**을 엽니다. 저장소 루트에서 실행합니다.

```powershell
cd .\LancerPickFrontend
npm.cmd ci
npm.cmd run dev
```

브라우저에서 `http://localhost:5173`에 접속합니다. 종료는 해당 터미널에서 `Ctrl+C`입니다. `npm ci`는 최초 실행 또는 잠금 파일의 의존성이 변경됐을 때 실행하고, 이후에는 `npm run dev`로 시작하면 됩니다.

PowerShell에서 `npm.ps1` 실행 정책 오류가 없다면 `npm.cmd` 대신 `npm`을 사용해도 됩니다. 5173 포트가 사용 중이면 기존 Vite 서버를 종료한 뒤 다시 실행합니다.

현재 `/`, `/orders`, `/login`에는 안내 화면이 연결되어 있습니다. 안내 화면은 백엔드 없이도 열 수 있습니다. 실제 API 기능 개발 시에는 백엔드도 함께 실행해야 합니다.

## 프런트와 백엔드 연결

```text
브라우저 → localhost:5173 → /api 요청 프록시 → localhost:8080
```

프런트에서는 `/api/...` 상대 경로로 요청합니다. 개발 프록시는 `LancerPickFrontend/vite.config.ts`에 설정되어 있습니다. 세션 로그인·CSRF·실제 업무 API 연결은 후속 구현 대상입니다.

## 빌드와 검사

프런트 폴더에서 실행합니다.

```powershell
npm.cmd run typecheck
npm.cmd run lint
npm.cmd run build
```

백엔드 폴더에서 실행합니다.

```powershell
.\gradlew.bat clean build
```

백엔드 통합 테스트는 `@IntegrationTest`와 `test` 프로필을 사용합니다. 개발용 개인 접속 파일을 읽지 않고 `TEST_DB_URL`, `TEST_DB_USERNAME`, `TEST_DB_PASSWORD`로 테스트 전용 MySQL에 접속합니다. 테스트 전용 DB·계정 준비와 PowerShell 실행 방법은 [테스트 DB 안내](docs/test-database.md)를 따릅니다. 테스트용 URL을 생략하면 `localhost:3306/lancerpick_test`를 사용하며 계정·비밀번호는 반드시 별도로 지정합니다. 일반 실행의 `DB_*`로 대체하지 않습니다.

공통 JPA 설정은 OSIV 비활성화, 스키마 검증(`validate`), JDBC 시각 UTC입니다. Flyway는 활성화하며 테이블은 SQL로 관리합니다. 아직 업무 엔티티·마이그레이션·인증 구현 완료를 의미하지 않습니다.

## GitHub Actions CI

저장소 루트의 `.github/workflows/ci.yml`이 `main` 대상 PR 생성·수정과 `main` push 시 자동 실행됩니다. GitHub Actions 탭에서 수동 실행도 가능합니다. 프런트와 백엔드는 별도 작업으로 병렬 검사합니다.

| 작업 | 검사 내용 |
| --- | --- |
| Frontend checks | 고정 Node 버전, npm ci, 타입 검사, 린트, 빌드 |
| Backend checks | JDK 25, 임시 MySQL 8.4, Gradle 빌드·테스트, JPA 스키마 검증 |

CI는 실행마다 `lancerpick_ci` DB를 만들고 종료 시 폐기합니다. 워크플로는 `TEST_DB_*`를 전달하며 JPA 설정은 백엔드 공통 파일을 사용합니다. 워크플로의 `ci_only_*` 값은 격리된 테스트 전용 값이며 실제 접속 정보가 아닙니다. 개인 설정 파일이나 로컬 DB를 사용하지 않고 별도 Secrets 등록도 필요하지 않습니다. JDBC의 공개키 조회 허용·TLS 비활성화 옵션은 CI 임시 DB 전용입니다.

테이블을 추가할 때는 엔티티와 Flyway SQL을 함께 반영해야 합니다. CI는 테스트를 생략하거나 스키마를 자동 생성해 검사를 우회하지 않습니다. 현재 프런트 자동 테스트 스크립트는 없으므로 타입·린트·빌드를 검사하고, 기능 테스트가 생기면 해당 명령을 추가합니다.

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
