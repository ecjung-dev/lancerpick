# 토스페이먼츠 테스트 키 설정 안내

작성일: 2026-11-02. 팀원의 로컬 개발 환경을 위한 설정 안내입니다.

**실제 테스트 키는 황준범이 필요한 팀원에게 별도로 전달합니다.** 이 문서·예시 파일·PR·Git 저장소에는 실제 키 값을 기록하지 않습니다. 아래 `CHANGE_ME`는 실제 키가 아닌 자리 표시자입니다.

## 1. 이번 설정의 범위

랜서픽에서는 실제 돈이 이동하지 않는 토스페이먼츠 테스트 연동을 준비합니다. 현재 확보한 키 종류는 다음과 같습니다.

| 키 | 사용할 곳 | 역할 |
| --- | --- | --- |
| `test_gck`로 시작하는 테스트 클라이언트 키 | React | 주문서형·결제창형 SDK 초기화 |
| `test_gsk`로 시작하는 테스트 시크릿 키 | Spring Boot 서버 | 결제 승인·조회·취소 API 인증 |

같은 상점에서 발급한 한 세트를 사용합니다. 라이브 키는 이번 테스트에 사용하지 않습니다.

**키 설정은 외부 결제 기능의 구현 완료를 뜻하지 않습니다.** 충전 주문·금액 검증·승인·중복 지급 방지·결과 재확인 등은 별도 구현해야 합니다. 현재 확장 계획은 외부 테스트 결제로 적립금을 충전하고 기존 주문의 적립금 결제를 유지하는 방식이며, 이 문서는 MVP 범위를 변경하지 않습니다.

2026-11-02 후속: [충전 저장 구조](wallet-topup-schema.md), [신청·조회](wallet-topup-api.md), [승인·지급·재확인](wallet-topup-approval.md), [React SDK·결과 화면](wallet-topup-frontend.md)을 준비했습니다. 외부 호출은 기본 비활성화이며 개인 설정의 TOSS_TOPUP_ENABLED=true와 테스트 시크릿으로 활성화합니다. 프런트는 기존 keys.env를 읽도록 연결했습니다. 실제 테스트키 SDK UI 로딩은 확인했고, 실제 결제·인증 포함 전체 검증·자동 복구는 후속입니다.

## 2. 백엔드 설정

파일 위치는 백엔드 프로젝트 루트의 `LancerPick/local-application.properties`입니다. `src/main/resources` 안에 만드는 파일이 아닙니다.

### 이미 개인 DB 설정 파일이 있는 경우

기존 DB 설정은 유지하고 아래 항목만 추가합니다. `CHANGE_ME`를 별도로 전달받은 테스트 **시크릿 키**로 바꿉니다.

```properties
TOSS_SECRET_KEY=CHANGE_ME
```

### 개인 설정 파일이 없는 경우

처음 한 번만 `LancerPick/local-application.properties.example`을 `local-application.properties`로 복사하고 본인의 DB 정보와 전달받은 테스트 시크릿 키를 입력합니다. 기존 개인 파일을 덮어쓰지 않습니다.

공유 파일인 `LancerPick/src/main/resources/application.properties`에는 실제 키 대신 아래 참조만 보관합니다.

```properties
toss.secret-key=${TOSS_SECRET_KEY}
```

일반적인 IntelliJ 기본 실행은 `application-default.properties`를 통해 개인 설정 파일을 읽습니다. Working directory는 `LancerPick` 폴더로 지정합니다. 별도 프로필 실행에서는 해당 개인 파일이 자동으로 import되지 않을 수 있으므로 실행 방식에 맞춰 서버 환경 변수로 주입해야 합니다.

현재 설정은 일반 로컬 실행을 위한 준비입니다. `demo` 프로필이나 CI·테스트에 실제 토스 키를 추가할 필요는 없으며, 외부 연동 테스트를 도입할 때는 별도로 실행 범위와 설정을 정합니다.

## 3. 프런트엔드 설정

현재 개인 파일은 `LancerPickFrontend/keys.env`입니다. 기존 파일을 그대로 사용하고 아래 항목을 설정합니다. Vite는 keys.env를 기본 이름으로 읽지 않으므로 저장소의 vite.config.ts가 클라이언트 키 항목만 명시적으로 연결합니다. 표준 .env.local 방식도 대체 설정으로 지원하지만 두 파일을 중복 작성할 필요는 없습니다.

```dotenv
VITE_TOSS_CLIENT_KEY=CHANGE_ME
```

`CHANGE_ME`를 별도로 전달받은 테스트 **클라이언트 키**로 바꿉니다. 파일이 없다면 `LancerPickFrontend/.env.example`을 `keys.env`로 복사합니다. 기존 개인 파일은 덮어쓰지 않습니다. 셸 변수 → keys.env → 표준 .env 계열 순서로 적용합니다.

추후 SDK 연동 코드는 다음으로 클라이언트 키를 읽습니다.

```ts
import.meta.env.VITE_TOSS_CLIENT_KEY
```

**`VITE_`로 시작하는 값은 브라우저 코드에 포함됩니다.** 시크릿 키를 `.env.local`의 `VITE_` 변수나 React 소스에 넣지 않습니다. 파일을 Git에서 제외해도 브라우저에 포함되는 값까지 숨겨지는 것은 아닙니다.

## 4. 설정 후 확인

1. 클라이언트에는 `test_gck`, 서버에는 같은 세트의 `test_gsk`가 들어갔는지 직접 확인합니다. 키를 로그나 화면에 출력하지 않습니다.
2. 백엔드와 Vite를 재시작합니다. Vite는 실행 시 환경 변수 파일을 읽습니다.
3. 저장소 루트에서 개인 파일이 Git 제외 대상인지 확인합니다.

```powershell
git check-ignore -v LancerPick/local-application.properties LancerPickFrontend/keys.env
git status --short
```

공유하는 파일은 `application.properties`의 변수 참조, `local-application.properties.example`·`.env.example`의 자리 표시자, 이 안내 문서입니다. 실제 개인 설정 파일이 추적되고 있다면 커밋 전에 중단하고 확인합니다. `.gitignore`는 이미 추적된 파일의 이력을 자동으로 제거하지 않습니다.

환경 변수를 읽도록 설정했다고 결제창이 자동으로 생기지는 않습니다. React SDK 초기화와 서버 승인 API 연결은 후속 구현 대상입니다.

## 5. API 키 접근 정책과 후속 설정

API 키 접근 정책은 프로젝트 폴더·`localhost:5173`을 등록하는 메뉴가 아닙니다. 시크릿 키로 토스 API를 호출하는 서버의 공인 IP 또는 국가를 제한하는 설정입니다.

정책을 연결했다면 실제 호출 환경이 허용되는지 확인합니다. IP를 사용할 경우 `127.0.0.1`이나 `192.168.x.x`가 아닌 인터넷으로 나가는 공인 IP를 사용합니다. 집·학원 이동으로 IP가 바뀌면 정책도 확인해야 합니다. 클라이언트 SDK 결제창과 웹훅 수신은 이 정책의 대상이 아닙니다.

결제 구현 시 `successUrl`·`failUrl`, 서버의 주문·금액 검증·승인, 필요 웹훅 수신·등록을 연결합니다. 키 확보와 별개로 구현·검증할 내용입니다.

## 6. 관련 문서

- [백엔드 공통 가이드](backend-common-guide.md)
- [프런트 공통 가이드](frontend-common-guide.md)
- [확장 계획의 결제사 테스트 충전](expansion-plan.md#결제사-테스트-충전)
- [토스 공식 API 키 안내](https://docs.tosspayments.com/reference/using-api/api-keys)
- [토스 공식 결제 연동 가이드](https://docs.tosspayments.com/guides/v2/payment-widget/integration)
- [토스 공식 API 키 접근 정책](https://docs.tosspayments.com/reference/using-api/api-key-access-policy)
