# 랜서픽 프런트엔드

React + TypeScript + Vite 개발 환경입니다. 상품·거래·로그인 경로에 안내 화면을 연결했습니다. 업무 기능과 인증은 아직 구현하지 않았습니다.

## 설치 기준

- Node.js 24.22.1 / npm 11.19.1
- React / React DOM 19.3.0
- Vite 8.3.5 / TypeScript 5.9 계열
- React Router 7.18.5 / TanStack Query 5.105.0

정확한 설치 결과는 package-lock.json으로 관리합니다. 앱 진입점에서 라우터와 QueryClientProvider를 연결합니다. 변경 요청의 자동 재시도는 비활성화합니다.

## 소스 구조

React 공통 기반·API 연결 방식은 황준범이 주담당하고 정은찬(조장)이 통합을 조율합니다. 김창재는 인증·운영, 박소현은 판매자·상품·후기, 정은찬은 주문, 황준범은 적립금·결제·정산의 기능별 화면·연결·테스트도 책임집니다. 각 기능 담당자가 직접 구현하며 공통 담당자는 예제와 어려운 로직을 지원합니다. [확정 역할 배정](../docs/work-plan.md#확정-역할-배정)을 따릅니다.

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
    api/client.ts              # 공통 HTTP 처리 구현 위치
    components/                # 공통 레이아웃·UI
    utils/                     # 공통 금액·날짜 표시 등
  main.tsx                     # 앱 실행
  index.css                    # 전역 스타일
```

auth와 products도 orders와 같은 하위 구조를 사용합니다. api.ts·types.ts·공통 client.ts는 구현 위치만 준비한 상태입니다. 실제로 반복되는 로직이 생기면 hooks를, 조회·갱신 로직이 늘면 queries.ts를 분리합니다. 업무 화면의 스타일은 해당 컴포넌트 옆에 *.module.css로 둡니다.

현재 경로는 / (상품), /orders (거래), /login (로그인)입니다. 거래 화면은 안내만 표시하며 실제 주문 데이터를 요청하지 않습니다. 인증 및 거래 접근 제어는 구현 시 별도로 연결합니다.

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

```powershell
npm.cmd run typecheck
npm.cmd run lint
npm.cmd run build
```

## 백엔드 연결 준비

Vite는 /api 요청을 http://localhost:8080 으로 전달합니다. 백엔드는 별도로 실행해야 합니다. 프런트 코드에서는 /api 상대 경로를 사용합니다. 이 프록시는 개발용이며 배포 환경은 Nginx 등에서 별도 설정합니다.

백엔드 연결·세션 로그인·CSRF 검증은 아직 수행하지 않았습니다. JDK는 백엔드 기준인 25를 따르며 React 실행에는 Node.js를 사용합니다.

package.json과 package-lock.json은 함께 커밋합니다. node_modules와 dist, 실제 환경변수 비밀값은 커밋하지 않습니다.
