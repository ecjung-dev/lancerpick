# 랜서픽 백엔드

Java 25 / Spring Boot 4.1.2 기반 백엔드입니다. 공통 HTTP·Security·문자열·시간 처리와 메모리 예시 상품 조회가 구현되어 있습니다. 지갑 최초 지급과 잔액·내역 조회 서비스·보호 Controller도 구현했으며, 인증 principal의 회원 ID 계약과 React 마이페이지를 준비했습니다. 주문 엔티티(`order/entity/Order`·`OrderStatus`·`OrderSnapshot`·`OrderHistory`·`OrderHistoryAction`)와 생성 요청 DTO의 초안·단위 테스트를 추가했습니다. 주문 이력은 최초 생성(`CREATED`) 팩터리까지 구현했으며 후속 행동의 이력 생성은 별도 작업입니다. 주문 생성 내부 서비스의 원자적 저장·재전송·동시 요청 처리와 MySQL 통합 테스트를 추가했습니다. 실제 로그인·회원가입 호출부·프런트 인증 상태 연결·주문 HTTP API·결제는 후속 작업입니다. 잠금·격리 수준·연동 조건과 검증 한계는 [주문 생성 서비스](../docs/order-creation.md)를 따릅니다. 공통 코드는 [백엔드 기능 개발 가이드](../docs/backend-common-guide.md)를 따라 재사용하고, 지갑 연결은 [초기 지급 계약](../docs/wallet-initialization.md)·[조회 연결 계약](../docs/wallet-query.md)을 참고합니다.

## 패키지 구조

토스 테스트 충전의 [V2 저장 구조](../docs/wallet-topup-schema.md), [신청·본인 조회](../docs/wallet-topup-api.md), [승인·지급·본인 재확인](../docs/wallet-topup-approval.md)을 추가했습니다. 일반 실행 시 Flyway가 V2를 적용할 수 있으므로 연결 대상 DB를 확인합니다. 외부 호출은 기본 비활성화이며 TOSS_TOPUP_ENABLED=true와 테스트 시크릿으로 활성화합니다. 검증된 외부 DONE에서만 잔액을 늘리며, 실제 토스 결제창·로그인·프런트 연동은 아직 검증 전입니다.

기본 패키지는 `com.example.lancerpick`입니다.

```text
com.example.lancerpick/
  LancerPickApplication.java
  auth/           # 로그인·로그아웃·CSRF 조회
  member/         # 가입·내 정보·표시 이름 수정
  seller/         # 판매자 프로필
  product/        # 상품·카테고리·검색·판매 상태
  order/          # 주문·납품·수정·취소 요청·구매 확정·이력
  wallet/         # 구매 적립금 잔액·변동 내역·초기 지급
  payment/        # 주문 결제·전액 환불
  settlement/     # 수수료·판매 수익 계산·조회
  review/         # 후기 작성·조회
  admin/          # 관리자 상품 조회·숨김·복구·운영 이력
  common/
    config/       # 애플리케이션 공통 설정
    security/     # Security 설정·인증 사용자·공통 접근 제어
    exception/    # 공통 예외·오류 응답 처리
```

각 기능에 아래 패키지를 두었습니다. `.gitkeep`은 빈 폴더를 Git에 보존하는 파일이며 Java 코드는 아닙니다. 실제 구현 파일이 생기면 해당 폴더의 `.gitkeep`을 제거할 수 있습니다. 모든 계층에 클래스를 만들어야 한다는 뜻은 아닙니다.

```text
order/
  controller/     # HTTP 요청·입력 검증·응답
  dto/            # API 요청·응답 객체
  entity/         # 해당 기능의 영속 엔티티
  repository/     # JPA 저장·조회·잠금 및 QueryDSL 조회 구현
  service/        # 권한·상태·업무 규칙·트랜잭션
```

## 경계와 협업

- 김창재 영역: auth·member·admin, common/security 등 필요한 공통 처리
- 박소현 영역: seller·product·review
- 정은찬 영역: order
- 황준범 영역: wallet·payment·settlement
- 김창재, 박소현, 정은찬, 황준범으로 배정했습니다. 정은찬은 조장·통합 조율, 황준범은 React 공통 기반을 겸합니다. 검토자·배포 담당은 별도 작업표에서 확정합니다.
- Member 엔티티는 member/entity에 둡니다. auth에서 동일한 회원 엔티티를 다시 만들지 않습니다.
- Category는 product, 납품·수정·취소 요청·주문 이력은 order에 둡니다.
- 관리자 상품 운영 이력은 admin에서 관리하며 상품 엔티티를 중복 정의하지 않습니다.
- 기능 간 협력은 내부 서비스 호출로 연결합니다. 업무 시작 서비스가 전체 트랜잭션을 관리하고 내부 DB 변경은 같은 트랜잭션에 참여합니다.
- 결제·환불·구매 확정의 호출 경계는 정은찬·황준범이 함께 설계합니다. 주문 → 구매자 지갑 잠금 순서를 유지합니다.
- 엔티티를 API 응답으로 직접 반환하지 않습니다. 기능별 DTO로 변환합니다.
- 기능 전용 규칙은 각 기능에 두고 실제 공통 정책만 common으로 옮깁니다.

## 개발 환경

프로젝트의 Gradle Wrapper와 JDK 25를 사용합니다. 개인 DB 접속 정보는 Git에 포함하지 않습니다. 프런트 개발 서버의 `/api` 프록시는 `http://localhost:8080`을 대상으로 합니다.

### 개인 DB 설정

백엔드 루트(`build.gradle`이 있는 폴더)의 `local-application.properties`에 `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` 값을 작성합니다. 이 파일은 Git에서 제외합니다. 신규 팀원은 루트에서 아래 명령을 한 번 실행한 뒤 자신의 접속 정보로 수정합니다. 기존 개인 파일이 있으면 덮어쓰지 않습니다.

```powershell
Copy-Item local-application.properties.example local-application.properties
```

공통 `src/main/resources/application.properties`에는 `${DB_URL}` 등의 참조와 JPA·Flyway 기준이 있습니다. OSIV는 비활성화하고 스키마는 `validate`, JDBC 시각은 UTC로 설정합니다. 개인 접속 파일 import는 `application-default.properties`에서 관리하므로 활성 프로필이 없는 일반 로컬 실행에서만 적용됩니다. 개인 파일은 resources 밖에 두므로 기본 JAR 패키징에 포함되지 않습니다. IDE의 Working directory는 백엔드 루트로 설정합니다. 배포에서는 `DB_*` 환경변수를 제공하며 별도 프로필 사용 시 개인 파일을 자동으로 읽지 않습니다.

예시 Controller·Service는 demo 패키지에 있고 기본(default)·demo에서만 동작하며 prod에서는 제외됩니다. 공통 Security 정책·CSRF 조회·401/403 JSON 응답과 인증 principal의 회원 ID 계약은 준비되어 있지만, 실제 로그인·로그아웃·비밀번호 인코더와 로그인 시 principal을 구성하는 연결은 후속 구현 대상입니다. 최초 SQL은 [Flyway V1](src/main/resources/db/migration/V1__init.sql)에 있습니다. MySQL 8.0.46 테스트 DB에서 V1 적용·clean build 성공을 사용자 실행 결과로 확인했습니다. [검증 기록](../docs/physical-db.md#2026-11-02-사용자-실행-검증-결과)과 미검증 범위를 구분합니다. 일반 실행·DB 통합 테스트는 연결된 DB에 마이그레이션을 시도하므로 대상과 기존 이력을 먼저 확인합니다. 스키마 초기 적용 결과가 회원 인증·업무 API·엔티티 매핑·동시성 검증 완료를 의미하지는 않습니다. 실행 환경은 팀의 공통 개발 환경 구축 가이드를 따릅니다.

## 실행

로컬 MySQL을 실행하고 개인 접속 정보를 작성한 뒤, 이 폴더에서 실행합니다.

```powershell
java -version
.\gradlew.bat --version
.\gradlew.bat bootRun
```

Java와 Gradle JVM은 25를 사용합니다. 백엔드는 기본 8080 포트에서 실행하며 종료는 Ctrl+C입니다. IntelliJ 실행 시에도 Working directory를 이 폴더로 지정합니다.

빌드·테스트는 테스트 전용 DB가 준비된 상태에서 `.\gradlew.bat clean build`로 실행합니다. DB를 사용하는 Spring 통합 테스트에는 `support.IntegrationTest`를 사용합니다. 이 어노테이션이 `test` 프로필과 접속 전 DB 이름 검사를 적용합니다. DB 통합 테스트는 `TEST_DB_*`만 사용하며 개발용 개인 파일을 읽지 않습니다. DB 없는 공통 HTTP·Security 검사 방법과 적용 범위도 아래 테스트 DB 안내에서 구분합니다. [테스트 DB 안내](../docs/test-database.md)에 따라 별도 계정·DB를 준비합니다. 업무 기능·인증의 실제 검증 완료를 의미하지 않습니다. 프런트와 함께 실행하는 순서와 오류 해결은 [저장소 실행 안내](../README.md)를 참고합니다.
