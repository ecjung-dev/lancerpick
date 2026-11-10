# 랜서픽 프런트엔드

React 19.3.0 + TypeScript + Vite 기반 화면입니다. 상품/판매자·회원/인증·구매/판매 주문·계약/PDF·지갑/수익·알림·주문 채팅·관리자 상품/문의/금융/운영 현황/알림 실패 화면이 라우터에 연결돼 있습니다. 관리자 회원 관리 화면은 main에 없으며 로컬 브랜치 기록으로 구분합니다. 현재 메뉴·도메인 규칙은 [기능 명세](../docs/features.md), API/계정 경계는 [시스템 설계](../docs/architecture.md), 기능별 구현·검증 한계는 [진행 현황](../docs/project-progress.md)를 따릅니다. 화면 코드 존재와 실서버 종단 검증 완료를 구분합니다.

## 설치 기준

- Node.js 24.22.1 / npm 11.19.1
- React / React DOM 19.3.0
- Vite 8.3.5 / TypeScript 5.9 계열
- React Router 7.18.5 / TanStack Query 5.105.0

정확한 설치 결과는 package-lock.json으로 관리합니다. 앱 진입점에서 라우터와 QueryClientProvider를 연결합니다. 변경 요청의 자동 재시도는 비활성화합니다.

## 소스 구조

React 공통 기반·API 연결 방식은 황준범이 주담당하고 정은찬(조장)이 통합을 조율합니다. 김창재는 인증·운영, 박소현은 판매자·상품·후기, 정은찬은 주문, 황준범은 적립금·결제·정산의 기능별 화면·연결·테스트도 책임집니다. 각 기능 담당자가 직접 구현하며 공통 담당자는 예제와 어려운 로직을 지원합니다. [확정 역할 배정](../docs/team-roles.md#b-1131-0)을 따릅니다.

```text
src/
  app/
    router.tsx                 # 전체 화면 경로
    providers.tsx              # 공통 Provider
  features/
    auth/                      # 인증
    products/                  # 상품
    orders/                    # 거래
      pages/                   # URL에 연결되는 화면
      components/              # 해당 기능의 UI 조각
      api.ts                   # 해당 기능의 API 요청
      types.ts                 # 요청·응답 타입
  shared/
    api/client.ts              # 쿠키·CSRF·204·공통 오류 처리
    api/types.ts               # 공통 목록 응답 타입
    components/                # 공통 레이아웃·UI
    lib/                       # 금액·한국 시간 표시·문자열 정규화
    utils/                     # 추가 유틸리티용 빈 폴더
  main.tsx                     # 앱 실행
  index.css                    # 전역 스타일
```

auth와 products도 orders와 같은 기능별 구조를 사용합니다. 공통 client.ts와 types.ts는 구현되어 있고 products에는 예시 조회용 api.ts·types.ts·queries.ts·카드·미리보기가 있습니다. auth에는 로그인·가입·로그아웃 api.ts·types.ts·queries.ts·session.ts·validation.ts가 있습니다. 공통 코드를 복사하지 말고 기능에서 재사용합니다. 업무 화면의 스타일은 해당 컴포넌트 옆에 *.module.css로 둡니다.

현재 `/`는 `/api/categories`, `/api/products`에서 실제 목록을 받아 검색·카테고리·페이지·상품 요약을 표시합니다. DB가 없는 demo 프로필로 자동 전환하지 않으며 실패 시 오류를 표시합니다. 상품 카드는 목록 응답의 요약을 보여주고 `/products/:id` 상세로 이동합니다. 상세에서 `/products/:id/order` 주문 작성 화면으로 연결됩니다. 운영 로그인부터 주문 생성·상세 이동까지의 브라우저/DB 통합 검증은 별도입니다. [현재 구현·검증 현황](../docs/project-progress.md#current-status)을 참고합니다.

## 팀원 실행

이 폴더에서 PowerShell로 실행합니다. 프로젝트 생성 명령을 다시 실행하지 않습니다.

저장소 루트에서 시작한다면 먼저 `cd .\LancerPickFrontend`로 이동합니다. Node.js 24와 npm 설치가 필요합니다.

```powershell
npm.cmd ci
npm.cmd run dev
```

브라우저에서 http://localhost:5173 으로 접속합니다. 종료는 터미널에서 Ctrl+C입니다.

최초 설치 또는 의존성 변경 때는 `npm ci`, 이후 실행은 `npm run dev`만 사용하면 됩니다. PowerShell 실행 정책 오류가 없다면 `npm.cmd` 대신 `npm`을 사용할 수 있습니다. 포트 충돌 시 기존 개발 서버를 먼저 종료합니다. 백엔드는 별도 터미널에서 실행합니다. 전체 순서는 [저장소 실행 안내](../README.md)를 참고합니다.

## 검사

판매자 프로필 관리는 `/mypage/seller-profile`에서 서버 세션 확인 후 조회·등록·수정합니다. 미등록 404는 빈 폼, 인증 만료는 로그인 안내, 통신 오류는 오류로 구분합니다. 마이페이지 메뉴에도 연결했으며 [구현·브라우저 검증 범위](../docs/features.md#products-seller)를 참고합니다. 로그인·회원가입 화면은 구현됐고, 운영 로그인에서 판매자 프로필까지의 브라우저 통합 검증은 별도입니다.

내 정보는 `/mypage/profile`에서 확인합니다. 표시 이름만 `PATCH /api/me`로 바꾸며, 저장 직전에 현재 세션이 같은 계정인지 다시 확인하고 성공하면 헤더 이름도 바로 바뀝니다. 마이페이지 메뉴의 "내 정보"와 연결했으며 [구현·검증 범위](../docs/features.md#b-217-0)를 참고합니다.

```powershell
npm.cmd run test:api
npm.cmd run typecheck
npm.cmd run lint
npm.cmd run build
```

## 백엔드 연결 준비

주문 상세의 채팅은 `/ws` 네이티브 WebSocket/STOMP를 사용합니다. Vite가 `/api`와 같은 `API_PROXY_TARGET`으로 연결하며, 세션·CSRF는 기존 로그인에서 가져옵니다. 의존성 변경 후 `npm ci`와 백엔드 재시작이 필요합니다. [연결 계약·로컬 두 계정 확인 방법](../docs/features.md#communication-chat)을 참고합니다.

Vite는 /api 요청을 기본 http://127.0.0.1:8080 으로 전달합니다. 포트 변경 시 셸의 API_PROXY_TARGET을 사용하고 [프런트 가이드](../docs/architecture.md#b-468-0)를 따릅니다. 백엔드는 별도로 실행해야 합니다. 프런트 코드에서는 /api 상대 경로를 사용합니다. 이 프록시는 개발용이며 배포 환경은 Nginx 등에서 별도 설정합니다.

공통 요청의 CSRF·204·오류·중복 토큰 조회 등의 동작은 `test:api`로 검사합니다. 로그인·로그아웃 뒤 CSRF·캐시 정리는 `resetAccountState()`로 연결했고 `tests/auth.test.mjs`로 검사합니다. 자동 테스트 통과가 브라우저·실제 DB의 종단 간 검증 완료를 뜻하지는 않습니다. 현재 CI 워크플로는 test:api를 실행합니다. 실제 성공 여부는 해당 커밋의 Actions 결과를 확인합니다. JDK는 백엔드 기준인 25를 따르며 React 실행에는 Node.js를 사용합니다. 자세한 구현 방법은 [프런트 공통 가이드](../docs/architecture.md#environment)를 참고합니다.

package.json과 package-lock.json은 함께 커밋합니다. node_modules와 dist, 실제 환경변수 비밀값은 커밋하지 않습니다.
