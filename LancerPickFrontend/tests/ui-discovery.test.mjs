import assert from 'node:assert/strict'
import { after, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-discovery',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { AccountLayout } = await server.ssrLoadModule('/src/features/account/components/AccountLayout.tsx')
const { ProductListPage } = await server.ssrLoadModule('/src/features/products/pages/ProductListPage.tsx')
const { AppLayout } = await server.ssrLoadModule('/src/shared/components/AppLayout.tsx')
const { ApiError } = await server.ssrLoadModule('/src/shared/api/client.ts')
after(async () => server.close())
const member = { id: '7', displayName: '판매회원', hasSellerProfile: true, email: 'a@example.test', role: 'USER' }
function render(path, Component, props = {}, setup = () => {}) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(['auth', 'me'], member)
  setup(client)
  try {
    return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [path] },
      createElement(QueryClientProvider, { client }, createElement(Component, props))))
  } finally { client.clear() }
}

test('general and seller destinations are separate and seller routes choose seller navigation', () => {
  const general = render('/mypage', AccountLayout)
  assert.match(general, /href="\/orders\?side=BUYER"/)
  assert.match(general, /href="\/mypage\/profile"/)
  assert.doesNotMatch(general, /href="\/orders\?side=SELLER"|href="\/mypage\/settlements"/)
  for (const path of ['/mypage/products', '/mypage/products/new', '/orders?side=SELLER', '/mypage/settlements']) {
    const seller = render(path, AccountLayout)
    assert.match(seller, /href="\/mypage\/seller-profile" aria-current="page"|href="\/mypage\/seller-profile"/)
    assert.match(seller, /href="\/orders\?side=SELLER"/)
    assert.match(seller, /href="\/mypage\/settlements"/)
    assert.doesNotMatch(seller, /href="\/orders\?side=BUYER"/)
  }
})

test('missing seller profile keeps registration available and disables selling entry points', () => {
  const html = render('/mypage/seller-profile', AccountLayout, {}, c => c.setQueryData(['auth', 'me'], { ...member, hasSellerProfile: false }))
  assert.match(html, /판매자 프로필을 등록하면/)
  assert.match(html, /href="\/mypage\/seller-profile"/)
  assert.equal((html.match(/aria-disabled="true"/g) ?? []).length, 4)
  assert.doesNotMatch(html, /href="\/mypage\/products|href="\/orders\?side=SELLER"|href="\/mypage\/settlements"/)
})

test('a failed member recheck hides cached names and does not enable seller links', () => {
  const html = render('/mypage/seller-profile', AccountLayout, {}, c => {
    c.getQueryCache().find({ queryKey: ['auth', 'me'] }).setState({ status: 'error', error: new ApiError(401, { code: 'AUTHENTICATION_REQUIRED', message: '로그인 필요' }) })
  })
  assert.doesNotMatch(html, /판매회원|href="\/mypage\/products/)
  assert.match(html, /회원 정보를 확인하지 못했습니다/)
})

test('catalog is a dedicated public page with 12-item pagination and retained filter values', () => {
  const html = render('/products?minPrice=10000&sort=PRICE_ASC&page=1', ProductListPage, { view: 'catalog' }, c => {
    c.setQueryData(['categories'], [])
    c.setQueryData(['products', { keyword: '', categoryId: '', page: 1, size: 12,
      minPrice: '10000', maxPrice: undefined, maxDurationDays: undefined, sort: 'PRICE_ASC' }],
      { items: [], page: 1, size: 12, totalElements: 30, totalPages: 3 })
  })
  assert.match(html, /<h1>서비스 둘러보기<\/h1>/)
  assert.match(html, /30개의 서비스/)
  assert.match(html, /현재 요청 2페이지/)
  assert.match(html, /value="10000"/)
  assert.doesNotMatch(html, /hero-title|HOW IT WORKS/)
})

test('home and global navigation expose the dedicated product catalog', () => {
  assert.match(render('/', ProductListPage), /href="\/products"/)
  const header = render('/', AppLayout, {}, c => c.setQueryData(['auth', 'me'], null))
  assert.match(header, /href="\/products"/)
})
