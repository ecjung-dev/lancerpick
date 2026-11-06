# 메일 발송 인프라 (C3)

담당: 정은찬(작업표 C3). main 반영: #90(2026-11-05). K1 담당자 전달 사항은 [작업표](work-plan.md#c3-반영-후-김창재-전달--2026-11-05). 계정 메일(현재 비밀번호 재설정)을 실제 SMTP로 보낸다. 메일 내용·토큰·요청 제한은 김창재 K1 범위이며 [로그인 문서](auth-login.md#메일-발송-경계-c3-연결-계약)의 연결 계약을 따른다. DB·Flyway 변경은 없다.

## 구성

| 파일 | 역할 |
| --- | --- |
| `auth/mail/SmtpAccountMailSender` | `AccountMailSender` 구현. 발송 스레드 1개 + 고정 크기 큐. `enqueue`는 큐에 넣고 바로 반환하고, SMTP 통신은 발송 스레드에서만 한다 |
| `auth/mail/MailInfrastructureConfiguration` | `lancerpick.mail.enabled=true`이고 demo 프로필이 아닐 때만 위 발송기를 Bean으로 등록 |
| `build.gradle` | `spring-boot-starter-mail` 추가 |
| `application.properties` | `lancerpick.mail.*`, `spring.mail.*`(환경변수로 덮어씀), SMTP 연결·읽기·쓰기 시간 제한 5초 |
| `application-test.properties` | `lancerpick.mail.enabled=false` 고정(테스트는 가짜 발송기 사용, 개인 `MAIL_ENABLED`에 영향받지 않음) |

발송기는 `auth/mail`에 둔다. 연결 계약(`AccountMailSender`)이 이 패키지에 있고 현재 사용처가 계정 메일뿐이라, 범용 `common/mail`을 미리 만들지 않는다.

## 동작

1. 발급 트랜잭션이 커밋된 뒤 `AccountMailDispatcher`가 `enqueue`를 부른다(요청 스레드).
2. 큐에 넣으면 true → `delivery_status`는 `PENDING`. 큐가 가득 찼거나 서버 종료 중이면 false → Dispatcher가 `FAILED`(`MAIL_QUEUE_FULL`).
3. 발송 스레드(`account-mail-sender`)가 `SimpleMailMessage`(UTF-8 평문)로 보낸 뒤 `AccountMailDeliveryRecorder`에 기록한다.

| 결과 | 기록 |
| --- | --- |
| 성공 | `SENT` |
| SMTP 인증 실패(`MailAuthenticationException`) | `FAILED` `MAIL_AUTH_FAILED` |
| 메시지 구성 오류(`MailParseException`) | `FAILED` `MAIL_INVALID_MESSAGE` |
| 연결·전송 실패(`MailSendException`) | `FAILED` `MAIL_SEND_FAILED` |
| 그 밖의 예외 | `FAILED` `MAIL_FAILED` |

- 기록이 실패해도 발송 스레드는 멈추지 않는다(행은 `PENDING`으로 남음).
- 로그에는 challengeId와 오류 코드·예외 클래스 이름만 남긴다. 수신자·본문(토큰)·예외 메시지는 남기지 않는다.
- 자동 재발송은 하지 않는다. 같은 토큰을 다시 보내지 않고, 사용자는 요청 제한(60초 간격·1시간 5회) 안에서 다시 요청한다.

## 설정

| 속성 | 환경변수 | 기본값 |
| --- | --- | --- |
| `lancerpick.mail.enabled` | `MAIL_ENABLED` | `false` |
| `lancerpick.mail.from` | `MAIL_FROM` | `LancerPick <no-reply@lancerpick.local>` |
| `lancerpick.mail.queue-capacity` | `MAIL_QUEUE_CAPACITY` | `100` |
| `spring.mail.host` / `port` | `MAIL_HOST` / `MAIL_PORT` | `localhost` / `1025`(Mailpit) |
| `spring.mail.username` / `password` | `MAIL_USERNAME` / `MAIL_PASSWORD` | 비어 있음 |

- 꺼져 있으면(기본) 메일 서버 없이 앱·테스트·CI가 켜지고, 계정 메일은 `FAILED`(`MAIL_NOT_CONFIGURED`)로 남는다.
- 켰는데 `JavaMailSender`를 만들 수 없거나 `from`이 비어 있으면 서버 시작이 실패한다(조용히 발송하지 않는 상태를 막는다).
- 실제 SMTP 계정·비밀번호는 `local-application.properties`나 셸 환경변수에만 둔다. 저장소·문서·로그에 남기지 않는다.
- 기본 설정은 인증·TLS 없는 Mailpit 기준이다. 외부 SMTP를 쓰려면 `spring.mail.properties.mail.smtp.auth=true`, `spring.mail.properties.mail.smtp.starttls.enable=true`(필요 시 `starttls.required=true`)를 개인 설정에 더한다. 이 조합은 검증하지 않았다.

## 로컬 Mailpit 확인

Docker Desktop을 켠 뒤:

```powershell
docker run -d --name lancerpick-mailpit -p 1025:1025 -p 8025:8025 axllent/mailpit:v1.33.0
```

Docker가 없으면 Mailpit 공식 Windows 실행 파일(GitHub 릴리스 `mailpit-windows-amd64.zip`, SHA-256 확인)을 `mailpit.exe --smtp 127.0.0.1:1025 --listen 127.0.0.1:8025`로 실행해도 같다(2026-11-06 김창재 확인). 2026-11-06부터 화면 회원가입도 이메일 인증이 필수라, 로컬에서 새 계정을 만들려면 이 설정이 필요하다([이메일 인증 가입](auth-login.md#이메일-인증-가입--2026-11-06-featureemail-verification)).

`LancerPick/local-application.properties`에 `MAIL_ENABLED=true`를 넣고 서버를 켠다(호스트·포트 기본값이 Mailpit). 화면 `/password-reset/request`에서 가입한 이메일로 요청하면 http://localhost:8025 받은편지함에 메일이 오고, 링크로 새 비밀번호를 설정할 수 있다. 끝나면 `docker rm -f lancerpick-mailpit`.

## 한계

- 메모리 큐라 서버가 종료되면 남은 메일은 버려지고 `PENDING`으로 남는다. 여러 서버로 늘리면 각 서버가 자기 큐만 처리한다(C4·Batch에서 다시 정한다).
- `PENDING`으로 오래 남은 행을 정리·재처리하는 작업은 없다.
- HTML 메일·첨부·발송량 제한(전체·IP)은 없다.
- 서버 종료 때 진행 중인 발송은 중단 신호를 받는다. 이미 SMTP 서버가 받은 뒤라면 실제로 전달됐어도 `FAILED`로 기록될 수 있다(사용자에게는 메일이 도착).
- 비밀번호 변경·재설정 완료 안내 메일, 이메일 인증 가입·아이디 안내 메일은 K1(김창재)에서 이 발송기를 그대로 사용해 추가한다.

## 검증

- `SmtpAccountMailSenderTests` 8건(DB·SMTP 없음): 발송 스레드에서 보낸 뒤 SENT 기록·보낸 사람/수신자/제목/본문, `enqueue`가 SMTP를 기다리지 않음, 큐 가득 참 → false, 인증·전송·예상 밖 예외의 안전한 코드와 이후 발송 계속, 기록 실패 후 발송 계속, 종료 후 false, 잘못된 설정 거부.
- `MailInfrastructureConfigurationTests` 4건: 기본 꺼짐, 켜면 발송기 1개, 켰는데 메일 서버 설정 없음 → 시작 실패, demo 프로필에서는 등록 안 함.
- 기존 `AccountMailDispatcherTests` 2건, `PasswordResetIntegrationTests`(가짜 발송기)는 그대로 사용.
- 백엔드 `clean build`(로컬 MySQL 8.4 `lancerpick_test`): 771건 통과(2026-11-05, 병합 전 작업 브랜치. 병합 후 main에서 재실행하지 않음).
- 실제 서버 + Mailpit(2026-11-05, 개발 DB, `MAIL_ENABLED=true`, API 호출로 확인): 가입 → 재설정 요청 202 → Mailpit에 메일 1통 도착(보낸 사람 `LancerPick <no-reply@lancerpick.local>`, 한글 제목·본문, `text/plain; charset=UTF-8`, 링크 `http://localhost:5173/password-reset#token=…` 43자) → 그 토큰으로 재설정 204 → 옛 비밀번호 로그인 401, 새 비밀번호 로그인 200 → 같은 토큰 재사용 400. 서버 로그에 메일 경고·이메일·토큰 없음.
- 확인하지 않은 것: `account_challenges.delivery_status`가 `SENT`인지 DB에서 직접 조회하지 않았다(로컬에 MySQL 클라이언트 없음, 기록 실패 로그가 없는 것으로만 확인). 브라우저 화면으로 재설정 링크를 연 확인은 하지 않았다. 60초 안에 같은 이메일로 다시 요청하면 메일이 오지 않는 것(요청 제한)은 의도된 동작임을 확인했다.
- 후속(2026-11-06, 김창재): 위 확인하지 않은 것 중 브라우저 화면 확인을 채웠다. Mailpit 실행 파일 + 내장 브라우저로 요청 → 수신 → 링크 → 새 비밀번호 → 세션 폐기·재로그인·같은 링크 재사용 거부를 확인했다. DB의 `SENT` 직접 조회는 여전히 하지 않았다. [로그인 문서 기록](auth-login.md#브라우저--mailpit-확인--2026-11-06).
