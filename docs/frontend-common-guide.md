# 프런트엔드 기능 개발 가이드

2026-11-04 최신화: 실제 메인은 공개 상품·카테고리 API를 사용한다. 아래 demo 주소·queryKey는 공통 기반 작성 당시의 학습 예제다. 현재 구현은 features/products/api.ts·queries.ts를 확인한다. 7절 결제 예제도 설명용이며 실제 구현은 features/orders/api.ts와 [거래 금융 화면](order-finance-ui.md)을 따른다. 주문·정산 조회 키는 wallet 하위다. 메뉴·구매/판매 URL 상태·CI 검사 범위는 [거래 통합](account-trade-integration.md)을 따른다.

처음에는 **1~6절의 상품 조회**만 따라 읽으세요. 조회 흐름이 이해되면 **7절의 결제 요청**으로 넘어갑니다. 예제마다 현재 실행되는 코드인지, 앞으로 구현할 설명용 코드인지 구분했습니다.

기획과 담당 배정은 기존 문서를 따릅니다. 이 문서는 그 내용을 코드로 옮기는 방법을 설명합니다.

## 목차

1. 무엇을 직접 만들고 무엇을 재사용하나요?
2. 화면 실행하기
3. 요청이 이동하는 길 이해하기
4. 상품 조회: 타입 → API 함수 → 조회 훅 → 화면
5. 로딩·오류·빈 결과 구분하기
6. 검색·페이지·화면 이동 이해하기
7. 결제: 변경 요청과 useMutation
8. 다른 기능으로 옮겨 적용하기
9. 오류를 찾는 순서와 작업 완료 확인

## 1. 무엇을 직접 만들고 무엇을 재사용하나요?

예를 들어 상품 조회와 결제는 서로 다른 기능입니다. 그래서 `getProducts`, `payOrder` 같은 함수는 각각 만듭니다. 하지만 쿠키를 보내고 서버 오류를 읽는 코드는 공통으로 사용합니다.

| 구분 | 파일 | 하는 일 |
| --- | --- | --- |
| 공통 | `src/shared/api/client.ts` | HTTP 요청, 세션 쿠키, CSRF, 204 응답, 공통 오류 처리 |
| 공통 | `src/shared/api/types.ts` | 목록 응답 `PageResponse<T>` |
| 공통 | `src/shared/components/` | Button, Input, AsyncState, Icon, AppLayout |
| 공통 | `src/shared/lib/` | 금액·시간 표시, 일반 문자열 정규화 |
| 기능별 | `src/features/기능/types.ts` | 해당 기능의 요청·응답 타입 |
| 기능별 | `src/features/기능/api.ts` | 해당 API의 주소·HTTP 메서드·전송값 |
| 기능별 | `src/features/기능/queries.ts` | 조회·변경 훅과 캐시 갱신 |
| 기능별 | `src/features/기능/pages/` | 화면 구성, 사용자 입력, 결과 표시 |
| 기능별 | `src/features/기능/components/` | 해당 기능에서 사용하는 카드·폼 등 |

**기능을 만들 때마다 client.ts를 복사하지 않습니다.** 기능별 `api.ts`에서 공통 함수를 import합니다. 공통 정책을 바꿔야 한다면 관련 담당자와 먼저 확인합니다.

`queries.ts`는 조회·변경 훅을 모으는 현재 예제의 파일 이름입니다. 파일이 커지면 기능 안에서 나눌 수 있으며, 모든 API에 무조건 파일 하나씩을 만들 필요는 없습니다.

## 2. 화면 실행하기

### 2-1. 백엔드 실행

IntelliJ에서 백엔드를 평소처럼 기본 실행합니다. 기본 포트는 8080이며 기존 개인 DB 설정은 필요합니다. 메인 예시 조회를 위한 추가 프로필 설정과 로그인은 필요 없습니다.

기본 실행과 DB 없는 선택 실행의 차이는 [백엔드 가이드](backend-common-guide.md)를 참고하세요.

### 2-2. 프런트 실행

새 PowerShell 터미널에서 실행합니다.

```powershell
cd C:\project\LancerPickFrontend
npm.cmd ci
npm.cmd run dev
```

`npm ci`는 최초 실행 또는 의존성 잠금 파일 변경 시 사용합니다. 이후에는 `npm.cmd run dev`만 실행하면 됩니다.

브라우저 주소는 `http://localhost:5173`입니다. 종료는 각 서버 터미널의 `Ctrl+C`입니다.

### 2-3. 백엔드 포트가 8081일 때만

```powershell
$env:API_PROXY_TARGET = "http://127.0.0.1:8081"
npm.cmd run dev
```

8080으로 돌아올 때는 같은 터미널에서 설정을 지운 뒤 Vite를 다시 실행합니다.

```powershell
Remove-Item Env:API_PROXY_TARGET -ErrorAction SilentlyContinue
npm.cmd run dev
```

이 환경 변수는 Vite 서버에서만 읽습니다. API 주소를 화면 파일마다 바꾸지 않습니다.

## 3. 요청이 이동하는 길 이해하기

화면에서 상품 목록을 요청하면 다음 순서로 이동합니다.

```text
ProductListPage: 어떤 상태를 보여줄지 결정
    ↓
useProducts: 조회 실행·로딩·오류·캐시 관리
    ↓
getProducts: 상품 API 주소와 검색 조건 구성
    ↓
apiRequest: 실제 HTTP 요청과 공통 처리
    ↓
Vite 프록시: 5173의 /api 요청을 백엔드 8080으로 전달
    ↓
Spring Controller → Service → 응답 DTO
    ↓
JSON 응답 → useProducts의 data → 화면 표시
```

현재 메인은 실제 HTTP 통신으로 **서버의 예시 데이터**를 받습니다. 아직 상품 DB 조회를 구현한 것은 아닙니다.

여기서 `await`는 해당 비동기 작업의 결과를 기다리게 합니다. 여러 사용자의 요청을 서버 전체에서 순차 처리하게 만드는 기능은 아닙니다.

## 4. 상품 조회: 타입 → API 함수 → 조회 훅 → 화면

이 절은 **현재 구현된 조회 흐름**을 설명합니다. 기존 파일을 통째로 덮어쓰지 말고, 각 파일에서 해당 부분을 찾아 읽으세요.

### 4-1. 먼저 백엔드와 약속한 응답을 확인합니다

현재 예시 API는 다음 요청을 받습니다.

```text
GET /api/demo/products?page=0&size=4
```

응답 형태는 아래와 같습니다. 이해를 위해 items를 한 건만 표시했습니다.

```json
{
  "items": [
    {
      "id": "108",
      "title": "브랜드의 첫인상을 만드는 로고 디자인",
      "priceAmount": 150000,
      "durationDays": 5,
      "category": { "id": "1", "name": "디자인", "displayOrder": 1 },
      "seller": { "id": "9108", "displayName": "스튜디오 모노" }
    }
  ],
  "page": 0,
  "size": 4,
  "totalElements": 8,
  "totalPages": 2
}
```

응답 필드 이름을 임의로 변경하면 화면이 값을 찾지 못합니다. 실제 API의 주소·권한·필드 기준은 [API 명세](api-spec.md)를 먼저 확인합니다.

### 4-2. types.ts: 데이터 모양을 정의합니다

파일: `src/features/products/types.ts`

```ts
export interface Category {
  id: string
  name: string
  displayOrder: number
}

export interface ProductSummary {
  id: string
  title: string
  priceAmount: number
  durationDays: number
  category: Category
  seller: { id: string; displayName: string }
}

export interface ProductSearch {
  keyword: string
  categoryId: string
  page: number
  size: number
}
```

`string`은 문자열, `number`는 숫자입니다. ID는 숫자처럼 보여도 우리 API에서는 문자열로 받습니다. 금액은 원 단위 정수로 받습니다.

공통 `PageResponse<ProductSummary>`는 **items가 ProductSummary 배열인 목록 응답**이라는 뜻입니다. 목록 형태를 기능마다 다시 정의할 필요는 없습니다.

타입은 개발 중 실수를 찾는 도구입니다. 실제 서버 응답을 자동으로 검사하거나 잘못된 값을 고쳐 주지는 않습니다.

### 4-3. api.ts: 주소와 요청값을 정합니다

파일: `src/features/products/api.ts`. 현재 함수의 핵심 부분입니다.

```ts
import { apiRequest } from '../../shared/api/client'
import type { PageResponse } from '../../shared/api/types'
import type { ProductSearch, ProductSummary } from './types'

export function getProducts(search: ProductSearch, signal?: AbortSignal) {
  const params = new URLSearchParams({
    page: String(search.page),
    size: String(search.size),
  })

  if (search.keyword) params.set('keyword', search.keyword)
  if (search.categoryId) params.set('categoryId', search.categoryId)

  return apiRequest<PageResponse<ProductSummary>>(
    `/demo/products?${params}`,
    { signal },
  )
}
```

하나씩 읽어보면 다음 뜻입니다.

| 코드 | 의미 |
| --- | --- |
| `search` | 화면에서 선택한 검색어·카테고리·페이지 |
| `URLSearchParams` | 한글·공백·특수문자를 포함한 검색 조건을 URL 쿼리로 구성 |
| `apiRequest<응답타입>` | 공통 요청 함수를 사용하고 기대 응답 타입 지정 |
| `signal` | 더 이상 필요 없는 조회를 취소할 수 있는 신호 |
| `return` | 요청 결과를 호출한 쪽으로 전달 |

`signal?`의 물음표는 선택 입력이라는 뜻입니다. 조회 훅이 신호를 전달하면 공통 fetch까지 이어집니다. 조회 취소는 서버의 이미 실행된 변경 작업을 되돌리는 기능이 아닙니다.

**공통 함수가 /api를 붙입니다.** 여기서 `/api/demo/products`라고 쓰면 `/api/api/demo/products`가 되므로 `/demo/products`만 작성합니다.

HTTP 메서드를 생략하면 GET입니다. 상품 주소는 getProducts가 알고 있으므로 화면은 `getProducts("주소")`처럼 호출하지 않습니다.

### 4-4. queries.ts: 조회 상태를 관리합니다

파일: `src/features/products/queries.ts`

```ts
import { useQuery } from '@tanstack/react-query'
import { getProducts } from './api'
import type { ProductSearch } from './types'

export function useProducts(search: ProductSearch) {
  return useQuery({
    queryKey: ['demo', 'products', search],
    queryFn: ({ signal }) => getProducts(search, signal),
  })
}
```

`useProducts`는 이 프로젝트에서 정의한 사용자 정의 훅입니다. 안에서 TanStack Query의 `useQuery`를 사용합니다.

- `queryFn`: 실제로 데이터를 가져올 함수입니다.
- `queryKey`: 조회 결과를 구분하는 이름표입니다.
- `search`를 이름표에 포함하므로 카테고리·검색어·페이지가 다른 결과를 구분합니다.

캐시는 이전에 받은 결과를 보관하는 것입니다. 서버에 데이터가 저장됐다는 뜻은 아닙니다. 현재 공통 설정은 조회 결과를 기본 30초간 최신 상태로 간주하고 자동 재시도를 끕니다. 명시적 재조회·캐시 무효화는 별도로 할 수 있습니다.

### 4-5. 페이지에서 훅을 사용합니다

아래는 현재 훅·컴포넌트를 이용한 **설명용 축약 화면**입니다. 실제 메인의 검색·카테고리·페이지 UI는 생략했습니다.

```tsx
import { useProducts } from '../queries'
import { AsyncState } from '../../../shared/components/AsyncState'
import { formatWon } from '../../../shared/lib/format'

export function ProductListExample() {
  const products = useProducts({
    keyword: '',
    categoryId: '',
    page: 0,
    size: 4,
  })

  if (products.isPending) {
    return <AsyncState title="상품을 불러오고 있습니다." />
  }

  if (products.isError) {
    return (
      <AsyncState
        title="상품을 불러오지 못했습니다."
        error={products.error}
        onRetry={() => void products.refetch()}
      />
    )
  }

  if (products.data.items.length === 0) {
    return <AsyncState title="조건에 맞는 상품이 없습니다." />
  }

  return (
    <ul>
      {products.data.items.map((product) => (
        <li key={product.id}>
          {product.title} · {formatWon(product.priceAmount)}
        </li>
      ))}
    </ul>
  )
}
```

`map`은 배열의 각 상품을 화면 요소로 바꿉니다. `key`는 React가 항목을 구분하는 값이므로 고유한 상품 ID를 사용합니다.

`onRetry={() => ...}`는 클릭했을 때 실행할 함수를 전달합니다. 화면을 그리는 중에 즉시 실행하는 방식이 아닙니다. 앞의 `void`는 Promise 반환값을 사용하지 않는다는 표시이며 오류를 자동 해결하는 기능은 아닙니다.

## 5. 로딩·오류·빈 결과 구분하기

| 상황 | 예 | 화면 동작 |
| --- | --- | --- |
| 첫 조회 중 | 아직 응답을 기다림 | 로딩 표시 |
| 조회 실패 | 서버 중단·400·500 | 오류 안내, 필요한 경우 조회 재시도 |
| 조회 성공·0건 | 검색 조건에 맞는 상품 없음 | 빈 결과 안내 |
| 조회 성공·1건 이상 | 목록 도착 | 상품 표시 |

오류가 났다고 빈 배열로 바꾸면 사용자는 서버 장애를 상품 0건으로 오해합니다. 현재 메인은 서버 오류를 프런트 샘플로 대체하지 않습니다.

인증이 필요한 조회는 로그인 상태를 확인한 뒤 활성화합니다. 메인 상품 조회는 비로그인에게 공개합니다. 인증·마이페이지 화면이 아직 구현된 것처럼 표시하지 않습니다.

## 6. 검색·페이지·화면 이동 이해하기

현재 메인은 URL에 검색 조건을 보관합니다.

```text
/?keyword=웹&categoryId=2&page=1
```

여기서 `page=1`은 두 번째 페이지입니다. 우리 목록 API는 0부터 시작합니다. 화면에 표시하는 페이지 번호와 서버에 보내는 번호를 혼동하지 않습니다.

검색어·카테고리가 바뀌면 첫 페이지로 돌아갑니다. URL에 저장해 두면 새로고침과 뒤로가기에서도 조건을 읽을 수 있습니다.

`navigate`는 화면 주소를 이동시키는 React Router 함수입니다. HTTP 요청을 보내는 함수가 아닙니다.

```ts
// 설명용 예시: 실제 상세 라우트가 준비된 뒤 사용
navigate(`/products/${productId}`)
```

현재 상품 카드 클릭은 상세 API 호출·상세 라우트 이동이 아닌 예시 미리보기입니다. 새 화면을 만들면 `src/app/router.tsx`에도 경로를 등록합니다. 이미 등록된 화면에 기능을 붙일 때는 라우트를 다시 만들지 않습니다.

## 7. 결제: 변경 요청과 useMutation

여기부터는 **아직 구현되지 않은 결제 기능을 설명하는 예제**입니다. 현재 상품 파일을 덮어쓰거나 결제 API가 완성됐다고 표현하지 않습니다. 실제 인증·주문·결제 API가 준비된 뒤 연결해야 합니다.

### 7-1. 조회와 변경을 구분합니다

| 작업 | HTTP | 사용할 훅 |
| --- | --- | --- |
| 상품·주문·잔액 조회 | GET | useQuery |
| 상품 등록·결제·취소·구매 확정 | POST 또는 PATCH | useMutation |

`useMutation`은 결제 전용 기능이 아닙니다. 사용자의 행동으로 서버 데이터를 바꾸는 요청을 관리합니다. 이 훅이 DB 트랜잭션이나 중복 결제 방지를 대신하지는 않습니다.

### 7-2. 먼저 실제 계약을 확인합니다

[API 명세 5·7절](api-spec.md)에 따른 결제 계약입니다.

```text
POST /api/orders/{id}/payment
본문: { "agreedToTerms": true }
응답: 200 OrderDetail
```

결제 주소는 `/payment`입니다. 앞선 대화의 `/pay`는 설명용 주소였으며 실제 코드에는 문서 주소를 사용합니다.

구매자 ID·금액·수수료는 화면에서 보내 결정하지 않습니다. 서버가 인증 정보와 주문 DB를 기준으로 판단합니다. 200 응답은 이미 처리된 결제의 현재 주문일 수도 있으므로 무조건 새로운 결제 성공으로 안내하지 않습니다.

### 7-3. 기능 타입을 정의합니다

아래 `features/payments/`는 **추가할 수 있는 프런트 폴더 예시**이며 현재 생성돼 있지 않습니다. 주문 담당자와 파일 위치를 합의한 뒤 추가합니다. 백엔드 패키지 이름은 기존 `payment`를 유지합니다.

예시 파일: `src/features/payments/types.ts`

```ts
export interface PayOrderInput {
  orderId: string
  agreedToTerms: true
}

// 서버 OrderDetail 응답 중 이 예제에서 사용하는 일부 필드만 표현했습니다.
// 실제 연결에서는 주문 담당자의 전체 OrderDetail 타입을 함께 사용합니다.
export interface PaymentOrderView {
  id: string
  status: string
  priceAmount: number
  paidAt: string | null
}
```

`agreedToTerms: true`는 이 함수에 동의한 값만 전달하도록 제한하는 타입입니다. 화면의 체크 확인과 서버 검증도 필요합니다.

### 7-4. API 함수를 작성합니다

예시 파일: `src/features/payments/api.ts`

```ts
import { apiRequest } from '../../shared/api/client'
import type { PayOrderInput, PaymentOrderView } from './types'

export function payOrder(input: PayOrderInput) {
  return apiRequest<PaymentOrderView>(
    `/orders/${input.orderId}/payment`,
    {
      method: 'POST',
      json: { agreedToTerms: input.agreedToTerms },
    },
  )
}
```

`json`은 공통 요청 함수가 제공하는 옵션입니다. JSON 문자열 변환·Content-Type·세션 쿠키·CSRF 헤더는 공통 함수가 처리합니다. 여기서 직접 세션 쿠키를 만들거나 `JSON.stringify`를 반복하지 않습니다.

### 7-5. 변경 훅을 작성합니다

예시 파일: `src/features/payments/queries.ts`

```ts
import { useMutation, useQueryClient } from '@tanstack/react-query'
import { payOrder } from './api'

export function usePayOrder() {
  const queryClient = useQueryClient()

  return useMutation({
    mutationFn: payOrder,
    retry: false,
    onSettled: async (_data, _error, input) => {
      await Promise.all([
        queryClient.invalidateQueries({
          queryKey: ['orders', input.orderId],
        }),
        queryClient.invalidateQueries({ queryKey: ['wallet'] }),
      ])
    },
  })
}
```

- `useQueryClient`: 조회 캐시를 관리하는 객체를 가져옵니다.
- `mutationFn`: 버튼을 눌렀을 때 실행할 변경 함수입니다.
- `retry: false`: 실패한 결제를 자동으로 다시 보내지 않습니다. 현재 전역 설정도 false입니다.
- `onSettled`: 성공·실패 응답 후 실행합니다. 통신 실패는 서버 처리 실패와 다를 수 있어 주문·잔액을 확인하도록 캐시를 무효화합니다.
- `invalidateQueries`: 이전 조회 결과를 오래된 것으로 표시합니다. 현재 활성화된 해당 조회는 다시 요청되며, 결제를 다시 실행하는 기능은 아닙니다.

이 예제의 `['orders', orderId]`, `['wallet']`는 **향후 조회 훅과 맞춰 사용할 키 예시**입니다. 실제 조회 훅이 다른 키를 사용하면 해당 키로 맞춰야 합니다. 훅이 아직 없거나 화면에서 비활성 상태라면 이 코드만으로 최신 정보가 화면에 나타나지는 않습니다. 인증 만료 시에는 보호 조회를 멈추고 이전 회원 캐시도 정리해야 합니다.

### 7-6. 버튼에서 호출합니다

예시 파일: `src/features/payments/components/PaymentButton.tsx`

```tsx
import { useState } from 'react'
import { usePayOrder } from '../queries'
import { ApiError } from '../../../shared/api/client'
import { Button } from '../../../shared/components/Button'

// 실제 연결에서는 인증·주문 조회와 현재 상태 검사를 추가합니다.
export function PaymentButton({ orderId }: { orderId: string }) {
  const [agreed, setAgreed] = useState(false)
  const payment = usePayOrder()

  return (
    <div>
      <label>
        <input
          type="checkbox"
          checked={agreed}
          disabled={payment.isPending}
          onChange={(event) => setAgreed(event.target.checked)}
        />
        주문 조건을 확인하고 결제에 동의합니다.
      </label>

      <Button
        disabled={!agreed || payment.isPending}
        onClick={() => {
          if (!agreed || payment.isPending) return
          payment.mutate({ orderId, agreedToTerms: true })
        }}
      >
        {payment.isPending ? '처리 중입니다…' : '테스트 적립금으로 결제'}
      </Button>

      {payment.isSuccess && (
        <p role="status">응답을 받았습니다. 갱신된 주문 상태를 확인해 주세요.</p>
      )}

      {payment.isError && (
        <p role="alert">
          {payment.error instanceof ApiError
            && payment.error.code === 'INSUFFICIENT_BALANCE'
            ? '테스트 적립금이 부족합니다.'
            : '요청 결과와 최신 주문·잔액을 확인해 주세요.'}
        </p>
      )}
    </div>
  )
}
```

`mutate`를 호출해야 변경 요청이 실행됩니다. `onClick={payment.mutate(...)}`처럼 렌더링 중 실행하지 않습니다.

버튼 비활성화는 연속 클릭을 줄이는 편의 기능입니다. 새 탭·직접 요청도 있으므로 서버의 주문·지갑 잠금과 고유 제약이 필요합니다. 완료·취소·환불된 주문의 화면 동작은 최신 주문 상태와 오류 code를 기준으로 구현해야 하며, 위 버튼만으로 결제 화면이 완성되지는 않습니다.

## 8. 다른 기능으로 옮겨 적용하기

**호출 구조는 재사용하고, 주소·입력·응답·후속 동작은 기능에 맞게 작성합니다.**

| 기능 | 기능별로 작성할 내용 | 재사용할 내용 |
| --- | --- | --- |
| 상품 조회 | 검색 조건, 상품 타입, 목록·카드 | apiRequest, PageResponse, AsyncState |
| 상품 등록 | 등록 입력, POST 함수, 폼, 성공 후 조회 갱신 | apiRequest, Input, Button |
| 주문 수락 | 주문 ID, 명령 API, 상태별 버튼 | apiRequest, Button |
| 결제 | 동의 확인, 결제 API, 주문·잔액 갱신 | apiRequest, Button |
| 후기 | 별점·본문 입력, 작성 자격 안내 | apiRequest, Input, Button |

인증 구현 시에는 로그인·로그아웃·계정 전환 뒤 `clearCsrfToken()`을 호출하고 이전 회원의 조회 캐시를 취소·정리하는 흐름도 연결합니다. 이 함수 하나가 사용자 정보·캐시를 모두 정리하지는 않습니다.

공통 함수는 다음을 처리하지만, 기능별 권한·상태 검증과 화면 이동은 대신하지 않습니다.

- `/api` 경로 구성과 세션 쿠키 전달
- 변경 요청 전 CSRF 토큰 획득·헤더 전달
- 204처럼 본문 없는 성공 처리
- 공통 오류를 ApiError로 변환
- CSRF 만료 시 토큰 캐시 초기화. 실패한 변경 요청은 자동 재전송하지 않음

## 9. 오류를 찾는 순서와 작업 완료 확인

### 9-1. 브라우저 개발자 도구에서 확인합니다

F12 → Network → Fetch/XHR → 해당 요청을 선택합니다.

1. Request URL: `/api`가 두 번 붙지 않았나요?
2. Request Method: GET·POST·PATCH가 명세와 같나요?
3. Query String / Payload: 필드 이름·값·JSON 타입이 맞나요?
4. Status: 200·400·401·403·409·500 중 무엇인가요?
5. Response: code와 traceId는 무엇인가요?

쿠키·CSRF 토큰·비밀번호가 포함된 원본 요청 전체를 공유 채팅이나 PR에 붙이지 않습니다.

| 상태 | 먼저 확인할 것 |
| --- | --- |
| 서버 연결 실패·502 | 백엔드 실행, 포트, 프록시 대상 |
| 400 | 필수값·타입·길이·범위·페이지 |
| 401 | 인증 필요·세션 만료 여부 |
| 403 | 권한 부족인지 CSRF_INVALID인지 code 확인 |
| 404 | 경로·리소스 존재 여부. 비공개 타인 자원도 404 가능 |
| 409 | 잔액·주문 상태·버전·중복 조건 |
| 500 | traceId로 백엔드 로그 확인 |

### 9-2. 제출 전에 확인합니다

```powershell
npm.cmd run typecheck
npm.cmd run lint
npm.cmd run test:api
npm.cmd run build
```

`test:api`는 Node 24 내장 테스트 러너로 공통 요청 함수를 검사합니다. 실제 API·DB·브라우저 기능 테스트를 대신하지 않습니다.

- 정상·로딩·빈 결과·오류를 직접 확인했나요?
- 검색·페이지가 있다면 조건 변경과 뒤로가기를 확인했나요?
- 변경 요청 후 관련 조회가 갱신되나요?
- 로그인 필요 기능과 공개 기능을 구분했나요?
- 확인한 결과와 아직 미구현인 부분을 PR에 구분했나요?

현재 실제 구현에는 주문 조회·수락/거절·결제·직접 취소·확정 화면, 지갑 세션 확인 후 조회, 정산·토스 충전 화면도 포함됩니다. 로그인·가입 화면 및 실제 토스 카드 검증은 남아 있습니다. 예제와 현재 코드가 다르면 위 현황표와 기능 문서를 확인합니다. Git 절차는 [팀 Git 협업 규칙](git-workflow.md)을 따릅니다.
