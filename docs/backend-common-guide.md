# 백엔드 기능 개발 가이드

처음에는 **1~5절**을 읽고, 현재 예시 API의 요청 하나가 어디로 이동하는지 따라가세요. 이후 실제 DB 연동과 결제 같은 변경 기능을 구현합니다.

이 문서는 구현 방법을 설명합니다. 기존 기획·담당 배정·API 계약을 바꾸거나 미구현 기능을 완료 처리하지 않습니다.

## 목차

1. 먼저 실행해 보기
2. 작성 순서와 실행 순서는 다릅니다
3. DB·Entity·Repository·DTO·Service·Controller 구분하기
4. API 명세에서 작업 내용 찾기
5. 현재 상품 조회 예제 따라 읽기
6. 예시 데이터를 실제 DB 조회로 바꾸기
7. 결제 같은 변경 기능의 작성 방법
8. 공통 코드와 Security 사용법
9. 기능 완료 전 확인하기

## 1. 먼저 실행해 보기

### 1-1. 기본 실행

IntelliJ에서 `LancerPick`을 Gradle 프로젝트로 열고 Project SDK·Gradle JVM을 JDK 25로 설정합니다. 실행 대상은 `LancerPickApplication`입니다.

- Working directory: `C:\project\LancerPick`
- 기본 서버 포트: 8080
- 기존 개인 DB 설정: `local-application.properties`
- 메인 예시 조회를 위한 추가 프로필: 필요 없음

터미널에서 실행하려면 다음을 사용합니다.

```powershell
cd C:\project\LancerPick
.\gradlew.bat bootRun
```

개인 DB 파일의 준비 방법은 [루트 README](../README.md)를 따릅니다. 예시 상품 조회는 DB 데이터를 쓰지 않지만, 기본 실행의 DB·JPA·Flyway 연결 설정 자체는 유지됩니다.

### 1-2. DB 없이 화면만 확인하고 싶을 때

다음은 선택 사항입니다. 모든 팀원이 이 설정을 해야 하는 것은 아닙니다.

```powershell
.\gradlew.bat bootRun --args="--spring.profiles.active=demo"
```

`demo` 프로필은 DB·JPA·Flyway 자동 설정을 제외하며 개인 DB 접속 파일을 읽지 않습니다. 따라서 이 프로필에서 기동에 성공한 것이 실제 DB 연결·매핑 성공을 뜻하지는 않습니다.

예시 API는 기본(default) 실행과 demo에서만 활성화하고, prod에서는 제외합니다. demo 프로필은 로컬 127.0.0.1에서만 수신하도록 설정했습니다. 기본 실행에도 같은 수신 주소 제한이 자동 적용되는 것은 아닙니다.

### 1-3. 8080이 이미 사용 중일 때

사용 중인 프로그램을 확인하고, 필요하다면 다른 포트를 선택합니다.

```powershell
.\gradlew.bat bootRun --args="--server.port=8081"
```

프런트의 프록시 대상도 8081로 맞춰야 합니다. 방법은 [프런트 가이드](frontend-common-guide.md)의 실행 절을 참고하세요. 서버 종료는 `Ctrl+C`입니다.

## 2. 작성 순서와 실행 순서는 다릅니다

### 2-1. 개발자가 작성하는 순서

작은 기능 하나를 골라 다음 순서로 연결합니다.

```text
기능·API 명세 확인
 → DB 설계·Flyway SQL 확인 또는 작성
 → Entity
 → Repository
 → 요청·응답 DTO
 → Service
 → Controller
 → 백엔드 검증
 → 프런트 타입·API 함수·훅·화면 연결
 → 전체 동작 검증
```

기존 테이블·Entity·Repository가 있다면 재사용합니다. 필드와 조회 메서드를 필요한 만큼 추가하면 됩니다. 모든 기능마다 새 테이블을 만드는 것은 아닙니다.

DTO를 먼저 작성해 입출력 계약을 맞춘 뒤 DB를 구현해도 됩니다. 위 순서는 작업을 시작하기 위한 기준이며 고정된 문법 규칙은 아닙니다.

### 2-2. 완성된 기능이 실행되는 순서

```text
React 요청
 → Security: 요청 경로의 인증·권한·CSRF 검사
 → Controller: 요청 수신·입력 검증
 → Service: 업무 규칙·대상별 권한·처리
 → Repository: DB 조회·저장
 → DB

DB 결과 → Service의 응답 DTO 변환 → Controller → JSON 응답 → React
```

Security에서 차단되면 Controller까지 도착하지 않습니다. 이 경우에는 ControllerAdvice 대신 공통 Security 오류 처리기가 응답합니다.

## 3. 각 구성 요소의 역할

예를 들어 상품 목록을 조회할 때의 책임입니다.

| 구성 요소 | 하는 일 | 상품 조회 예 |
| --- | --- | --- |
| DB 테이블 | 데이터를 실제 저장 | products의 가격·판매 상태·판매자 |
| Entity | 테이블을 Java 객체로 매핑 | Product 객체와 컬럼 매핑 |
| Repository | DB에 조회·저장 요청 | 공개·판매 중 상품 조회 |
| 요청 DTO | 클라이언트 입력 모양 정의 | 등록 제목·가격, 결제 동의 |
| 응답 DTO | 클라이언트에 공개할 결과 정의 | 상품명·가격·판매자 표시 이름 |
| Service | 업무 규칙과 처리 흐름 | 공개 조건 검사, DTO 변환 |
| Controller | HTTP 입출력 연결 | GET /api/products 수신·응답 |

**Entity와 DTO는 둘 중 하나를 고르는 관계가 아닙니다.**

- Entity는 DB와 Java 사이의 매핑에 사용합니다.
- DTO는 서버와 화면 사이의 입출력에 사용합니다.
- API 응답에 Entity를 그대로 반환하지 않습니다.

예를 들어 회원 Entity에 비밀번호 해시가 있어도 상품 응답 DTO에는 이를 포함하지 않습니다. DTO가 있다고 권한 검사가 자동으로 되는 것은 아니므로 Service에서도 검사합니다.

## 4. API 명세에서 작업 내용 찾기

코드를 작성하기 전에 [API 명세](api-spec.md)에서 다음을 확인합니다.

| 확인 항목 | 상품 조회 | 결제 |
| --- | --- | --- |
| 주소 | GET /api/products | POST /api/orders/{id}/payment |
| 로그인 | 비로그인 공개 | 주문 구매자 |
| 입력 | keyword, categoryId, page, size | agreedToTerms=true |
| 응답 | ProductSummary 목록 | OrderDetail |
| 주요 오류 | 잘못된 검색·페이지 입력 | 잔액 부족·주문 상태 충돌·환불 완료 |
| DB 변경 | 없음 | 지갑·결제·주문·이력 |

이 단계에서 프런트 담당자와 필드 이름·타입을 맞춥니다. 서버가 priceAmount를 반환하는데 화면이 price를 읽으면 표시되지 않습니다.

테이블·금액·상태·권한 정책을 구현 편의에 맞춰 임의로 바꾸지 않습니다. 명세가 모호하면 관련 담당자와 먼저 정합니다.

## 5. 현재 상품 조회 예제 따라 읽기

이 절은 **현재 실행 가능한 예제**입니다. 업무용 상품 Entity·Repository를 구현한 예제는 아니며 메모리 데이터를 사용합니다.

파일은 `src/main/java/com/example/lancerpick/` 아래에 있습니다.

### 5-1. 브라우저에서 응답 확인하기

기본 포트로 서버를 실행한 후 브라우저 주소창에서 확인합니다.

```text
http://localhost:8080/api/demo/products?page=0&size=4
```

JSON이 보이면 프런트 화면 없이 백엔드 조회를 확인한 것입니다. 주소창 요청은 GET이므로 POST 결제를 주소창으로 실행할 수는 없습니다.

`page=0`은 첫 페이지입니다. `size=4`는 한 번에 네 개를 요청한다는 뜻입니다.

### 5-2. Controller: 요청값을 받아 서비스로 전달합니다

파일: `demo/controller/DemoCatalogController.java`. 기존 클래스의 메서드를 읽는 예입니다.

```java
@GetMapping("/products")
public PageResponse<Product> products(
        @RequestParam(defaultValue = "") String keyword,
        @RequestParam(required = false) String categoryId,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "20") int size) {
    return catalog.products(keyword, categoryId, page, size);
}
```

클래스 위의 `@RequestMapping("/api/demo")`와 메서드의 `/products`가 합쳐져 `/api/demo/products`가 됩니다.

- `@RequestParam`: URL의 `?page=0` 같은 쿼리를 받습니다.
- `defaultValue`: 생략했을 때 사용할 기본값입니다.
- `catalog.products(...)`: 생성자로 주입받은 서비스 호출입니다.
- `PageResponse<Product>`: Product DTO의 목록과 페이지 정보를 반환합니다.

Controller 안에서 모든 DB 조회와 업무 처리를 작성하지 않습니다. 입력을 받고 Service로 넘기는 책임을 중심으로 유지합니다.

### 5-3. Service: 입력과 조회 조건을 처리합니다

파일: `demo/service/DemoCatalogService.java`

현재 구현은 다음 순서로 동작합니다.

1. 일반 문자열 규칙으로 검색어를 정규화합니다.
2. 검색어 길이와 page·size·categoryId 형식을 확인합니다.
3. 불변 예시 상품 목록에서 카테고리·제목 조건으로 찾습니다.
4. 해당 페이지의 상품을 잘라냅니다.
5. PageResponse를 반환합니다.

실제 상품 API는 DB 조회와 상품 설명 검색도 구현해야 합니다. 메모리 예제를 실제 DB의 성능·잠금 구현으로 간주하지 않습니다.

허용하지 않는 입력에서는 공통 예외를 사용합니다.

```java
throw new BusinessException(
        HttpStatus.BAD_REQUEST,
        "VALIDATION_FAILED",
        "검색 조건과 페이지 범위를 확인해 주세요.");
```

이 코드는 Service 안의 사용 예입니다. 파일 전체 예제가 아니므로 사용하는 클래스에서 해당 import가 필요합니다.

### 5-4. 응답 DTO: 화면에 필요한 필드를 정합니다

파일: `demo/dto/DemoCatalogResponse.java`. 해당 클래스 안에 선언된 record의 일부입니다.

```java
public record Category(String id, String name, int displayOrder) {}
public record Seller(String id, String displayName) {}
public record Product(
        String id,
        String title,
        long priceAmount,
        int durationDays,
        Category category,
        Seller seller) {}
```

record는 이 예제에서 값을 전달하는 DTO를 간결하게 정의하는 Java 문법입니다. Entity가 아니며 DB를 자동 생성하지 않습니다.

목록은 공통 `common/web/PageResponse.java`를 사용합니다. items·page·size·totalElements·totalPages 형태를 각 기능마다 다르게 만들지 않습니다.

### 5-5. 오류도 JSON으로 전달됩니다

Service의 BusinessException은 `common/exception/ApiExceptionHandler`가 처리합니다.

```json
{
  "code": "VALIDATION_FAILED",
  "message": "검색 조건과 페이지 범위를 확인해 주세요.",
  "fieldErrors": [],
  "traceId": "서버가-요청마다-만든-식별자"
}
```

code는 화면이 오류 종류를 구분하는 값입니다. traceId는 같은 요청의 로그와 응답을 연결하는 식별자입니다. SQL·스택 추적·비밀번호를 사용자 응답에 넣지 않습니다.

입력값 자체를 응답이나 로그에 그대로 남기지 않습니다. 현재 예기치 않은 오류 로그는 traceId와 예외 타입을 기록하며, 비밀값이 포함될 수 있는 예외 메시지·요청 본문은 기록하지 않습니다.

## 6. 예시 데이터를 실제 DB 조회로 바꾸기

이 절은 **후속 구현 순서**입니다. 아래 이름의 업무 클래스가 이미 작성돼 있다는 뜻은 아닙니다.

### 6-1. DB와 Flyway SQL을 확인합니다

현재 검토용 원본은 `docs/sql/V1__init.sql`이며 애플리케이션 Flyway 경로에 적용된 파일이 아닙니다. 실제 적용은 [물리 DB 설계](physical-db.md)의 절차를 따릅니다.

- 기존 테이블·데이터·Flyway 적용 이력을 먼저 확인합니다.
- 검토·검증 후 애플리케이션 마이그레이션에 반영합니다.
- 엔티티와 SQL을 같은 PR에서 맞춥니다.
- 공유 DB에 적용한 마이그레이션은 수정하지 않고 다음 버전을 작성합니다.
- 같은 DB에 수동 생성한 뒤 V1을 다시 적용하지 않습니다.

### 6-2. Entity를 매핑합니다

`product/entity/`에 실제 상품 테이블을 매핑합니다. 처음에는 컬럼 하나씩 물리 설계와 비교합니다.

- PK·FK·NULL 허용·자료형·컬럼 이름이 맞는가?
- DB의 price와 API의 priceAmount처럼 이름이 다른 부분은 어떻게 변환할 것인가?
- 상품 version과 판매 상태·숨김 상태를 어떻게 매핑할 것인가?
- 판매자·카테고리 관계는 기존 Entity를 참조하는가?

Hibernate `validate`로 실제 스키마와 비교합니다. `ddl-auto=update`로 차이를 임의로 덮어 해결하지 않습니다.

### 6-3. Repository에 조회를 추가합니다

`product/repository/`에서 Entity를 조회합니다. 공개 목록이라면 **판매 중이고 관리자에게 숨겨지지 않은 상품**만 조회해야 합니다.

검색 조건·정렬·페이지도 Repository 조회에 반영합니다. 모든 데이터를 가져온 뒤 프런트에서 숨김 상품을 제외하는 방식은 피합니다. 조회 방법은 JPA 또는 필요한 경우 QueryDSL을 사용하되 실제 SQL과 결과를 확인합니다.

### 6-4. Service에서 Entity를 DTO로 변환합니다

`product/service/`에서 Repository를 호출하고 응답 DTO로 변환합니다. 읽기 작업은 필요한 범위에서 `@Transactional(readOnly = true)`를 적용합니다.

OSIV가 꺼져 있으므로 Entity의 지연 로딩 관계를 Controller나 화면 직렬화 중에 접근하도록 넘기지 않습니다. 필요한 판매자·카테고리를 조회하고 서비스 트랜잭션 안에서 DTO로 변환합니다. 반복 조회가 늘면 실제 SQL로 N+1 여부를 확인합니다.

### 6-5. Controller와 프런트를 연결합니다

`product/controller/`에서 명세의 `/api/products`를 제공합니다. 서버 검증 후 프런트 `features/products/api.ts`의 경로를 실제 API로 바꿉니다.

상품 응답이 준비되지 않았는데 예시 표시만 없애거나 예시 데이터를 실제 판매 데이터처럼 표현하지 않습니다. 프런트 타입·조회 키와 API 계약도 함께 확인합니다.

## 7. 결제 같은 변경 기능의 작성 방법

아래는 **설계에 따른 처리 순서 설명**입니다. 현재 결제 API·트랜잭션·잠금 기능이 구현된 것은 아닙니다. 실제 정은찬·황준범 담당 서비스 간 호출 경계는 [작업표의 연결 계약](work-plan.md#5-개발-착수-순서와-연결-계약)에 결정과 근거를 기록한 뒤 구현합니다.

### 7-1. Controller의 역할

`POST /api/orders/{id}/payment`에서 주문 ID와 동의 여부를 받고 업무 시작 서비스를 호출합니다. 로그인 회원 ID는 요청 본문이 아닌 인증 정보에서 얻습니다.

입력 필수값 검증과 별개로 Service에서 실제 구매자·주문 상태를 다시 확인합니다. Controller에서 금액을 계산하거나 잔액을 임의 변경하지 않습니다.

### 7-2. Service의 역할

```text
같은 트랜잭션 시작
 → 로그인 회원과 대상 주문 확인
 → 주문 잠금 후 최신 상태·구매자·기존 결제·취소 조건 검사
 → 최초 결제가 가능한 경우 구매자 지갑 잠금
 → 잠금 후 잔액 검사
 → 잔액 차감·적립금 내역·결제 기록 저장
 → 주문 진행 상태·납기·확인 시각·이력 저장
 → 모두 성공하면 커밋
```

이 순서는 핵심 흐름이며 기존 결제 재호출·환불 완료·오류 응답의 정확한 분기는 API 명세 7절을 따릅니다. 모든 재호출에서 최초 차감을 다시 수행하지 않습니다.

| 장치 | 해결하려는 문제 |
| --- | --- |
| `@Transactional` | 중간 실패 시 함께 롤백 |
| 주문 잠금·상태 검사 | 같은 주문 중복 결제, 취소·확정 충돌 |
| 지갑 잠금 후 잔액 검사 | 서로 다른 주문의 동시 잔액 초과 사용 |
| 고유 제약·기존 기록 확인 | 중복 결제·내역 생성 방지 |

`@Transactional` 하나만 붙여서 동시성까지 해결됐다고 판단하지 않습니다. 필요한 조회에 잠금을 적용하고 실제 MySQL에서 검증해야 합니다.

### 7-3. 다른 담당자의 서비스와 연결합니다

업무 시작 서비스가 전체 트랜잭션을 관리하고 내부 금액 처리도 같은 트랜잭션에 참여합니다. 주문 → 구매자 지갑 잠금 순서를 통일합니다.

실패를 catch해서 로그만 남기고 정상 반환하면 바깥 처리가 성공한 것으로 이어질 수 있습니다. 실패를 전파해 전체 롤백을 보장합니다. Spring 기본 롤백은 RuntimeException·Error에 적용되므로 checked exception을 사용할 때는 롤백 정책도 명시적으로 확인합니다.

네트워크 호출·메일·외부 결제사가 DB와 같은 트랜잭션으로 롤백되는 것은 아닙니다. 현재 MVP의 내부 적립금 결제와 향후 외부 결제를 구분합니다.

## 8. 공통 코드와 Security 사용법

| 공통 코드 | 기능에서 사용할 방법 |
| --- | --- |
| `PageResponse<T>` | 목록 응답 구성 |
| `BusinessException` | 명세에 있는 상태·code로 업무 오류 발생 |
| `ApiExceptionHandler` | 공통 JSON 오류 변환. 기능마다 같은 Advice를 복사하지 않음 |
| `TraceIdFilter` | 요청 추적 ID 생성·응답 헤더·로그 문맥 제공 |
| `TimeConfig`의 Clock | UTC 시각 주입. 사건 시각·정밀도는 물리 설계에 맞춤 |
| `TextNormalizer` | 일반 문자열 정규화. 비밀번호에는 사용하지 않음 |
| `SecurityConfig` | 경로별 기본 접근 정책·CSRF 보호 |
| `CsrfController` | 프런트의 CSRF 토큰 획득 |
| `SecurityErrorWriter` | Security 필터의 401·403도 공통 JSON으로 반환 |

### 로그인 여부와 대상별 권한은 별개입니다

로그인했다는 사실만으로 다른 사람의 주문을 결제할 수는 없습니다.

```text
인증 정보의 회원 ID ↔ DB 주문의 구매자 ID 비교
```

상품 소유자·주문 참여자·후기 작성 자격은 해당 기능 Service에서 검사합니다. 프런트가 버튼을 숨기는 것은 서버 권한 검사를 대신하지 않습니다.

공개 메인에 필요하다고 모든 `/api/**`를 permitAll로 열거나 CSRF를 끄지 않습니다. 필요한 공개 경로만 HTTP 메서드와 함께 확인합니다.

### Security의 현재 구현 범위

- 기본 세션 CSRF 보호, 공개 조회 경로, 관리자 경로 정책, 401·403 JSON 응답의 틀은 구현됐습니다.
- 실제 회원 인증·세션 발급·보안 컨텍스트 저장·로그아웃·비밀번호 인코더는 후속 작업입니다.
- formLogin·httpBasic·기본 logout은 꺼져 있습니다. 자동 생성 비밀번호를 실제 로그인 구현으로 사용하지 않습니다.
- 인증 구현 후 로그인·로그아웃 시 세션 보호와 CSRF 갱신을 처리하고 프런트 캐시 정리도 연결해야 합니다.
- 가입의 회원·지갑·최초 적립금 원자 생성은 별도 구현해야 합니다.
- 실제 배포 전 HTTPS·Secure·HttpOnly·SameSite·세션 만료 정책도 적용·검증해야 합니다.

## 9. 기능 완료 전 확인하기

### 9-1. 현재 공통 예제 검사

```powershell
.\gradlew.bat test --tests "com.example.lancerpick.common.*"
```

기본·demo 컨텍스트의 공개 조회·입력 오류·401·403·CSRF 토큰 왕복·204·문자열 정규화를 검사합니다. 기본 프로필 검사는 테스트 설정으로 DB를 제외하고 개인 접속 파일을 읽지 않습니다. 실제 기본 실행의 DB 연결 성공을 대신하지 않습니다.

### 9-2. 실제 DB 기능 검사

전체 빌드·통합 테스트는 [테스트 DB 안내](test-database.md)에 따라 격리된 TEST_DB_* 환경을 준비한 뒤 실행합니다.

```powershell
.\gradlew.bat clean build
```

개발 DB를 테스트 DB로 사용하지 않습니다. DB 제약·잠금·동시성은 실제 MySQL로 확인합니다. 조건식·단위 테스트만으로 DB 검증을 완료 처리하지 않습니다.

### 9-3. 결과를 기록합니다

- 정상 입력, 잘못된 입력, 없는 대상, 권한 오류를 확인했나요?
- 변경 기능에서 중복·동시 요청·중간 실패를 확인했나요?
- Entity·Flyway SQL·DTO·화면의 계약이 같은가요?
- 프런트 화면에서 실제 요청과 응답을 확인했나요?
- PR에 실행한 검사·결과·남은 제한을 구분했나요?

예를 들어 결제는 잔액 7만 원, 서로 다른 주문 각 5만 원을 동시에 결제해 한 건만 성공하고 잔액 2만 원이 되는지 확인합니다. 순차 요청을 여러 번 보낸 검증과 동시에 실행한 검증은 다릅니다.

Git의 브랜치·커밋·PR·리뷰·병합 과정은 [팀 Git 협업 규칙](git-workflow.md)을 따릅니다.

현재 인증 정보에서 업무 회원 ID를 얻는 구체적인 principal·공통 접근 방식은 아직 미확정입니다. 클라이언트의 buyerId를 신뢰하거나 Security를 해제해 대신하지 않습니다. 인증 담당자와 계약을 정하고 기능 테스트의 인증 구성도 함께 기록합니다.
