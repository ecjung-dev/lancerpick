# 랜서픽 문서 안내

정리일: 2026-11-05. 원격 main과 동일한 `1e10d78` 기준으로 전체 문서의 현황·담당·검증 기록과 첨부 시트를 대조했다. 개인 기능 35/35 완료와 공통 0/8 완료를 [팀 진행도](team-progress.md)에 정리했다. 이번 문서 편집은 테스트·원격 CI 재실행을 포함하지 않는다. 후속(같은 날): main `1941ad1`까지의 기존 주문 알림(#79, #80)과 담당별 [고도화 작업표](work-plan.md#고도화-작업표--2026-11-05)를 반영했다. 이어서 main `a7057d6`까지의 V2 생성 경로(#82)·비밀번호 변경(#84)·V2 수락/계약 성립(#85) 반영 상태를 현황·작업표에 맞췄다.

## 먼저 읽을 문서

1. [현재 구현·검증 현황](current-status.md): 구현된 기능, 남은 개발, 검증 한계.
2. [작업표](work-plan.md): 확정 담당, [고도화 작업표](work-plan.md#고도화-작업표--2026-11-05)(담당별 항목·선행·상태), 현재 작업, 과거 개발 기록.
3. [루트 README](../README.md): 기존 저장소를 복제한 뒤 실행하는 절차.
4. 아래 분야별 문서: 해당 기능의 정책·API·DB·구현 근거.

시트와 비교할 때는 [팀 진행도](team-progress.md)에서 개인 35개·공통 8개와 별도 고도화를 구분한다.

## 문서별 기준

| 판단할 내용 | 기준 문서 |
| --- | --- |
| 기능 범위·업무 정책 | MVP 명세 |
| 요청·응답·권한·중복 처리 | API 명세 |
| 데이터 관계·컬럼 | 실행 Flyway V1~V6 누적 29개·고도화 DB 설계. MVP ERD·물리 DB 이미지의 V1 기본 16개와 V2 포함 17개는 과거 범위 |
| 개발 방식·협업 | 공통 개발 규칙·공통 가이드·Git 협업 |
| 구현 현황·미완료 | 현재 현황과 기능별 최신 기록 |
| 담당 | 작업표. 고도화 항목별 담당·선행·상태는 [고도화 작업표](work-plan.md#고도화-작업표--2026-11-05), 배정 근거는 최신 4인 설계, 기반·활성화 조건은 팀원 시작 안내와 적용 순서. 확장 계획과 2·3인 설계는 해당 단계의 이력·선택 범위 |
| 실행·검증 결과 | 날짜·소스·환경이 명시된 기능/통합 기록과 해당 PR/CI |

문서에 적힌 계약·목표·설정 예시는 구현 완료 증거가 아니다. 과거 기록의 ‘미구현’, ‘후속’, ‘병합 전’은 당시 상태다. 과거 테스트 수를 합산하거나 최신 커밋의 실행 결과로 표현하지 않는다. 불일치가 남으면 현재 현황과 해당 기능의 후속 기록을 함께 확인하고, 정책을 임의 변경하지 않는다.

## 전체 문서 색인

- [박소현 담당 최종 검증과 PR 단위](sohyun-final-verification.md)

- [사용자 수동 거래 검증 현황](manual-trade-verification.md)
- [주문 흐름 예외·동시 상황 확인](order-flow-edge-check.md)
- [개발용 수동 거래 데이터 SQL](sql/seed-manual-trade-demo.sql)

- [작업표](work-plan.md)
- [팀 진행도·개인/공통 시트 대조](team-progress.md)
- [현재 현황](current-status.md)
- [백엔드 README](../LancerPick/README.md)
- [프런트 README](../LancerPickFrontend/README.md)
- [ERD 이미지 안내](images/README.md)

### 기준·설계

- [고도화 공통 기반 팀원 시작 안내](advanced-team-start.md): main V1~V6 제공 범위·담당별 착수 위치·후속 기능 활성화 순서.
- [고도화 전체 업무 스키마 V5/V6](advanced-schema-foundation.md): 목표 신규 12개 테이블 준비, 누적 29개·기존 엔티티 호환·후속 분할 결제 전환 경계.
- [설계 리뷰 후 정책 경계 보완](advanced-policy-boundary.md): 정책 DTO·허용 행동·기존 명령 차단·시각 검증과 남은 활성화 조건.
- [V5 이후 적용·결제 회차 전환 기준](advanced-rollout-order.md): DDL·회차별 조회·부분 반환의 공동 전환 시점과 담당 연결.
- [주문 알림·아웃박스](notifications.md): 기존 주문 상태 변경 알림, 트랜잭션 아웃박스·재시도·실패 격리, 목록·읽음 API/화면(#79, #80).

- [4인 고도화 설계안 v0.1 — 현재 검토안](advanced-design-four-person.md): 회원·상품 보완, 채팅·알림·전자계약서, 정은찬·황준범의 Redis·Batch 등 공통 기반 담당. 이전 2인·3인 설계안은 이력으로 보존한다.
- [고도화 DB 설계안 v0.3](advanced-db-design.md): 신규 업무 테이블 12개, 현재 계약·결제 회차·타이머·금액 분배 제약.
- [고도화 상태·제약 명세](advanced-db-transitions.md): 관리자 타이머 해제·만료 우선·서비스 검증 책임·격리 DB 검사 기록.
- [고도화 금융 기반 1차 구현](advanced-finance-foundation.md): V3·엔티티 확장·고정 비율 금액 계산, 기존 거래 유지와 후속 연결 범위.
- [전자계약 버전·서명 기반](order-contract-foundation.md): V4 계약 테이블·내부 명령·문서 해시·양측 서명. PDF/화면/분할 결제 활성화는 후속.
- [고도화 DDL 후보](sql/advanced-db-candidate.sql): V1/V2 후속 검토 SQL. 실행 Flyway 자동 적용 대상이 아니며 서비스 호환 변경 후 단계별 적용.

- [랜서픽 MVP 기획 명세서 v1.0](mvp-spec.md)
- [랜서픽 MVP 화면 흐름 v1.0 — 검토 반영본](screen-flow.md)
- [랜서픽 MVP API 명세서](api-spec.md)
- [랜서픽 MVP ERD 설계 v1.0](erd.md)
- [랜서픽 MVP 물리 DB 설계](physical-db.md)
- [랜서픽 공통 개발 규칙 v1.0](development-rules.md)
- [랜서픽 Git 협업 및 Pull Request 운영 규칙](git-workflow.md)
- [랜서픽 확장 계획서 v1.0](expansion-plan.md)
- [2인 고도화 설계안 v0.1](advanced-design-two-person.md): 황준범 채팅·WebSocket 중심, 정은찬 알림 담당 제안, 기술 조합 5개와 추천 순위. 검토안이며 기존 MVP 범위를 변경하지 않는다.
- [3인 고도화 설계안 v0.1](advanced-design-three-person.md): 김창재 회원·인증 고도화 추가, 이메일 인증 가입·비밀번호 재설정/변경·세션 폐기 설계. 2인 설계안은 보존하며 아이디 찾기의 현재 데이터 한계도 구분한다.

### 실행·개발

- [랜서픽 공통 개발 환경 구축 가이드](setup-guide.md)
- [백엔드 공통 개발 가이드](backend-common-guide.md)
- [프런트엔드 공통 개발 가이드](frontend-common-guide.md)
- [공통 JPA 설정과 테스트 DB 실행](test-database.md)
- [토스페이먼츠 테스트 키 설정 안내](toss-test-payment-setup.md)

### 회원·상품·관리자

- [후기 작성·조회](reviews.md): 완료 주문 구매자 후기 작성, 공개 상품 후기 목록과 화면·검증 기록.

- [내 상품 조회](my-products.md): 본인 전용 목록 API·관리 화면, 중지/숨김 포함·페이지·수정/상태 관리 연결과 검사 기록.
- [로그인·로그아웃·내 정보 API](auth-login.md)
- [판매자 프로필 관리](seller-profile.md)
- [카테고리 공개 조회](category-query.md)
- [공개 상품 검색 API](product-search.md)
- [공개 상품 상세 조회](product-detail.md)
- [상품 등록·수정·버전 관리](product-management.md)
- [관리자 상품 조회·숨김·복구 API](admin-product-moderation.md)
- [상품 판매 상태 관리](product-sale-status.md)

### 주문·거래

- [상품 상세에서 주문 진입](order-entry.md)
- [주문 생성 서비스·HTTP 연결](order-creation.md)
- [주문 목록·상세 조회 API](order-query.md)
- [주문 수락·거절 API](order-decision.md)
- [최초 납품·재납품 API](order-delivery.md)
- [구매자 수정 요청 API](order-revision.md)
- [구매자 취소 요청·판매자 처리 API](order-cancellation-requests.md)
- [주문 적립금 결제](order-wallet-payment.md)
- [주문 직접 취소·적립금 환불](order-refund.md)
- [구매 확정·판매 수익 정산](order-settlement.md)
- [거래 조회·결제·취소·구매 확정 화면 연결](order-finance-ui.md)

### 지갑·충전

- [지갑 생성·최초 테스트 적립금 지급](wallet-initialization.md)
- [잔액·적립금 내역 조회](wallet-query.md)
- [토스 테스트 충전 저장 구조](wallet-topup-schema.md)
- [토스 테스트 충전 신청·본인 조회 API](wallet-topup-api.md)
- [토스 테스트 충전 승인·지급·본인 재확인](wallet-topup-approval.md)
- [React 토스 테스트 충전 화면](wallet-topup-frontend.md)

### 통합·검증·변경 이력

- [상품 변경 후 기존 주문 유지 회귀 검사](order-product-change-regression.md)

- [마이페이지 동선·거래 화면 통합](account-trade-integration.md)
- [실제 로그인 세션 기반 거래 API 통합 검증](session-trade-integration.md)
- [실제 세션과 지갑 화면 연결](wallet-session-integration.md)
- [충전 중복·실패·세션 복구 검사](wallet-topup-failure-verification.md)
- [최신 main 반영 후 지갑·충전 회귀 검증](wallet-regression-20261102.md)
- [프로젝트 JPA·QueryDSL 통일](project-persistence.md)
- [결제·정산의 JPA·QueryDSL 전환](finance-persistence.md)
- [검증 이력](verification-history.md)
- [랜서픽 실제 프로젝트 구조 검토](project-review.md)
- [2026-10-29 문서 검토 반영 기록](review-changes.md)

## 문서 관리

현재 상태는 현황표에 모으고 상세 계약을 여러 문서에 복제하지 않는다. 기능 문서에는 구현 방식·검증 시점·제한을 남긴다. 실행 원본 DDL은 백엔드의 Flyway에서 관리하며 docs에 복사본을 만들지 않는다. 이미 적용한 마이그레이션은 수정하지 않는다.

과거 이력은 삭제하거나 현재 결과로 다시 쓰지 않는다. 새 개발·검증 결과가 생기면 기준 커밋·환경·실행 범위·남은 사항을 함께 기록한다. 추가 확장 작업은 이번 정리에서 착수하지 않는다.
