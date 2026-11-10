# LancerPick 문서 안내

기준일: **2026-11-10**, 정적 대조 소스: **main a7e889b**. 현재 규칙은 해당 주제 문서에서, 날짜·커밋별 검증과 대체된 설계는 change-history.md에서 읽는다. 문서는 8개를 유지한다.

## 어디부터 읽을지

| 목적 | 문서 |
| --- | --- |
| 서비스 목적·범위·기술 스택 | [project-overview.md](project-overview.md) |
| 현재 구현·남은 일·마일스톤·활성화 조건 | [project-progress.md](project-progress.md#current-status) |
| 담당·책임·초기 업무 비중·협업/리뷰 규칙 | [team-roles.md](team-roles.md) |
| 계층·인증·HTTP API·트랜잭션·실행 환경 | [architecture.md](architecture.md) |
| 도메인별 현재 규칙·API/입력·화면·오류 | [features.md](features.md) |
| 테이블·컬럼·관계·제약·인덱스·마이그레이션 | [database-design.md](database-design.md) |
| 왜 바뀌었는지·당시 검증·실패/보완·과거 계획 | [change-history.md](change-history.md) |
| 목차·관리 원칙·원본 이동 경로 | [README.md](README.md) |

현재 구현이 궁금하면 [진행 현황](project-progress.md#current-status), 특정 기능을 수정하려면 [기능 주제별 목차](features.md#주제별-목차), 예전 테스트 수·커밋·설계 근거가 필요할 때만 [이력 목차](change-history.md#이력-목차)를 연다.

## 현재와 과거를 구분하는 원칙

- 현재 기능/설계 문서는 **주제 → 하위 기능 → 업무 규칙·API·처리 순서·화면·데이터** 순으로 읽는다. 원본 파일별로 같은 설명을 다시 나열하지 않는다.
- 구현/검증 날짜·브랜치·커밋·당시 미구현/병합 전 상태·대체된 계획은 변경 이력의 **주제·시점별 접기 섹션**에 둔다. 과거 문구가 현재 상태를 되돌리지 않는다.
- LEGACY_V1·CONTRACT_V2·CONTRACT_V3의 호환 규칙은 현재 명세에도 필요하다. 버전별로 표시하고 현재 신규 정책과 구분한다.
- 게이트 c0009f2 통과 후 #152가 V3 생성 기본값을 true로 바꿨다. V2는 false, test 프로필은 V3 false다. 과거 테스트 수를 합산하거나 현재 main 재검사 결과로 표현하지 않는다.
- 개인 MVP 35/35와 공통 완료 0/8은 2026-11-05 시트 기준이다. 전체 고도화 완료율로 확대하거나 합산하지 않는다.
- 문서끼리 충돌하면 코드·후속 합의·검증 시점을 근거로 [판단](change-history.md#conflicts)을 기록한다. 확인되지 않은 내용은 **확인 필요**라고 적는다.

## 향후 문서 관리 원칙

1. **사용자가 명시적으로 요청하지 않는 한 새 Markdown 문서 파일을 만들지 않는다.** 기능마다 새 문서를 만드는 대신 아래 담당 섹션을 갱신한다.
2. 신규 기능·업무 규칙·입력·예외는 features.md의 해당 도메인, 진행 상태·TODO·미완료·선행·마일스톤은 project-progress.md에서 관리한다.
3. 시스템/API/인증/기술 설계는 architecture.md, DB 컬럼·관계·제약·인덱스는 database-design.md, 합의된 역할 변경은 team-roles.md에 반영한다.
4. 중요한 수정·개선·설계 결정·트러블슈팅·검증은 change-history.md에 날짜·커밋·환경·실행 범위·결과·한계를 함께 남긴다. 단순 코드 수정/리팩토링은 중요한 설계 변경이 없으면 문서화하지 않아도 된다.
5. 고유 정보·설계 의도·비즈니스 규칙·TODO·결정 이유·개발/검증 이력을 문서 수나 분량을 줄이려고 삭제하거나 과도하게 요약하지 않는다.
6. 중복은 동일함과 적용 범위를 확인한 뒤 한곳으로 통합한다. 서로 다른 체크리스트의 짧은 조건·역할/단계/시점이 다른 문장은 문맥을 유지한다.
7. 구현 판단은 실제 코드/설정을 따른다. 과거 설계·미구현 계획·보류/폐기 결정은 이유와 시점을 보존한다. 팀원의 담당·작업 상태를 임의 변경하지 않는다.
8. 통합 시 원본 본문·표·코드 블록과 새 위치를 대조하고 파일/앵커/외부 README 링크를 검증한다. **보존이 검증되지 않은 원본은 삭제하지 않는다.**
9. 실행 DDL의 원본은 백엔드 Flyway다. 문서에 실행 SQL 복사본을 새로 만들거나 적용된 마이그레이션을 수정하지 않는다. 후보 SQL·그림·예시는 버전/범위를 표시한다.
10. 에이전트 메모리·설정·지침은 수정/삭제하지 않는다. 이번 후속 정리는 docs/의 8개 Markdown과 사용자가 허용한 루트·백엔드·프런트 README만 수정한다. 코드·테스트·설정·DB·비 Markdown 리소스는 변경하지 않는다.

<a id="source-map"></a>
## 원본 100개 문서의 이동 경로

1차 통합에서 기존 README는 재작성하고 나머지 99개 원본 Markdown은 통합/보존 검증 후 삭제했다. 이번에는 새 파일을 만들거나 추가 원본을 삭제하지 않고 **8개 문서 안의 배치**를 고쳤다. 아래 '현재/주제'는 첫 읽기 위치, '기록'은 원본 내용이 남아 있는 첫 기록이다. 원래 경로·행 번호는 역사적 참조로만 읽는다.

| 기존 docs/ 상대 경로 | 현재/주제 | 기록 |
| --- | --- | --- |
| `README.md` | [현재/주제](README.md#source-map) | [기록](change-history.md#b-0-0) |
| `account-trade-integration.md` | [현재/주제](change-history.md#b-12-0) | [기록](change-history.md#b-12-0) |
| `admin-action-audit-design.md` | [현재/주제](change-history.md#b-19-0) | [기록](change-history.md#b-19-0) |
| `admin-cancellation-review.md` | [현재/주제](features.md#orders-cancel) | [기록](change-history.md#b-27-0) |
| `admin-contract-pdf-inspection.md` | [현재/주제](features.md#orders-contract) | [기록](change-history.md#b-36-0) |
| `admin-finance-support.md` | [현재/주제](features.md#finance-batch) | [기록](change-history.md#b-42-0) |
| `admin-member-management.md` | [현재/주제](features.md#members-sanctions) | [기록](change-history.md#b-51-0) |
| `admin-product-moderation.md` | [현재/주제](features.md#products-manage) | [기록](change-history.md#b-12-1) |
| `advanced-db-design.md` | [현재/주제](database-design.md#extensions) | [기록](change-history.md#b-65-0) |
| `advanced-db-transitions.md` | [현재/주제](change-history.md#b-65-0) | [기록](change-history.md#b-65-0) |
| `advanced-design-four-person.md` | [현재/주제](change-history.md#b-94-0) | [기록](change-history.md#b-94-0) |
| `advanced-design-three-person.md` | [현재/주제](change-history.md#b-115-0) | [기록](change-history.md#b-115-0) |
| `advanced-design-two-person.md` | [현재/주제](change-history.md#b-139-0) | [기록](change-history.md#b-139-0) |
| `advanced-finance-foundation.md` | [현재/주제](change-history.md#b-65-0) | [기록](change-history.md#b-65-0) |
| `advanced-policy-boundary.md` | [현재/주제](change-history.md#b-65-0) | [기록](change-history.md#b-65-0) |
| `advanced-rollout-order.md` | [현재/주제](change-history.md#b-170-0) | [기록](change-history.md#b-170-0) |
| `advanced-schema-foundation.md` | [현재/주제](change-history.md#b-175-0) | [기록](change-history.md#b-175-0) |
| `advanced-team-start.md` | [현재/주제](change-history.md#b-181-0) | [기록](change-history.md#b-181-0) |
| `api-spec.md` | [현재/주제](architecture.md#api) | [기록](change-history.md#b-187-0) |
| `auth-login.md` | [현재/주제](features.md#members-session) | [기록](change-history.md#b-211-0) |
| `auto-confirm-timer.md` | [현재/주제](features.md#communication-timer) | [기록](change-history.md#b-242-1) |
| `backend-common-guide.md` | [현재/주제](architecture.md#conventions) | [기록](change-history.md#b-248-0) |
| `category-query.md` | [현재/주제](features.md#products-catalog) | [기록](change-history.md#b-12-1) |
| `contract-pdf-delivery.md` | [현재/주제](features.md#orders-contract) | [기록](change-history.md#b-259-0) |
| `current-status.md` | [현재/주제](project-progress.md#current-status) | [기록](change-history.md#b-268-0) |
| `development-rules.md` | [현재/주제](architecture.md#conventions) | [기록](change-history.md#b-327-1) |
| `erd.md` | [현재/주제](database-design.md#schema) | [기록](change-history.md#b-365-0) |
| `event-contract.md` | [현재/주제](architecture.md#events) | [기록](change-history.md#b-421-0) |
| `expansion-plan.md` | [현재/주제](change-history.md#b-65-0) | [기록](change-history.md#b-65-0) |
| `finance-persistence.md` | [현재/주제](architecture.md#persistence) | [기록](change-history.md#b-12-1) |
| `frontend-common-guide.md` | [현재/주제](architecture.md#environment) | [기록](change-history.md#b-465-0) |
| `full-funding-payment.md` | [현재/주제](features.md#finance-funds) | [기록](change-history.md#b-473-0) |
| `full-funding-policy.md` | [현재/주제](features.md#finance-funds) | [기록](change-history.md#b-479-0) |
| `git-workflow.md` | [현재/주제](team-roles.md#collaboration) | [기록](change-history.md#b-488-1) |
| `header-notification-panel.md` | [현재/주제](features.md#communication-notifications) | [기록](change-history.md#b-537-0) |
| `history/advanced-db-design-installment.md` | [현재/주제](change-history.md#b-542-0) | [기록](change-history.md#b-542-0) |
| `history/advanced-design-four-person-installment.md` | [현재/주제](change-history.md#b-559-0) | [기록](change-history.md#b-559-0) |
| `history/advanced-rollout-order-installment.md` | [현재/주제](change-history.md#b-580-0) | [기록](change-history.md#b-580-0) |
| `images/README.md` | [현재/주제](database-design.md#erd-assets) | [기록](change-history.md#b-585-4) |
| `mail-infrastructure.md` | [현재/주제](architecture.md#mail) | [기록](change-history.md#b-592-0) |
| `manual-trade-verification.md` | [현재/주제](change-history.md#b-593-0) | [기록](change-history.md#b-593-0) |
| `member-change-policy.md` | [현재/주제](features.md#members-profile) | [기록](change-history.md#b-600-0) |
| `member-sanction-foundation.md` | [현재/주제](features.md#members-sanctions) | [기록](change-history.md#b-607-0) |
| `mvp-spec.md` | [현재/주제](change-history.md#b-614-0) | [기록](change-history.md#b-614-0) |
| `my-products.md` | [현재/주제](features.md#products-manage) | [기록](change-history.md#b-637-0) |
| `notifications.md` | [현재/주제](features.md#communication-notifications) | [기록](change-history.md#b-646-0) |
| `order-cancellation-requests.md` | [현재/주제](features.md#orders-cancel) | [기록](change-history.md#b-12-1) |
| `order-chat-pending-send.md` | [현재/주제](features.md#communication-chat) | [기록](change-history.md#b-661-0) |
| `order-chat-realtime.md` | [현재/주제](features.md#communication-chat) | [기록](change-history.md#b-665-0) |
| `order-chat-storage.md` | [현재/주제](features.md#communication-chat) | [기록](change-history.md#b-672-0) |
| `order-chat-workspace.md` | [현재/주제](features.md#communication-chat) | [기록](change-history.md#b-678-0) |
| `order-contract-foundation.md` | [현재/주제](features.md#orders-contract) | [기록](change-history.md#b-684-0) |
| `order-creation.md` | [현재/주제](features.md#orders-create) | [기록](change-history.md#b-12-1) |
| `order-decision.md` | [현재/주제](features.md#orders-create) | [기록](change-history.md#b-12-1) |
| `order-delivery.md` | [현재/주제](features.md#orders-delivery) | [기록](change-history.md#b-12-1) |
| `order-entry.md` | [현재/주제](features.md#orders-create) | [기록](change-history.md#b-710-1) |
| `order-finance-ui.md` | [현재/주제](features.md#finance-funds) | [기록](change-history.md#b-65-0) |
| `order-flow-edge-check.md` | [현재/주제](change-history.md#b-720-0) | [기록](change-history.md#b-720-0) |
| `order-product-change-regression.md` | [현재/주제](change-history.md#b-726-0) | [기록](change-history.md#b-726-0) |
| `order-query.md` | [현재/주제](features.md#orders-query) | [기록](change-history.md#b-12-1) |
| `order-refund.md` | [현재/주제](features.md#orders-cancel) | [기록](change-history.md#b-65-0) |
| `order-revision.md` | [현재/주제](features.md#orders-delivery) | [기록](change-history.md#b-12-1) |
| `order-settlement.md` | [현재/주제](features.md#finance-settlement) | [기록](change-history.md#b-750-0) |
| `order-wallet-payment.md` | [현재/주제](features.md#finance-funds) | [기록](change-history.md#b-65-0) |
| `payment-status-enums.md` | [현재/주제](features.md#finance-funds) | [기록](change-history.md#b-759-0) |
| `physical-db.md` | [현재/주제](database-design.md#schema) | [기록](change-history.md#b-65-0) |
| `product-detail.md` | [현재/주제](features.md#products-catalog) | [기록](change-history.md#b-12-1) |
| `product-management.md` | [현재/주제](features.md#products-manage) | [기록](change-history.md#b-804-0) |
| `product-review-browser-check.md` | [현재/주제](change-history.md#b-809-0) | [기록](change-history.md#b-809-0) |
| `product-sale-status.md` | [현재/주제](features.md#products-manage) | [기록](change-history.md#b-816-0) |
| `product-search.md` | [현재/주제](features.md#products-catalog) | [기록](change-history.md#b-821-3) |
| `project-persistence.md` | [현재/주제](architecture.md#persistence) | [기록](change-history.md#b-12-1) |
| `project-review.md` | [현재/주제](change-history.md#b-65-0) | [기록](change-history.md#b-65-0) |
| `review-changes.md` | [현재/주제](change-history.md#b-12-1) | [기록](change-history.md#b-12-1) |
| `review/advanced-foundation-review.md` | [현재/주제](change-history.md#b-865-0) | [기록](change-history.md#b-865-0) |
| `reviews.md` | [현재/주제](features.md#products-reviews) | [기록](change-history.md#b-872-0) |
| `screen-flow.md` | [현재/주제](features.md#screens-navigation) | [기록](change-history.md#b-879-0) |
| `seller-profile.md` | [현재/주제](features.md#products-seller) | [기록](change-history.md#b-12-1) |
| `session-trade-integration.md` | [현재/주제](change-history.md#b-12-1) | [기록](change-history.md#b-12-1) |
| `settlement-period-query.md` | [현재/주제](features.md#finance-settlement) | [기록](change-history.md#b-901-0) |
| `setup-guide.md` | [현재/주제](architecture.md#environment) | [기록](change-history.md#b-12-1) |
| `sohyun-final-verification.md` | [현재/주제](change-history.md#b-937-0) | [기록](change-history.md#b-937-0) |
| `team-progress.md` | [현재/주제](project-progress.md#team-progress) | [기록](change-history.md#b-943-0) |
| `test-database.md` | [현재/주제](architecture.md#environment) | [기록](change-history.md#b-953-0) |
| `toss-test-payment-setup.md` | [현재/주제](architecture.md#environment) | [기록](change-history.md#b-12-1) |
| `ui-renewal-member-policy.md` | [현재/주제](change-history.md#b-971-0) | [기록](change-history.md#b-971-0) |
| `v3-activation-gate.md` | [현재/주제](project-progress.md#activation-gate) | [기록](change-history.md#b-997-0) |
| `v3-notices.md` | [현재/주제](features.md#communication-timer) | [기록](change-history.md#b-1020-0) |
| `verification-history.md` | [현재/주제](change-history.md#b-12-1) | [기록](change-history.md#b-12-1) |
| `wallet-initialization.md` | [현재/주제](features.md#finance-wallet) | [기록](change-history.md#b-12-1) |
| `wallet-query.md` | [현재/주제](features.md#finance-wallet) | [기록](change-history.md#b-65-0) |
| `wallet-regression-20261102.md` | [현재/주제](change-history.md#b-12-1) | [기록](change-history.md#b-12-1) |
| `wallet-session-integration.md` | [현재/주제](change-history.md#b-12-0) | [기록](change-history.md#b-12-0) |
| `wallet-topup-api.md` | [현재/주제](features.md#finance-topup) | [기록](change-history.md#b-12-1) |
| `wallet-topup-approval.md` | [현재/주제](features.md#finance-topup) | [기록](change-history.md#b-12-1) |
| `wallet-topup-failure-verification.md` | [현재/주제](change-history.md#b-12-0) | [기록](change-history.md#b-12-0) |
| `wallet-topup-frontend.md` | [현재/주제](features.md#finance-topup) | [기록](change-history.md#b-12-0) |
| `wallet-topup-recovery-batch.md` | [현재/주제](features.md#finance-batch) | [기록](change-history.md#b-1084-0) |
| `wallet-topup-schema.md` | [현재/주제](database-design.md#topups) | [기록](change-history.md#b-12-1) |
| `work-plan.md` | [현재/주제](project-progress.md#remaining) | [기록](change-history.md#b-1104-0) |
| `history/current-status-log.md` | [현재 main](project-progress.md#current-status) | [날짜별 기록](change-history.md#status) |

## main pull 후 반영 기준

#149 알림 실패 조회·#151 운영 대시보드·#152 V3 생성 기본 활성·#153 판매 중 상품 최소 금액을 포함한 main a7e889b를 기준으로 한다. 로컬 60edafd의 관리자 회원 HTTP/화면·상품 등록 제재 검사는 [로컬 브랜치 기록](change-history.md#local-admin-member-branch)으로 옮기고 기능 코드는 이번 PR에 포함하지 않는다. 새 main의 고유 문서 내용 26개 본문 블록과 이동된 current-status-log를 검증해 함께 보존했다. [main 통합 기록](change-history.md#main-sync-20261110).

## 이번 정리의 결과·검증

- 원본 본문 블록 3643개 대조: 누락 0개. 원본 코드 블록 90개 대조: 누락 0개. 제목/앵커·링크 목적지·공백 변경은 본문 손실로 계산하지 않는다.
- 내부/외부 README의 로컬 링크 1370건·대상 파일/앵커 검사: 오류 0개, 중복 명시 앵커 0개. 외부 옛 링크 47개 처리 후 남은 삭제 문서 링크 0개.
- 현재 규칙을 읽는 6개 문서(목차/이력 제외)는 562,046자 → 282,381자, 약 50% 감소. 과거 기록은 삭제하지 않고 별도 이력에 보존했다.
- 원본 본문 3643개 중 완전히 같은 긴 중복 113개를 통합했다. 짧은 체크 항목 97개는 서로 다른 적용 문맥을 유지했다.
- 역할·미완료·정책 버전·검증 한계는 유지하며 날짜/커밋별 기록은 주제·시점별 접기 섹션으로 이동했다.
- 변경 허용 범위는 docs/ Markdown 8개와 외부 README 3개뿐이다. 저장본 동일 내용·링크 및 Git 변경 범위를 재검증했다.

외부 README 3개의 옛 Markdown 링크 47개는 새 현재 주제/정확한 이력 위치로 교체하거나 최신 소개에 통합했다. “계약 API 없음”, “현재는 실시간 수신 없음”, 과거 누적 테이블 수를 현재 상태처럼 보여 주던 표현은 현재화하거나 이력으로 이동했다. 대체한 외부 소개 9개 문단은 [이력](change-history.md#external-readme-update)에 보존했다.

소스/SQL 주석에 남은 옛 docs 경로는 코드·비 Markdown 수정 금지 범위라 그대로 유지한다. 이미지·스크립트·SQL·git-commands.txt는 원래 위치에 보존한다. V1 ERD 이미지는 최신 전체 41개 테이블 그림이 아니다. 애플리케이션 빌드·DB 적용·브라우저·원격 CI는 이번 문서 정리에서 재실행하지 않았다. 문서 정리 중에는 Git commit·push·reset·clean을 실행하지 않았다. 이후 사용자 요청으로 최신 main을 pull하고 문서 전용 브랜치를 commit·push해 PR을 생성한다. reset·clean은 실행하지 않는다.

### 최신 main 통합 추가 검증

main a7e889b의 Markdown 100개 본문 7,184줄을 대조해 누락 0을 확인했다. 기존 로컬 기준의 본문 3,643블록·코드 블록 90개도 보존했다. 이전 기본값/로컬 구현 설명을 삭제하지 않고 main 통합 이력에 분리했다. 코드·테스트·설정·SQL은 main과 동일하며 이 PR에서 수정하지 않는다.
