# 팀 진행도 — 2026-11-05

## 기준과 판단

- 저장소 기준: 원격 main을 fetch한 뒤 로컬과 동일한 `1e10d78`임을 확인했다.
- 시트 기준: 사용자가 이번 대화에 첨부한 개인 담당표 2장·공통 담당표 1장이다. 원본 시트의 셀을 직접 수정한 것은 아니다.
- 검토 범위: 추적 중인 Markdown 74개(루트·백엔드·프런트 README와 docs 71개)의 현황·담당·검증·설계 기록 및 문서 연결을 대조했다. 실제 반영 여부는 Flyway V1~V6, 주문 정책 DTO·명령 경계, CI 설정도 정적으로 확인했다.
- 작업 시작 시 미추적으로 확인된 `advanced-foundation-review.md`, `eunchan-advanced-tasks.md`는 이번 변경에 포함하지 않는다. 현재 근거는 추적 중인 문서와 main이다.
- 이번 작업은 문서 갱신이다. 빌드·DB·브라우저·원격 CI를 새로 실행하거나 과거 검사 수를 합산하지 않는다.

개인 기능의 완료 표시는 유지할 수 있다. 기능과 연결 대상이 구현되고 대표 연결 검증 기록도 있다. 다만 모든 예외·기기·운영 환경의 확인까지 끝났다는 의미는 아니다. 공통 업무와 고도화 기능은 개인 MVP 기능 완료와 따로 관리한다.

## 시트 요약

| 구분 | 완료 | 진행 중 | 시작 전 | 합계 |
| --- | --- | --- | --- | --- |
| 김창재 | 6 | 0 | 0 | 6 |
| 박소현 | 7 | 0 | 0 | 7 |
| 정은찬 | 12 | 0 | 0 | 12 |
| 황준범 | 10 | 0 | 0 | 10 |
| 개인 기능 합계 | **35** | **0** | **0** | **35** |
| 공통 | **0** | **7** | **1** | **8** |

이는 시트 항목 수 집계다. 작업량·검증 범위가 다른 개인 기능 35개와 공통 8개를 더해 프로젝트 완료율로 계산하지 않는다. 시트의 검토·막힘 집계는 모두 0이지만 미검증 범위가 없다는 뜻은 아니다.

## 개인 기능별 대조

아래 완료는 MVP 구현·연결 기준이다. 링크된 문서의 날짜·검사 환경·제외 범위는 그대로 유효하다.

### 김창재 — 6/6 완료

| 시트 항목 | 상태 | 연결 대상·근거 |
| --- | --- | --- |
| 회원가입 | 완료 | 황준범 지갑 생성·초기 지급과 원자 연결. [인증](auth-login.md), [지갑 초기화](wallet-initialization.md) |
| 로그인·로그아웃 | 완료 | 황준범 공통 요청·오류 처리와 연결. [인증](auth-login.md), [세션·지갑](wallet-session-integration.md) |
| 내 정보 조회·수정 | 완료 | 표시 이름 변경·헤더 갱신·저장 전 계정 확인. [인증](auth-login.md) |
| 세션 인증·CSRF·접근 권한 | 완료 | 공통 화면·이동 경로와 보호 거래 API 연결. [실제 세션 거래](session-trade-integration.md) |
| 관리자 상품 조회·운영 이력 | 완료 | 관리자 목록·상세·실제 운영 이력 확인. [관리자](admin-product-moderation.md) |
| 상품 숨김·복구 | 완료 | 박소현 상품 관리·판매 상태, 정은찬 주문 조건 보존과 연결. [관리자](admin-product-moderation.md), [상품 변경 회귀](order-product-change-regression.md), [대표 주문 3건](manual-trade-verification.md) |

### 박소현 — 7/7 완료

| 시트 항목 | 상태 | 연결 대상·근거 |
| --- | --- | --- |
| 판매자 프로필 관리 | 완료 | [판매자 프로필](seller-profile.md), [실제 화면 확인](product-review-browser-check.md) |
| 카테고리 조회·상품 검색 | 완료 | [카테고리](category-query.md), [검색](product-search.md), [실제 화면 확인](product-review-browser-check.md) |
| 상품 상세·주문 진입 | 완료 | 정은찬 주문 작성·조건 확인으로 연결. [상품 상세](product-detail.md), [주문 진입](order-entry.md), [로그인 후 생성](order-flow-edge-check.md) |
| 상품 등록·수정·버전 관리 | 완료 | 정은찬 주문 생성·기존 조건 보존과 연결. [상품 관리](product-management.md), [상품 변경 회귀](order-product-change-regression.md) |
| 상품 판매 상태 관리 | 완료 | 신규 주문 차단과 기존 주문 유지. [판매 상태](product-sale-status.md), [대표 주문 3건](manual-trade-verification.md) |
| 내 상품 조회 | 완료 | 본인 목록·페이지 복원·수정·상태 관리 연결. [내 상품](my-products.md), [실제 화면 확인](product-review-browser-check.md) |
| 후기 작성·조회 | 완료 | 정은찬 구매 확정 후 작성, 공개 조회·응답 유실 복구 확인. [후기](reviews.md), [실제 세션 회귀](sohyun-final-verification.md), [브라우저 확인](product-review-browser-check.md) |

### 정은찬 — 12/12 완료

| 시트 항목 | 상태 | 연결 대상·근거 |
| --- | --- | --- |
| 주문 작성·거래 조건 확인 | 완료 | 상품 상세·로그인 복귀·요구사항·조건 확인. [주문 진입](order-entry.md), [예외 확인](order-flow-edge-check.md) |
| 주문 생성·거래 조건 보관 | 완료 | 스냅샷·요청 키·상품 버전·경쟁 처리. [주문 생성](order-creation.md), [상품 변경 회귀](order-product-change-regression.md) |
| 구매·판매 주문 목록·상세 조회 | 완료 | 황준범 결제·잔액 차감 결과와 금융 내역 표시. [주문 조회](order-query.md), [거래 금융 화면](order-finance-ui.md) |
| 주문 수락·거절 | 완료 | 상태 전환·사유·허용 행동. [수락·거절](order-decision.md) |
| 최초 납품·재납품 | 완료 | 회차·결과물 링크·기한·이력. [납품](order-delivery.md), [거래 통합](account-trade-integration.md) |
| 구매자 수정 요청·수정 기한 | 완료 | 1회 요청·주문 당시 소요일·재납품 연결. [수정](order-revision.md), [거래 통합](account-trade-integration.md) |
| 결제 전 취소 | 완료 | 미결제 주문 취소와 환불 안내 구분. [직접 취소](order-refund.md) |
| 판매자 직접 취소 | 완료 | 황준범 전액 환불·잔액 반환과 같은 트랜잭션. [직접 취소](order-refund.md), [주문 #2](manual-trade-verification.md) |
| 구매자 취소 요청·철회 | 완료 | 요청·철회 API/화면, 철회 시 주문·결제 유지. [취소 요청](order-cancellation-requests.md) |
| 판매자 취소 요청 승인·거절 | 완료 | 승인은 황준범 전액 환불·잔액 반환 연결, 거절은 주문·금액 유지. [취소 요청](order-cancellation-requests.md), [주문 #3](manual-trade-verification.md) |
| 구매 확정 | 완료 | 황준범 정산 원자 생성 연결. [정산](order-settlement.md), [주문 #1](manual-trade-verification.md) |
| 주문 상태·납기·이력 표시 | 완료 | 보존 조건·회차별 지각·상태·금융 기록. [주문 조회](order-query.md), [API 명세](api-spec.md) |

### 황준범 — 10/10 완료

| 시트 항목 | 상태 | 연결 대상·근거 |
| --- | --- | --- |
| 지갑 생성·초기 적립금 지급 | 완료 | 가입 트랜잭션·최초 100만원·중복 지급 방지. [초기화](wallet-initialization.md), [인증](auth-login.md) |
| 적립금 잔액·변동 내역 조회 | 완료 | 본인 세션·원장·마이페이지. [지갑 조회](wallet-query.md), [세션 연결](wallet-session-integration.md) |
| 주문 결제·잔액 차감 | 완료 | 주문 상태·지갑·결제·원장 원자 처리. [주문 결제](order-wallet-payment.md) |
| 전액 환불·잔액 반환 | 완료 | 직접 취소·취소 승인과 연결. [환불](order-refund.md), [사용자 확인](manual-trade-verification.md) |
| 구매 확정 시 정산 생성 | 완료 | 주문 완료·정산 1회 생성. [정산](order-settlement.md) |
| 판매 수익·정산 조회 | 완료 | 집계·수수료·환불 주문 제외·마이페이지 연결. [정산](order-settlement.md), [거래 통합](account-trade-integration.md) |
| 토스 테스트 충전 | 완료 | 신청·승인·지급·재확인, 사용자 5만원 충전 및 기본 실패 흐름 확인. [충전 화면](wallet-topup-frontend.md), [실패 흐름](wallet-topup-failure-verification.md) |
| 공통 화면 구성·이동 경로 | 완료 | 실제 마이페이지 구매·판매·수익·지갑 동선. [거래 통합](account-trade-integration.md) |
| 공통 요청·오류 처리 | 완료 | 세션 쿠키·CSRF·오류·변경 자동 재전송 금지. [프런트 가이드](frontend-common-guide.md) |
| 인증 상태·사용자 캐시 관리 | 완료 | 로그인·로그아웃·이전 계정 캐시 정리와 사용자 확인. [인증](auth-login.md), [수동 확인](manual-trade-verification.md) |

## 공통 — 완료 0/8 유지

공통 업무에는 이미 구현·검증된 기반과 계속 운영할 일이 함께 있다. 첨부 시트의 상태는 유지한다. 오른쪽 열은 완료 판정을 위한 제안이며 새 팀 합의나 새 담당 배정을 뜻하지 않는다.

| 시트 항목 | 상태 | 현재 근거 | 종료 판단에 필요한 정리(제안) |
| --- | --- | --- | --- |
| 개발환경·테스트 DB 관리 | 진행 중 | [설정](setup-guide.md), [테스트 DB 격리](test-database.md), 격리 DB 검사 기록 | 팀원별 최신 소스 실행 재현 여부와 환경 차이 기록 |
| API·입력·오류 응답 계약 | 진행 중 | [API 명세](api-spec.md), 공통 오류·CSRF, 정책 DTO | 현 MVP 계약과 차기 V2 계약을 구분해 변경 관리 |
| 물리 DB·Flyway·엔티티 매핑 검토 | 진행 중 | V1~V6 main 반영, 29개 업무 테이블, [스키마 검사](advanced-schema-foundation.md) | 새 엔티티 추가 시 validate·migration 정합성 확인, 결제 회차 전환은 후속 |
| 거래 통합·동시성·롤백 검증 | 진행 중 | [실제 세션](session-trade-integration.md), [상품 변경 회귀](order-product-change-regression.md), [주문 예외](order-flow-edge-check.md), [사용자 거래](manual-trade-verification.md) | 이미 통과한 범위는 유지하고 새 변경·남은 경합만 선별 확인 |
| CI·코드 리뷰·병합 관리 | 진행 중 | main 대상 PR/push CI 설정·브랜치 이력 | 해당 PR의 실제 검사·리뷰·승인 확인. 이번 문서 검토는 원격 CI 결과를 판정하지 않음 |
| 배포·로그·백업·실행 재현 | 시작 전 | [개발 규칙](development-rules.md), 디자인·배포 보류 결정 | 배포 재개와 담당·환경 결정 후 HTTPS·로그·백업/복구·실행 재현 확인. 현재 착수하지 않음 |
| 문서·API 명세·ERD 관리 | 진행 중 | 이번 현황 갱신, MVP ERD와 고도화 실제 스키마 구분 | 문서 변경·구현 변경을 계속 대조. V1 이미지가 최신 29개 전체 ERD인 것처럼 표시하지 않음 |
| 통합 일정·협업 조율 | 진행 중 | [작업표](work-plan.md), [4인 고도화 방향](advanced-design-four-person.md) | 기능별 선행 PR·DB 담당·완료 범위를 팀에서 조율 |

## 고도화는 별도 진행

| 범위 | main `1e10d78` 기준(알림 행은 `1941ad1`) |
| --- | --- |
| V3 금융 컬럼·V4 계약/서명·V5/V6 업무 스키마 | 반영 완료. V1/V2 17개 + V4 2개 + V5 7개 + V6 3개 = 29개 |
| 금액 계산·내부 계약 초안/서명·정책 DTO·기존 명령 차단 | 기반 반영 완료. [정책 경계](advanced-policy-boundary.md) |
| 새 계약 주문 생성·정식 상태 전환·공개 API/화면 | 미연결. 현재 생성은 LEGACY_V1 유지 |
| 선금/잔금·부분 반환·보상 정산 | 미연결. 단일 결제 UNIQUE·전액 반환 CHECK 유지 |
| PDF 실물 생성·비공개 저장·당사자 다운로드 | 미연결. 내부 계약 메타데이터 준비와 구분 |
| 알림·아웃박스 | 기존(LEGACY_V1) 주문 알림 연결(#79), 실패 처리 격리 보완(#80). V2 계약 주문 알림·FAILED 재처리 명령은 후속. [주문 알림](notifications.md) |
| 채팅·회원 복구·문의·타이머 | 스키마 준비. 기능별 서비스·API·화면 연결은 후속 |
| Redis·Batch·메일 인프라 | 이번 기반에서 설치·활성화하지 않음 |

항목별 담당·선행·상태는 [고도화 작업표](work-plan.md#고도화-작업표--2026-11-05), 담당 근거는 [4인 설계](advanced-design-four-person.md), 선행 순서는 [적용·활성화 기준](advanced-rollout-order.md)을 따른다. 신규 DDL은 황준범이 맡는다. 정은찬의 알림·아웃박스는 이후 #79·#80으로 기존 주문 범위에서 구현됐고, 주문/계약 상태 연결은 고도화 작업표 E3(#82)·E4(#85)로 생성 경로와 계약 성립까지 반영됐고 E5 이후가 남았다.

## 남은 검증 한계

320px 화면 및 상품·후기·주문 예외의 브라우저 확인 기록은 있다. 실기기 모바일·다른 브라우저·터치/가상 키보드·모든 네트워크/경쟁 조합·운영 배포 확인은 별도다. 과거 문서에 남아 있는 광범위한 미확인 문장을 이미 완료한 대표 흐름의 재개발 작업으로 해석하지 않는다.

문서 기록의 651건 전체 통과는 V3/V4 당시다. V5/V6 기록은 전체 651건 중 1건 실패 후 관련 24건 재통과, 정책 경계 기록은 전체 654건 중 1건 실패 후 계약 11건 재통과다. 프런트 129건·lint·build 및 스키마 33건은 각각 당시 실행 결과다. 이들을 최신 main의 전체 일괄 통과로 합산하거나 이번 재실행 결과로 쓰지 않는다.
