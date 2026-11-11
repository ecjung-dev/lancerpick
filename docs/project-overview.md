# 프로젝트 개요

[문서 목차와 관리 원칙](README.md) · [진행 현황](project-progress.md) · [담당](team-roles.md)

## 서비스와 목적

LancerPick(랜서픽)은 고정 가격의 프리랜서 서비스를 등록하고 주문·계약·테스트 적립금 결제·납품·수정·구매 확정·판매 수익·후기까지 관리하는 4인 팀 프로젝트다. 학원 취업·포트폴리오와 상태 전환·권한·금액 일관성의 학습을 목적으로 한다. 한 계정이 구매와 판매를 겸하며, 카테고리는 탐색용이다. 고객 인터뷰로 검증된 사업 가설이나 상용 금융 서비스로 표현하지 않는다.

공식 개발 기간은 2026-10-28~11-18, 12-05부터 기능 추가 중단, 발표는 12-11이다. 2026-11-11 일정 변경으로 검증은 11-11, 웹 디자인은 11-12, 발표 준비는 11-17(화)까지 마무리한다. 기존 합의된 목표이며 달성 기록과 구분한다. 상세 일정은 [진행 현황](project-progress.md)에서 관리한다.

## 현재 코드에서 확인한 기술 스택

2026-11-09 작업 트리 main a7e889b의 빌드·설정을 정적으로 확인했다. 설치·서비스 기동·테스트는 이번 정리에서 다시 실행하지 않았다.

| 영역 | 현재 선언·구조 | 근거 |
| --- | --- | --- |
| 백엔드 | Java 25, Spring Boot 4.1.2, MVC·Security·Validation·JPA·QueryDSL 7.7 | [build.gradle](../LancerPick/build.gradle) |
| 데이터 | MySQL 8.4 설계 기준, Flyway V1~V11, Hibernate validate·UTC | [설정](../LancerPick/src/main/resources/application.properties), [마이그레이션](../LancerPick/src/main/resources/db/migration) |
| 프런트 | React 19.3.0, TypeScript ~5.9.3, Vite 8.3.5, React Router 7.18.5, TanStack Query 5.105.0, CSS Modules | [package.json](../LancerPickFrontend/package.json) |
| 소통·외부 연동 | WebSocket/STOMP·@stomp/stompjs 7.2.0, MySQL 아웃박스, Spring Mail, PDFBox 3.0.8, Toss 테스트 SDK 2.10.0, Spring Batch JDBC | 빌드·패키지 선언 및 도메인 계약 |
| 개발·협업 | Gradle Wrapper, GitHub Actions, 격리 MySQL 테스트, Mailpit 로컬 확인 기록 | [구조·개발 환경](architecture.md), [협업](team-roles.md) |
| 계획·보류 | Docker Compose·Nginx·HTTPS 배포 구상, Redis·C5 계측은 이번 발표 제외, 객체 저장소는 과거 선택안 | [남은 작업](project-progress.md#remaining) |

테스트 적립금은 실제 현금 가치·출금이 없다. MVP와 후속 CONTRACT_V3는 수익 지급·자동 확정 정책이 다르므로 [정책 구분](features.md#policy-versions)을 따른다. 신규 생성 설정과 활성화 기록은 [진행 현황](project-progress.md#current-status)에서 확인한다.

## 범위와 한계

상용 자금·출금·법적 효력이 보장된 전자서명·외부 파일 원본 보존은 제공 범위로 표현하지 않는다. 정책 버전별 차이는 [기능 명세](features.md#policy-versions), 일정과 남은 일은 [진행 현황](project-progress.md), 초기 기획/변경 근거는 [변경 이력](change-history.md#decisions)을 따른다.
