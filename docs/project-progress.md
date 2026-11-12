# 프로젝트 진행 현황

[목차](README.md) · [역할과 책임](team-roles.md) · [검증·변경 이력](change-history.md)

<a id="current-status"></a>
## 현재 상태 — 2026-11-10 정적 대조

기준은 main `80d1f5b`다(이후 #180은 문서만 변경). 아래 표의 기능 판단은 #168까지의 대조이며, 그 뒤 #170~#179는 전체 리팩터링(동작·API·DB 변경 없음, 의도한 검증 강화는 401 코드 통일 #170과 복구 Job 파라미터 9999년 상한 #179, [마무리 검토](change-history.md#refactoring-wrapup))이고, 2026-11-12 2차 점검은 죽은 코드 삭제·상품 편집 화면의 `/me` 직접 호출 정리만 했다([기록](change-history.md#refactoring-second-pass), 동작 변경 없음). 이전 기준은 main `e276405`(#156 비밀번호 변경 권고 알림·#157 관리자 회원 조회·제재 화면·#159 판매 수익 검색·#155 아이디 안내 메일·#160 주문 금액 경로 처리 시각 보완·#161 계약·채팅 잠금 공통화·#163 보안 잠금 미전달 자동 재시도·#162 기존 회원 이메일 인증 전환·#164 보안 잠금 구매자 확정/사전 안내 잠금 순서 보완·#165 보안 잠금 판매자 거래 진전 차단·#166 메일 대기 결과 정리·#167·#168 K6 관리자 재처리·감사 이력 병합 후, `cefd3e5`)이다. 이후 실행 검증은 2026-11-11 main `5f089c3` 기준 박소현·김창재 실서버 확인과 main `2fd0d2f`(#194) 기준 [대표 V3 거래 확인](change-history.md#v3-representative-trade-check)이다. 소스 존재는 운영 검증 완료의 증거와 구분한다. 관리자 회원 통합 검사는 [검증 이력](change-history.md#admin-member-main-integration), 병합 후 U7·U8 화면 확인은 [제재 화면 확인](change-history.md#sanction-screen-check)에 기록한다.

| 범위 | 현재 판단 | 보존 근거·한계 |
| --- | --- | --- |
| 개인 MVP | 2026-11-05 시트 기준 35/35 완료 | 김창재 6·박소현 7·정은찬 12·황준범 10. 기존 담당·완료 수 유지 |
| 공통 업무 | 같은 시트 기준 완료 0/8, 진행 7·시작 전 1 | 개인 35개와 합산한 프로젝트 완료율을 만들지 않음 |
| V3 계약·전액 보관·확정/반환·타이머 | 코드와 API/화면 존재, 신규 V3 생성 기본 활성(#152) | full-funding-creation-enabled=true(#152), V2는 false. 기본 신규 생성은 CONTRACT_V3 |
| V3 활성화 게이트 | 후보 c0009f2 판정 통과 기록 | 백엔드 1021건·프런트 282건 및 실서버·브라우저 기록. 현재 HEAD의 재검사 결과가 아님 |
| 관리자 회원 관리 | 목록·검색·상태·상세·제재 적용/해제·이력 API/화면 main 반영(#157) | 상품 등록 제재 검사와 V3 최소 금액 검증 모두 유지. 새 DDL 없음 |
| 회원 변경 정책 U6/B3 | V11·닉네임 6개월·비밀번호 권고 기준일/조회 기반과 권고 알림 발송 main 반영(#156) | 로그인 때 회차별 1건·스케줄러 없음([권고 알림](features.md#password-recommendation)) |
| 회원 제재 U7/U8 | 주문 생성·수락 차단과 보안 잠금 타이머 중단 존재. 관리자 화면으로 적용·해제해 개인 DB 실서버·브라우저 확인(2026-11-10) | 일반 제한은 기존 거래 허용. 미전달 자동 재시도 main 반영(#163), 기존 만료 재검사 방어 유지. 잠긴 구매자의 수동 확정 차단·사전 안내 잠금 순서 보완은 main 반영(#164). 잠긴 판매자의 거래 진전 명령 차단은 main 반영(#165, [기록](change-history.md#security-lock-confirm-notice)) |
| 관리자 운영·금융·문의·PDF | 기존 조회/승인/타이머에 #149 알림 실패 조회·#151 운영 대시보드 추가 | K6 V13 감사 기반에 관리자 충전 재확인·Batch 재시작·PDF 생성 실행 내부 입구 main 반영(#167). 관리자 실행/감사 목록 API·화면 main 반영(#168, [API·화면](features.md#admin-reprocessing-api)). 기존 회원 제재 이력은 유지 |
| 알림·채팅·화면 개편 | 저장/실시간·세션 경계, U1~U5 관련 구현 기록 보존 | #147 같은 UUID 복구 수정 보존. 실제 서버와 API 대역 검사를 구분 |
| DB | V1~V13 파일, 업무 33개·Batch 시스템 9개 | 연결 DB에 실제 적용된 버전은 접속 조회하지 않았으므로 확인 필요 |

[날짜별 현황 기록](change-history.md#status) · [개인·공통 집계](project-progress.md#team-progress) · [담당별 작업표](project-progress.md#remaining) · [충돌 판단](change-history.md#conflicts)

<a id="remaining"></a>
## 진행·예정·보류 작업

기존 미완료 기록을 모은 목록이며 새 담당 배정이나 착수 지시가 아니다. 항목별 과거 작업표의 선행·상태·제외 조건은 [이력](change-history.md#status)에 보존한다.

| 항목 | 기존 담당·상태 | 후속·검증 경계 |
| --- | --- | --- |
| V3 생성 기본값 | 완료(#152), 정은찬·황준범 공동 검토 경계 유지 | V3 true·test false. 발표 후보에서 대표 V3 거래 재확인, 기존 주문 정책 유지 |
| 보안 잠금 전달 실패 재시도 | 황준범 구현, main 반영(#163). 기존 주문 타이머 정은찬 경계 유지 | 전용 스케줄러·ID 순회·조건부 완료 기록 연결([설정·동작](features.md#members-sanctions), [검증](change-history.md#security-lock-delivery-retry)). 재전달 전까지 일부 타이머 ACTIVE 표시, 중단 후 같은 기산점 재시작 409·해제 후 자동 재개 없음. 자동 확정은 만료 재검사로 방어 |
| K6 실제 재처리와 감사 이력 | 김창재 운영 API·화면, 황준범 DB/금융/PDF 기반. main 반영(#167·#168) | V13·JPA 감사 기반과 `AdminReprocessingService` 세 입구·업무별 결과 코드·감사 증명/처리권(#167, 전체 백엔드 1,134건 통과, 토스 대역). 관리자 실행 POST 3개·감사 이력 GET·화면(`/admin/finance` 재처리 칸·작업 이력 탭, 주문 금액 화면 PDF 칸)(#168, 전체 백엔드 1,141건 통과, [API·화면](features.md#admin-reprocessing-api)). 실서버·브라우저에서 PDF 재생성·충전 재확인(더미 UNKNOWN, `TOPUP_RESULT_UNCONFIRMED`)·Batch 재시작과 감사 이력 3건 일치 확인(2026-11-11 김창재, [검증](change-history.md#k6-member-mail-browser-verification)). 실제 결제 재조회의 `TOPUP_CREDITED`는 미확인. 관리자 PDF 재생성 잠금(관리자 공유 → 감사 요청 → 주문)은 기존 주문 잠금 순서와 충돌 없음(정은찬 정적 검토, 2026-11-10). [호출 계약](features.md#admin-reprocessing), [후속 검증](change-history.md#admin-reprocessing-services), [기반 검증 1,114건](change-history.md#admin-action-audit-foundation) |
| 수익 조회 후속 | 황준범, main 반영(#159) | 상품명/주문번호 검색·구매자/보관 상품 표시·원장 기반 지급 구분 연결. 관련 백엔드 40건·프런트 303건/네 검사·API 대역 브라우저 통과([기록](change-history.md#settlement-query-details)). 실제 서버 브라우저 확인은 별도 |
| 아이디 안내·메일 결과 정리 | 김창재 회원 기능·황준범 C3 경계. 아이디 안내 #155·기존 회원 이메일 인증 전환 #162·메일 결과 정리 #166은 main 반영 | V12·오래된 PENDING의 UNKNOWN 정리·늦은 실제 결과 확정. #166 전 전체 백엔드 1,098건 통과. [규칙](features.md#mail-delivery-reconciliation), [검증](change-history.md#mail-delivery-reconciliation). 자동 재발송은 사용자 결정으로 제외. 아이디 안내·비밀번호 재설정·이메일 인증 전환을 Mailpit 실서버로 확인하고 DB 발송 상태와 대조(2026-11-11 김창재, [검증](change-history.md#k6-member-mail-browser-verification)). 실제 SMTP 운영 발송과 구분. 재설정 성공 시 인증 시각 기록은 하지 않음 |
| R1 잠금 공통화 | 완료(#161, 정은찬). 계약·계약 문서·채팅이 `OrderCommandLocks` 사용([기록](change-history.md#order-lock-common)) | 401/404 상태·코드·문구와 계약 문서 경로의 회원 없음 404 유지, 계약 경로는 `lockFulfillmentOrder` 미사용. R2 enum 반영과 구분 |
| 권장 E5·운영 확인 | 기존 담당별 검증 후속 | 계정 전환 캐시는 같은 탭 브라우저로 확인(2026-11-10, [기록](change-history.md#account-switch-cache-check)). 원격 CI·실제 48시간/7일 경과는 미확인. DB 시각 조정과 구분 |
| 발표 후보 확인 | 정은찬(조장), 11/11까지 | 순서: 후보 SHA 고정 → 그 SHA의 원격 CI 확인 → 대표 V3 거래 1건 실서버·브라우저 확인 → 이 문서의 기준 커밋·검증 범위 갱신. 과거 게이트 기록(`c0009f2`)은 현재 HEAD의 근거가 아니다. 대표 V3 거래 1건(주문 #59, 계약 PDF 해시·결제 보관·채팅·납품·구매 확정·원장/정산 대조·320px)과 #175 발송·같은 탭 계정 전환, #177 위젯 준비·금액 변경은 main `2fd0d2f`에서 실서버·브라우저로 확인했다([기록](change-history.md#v3-representative-trade-check)). 남은 것: 후보 SHA 고정(11/12 디자인 병합 후 권장) → 그 SHA의 원격 CI → `2fd0d2f` 이후 변경의 영향 화면만 재확인 → 이 문서 기준 커밋 갱신. 채팅 미확정 재전송·실제 OS 한글 입력기·충전 신청 후 상태 이동은 미확인. 메시지 사이드바가 헤더 로그아웃을 덮던 문제는 #196으로 수정했다(API 대역 브라우저 확인, 실서버 재확인은 영향 화면 재확인에 포함) |
| 남은 검증 공지 | 정은찬 정리(2026-11-10), 담당별 확인 제안, 11/11까지 마무리 | 구현 잔여 없음. 필수(발표 후보 SHA·원격 CI·대표 V3 거래·#175/#177·팀원 PC 재현)와 담당별 실서버 확인(김창재 K6 실행·메일, 황준범 #159·#166·소켓 폐기·모바일)을 [공지 정리](change-history.md#verification-notice)에 둔다 |
| 11/11 로컬 검증 | 박소현, main `5f089c3` | 개인 PC 실행·Flyway V13 및 판매 수익 #159·메일 #166·보안 잠금과 소켓 종료·320px 거래 상세 확인. 후속이던 K6 재처리 결과와 감사 이력 대조는 김창재 실서버 검증으로 완료. [검증 기록](change-history.md#showcase-browser-verification) |
| 11/11 실서버 검증(김창재) | 김창재, main `5f089c3` | 김창재 배정분·황준범 이관분 완료: 메일 3종·#166 대조·#149·#151·K6 세 명령과 감사 이력·보안 잠금 소켓 종료·320px·#159. 실패 없음. [검증 기록](change-history.md#k6-member-mail-browser-verification) |
| 발표 환경·팀원 재현 | 공통 업무 | 배포 보류와 별개로 발표 환경(실행 방법·DB·키 설정)과 팀원 PC 재현을 완료 조건으로 정한다([실행 환경](architecture.md#environment)) |
| 모바일·브라우저·경합 확장 | 각 담당 및 공통 업무 | 320px/대표 거래와 실기기·모든 조합 검증 구분 |
| 화면 개편·디자인 | 정은찬·황준범, 웹 디자인 작업 11/12까지 마무리 | 관리자 화면 정리 브랜치 `feature/admin-design-polish`(1차는 #183, 2~6차는 #189로 main 반영. 본문 폭·보조 버튼·빈 상태·대시보드 카드·상태 배지 톤, 2차: 페이지 이동 조건·금융 상세 정렬·모바일 한 열·하위 탭 칩·회원 상태 카드, 3차: 목록 행 재구성·필터 초기화 통일·상태 코드 한글 라벨·회원·주문 상호 링크·금융 상세 카드, 4차: 알림 처리·타이머·상품 상세 연결·회원 상세 세부, 5차: 목록 상태 표시·행 높이, 6차: 목록·이력 열 구분 표·페이지 이동 통합·상품/문의/주문 금융 상세 카드·회원 화면 해요체, [기록](change-history.md#admin-design-polish)). 거래 상세 탭(#186)·메시지·알림 사이드바(#187)·거래 목록(#188·#191)·상품 등록 폼과 서비스 조회 조건(#191)·내 상품(#192)·상품 상세와 후기(#193)·채팅 한글 조합과 계약서 가독성(#194)·사이드바의 헤더 가림 수정(#196)은 main 반영(각 PR은 API 대역 브라우저 확인, 거래 상세·계약서·채팅은 [대표 거래](change-history.md#v3-representative-trade-check)에서 실서버 확인). 판매 수익 화면 카드·필터 정리와 거래 목록/상세 보완(정은찬, 2026-11-11 브랜치 `feature/settlement-design-polish`, 실서버·브라우저 확인). 화면 문구·스타일 기준안과 알림 화면 정리(정은찬, 2026-11-11, main 반영(#199), [기준](architecture.md#ui-consistency)). 나머지 화면의 해요체 전환·마이페이지 제목 영역(`PageHeading`)·사용자 목록 `Pager`·관리자 버튼 높이는 main 반영(#200, 정은찬). 보조 글자 대비·남은 해요체·계약 버튼 규격은 main 반영(#201, 정은찬). #202에서 모바일 홈 겹침(380px 이하 히어로 카드가 카테고리를 가림)·판매자 공개 페이지·404 화면도 정리했다(실서버 1280·375px 확인). 제목 영역 `PageHeading` 통일·관리자 버튼 클래스화·상태 색 토큰은 main 반영(#202 `d6d4d8f`, 정은찬, 2026-11-11, 자동 검사·사용자 화면 실서버 확인). 이어 관리자 화면 10개 경로를 실서버 1280/375px로 확인하고 버튼 비활성 투명도를 `--disabled-opacity` 토큰으로 통일했다(#203 `0c7af6a`, 정은찬, 2026-11-11, [기록](change-history.md#disabled-opacity-token)). 관리자 상태 배지(결제·환불·충전·문의·제재 등)를 같은 모양·뜻별 색으로 통일했다(#203 `0c7af6a`, [기록](change-history.md#admin-status-badge)). 이어 읽기 어려운 정보 글자를 11px 이상으로, 기준을 벗어난 버튼을 40px·모서리 8px로, 옅은 보조 글자를 `--muted`로, 불러오기·재시도 문구를 기준대로 맞췄다(#204 `53e1107`, 정은찬, 2026-11-11, 실서버 375·390·1280px 확인, [기록](change-history.md#ui-scale-consistency)). 화면 맨 위 되돌아가기 링크 15곳은 공통 `BackLink`(보조 버튼 모양)로 통일했다(#205 `92d1cc8`, 정은찬, 2026-11-12, 실서버 1280/375px 확인, [기록](change-history.md#back-link)). 사용자 화면 상태 배지(거래 목록·주문 상세·판매 수익·계약)를 관리자와 같은 공통 `StatusBadge`로, 거래 목록·주문 상세·판매 수익·알림의 조회 로딩·오류를 공통 `AsyncState`로 맞췄다(#207 `a416518`, 정은찬, 2026-11-12, 실서버 1280/375px 확인, [기록](change-history.md#user-status-badge)). 마지막으로 토큰과 같은 값의 직접 지정 색·근접 테두리 회색을 토큰으로 바꾸고 9px 영문 소제목을 10px로 올렸다(#208 `7e0e2d9`, 정은찬, 2026-11-12, 실서버 1280/721/375px 확인, [기록](change-history.md#color-tokens)). 카드 모서리·글자 굵기·중단점 값의 전역 통일은 새 디자인 결정이라 하지 않기로 했다(Codex 검토). 11/12 이후 추가 디자인 개선은 별도 잔여 작업에 집계하지 않는다. 이번 결정은 모든 화면의 검증 완료를 뜻하지 않으며 중대한 결함은 보완한다 |
| 배포 | 기존 보류 유지 | 화면 개편 마무리와 별개이며 배포 완료로 처리하지 않는다 |
| C4 Redis·C5 계측 | 이번 발표 범위 제외 | 객체 저장소·소셜/파일 확장 등은 과거 선택안으로 보존하며 MVP 완료 조건에 추가하지 않음 |

## 마일스톤

| 기간 | 기존 목표 | 판단 |
| --- | --- | --- |
| 10/28~10/30 | 설계·공통 환경·기본 배포 | 최초 일정안. 배포 완료를 뜻하지 않음 |
| 11/02~11/06 | 인증·상품·주문·기본 결제 통합 | 날짜별 검증 기록 보존 |
| 11/09~11/13 | 납품·수정·환불·구매 확정·정산 | 목표 일정과 이미 완료된 구현 구분 |
| 11/11 | 검증 작업 마무리 | 2026-11-11 일정 변경 |
| 11/12 | 웹 디자인 작업 마무리 | 2026-11-11 일정 변경 |
| 11/16~11/18 | 후기·최소 운영·전체 검증·공식 결과 | 공식 개발 종료 목표 |
| 11/17(화) | 발표 준비 마무리 | 2026-11-11 일정 변경(기존 12/10 안정화·문서·발표 준비) |
| 12/05 | 기능 추가 중단 | 기존 합의 유지 |
| 12/11 | 발표 | 기존 일정 유지 |

<a id="team-progress"></a>
## 개인 기능 완료 근거 — 2026-11-05 시트 기준

개인 35개·공통 8개를 합산한 프로젝트 완료율을 만들지 않는다. 아래 완료는 당시 MVP 구현·연결 기준이며 고도화 완료율이 아니다.

### 김창재 — 6/6 완료

| 시트 항목 | 상태 | 연결 대상·근거 |
| --- | --- | --- |
| 회원가입 | 완료 | 황준범 지갑 생성·초기 지급과 원자 연결. [인증](features.md#members-session), [지갑 초기화](features.md#finance-wallet) |
| 로그인·로그아웃 | 완료 | 황준범 공통 요청·오류 처리와 연결. [인증](features.md#members-session), [세션·지갑](change-history.md#b-12-0) |
| 내 정보 조회·수정 | 완료 | 표시 이름 변경·헤더 갱신·저장 전 계정 확인. [인증](features.md#members-session) |
| 세션 인증·CSRF·접근 권한 | 완료 | 공통 화면·이동 경로와 보호 거래 API 연결. [실제 세션 거래](change-history.md#b-12-1) |
| 관리자 상품 조회·운영 이력 | 완료 | 관리자 목록·상세·실제 운영 이력 확인. [관리자](features.md#products-manage) |
| 상품 숨김·복구 | 완료 | 박소현 상품 관리·판매 상태, 정은찬 주문 조건 보존과 연결. [관리자](features.md#products-manage), [상품 변경 회귀](change-history.md#b-726-0), [대표 주문 3건](change-history.md#b-593-0) |

### 박소현 — 7/7 완료

| 시트 항목 | 상태 | 연결 대상·근거 |
| --- | --- | --- |
| 판매자 프로필 관리 | 완료 | [판매자 프로필](features.md#products-seller), [실제 화면 확인](change-history.md#b-809-0) |
| 카테고리 조회·상품 검색 | 완료 | [카테고리](features.md#products-catalog), [검색](features.md#products-catalog), [실제 화면 확인](change-history.md#b-809-0) |
| 상품 상세·주문 진입 | 완료 | 정은찬 주문 작성·조건 확인으로 연결. [상품 상세](features.md#products-catalog), [주문 진입](features.md#orders-create), [로그인 후 생성](change-history.md#b-720-0) |
| 상품 등록·수정·버전 관리 | 완료 | 정은찬 주문 생성·기존 조건 보존과 연결. [상품 관리](features.md#products-manage), [상품 변경 회귀](change-history.md#b-726-0) |
| 상품 판매 상태 관리 | 완료 | 신규 주문 차단과 기존 주문 유지. [판매 상태](features.md#products-manage), [대표 주문 3건](change-history.md#b-593-0) |
| 내 상품 조회 | 완료 | 본인 목록·페이지 복원·수정·상태 관리 연결. [내 상품](features.md#products-manage), [실제 화면 확인](change-history.md#b-809-0) |
| 후기 작성·조회 | 완료 | 정은찬 구매 확정 후 작성, 공개 조회·응답 유실 복구 확인. [후기](features.md#products-reviews), [실제 세션 회귀](change-history.md#b-937-0), [브라우저 확인](change-history.md#b-809-0) |

### 정은찬 — 12/12 완료

| 시트 항목 | 상태 | 연결 대상·근거 |
| --- | --- | --- |
| 주문 작성·거래 조건 확인 | 완료 | 상품 상세·로그인 복귀·요구사항·조건 확인. [주문 진입](features.md#orders-create), [예외 확인](change-history.md#b-720-0) |
| 주문 생성·거래 조건 보관 | 완료 | 스냅샷·요청 키·상품 버전·경쟁 처리. [주문 생성](features.md#orders-create), [상품 변경 회귀](change-history.md#b-726-0) |
| 구매·판매 주문 목록·상세 조회 | 완료 | 황준범 결제·잔액 차감 결과와 금융 내역 표시. [주문 조회](features.md#orders-query), [거래 금융 화면](features.md#finance-funds) |
| 주문 수락·거절 | 완료 | 상태 전환·사유·허용 행동. [수락·거절](features.md#orders-create) |
| 최초 납품·재납품 | 완료 | 회차·결과물 링크·기한·이력. [납품](features.md#orders-delivery), [거래 통합](change-history.md#b-12-0) |
| 구매자 수정 요청·수정 기한 | 완료 | 1회 요청·주문 당시 소요일·재납품 연결. [수정](features.md#orders-delivery), [거래 통합](change-history.md#b-12-0) |
| 결제 전 취소 | 완료 | 미결제 주문 취소와 환불 안내 구분. [직접 취소](features.md#orders-cancel) |
| 판매자 직접 취소 | 완료 | 황준범 전액 환불·잔액 반환과 같은 트랜잭션. [직접 취소](features.md#orders-cancel), [주문 #2](change-history.md#b-593-0) |
| 구매자 취소 요청·철회 | 완료 | 요청·철회 API/화면, 철회 시 주문·결제 유지. [취소 요청](features.md#orders-cancel) |
| 판매자 취소 요청 승인·거절 | 완료 | 승인은 황준범 전액 환불·잔액 반환 연결, 거절은 주문·금액 유지. [취소 요청](features.md#orders-cancel), [주문 #3](change-history.md#b-593-0) |
| 구매 확정 | 완료 | 황준범 정산 원자 생성 연결. [정산](features.md#finance-settlement), [주문 #1](change-history.md#b-593-0) |
| 주문 상태·납기·이력 표시 | 완료 | 보존 조건·회차별 지각·상태·금융 기록. [주문 조회](features.md#orders-query), [API 명세](architecture.md#api) |

### 황준범 — 10/10 완료

| 시트 항목 | 상태 | 연결 대상·근거 |
| --- | --- | --- |
| 지갑 생성·초기 적립금 지급 | 완료 | 가입 트랜잭션·최초 100만원·중복 지급 방지. [초기화](features.md#finance-wallet), [인증](features.md#members-session) |
| 적립금 잔액·변동 내역 조회 | 완료 | 본인 세션·원장·마이페이지. [지갑 조회](features.md#finance-wallet), [세션 연결](change-history.md#b-12-0) |
| 주문 결제·잔액 차감 | 완료 | 주문 상태·지갑·결제·원장 원자 처리. [주문 결제](features.md#finance-funds) |
| 전액 환불·잔액 반환 | 완료 | 직접 취소·취소 승인과 연결. [환불](features.md#orders-cancel), [사용자 확인](change-history.md#b-593-0) |
| 구매 확정 시 정산 생성 | 완료 | 주문 완료·정산 1회 생성. [정산](features.md#finance-settlement) |
| 판매 수익·정산 조회 | 완료 | 집계·수수료·환불 주문 제외·마이페이지 연결. [정산](features.md#finance-settlement), [거래 통합](change-history.md#b-12-0) |
| 토스 테스트 충전 | 완료 | 신청·승인·지급·재확인, 사용자 5만원 충전 및 기본 실패 흐름 확인. [충전 화면](features.md#finance-topup), [실패 흐름](change-history.md#b-12-0) |
| 공통 화면 구성·이동 경로 | 완료 | 실제 마이페이지 구매·판매·수익·지갑 동선. [거래 통합](change-history.md#b-12-0) |
| 공통 요청·오류 처리 | 완료 | 세션 쿠키·CSRF·오류·변경 자동 재전송 금지. [프런트 가이드](architecture.md#environment) |
| 인증 상태·사용자 캐시 관리 | 완료 | 로그인·로그아웃·이전 계정 캐시 정리와 사용자 확인. [인증](features.md#members-session), [수동 확인](change-history.md#b-593-0) |

<a id="activation-gate"></a>
## V3 생성 활성화 기준

c0009f2 통과는 [고정 후보의 검증 기록](change-history.md#gate)이다. 기본값은 #152로 V3 true·V2 false다. 이후 변경의 영향·증거 승계·열린 결함·경합·불변식을 확인하고 발표 후보에서 대표 V3 거래 1건을 다시 확인한다. 되돌릴 때는 신규 생성만 false로 바꾸며 기존 V3 주문은 V3 경로로 완료한다.

### 업무 규칙·연결

#### 시나리오

표기: ● 필수, ○ 권장. 근거 클래스는 `LancerPick/src/test/java/com/example/lancerpick/` 기준.

#### 시나리오 / A. 정상 흐름

| ID | 시나리오 | 기대 결과 | 현재 근거 | 남은 확인 |
| --- | --- | --- | --- | --- |
| A1 ● | 스위치 on 상태에서 HTTP 주문 생성 → 수락 → 초안 → PDF → 양측 서명 → 결제 → 납품 → 구매 확정 | 정책 V3 고정, 결제액만 차감·HELD, 확정 시 판매자 +90%·수수료 10%·완료, 이력·알림 순서 | `V3ActivationGateIntegrationTests`.switchOnHttpTradeRuns…(실제 설정 on, 전 단계 HTTP·이력·아웃박스 순서·대금 조회). 실서버 API 실행 기록: [V3 알림](change-history.md#b-1026-0) | 화면 버튼으로 하는 전체 거래(G1 행) |
| A2 ● | 수정 요청 1회 → 재납품 → 확정 | 재납품 기한=요청 시각+소요일, 두 번째 수정 요청 거절 | `ContractV3FulfillmentApiIntegrationTests.v3DeliveryRevisionAndRedelivery…`, `ReviewTimerIntegrationTests.revisionNotifiesRedeliveryDeadline…`. 브라우저 [10절](change-history.md#b-1015-0)(주문 37) | — |
| A3 ● | 납품 후 48시간 → 판매자 타이머 시작 → 7일 만료 → 자동 확정 | SYSTEM 완료·AUTO_CONFIRMED closure·수수료 동일·양측 알림·후기 가능 | `ReviewTimerIntegrationTests.sellerStartsAfter48Hours…`, `concurrentExpiryRunsPaySellerOnlyOnce`. 실서버 스케줄러·후기 등록 [10절](change-history.md#b-1015-0)(주문 39) | — |
| A4 ○ | 만료 24시간 전 구매자 사전 안내 | 구매자에게 1회, 판매자 없음 | `autoConfirmNoticeIsSentOnceToBuyer…`, `…NotSentForEndedOrExpiredTimers`. 실서버 알림 목록 [10절](change-history.md#b-1015-0)(주문 39) | — |
| A5 ● | 결제 시 납기 통지 | `DEADLINE_NOTIFIED` 1건·`orders.due_at`과 일치 | `paymentNotifiesDeliveryDeadlineOnce…`. 거래 이력 화면 [10절](change-history.md#b-1015-0) | — |

#### 시나리오 / B. 취소·반환

| ID | 시나리오 | 기대 결과 | 현재 근거 | 남은 확인 |
| --- | --- | --- | --- | --- |
| B1 ● | 결제 전 취소(수락 전·계약 대기·서명 후) | 금액 이동 없음, `ORDER_CANCELLED` | `V3ActivationGateIntegrationTests`.cancellationBeforePaymentMovesNoMoney…(V3 수락 전·계약 대기·서명 후 구매자/판매자), V2 기준 `OrderContractIntegrationTests` | — |
| B2 ● | 결제 후 구매자 취소 요청 → 판매자 승인 | 보관금 전액 반환·REFUNDED·수익/수수료 0·`ORDER_FUNDS_REFUNDED` | `FullFundingTradeIntegrationTests.approvedCancellationRefundsOnce…`, `manualConfirmationAndApprovedRefundUseV3FundsWording`. 브라우저 [10절](change-history.md#b-1015-0)(주문 38) | — |
| B3 ● | 취소 요청 거절·철회 후 재개 | 타이머 종료, 해결 시각이 새 기산점 | `cancellationRequestCancelsTimerAndRejectionRestartsReviewWindow` | — |
| B4 ● | 판매자 무응답 → 운영 문의 → 관리자 승인 취소(K4) | 전액 반환·`ADMIN_CANCELLATION`(`inquiry_id`)·문의 종료 한 트랜잭션 | 서비스 통합 테스트(`AdminCancellationReviewIntegrationTests`, 승인·거절·재전송·실패 롤백, main 반영 #121). 실서버·브라우저 [9절](change-history.md#b-1014-0)(주문 34 승인·35 거절) | — |
| B5 ● | 결제 후 판매자 단독 취소 시도 | 409 `CANCELLATION_REQUEST_REQUIRED` | `pendingCancellationBlocksConfirmationAndWrongActors…` | — |

#### 시나리오 / D. 실패 시 전체 롤백

| ID | 시나리오 | 기대 결과 | 현재 근거 |
| --- | --- | --- | --- |
| D1 ● | 결제 중 아웃박스 실패 | 차감·결제·보관·이력 모두 없음 | `paymentFailureRollsBackDebitPaymentAndHolding` |
| D2 ● | 확정 중 판매자 지급 이후 실패 | 판매자 잔액·원장·closure·완료 모두 롤백 | `payoutFailureRollsBackWalletLedgerClosureAndOrderTogether` |
| D3 ● | 승인 환불 중 실패 | 요청 승인·잔액·closure 롤백 | `refundFailureRollsBackRequestApprovalWalletAndClosure` |
| D4 ● | 자동 확정 실패 | 지급 롤백·타이머 활성 유지·다른 타이머 계속 처리 | `autoConfirmFailureRollsBack…`, `persistentlyFailingTimerDoesNotBlock…` |
| D5 ● | 기한 통지 저장 실패 | 결제·수정 요청도 롤백 | 같은 원천 키 UNIQUE 위반 검사(`paymentNotifiesDeliveryDeadlineOnce…`) |

#### 시나리오 / E. 권한·보안

| ID | 시나리오 | 기대 결과 | 현재 근거 | 남은 확인 |
| --- | --- | --- | --- | --- |
| E1 ● | 제3자·반대 역할·익명의 결제/확정/승인/대금 조회 | 401/403/404, 금액 불변 | `pendingCancellationBlocksConfirmationAndWrongActors…`, `httpEndpointsCheckParticipantRoleAndCsrf` | — |
| E2 ● | 클라이언트가 보낸 금액·회원 ID 무시 | 서버 계약 금액으로 차감 | `httpPaymentIgnoresClientMoney…` | — |
| E3 ● | 서명 누락·해시 불일치 상태에서 결제 | 차감 없음 | `missingSignatureCannotDebit…`, `OrderContractIntegrationTests.fileHashMismatch…` | — |
| E4 ● | 계약 PDF 다운로드는 당사자만, 변조 파일은 503 | 바이트 노출 없음 | `ContractDocumentIntegrationTests.finishedDocument…OnlyToParties`, `missingAndTamperedFiles…`. 실제 다운로드·해시 대조 [10절](change-history.md#b-1015-0)(주문 37~39) | — |
| E5 ○ | 계정 전환 시 대금·주문 캐시 정리 | 이전 계정 금액 미표시 | 프런트 `test:api`(order-funds queryKey). 브라우저 한 탭에서 세 계정 전환 확인(2026-11-10, main `3353e57`, [기록](change-history.md#account-switch-cache-check)) | — |

#### 시나리오 / F. 호환·회귀

| ID | 시나리오 | 기대 결과 | 현재 근거 |
| --- | --- | --- | --- |
| F1 ● | 스위치 on 전후 기존 LEGACY_V1 주문 처리 | 정책 값 불변, 기존 결제·환불·정산 그대로 | `replayKeepsPolicyFixedAtCreationEvenAfterSwitch`, `LegacyPaymentCompatibilityTests`, 기존 주문 통합 테스트 |
| F2 ● | CONTRACT_V2 기존 주문 | 결제 후 명령 계속 차단, 계약 원문·해시 보존 | `v2PaidOrderStaysBlocked`, `OrderContractIntegrationTests.replacementPreservesOldSignature…`, `OrderCreationIntegrationTests.fullFundingOrderFreezesNewTerms…`(기존 V1/V2 원문 보존) |
| F3 ● | 금액 하한·상한 | 10,000원 미만 생성·확정·결제 거절 | `contractOrderRejectsTotalBelowMinimum…`, `contractOrderAcceptsMinimumBoundary`, DB CHECK |
| F4 ● | 스위치를 다시 끄면 | 신규만 LEGACY_V1, 이미 만든 V3 주문은 끝까지 처리 | 정책 고정 검사(F1)로 간접 확인. 실서버 on→off 후 V3 주문 35 확정·신규 주문 36 LEGACY_V1([9절](change-history.md#b-1014-0)) |

#### 시나리오 / G. 실서버·브라우저(자동 테스트로 대체 불가)

| ID | 확인 | 담당 |
| --- | --- | --- |
| G1 ● | 두 계정 브라우저로 A1·A2·B2 전체 거래: 계약 화면, 서명, 대금 패널(보관→지급/반환), 지갑 원장 `SELLER_PAYOUT`/`REFUND`, 판매 수익 화면 | 정은찬·황준범 |
| G2 ● | 실서버 스케줄러: 타이머 만료 자동 확정, 사전 안내, 아웃박스 → 알림 생성 | 정은찬 |
| G3 ● | 거래 이력 "납기 통지 · 시스템"·알림 문구(보관·지급·반환·자동 확정) 표시 | 정은찬 |
| G4 ○ | 좁은 화면에서 계약·대금·타이머 패널 | 황준범 |
| G5 ● | 실제 V3 PDF 한글·금액·특약 표시, 다운로드 파일 해시가 화면 값과 일치 | 황준범 |

#### 활성화 절차

<a id="b-1010-0"></a>
1. 후보 커밋 확정 → 1절 같은 소스 검사 → 2절 필수 항목·4절 0행 확인 → 6절 기록.
2. 정은찬이 6절 판정을 기록한 뒤 `application.properties`의 기본값을 true로 바꾸는 PR을 올린다(설정 한 줄 + 문서 갱신만, 다른 변경 섞지 않음). 황준범의 공동 검토는 이 PR 리뷰 승인으로 한다.
3. 되돌리기: 기본값을 false로 되돌리면 신규 주문만 LEGACY_V1로 돌아가고 이미 만든 V3 주문은 정책 고정으로 끝까지 처리된다(F4). 기존 주문의 `policy_version`을 DB에서 바꾸지 않는다.

### 처리 순서·동시성·재전송

#### 시나리오 / C. 경합(실제 커밋·동시 스레드)

| ID | 시나리오 | 기대 결과 | 현재 근거 | 남은 확인 |
| --- | --- | --- | --- | --- |
| C1 ● | 같은 지갑으로 두 주문 동시 결제 | 잔액 초과 없음 | `simultaneousPaymentsUseSharedWalletWithoutOverspending` | — |
| C2 ● | 같은 주문 동시 결제·재전송 | 차감 1건 | `repeatedConcurrentPaymentsCreateOneDebit` | — |
| C3 ● | 동시 구매 확정 | 판매자 지급 1건 | `simultaneousConfirmationsCreditSellerOnlyOnce` | — |
| C4 ● | 취소 승인 vs 구매 확정 | 하나만 성공 | `cancellationAndConfirmationCannotBothWin` | — |
| C5 ● | 타이머 만료 vs 구매자 확정/수정/취소 요청 | 만료 시각 이상이면 만료 우선(409 `AUTO_CONFIRM_DUE`), 응답이 먼저 커밋되면 만료 작업은 지급 안 함 | 순차: `ReviewTimerIntegrationTests`(AUTO_CONFIRM_DUE 3건). 동시: `V3ActivationGateIntegrationTests`.expiryRacingBuyerResponses…(만료 후 만료 처리·수정·취소 요청·확정 4개 동시), responseCommittedBeforeExpiry…(만료 전 동시 → 끝난 타이머는 이후 만료 실행에서도 지급 안 함) | — |
| C6 ● | 관리자 해제 vs 자동 확정 | 같은 잠금으로 직렬화, 만료 후 해제는 409 | 순차: `adminCannotStopDueTimerBecauseExpiryWins`. 동시: `V3ActivationGateIntegrationTests`.adminStopRacingDueExpiry…, adminStopBeforeExpiry… | — |
| C7 ● | 여러 서버의 만료 스케줄러 동시 실행 | 지급 1건 | `concurrentExpiryRunsPaySellerOnlyOnce` | — |
| C8 ○ | 관리자 승인 취소 vs 판매자 승인/구매 확정 | 하나만 성공 | 관리자 승인 vs 판매자 승인·구매자 철회·구매 확정 동시 실행(`AdminCancellationReviewIntegrationTests`, main 반영 #121) | — |
