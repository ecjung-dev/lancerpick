# 프런트엔드 공통 개발 가이드

2026-11-04. 현재 구현을 기준으로 관리한다. 기능 완료 여부는 [현재 현황](current-status.md), 업무 계약은 [API 명세](api-spec.md), 협업 절차는 [Git 규칙](git-workflow.md)을 따른다.

## 기능별 구조

| 위치 | 책임 |
| --- | --- |
| src/app | 라우팅·QueryClient Provider |
| src/features/기능/types.ts | 요청·응답·기능 타입 |
| src/features/기능/api.ts | 공통 apiRequest를 사용하는 기능별 HTTP 통신 |
| src/features/기능/queries.ts | 조회 훅·queryKey·재조회·캐시 정책 |
| src/features/기능/pages | 화면 구성·입력 상태·이벤트 처리 |
| src/features/기능/components | 기능 전용 UI |
| src/shared/api | HTTP·CSRF·공통 오류·페이지 응답·계정 캐시 제거 정책 |
| src/shared/components | 레이아웃·입력·버튼·상태 안내 |
| src/shared/lib | 금액·시간·문자열·URL 페이지 처리 |

같은 조회를 여러 화면에서 사용하면 기능 queries.ts를 재사용한다. 변경 이벤트의 입력·진행 상태·화면 이동은 화면에서 관리할 수 있다. 변경 API와 검증 로직은 기능 파일에서 재사용하고, 필요한 경우 훅으로 분리한다. 사용하지 않는 components 폴더나 빈 공통화를 미리 추가하지 않는다.

## 실제 조회 흐름

상품 목록은 products/api.ts와 queries.ts, 상품 상세는 useProductDetail을 사용한다. 주문 목록·상세·작성 진입은 orders/queries.ts, 판매자 프로필은 seller/queries.ts, 지갑은 wallet/queries.ts, 정산은 settlements/queries.ts를 사용한다. 화면에서 API 주소를 직접 작성하지 않는다.

개발 시 프런트 경로는 http://localhost:5173, API 프록시 기본 대상은 http://127.0.0.1:8080이다. 프런트 디렉터리에서 npm ci 후 npm run dev로 실행한다. 서버 실행·개인 DB 설정은 [설정 안내](setup-guide.md)를 따른다.

### 2-3. 백엔드 포트가 8081일 때만

PowerShell에서 API_PROXY_TARGET 환경변수를 http://127.0.0.1:8081로 지정하고 Vite를 다시 실행한다. 기본 포트로 돌아오면 해당 환경변수를 제거한다. vite.config.ts의 설정을 기능별 API 주소로 복제하지 않는다.

## 인증·개인 캐시

- auth/api.ts의 getMemberSession은 현재 회원을 조회하고 401을 그대로 전달한다. 지갑·판매자·주문 진입의 회원 확인은 이 API를 재사용한다.
- getCurrentMember는 헤더의 익명 상태를 표현하기 위해 401만 null로 변환한다. 나머지 실패는 호출자에 전달한다.
- 회원 데이터의 공통 타입은 auth/types.ts의 Member다. 일부 필드만 필요한 기능은 Pick으로 정의한다.
- 헤더·지갑·주문 작성·판매자 화면의 조회 키와 재확인 조건은 기능에 맞게 유지한다. 기능별 재확인을 없애거나 401을 임의 데이터로 대체하지 않는다.
- 로그인·로그아웃 성공은 resetAccountState, 불명확한 결과는 recheckSession을 사용한다. 진행 중인 이전 회원 조회를 먼저 취소한다.
- shared/api/accountCache.ts는 auth 이외의 조회 취소·제거 정책을 관리한다. 인증 계층이 회원 상태를 갱신하고 wallet/cache.ts가 충전 진행 정보도 정리한다.
- 주문·정산의 wallet 하위 키는 기존 로그아웃 계약으로 유지한다. 키를 변경할 때 관련 취소·갱신·제거와 회귀 검사까지 함께 수정한다.

## API·입력·화면 규칙

ID는 문자열로 보존한다. 금액·권한·주문 상태는 서버가 최종 판단한다. 화면은 API 엔티티 대신 정의된 응답 타입을 사용한다. 로딩·401·404·서버 오류·빈 목록을 구분하고 통신 오류를 빈 결과로 바꾸지 않는다.

API 변경 요청은 자동 재전송하지 않는다. 처리 중 중복 클릭을 막고, 결과 불명확 시 서버 상태를 확인한다. 동일 주문 생성 재확인은 기존 요청 키와 입력을 보존한다. 주문 행동은 allowedActions로 표시하고 서버도 권한·상태를 재검증한다.

목록의 선택·페이지는 URL로 유지한다. 기능 내 상태·입력은 React 상태로 관리하고 서버 데이터를 별도 전역 상태에 복제하지 않는다. 큰 화면은 실제로 구분되는 UI 단위로 분리하고 압축된 JSX를 작성하지 않는다.

## 완료 확인

프런트 디렉터리에서 npm run test:api, npm run lint, npm run build를 실행한다. 기존 테스트 파일은 tests/*.test.mjs로 CI에 포함된다. React 렌더링 검사와 실제 브라우저 검증은 PR에서 구분한다. API 계약·화면 동선·캐시 키를 바꿨다면 관련 테스트를 확인한다.
