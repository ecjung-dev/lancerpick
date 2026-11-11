import assert from 'node:assert/strict'
import { after, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Routes, Route } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-product-detail',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { ProductDetailPage } = await server.ssrLoadModule('/src/features/products/pages/ProductDetailPage.tsx')
const { PublicSellerPage } = await server.ssrLoadModule('/src/features/seller/pages/PublicSellerPage.tsx')
const { ApiError } = await server.ssrLoadModule('/src/shared/api/client.ts')
after(async () => { await server.close() })

test('direct detail route displays the matching product, escapes text and formats UTC in KST', () => {
  const id = '9007199254740993'
  const client = new QueryClient()
  client.setQueryData(['products', 'detail', id], {
    id, title: '상세 테스트', priceAmount: 50000, durationDays: 3,
    category: { id: '1', name: '개발', displayOrder: 0 }, seller: { id: '2', displayName: '판매자' },
    description: '<script>alert(1)</script>\n둘째 줄', scopeDescription: '작업 범위 원문',
    preparationDescription: '준비사항 원문', version: 2, saleStatus: 'ON_SALE',
    createdAt: '2026-11-04T00:00:00Z', updatedAt: '2026-11-04T01:00:00Z',
  })
  try {
    const html = renderToStaticMarkup(createElement(MemoryRouter,
      { initialEntries: [{ pathname: `/products/${id}`, state: { from: '/?keyword=test&page=2' } }] },
      createElement(QueryClientProvider, { client }, createElement(Routes, null,
        createElement(Route, { path: '/products/:id', element: createElement(ProductDetailPage) })))))
    assert.match(html, /상세 테스트/)
    assert.match(html, /작업 범위 원문/)
    assert.match(html, /준비사항 원문/)
    assert.match(html, /50,000원/)
    assert.match(html, /&lt;script&gt;alert\(1\)&lt;\/script&gt;/)
    assert.doesNotMatch(html, /<script>/)
    assert.match(html, /오전 9:00/)
    assert.match(html, /href="\/\?keyword=test&amp;page=2"/)
    assert.match(html, /href="\/sellers\/2"/)
  } finally { client.clear() }
})

test('public seller shows the scoped product page and escapes profile text', () => {
  const id = '9007199254740993'
  const client = new QueryClient()
  const search = { keyword: '', categoryId: '', sellerId: id, page: 1, size: 8 }
  client.setQueryData(['public-seller-profile', id], {
    id, displayName: '공개 판매자', introduction: '<script>소개</script>',
    specialty: '디자인', portfolioUrl: 'javascript:alert(1)',
  })
  client.setQueryData(['products', search], {
    items: [{ id: '9', title: '판매자 상품', priceAmount: 10000, durationDays: 2,
      seller: { id, displayName: '공개 판매자' }, category: { id: '1', name: '디자인' } }],
    page: 1, size: 8, totalElements: 9, totalPages: 2,
  })
  try {
    const html = renderToStaticMarkup(createElement(MemoryRouter,
      { initialEntries: [`/sellers/${id}?page=1`] },
      createElement(QueryClientProvider, { client }, createElement(Routes, null,
        createElement(Route, { path: '/sellers/:id', element: createElement(PublicSellerPage) })))))
    assert.match(html, /공개 판매자/)
    assert.match(html, /판매자 상품/)
    assert.match(html, /2페이지 \/ 총 2페이지/)
    assert.match(html, /&lt;script&gt;소개&lt;\/script&gt;/)
    assert.doesNotMatch(html, /javascript:|<script>/)
  } finally { client.clear() }
})

test('public product detail replaces the order link with the active contract minimum notice', () => {
  const client = new QueryClient()
  client.setQueryData(['products', 'detail', '1'], {
    id: '1', title: '저가 상품', priceAmount: 9999, minimumOrderAmount: 10000, durationDays: 2,
    category: { name: '개발' }, seller: { id: '2', displayName: '판매자' },
    createdAt: '2026-11-04T00:00:00Z', updatedAt: '2026-11-04T00:00:00Z',
  })
  try {
    const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/products/1'] },
      createElement(QueryClientProvider, { client }, createElement(Routes, null,
        createElement(Route, { path: '/products/:id', element: createElement(ProductDetailPage) })))))
    assert.match(html, /저가 상품/)
    assert.match(html, /계약 주문은 10,000원 이상/)
    assert.doesNotMatch(html, /href="\/products\/1\/order"/)
  } finally { client.clear() }
})

test('missing detail hides cached content and server failure offers retry with a safe back link', () => {
  for (const status of [404, 500]) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
    const query = client.getQueryCache().build(client, { queryKey: ['products', 'detail', '1'] })
    query.setState({ status: 'error', fetchStatus: 'idle', error: new ApiError(status, { code: 'RESOURCE_NOT_FOUND', message: '조회 오류', fieldErrors: [], traceId: 'test' }) })
    try {
      const html = renderToStaticMarkup(createElement(MemoryRouter,
        { initialEntries: [{ pathname: '/products/1', state: { from: '//external.invalid' } }] },
        createElement(QueryClientProvider, { client }, createElement(Routes, null,
          createElement(Route, { path: '/products/:id', element: createElement(ProductDetailPage) })))))
      assert.match(html, /href="\/"/)
      assert.doesNotMatch(html, /external.invalid|고정 가격/)
      assert.match(html, status === 404 ? /현재 조회할 수 없는 상품/ : /상품을 불러오지 못했어요/)
      if (status === 500) assert.match(html, /다시/)
    } finally { client.clear() }
  }
})
