# 랜서픽 문서 안내

정리일: 2026-11-04. 소스 `235ae8f`와 사용자 수동 검증 완료 보고를 대조했다. 이번 문서 편집은 테스트·원격 CI 재실행을 포함하지 않는다.

## 먼저 읽을 문서

1. [현재 구현·검증 현황](current-status.md): 구현된 기능, 남은 개발, 검증 한계.
2. [작업표](work-plan.md): 확정 담당, 현재 작업, 과거 개발 기록.
3. [루트 README](../README.md): 기존 저장소를 복제한 뒤 실행하는 절차.
4. 아래 분야별 문서: 해당 기능의 정책·API·DB·구현 근거.

## 문서별 기준

| 판단할 내용 | 기준 문서 |
| --- | --- |
| 기능 범위·업무 정책 | MVP 명세 |
| 요청·응답·권한·중복 처리 | API 명세 |
| 데이터 관계·컬럼 | ERD·물리 DB 설계. V1 기본 16개와 V2 포함 17개를 구분 |
| 개발 방식·협업 | 공통 개발 규칙·공통 가이드·Git 협업 |
| 구현 현황·미완료 | 현재 현황과 기능별 최신 기록 |
| 담당 | 작업표. 고도화 배정·착수 조건은 확장 계획 |
| 실행·검증 결과 | 날짜·소스·환경이 명시된 기능/통합 기록과 해당 PR/CI |

문서에 적힌 계약·목표·설정 예시는 구현 완료 증거가 아니다. 과거 기록의 ‘미구현’, ‘후속’, ‘병합 전’은 당시 상태다. 과거 테스트 수를 합산하거나 최신 커밋의 실행 결과로 표현하지 않는다. 불일치가 남으면 현재 현황과 해당 기능의 후속 기록을 함께 확인하고, 정책을 임의 변경하지 않는다.

## 전체 문서 색인

- [사용자 수동 거래 검증 현황](manual-trade-verification.md)
- [개발용 수동 거래 데이터 SQL](sql/seed-manual-trade-demo.sql)

- [작업표](work-plan.md)
- [현재 현황](current-status.md)
- [백엔드 README](../LancerPick/README.md)
- [프런트 README](../LancerPickFrontend/README.md)
- [ERD 이미지 안내](images/README.md)

### 기준·설계

- [랜서픽 MVP 기획 명세서 v1.0](mvp-spec.md)
- [랜서픽 MVP 화면 흐름 v1.0 — 검토 반영본](screen-flow.md)
- [랜서픽 MVP API 명세서](api-spec.md)
- [랜서픽 MVP ERD 설계 v1.0](erd.md)
- [랜서픽 MVP 물리 DB 설계](physical-db.md)
- [랜서픽 공통 개발 규칙 v1.0](development-rules.md)
- [랜서픽 Git 협업 및 Pull Request 운영 규칙](git-workflow.md)
- [랜서픽 확장 계획서 v1.0](expansion-plan.md)

### 실행·개발

- [랜서픽 공통 개발 환경 구축 가이드](setup-guide.md)
- [백엔드 공통 개발 가이드](backend-common-guide.md)
- [프런트엔드 공통 개발 가이드](frontend-common-guide.md)
- [공통 JPA 설정과 테스트 DB 실행](test-database.md)
- [토스페이먼츠 테스트 키 설정 안내](toss-test-payment-setup.md)

### 회원·상품·관리자

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
