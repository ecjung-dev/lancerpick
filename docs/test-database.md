# 공통 JPA 설정과 테스트 DB 실행

2026-11-04: test 프로필은 `toss.topup-enabled=false`로 외부 결제 호출을 끈다. 개인 토스 키 없이 전체 테스트가 실행되어야 하며 제공자 대역·로컬 HTTP 서버를 사용한다. 공통 개발 설정의 활성화 기본값을 test가 상속하지 않는다. [발견·검증 기록](account-trade-integration.md)을 참고한다.

역할 배정 갱신 2026-10-29: [확정 담당·지원 원칙·업무 비중](work-plan.md#확정-역할-배정)을 따른다. 상세 일정·검토자·배포 담당은 별도 확정한다.

변경일: 2026-10-29. 개인 접속 파일과 기존 DB 데이터는 변경하지 않았다.

## 1. 설정 구분

| 구분 | 설정 파일·입력 | 적용 내용 |
| --- | --- | --- |
| 공통 | LancerPick/src/main/resources/application.properties | OSIV=false, ddl-auto=validate, JDBC UTC, Flyway 활성화 |
| 일반 로컬 실행 | application-default.properties | 기존 local-application.properties를 선택적으로 import, DB_* 사용 |
| demo 예시 실행 | application-demo.properties | DB·JPA·Flyway 자동 설정 제외, 개인 파일 import 없음, 로컬 127.0.0.1 수신 |
| 통합 테스트 | src/test/resources/application-test.properties | TEST_DB_* 사용, 일반 개인 파일 import 없음 |
| CI | .github/workflows/ci.yml | 임시 lancerpick_ci와 TEST_DB_*, JPA 공통 설정 재사용 |

`@IntegrationTest`는 `@SpringBootTest`, `@ActiveProfiles("test")`, DB 접속 전 검사 Initializer를 묶은 테스트 어노테이션이다. DB를 사용하는 새 Spring 통합 테스트에도 이를 사용한다. 순수 단위 테스트와 DB를 명시적으로 제외한 공통 HTTP 테스트에는 적용하지 않는다. DB를 사용하는 애플리케이션 실행 프로필을 별도로 지정하면 default 프로필의 개인 파일 import가 적용되지 않으므로 DB_* 환경변수를 제공한다. demo는 의도적으로 DB 자동 설정을 제외하므로 DB_*가 필요 없다. prod는 배포용 이름이며 현재 파일만으로 배포 환경·인증·쿠키 정책이 완성되지는 않는다. 예시 API는 prod에서 제외된다.

## 2. 로컬 테스트 DB 준비

MySQL 관리 권한으로 개발 DB `lancerpick`과 다른 `lancerpick_test`를 준비한다. 아래는 직접 실행할 예시이며 이번 작업에서 실행하지 않았다. 실제 비밀번호는 예시와 다르게 정하고 저장소에 넣지 않는다. 같은 이름의 DB나 계정이 이미 있다면 먼저 기존 용도를 확인한다.

```sql
CREATE DATABASE IF NOT EXISTS lancerpick_test CHARACTER SET utf8mb4;
CREATE USER 'lancerpick_test'@'localhost' IDENTIFIED BY 'REPLACE_WITH_TEST_ONLY_PASSWORD';
GRANT ALL PRIVILEGES ON lancerpick_test.* TO 'lancerpick_test'@'localhost';
```

이 계정에는 개발 DB 권한을 부여하지 않는다. 접속 호스트에 따른 MySQL 계정 매칭은 실제 환경에서 확인한다. DB 이름 검사에 더해 계정 권한으로도 실수에 의한 개발 DB 변경을 제한한다. 테스트가 데이터를 생성할 수 있으므로 개인·공유 개발 DB를 테스트용 이름으로 바꿔 재사용하지 않는다.

## 3. PowerShell에서 실행

저장소 루트에서 새 터미널을 열고 실행한다. 테스트용 URL·계정·비밀번호는 현재 프로세스에만 전달하며 파일로 저장하지 않는다.

```powershell
$env:TEST_DB_URL = 'jdbc:mysql://localhost:3306/lancerpick_test?connectionTimeZone=UTC'
$env:TEST_DB_USERNAME = 'lancerpick_test'
$testDbCredential = Get-Credential -UserName lancerpick_test -Message '테스트 DB 계정 비밀번호'
$env:TEST_DB_PASSWORD = $testDbCredential.GetNetworkCredential().Password
cd .\LancerPick
.\gradlew.bat clean build
```

종료 후 같은 창에서 필요하면 테스트 비밀번호 환경변수를 제거한다.

```powershell
Remove-Item Env:TEST_DB_PASSWORD -ErrorAction SilentlyContinue
$testDbCredential = $null
```

일반 앱은 기존 방식으로 `bootRun`한다. `TEST_DB_*`가 없으면 통합 테스트는 실패하며 개발용 `DB_*`를 대신 사용하지 않는다. URL은 생략 시 `localhost:3306/lancerpick_test?connectionTimeZone=UTC`이고 계정·비밀번호는 필수다. 포트가 3307이면 TEST_DB_URL도 변경한다.

테스트 URL은 단일 MySQL 호스트와 DB 이름 `lancerpick_test` 또는 `lancerpick_ci`만 허용한다. 개발 DB·유사한 접미 이름·다른 DB 드라이버는 컨텍스트 초기화 단계에서 거부한다. 커스텀 TEST_DB_URL에는 `connectionTimeZone=UTC`를 유지한다. IPv6·다중 호스트 등 다른 URL 형식이 필요하면 검토 후 검사와 테스트를 함께 수정한다.

## 4. 검사 구분

검사는 실행 대상에 따라 아래처럼 구분한다.

| 검사 | DB·개인 파일 사용 | 확인하는 것 |
| --- | --- | --- |
| common 패키지 테스트 | 없음; 기본 프로필 검사도 테스트 설정으로 DB·개인 import 제외 | 예시 API·401/403·CSRF·204·정규화 |
| TestDatabaseInitializerTests | 없음 | 테스트 DB URL·설정 보호 |
| @IntegrationTest 전체 컨텍스트·업무 DB 테스트 | TEST_DB_*와 전용 MySQL 필요 | 컨텍스트·마이그레이션·해당 업무 검증 |
| CI 전체 빌드 | 임시 lancerpick_ci | 저장소에 실제 포함된 테스트; 미구현 기능 검증은 아님 |

DB 없는 공통 검사는 다음과 같다.

```powershell
.\gradlew.bat test --tests "com.example.lancerpick.common.*" --tests "com.example.lancerpick.support.TestDatabaseInitializerTests"
```

DB 없이 보호 검사만 실행하려면 백엔드 폴더에서 다음 명령을 사용한다.

```powershell
.\gradlew.bat test --tests com.example.lancerpick.support.TestDatabaseInitializerTests
```

이 검사는 허용 DB, 개발 DB·유사 이름 차단, 실제 설정 override 차단과 오류의 접속 정보 미노출을 확인한다. 실제 DB 연결·Flyway 실행·업무 동시성 검증과는 다르다.

CI의 임시 MySQL은 실행마다 생성·폐기한다. 로컬 전용 테스트 DB는 자동으로 삭제하거나 초기화하지 않는다. 향후 데이터 변경 테스트에서는 트랜잭션 롤백·데이터 정리·병렬 실행 기준을 별도 설계한다. V1과 엔티티는 함께 추가하고 `validate`를 끄거나 H2로 바꿔 검사를 우회하지 않는다.

## 5. 루트 제외 규칙

루트 `.gitignore`에 IDE 설정·환경변수·개인 접속 파일·일반 비밀키 파일·캐시를 추가했다. 하위 규칙을 유지하며 Gradle Wrapper와 npm 잠금 파일은 제외하지 않는다. 이미 Git이 추적하는 파일에는 `.gitignore`가 소급 적용되지 않는다. 이번 작업에서는 Git 인덱스를 변경하거나 기존 파일을 삭제하지 않았다.

## 6. 이번 변경의 검증 결과

JDK 25와 Gradle Wrapper 9.8.2에서 다음 명령으로 5개 검사를 실행했다. 결과는 성공 5개, 실패·오류·건너뜀 0개다. 애플리케이션과 테스트 소스 컴파일도 성공했다.

```powershell
# LancerPick 폴더
.\gradlew.bat --gradle-user-home ..\.gradle-user-home test --tests com.example.lancerpick.support.TestDatabaseInitializerTests --no-daemon
```

확인한 내용은 허용 DB 이름, 개발 DB·유사 이름 차단, 실제 datasource override 검사, 오류에 URL 비노출, 공통 JPA·test 프로필의 로딩, 개발 계정으로의 대체 금지다. CI에서 JPA 환경변수 중복을 제거하고 공통 파일을 참조하는지도 점검했다.

기존 사용자 Gradle 캐시 잠금 접근 오류와 샌드박스의 네트워크·데몬 연결 제한 때문에 검증 시 프로젝트 내부 캐시를 사용하고 승인된 실행으로 재검사했다. 이 캐시는 루트 .gitignore에서 제외한다. 테스트 환경변수·계정 설정을 대신하지는 않는다.

실제 MySQL 테스트 DB·계정 생성, DB 연결이 필요한 LancerPickApplicationTests, 전체 clean build, 원격 CI는 이번에 실행하지 않았다. 테스트 전용 계정 준비 후 전체 빌드와 CI 결과를 확인해야 한다. 업무 SQL은 아직 추가하지 않았다.

설정 분리는 Spring Boot의 [프로필 안내](https://docs.spring.io/spring-boot/reference/features/profiles.html), 접속 전 검사는 Spring의 [Context Initializer 안내](https://docs.spring.io/spring-framework/reference/testing/testcontext-framework/ctx-management/initializers.html)를 참고했다.
