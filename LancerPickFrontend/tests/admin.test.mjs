import assert from 'node:assert/strict'
import { after, afterEach, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Route, Routes } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import { changeVisibility, getAdminProduct, getAdminProducts, getModerationLogs } from '../src/features/admin/api.ts'
import { accessProblem } from '../src/features/admin/access.ts'
import { readFilter, validateReason } from '../src/features/admin/validation.ts'
import { ApiError, clearCsrfToken } from '../src/shared/api/client.ts'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-admin',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { AdminProductListPage } = await server.ssrLoadModule('/src/features/admin/pages/AdminProductListPage.tsx')
const { AdminProductDetailPage } = await server.ssrLoadModule('/src/features/admin/pages/AdminProductDetailPage.tsx')
const { MemberNav } = await server.ssrLoadModule('/src/features/auth/components/MemberNav.tsx')
const ssr = await server.ssrLoadModule('/src/shared/api/client.ts')
after(async () => { await server.close() })

const original = globalThis.fetch
afterEach(() => { globalThis.fetch = original; clearCsrfToken() })
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
const apiError = (ErrorClass, status, code) => new ErrorClass(status, { code, message: '서버 메시지', fieldErrors: [], traceId: 't' })

const id = '9007199254740993'
const summary = {
  id, title: '로고 디자인', priceAmount: 150000, durationDays: 7,
  category: { id: '1', name: '디자인' }, seller: { id: '20', displayName: '판매자' },
  saleStatus: 'STOPPED', isHidden: true, version: 3,
  createdAt: '2026-11-04T00:00:00Z', updatedAt: '2026-11-04T01:00:00Z',
}
const detail = { ...summary, description: '<script>alert(1)</script>\n둘째 줄', scopeDescription: '범위', preparationDescription: '준비' }

// ---------- API 함수 ----------

test('list request sends only allowed filters, literal keyword and page without client identity', async () => {
  const urls = []
  globalThis.fetch = async (url, options) => {
    urls.push(url)
    assert.equal(options.credentials, 'same-origin')
    assert.equal(options.body, undefined)
    return json({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
  }
  await getAdminProducts({ keyword: '50%_off', isHidden: 'true', saleStatus: 'STOPPED' }, 2)
  await getAdminProducts({ keyword: '', isHidden: '', saleStatus: '' }, 0)
  assert.deepEqual(urls, [
    '/api/admin/products?page=2&size=20&keyword=50%25_off&isHidden=true&saleStatus=STOPPED',
    '/api/admin/products?page=0&size=20',
  ])
})

test('invalid product IDs never reach the network', async () => {
  let calls = 0
  globalThis.fetch = async () => { calls++; return json({}) }
  for (const bad of ['', 'abc', '0', '-1', '1.5', '9223372036854775808']) {
    await assert.rejects(getAdminProduct(bad))
    await assert.rejects(getModerationLogs(bad, 0))
    await assert.rejects(changeVisibility(bad, { hidden: true, reason: '사유', expectedVersion: 0 }))
  }
  assert.equal(calls, 0)
  globalThis.fetch = async (url) => { calls++; assert.equal(url, `/api/admin/products/${id}/moderation-logs?page=1&size=20`); return json({ items: [] }) }
  await getModerationLogs(id, 1)
})

test('visibility change sends PATCH with CSRF and exactly hidden, reason and expectedVersion, without retry', async () => {
  let writes = 0
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
    writes++
    assert.equal(url, `/api/admin/products/${id}/visibility`)
    assert.equal(options.method, 'PATCH')
    assert.equal(options.headers.get('X-CSRF-TOKEN'), 'csrf')
    assert.deepEqual(JSON.parse(options.body), { hidden: false, reason: '복구 사유', expectedVersion: 3 })
    return json({ code: 'PRODUCT_VERSION_CONFLICT', message: '충돌', fieldErrors: [], traceId: 't' }, 409)
  }
  await assert.rejects(changeVisibility(id, { hidden: false, reason: '복구 사유', expectedVersion: 3 }), (e) => e.code === 'PRODUCT_VERSION_CONFLICT')
  assert.equal(writes, 1, '충돌이 나도 자동으로 다시 보내지 않는다')
  globalThis.fetch = async (url) => {
    if (url === '/api/auth/csrf') return json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
    writes++
    throw new TypeError('network failed')
  }
  await assert.rejects(changeVisibility(id, { hidden: true, reason: '사유', expectedVersion: 3 }), (e) => e.code === 'NETWORK_ERROR')
  assert.equal(writes, 2, '통신 오류도 자동으로 다시 보내지 않는다')
})

// ---------- 입력 규칙 ----------

test('URL filters accept only the documented values', () => {
  const ok = readFilter(new URLSearchParams('keyword=%20%20logo%20&isHidden=false&saleStatus=ON_SALE'))
  assert.deepEqual(ok, { keyword: 'logo', isHidden: 'false', saleStatus: 'ON_SALE' })
  const bad = readFilter(new URLSearchParams('isHidden=TRUE&saleStatus=DELETED'))
  assert.deepEqual(bad, { keyword: '', isHidden: '', saleStatus: '' })
  assert.deepEqual(readFilter(new URLSearchParams('isHidden=&saleStatus=')), { keyword: '', isHidden: '', saleStatus: '' })
})

test('reason is normalized and must be 1 to 1000 code points', () => {
  assert.deepEqual(validateReason('  부적절한 내용\r\n둘째 줄 '), { reason: '부적절한 내용\n둘째 줄', error: undefined })
  assert.notEqual(validateReason(' 　 ').error, undefined)
  assert.equal(validateReason('😀'.repeat(1000)).error, undefined)
  assert.notEqual(validateReason('😀'.repeat(1001)).error, undefined)
})

test('only 401 and 403 FORBIDDEN are access problems; CSRF 403 and others are ordinary errors', () => {
  assert.equal(accessProblem(apiError(ApiError, 401, 'AUTHENTICATION_REQUIRED')), 'login')
  assert.equal(accessProblem(apiError(ApiError, 403, 'FORBIDDEN')), 'forbidden')
  assert.equal(accessProblem(apiError(ApiError, 403, 'CSRF_INVALID')), undefined)
  assert.equal(accessProblem(apiError(ApiError, 500, 'INTERNAL_ERROR')), undefined)
  assert.equal(accessProblem(new TypeError('x')), undefined)
})

// ---------- 화면 렌더링 ----------

function render(url, path, element, client) {
  return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [url] },
    createElement(QueryClientProvider, { client }, createElement(Routes, null, createElement(Route, { path, element: createElement(element) })))))
}

function failed(client, key, status, code) {
  client.getQueryCache().build(client, { queryKey: key })
    .setState({ status: 'error', fetchStatus: 'idle', error: apiError(ssr.ApiError, status, code) })
}

test('list restores filters and page from the URL and distinguishes hidden from stopped', () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
  client.setQueryData(['admin', 'products', { keyword: 'logo', isHidden: 'true', saleStatus: 'STOPPED' }, 1],
    { items: [summary], page: 1, size: 20, totalElements: 21, totalPages: 2 })
  try {
    const html = render('/admin/products?keyword=logo&isHidden=true&saleStatus=STOPPED&page=1', '/admin/products', AdminProductListPage, client)
    assert.match(html, /value="logo"/)
    assert.match(html, /value="true" selected=""/)
    assert.match(html, /value="STOPPED" selected=""/)
    assert.match(html, new RegExp(`href="/admin/products/${id}"`))
    assert.match(html, /로고 디자인/)
    assert.match(html, />숨김</)
    assert.match(html, />판매 중지</)
    assert.match(html, /2페이지/)
    assert.match(html, /총 21개/)
  } finally { client.clear() }
})

test('list shows login guidance for 401 and a no-permission notice for 403 without product data', () => {
  for (const [status, code, text] of [[401, 'AUTHENTICATION_REQUIRED', /로그인 후 이용/], [403, 'FORBIDDEN', /접근 권한이 없어요/]]) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
    failed(client, ['admin', 'products', { keyword: '', isHidden: '', saleStatus: '' }, 0], status, code)
    try {
      const html = render('/admin/products', '/admin/products', AdminProductListPage, client)
      assert.match(html, text)
      assert.doesNotMatch(html, /총 \d+개/)
      if (status === 401) assert.match(html, /href="\/login\?next=%2Fadmin%2Fproducts"/)
    } finally { client.clear() }
  }
})

test('keyword over 100 code points is not sent to the server', () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
  try {
    const html = render(`/admin/products?keyword=${'가'.repeat(101)}`, '/admin/products', AdminProductListPage, client)
    assert.match(html, /검색어는 최대 100자/)
    assert.equal(client.getQueryCache().findAll({ queryKey: ['admin', 'products'] }).length, 0)
  } finally { client.clear() }
})

test('detail of a hidden product offers restore, escapes text and shows moderation history', () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
  client.setQueryData(['admin', 'product', id], detail)
  client.setQueryData(['admin', 'logs', id, 0], { items: [{ id: '7', adminId: '1', action: 'HIDE', reason: '부적절한 내용', createdAt: '2026-11-04T02:00:00Z' }], page: 0, size: 20, totalElements: 1, totalPages: 1 })
  try {
    const html = render(`/admin/products/${id}`, '/admin/products/:id', AdminProductDetailPage, client)
    assert.match(html, /숨김 복구하기/)
    assert.match(html, />복구하기</)
    assert.match(html, /판매 중지 상태는 판매자가 따로 관리/)
    assert.match(html, /&lt;script&gt;alert\(1\)&lt;\/script&gt;/)
    assert.doesNotMatch(html, /<script>/)
    assert.match(html, /버전 3/)
    assert.match(html, /부적절한 내용/)
    assert.match(html, /관리자 #1/)
    assert.match(html, /href="\/admin\/products"/)
  } finally { client.clear() }
})

test('detail for a visible product offers hide, and invalid or missing IDs show not found', () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
  client.setQueryData(['admin', 'product', id], { ...detail, isHidden: false, saleStatus: 'ON_SALE' })
  client.setQueryData(['admin', 'logs', id, 0], { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
  try {
    const html = render(`/admin/products/${id}`, '/admin/products/:id', AdminProductDetailPage, client)
    assert.match(html, /상품 숨기기/)
    assert.match(html, />숨기기</)
    assert.match(html, /아직 운영 이력이 없어요/)
  } finally { client.clear() }

  const missing = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
  failed(missing, ['admin', 'product', '5'], 404, 'RESOURCE_NOT_FOUND')
  try {
    assert.match(render('/admin/products/5', '/admin/products/:id', AdminProductDetailPage, missing), /상품을 찾을 수 없어요/)
    assert.match(render('/admin/products/abc', '/admin/products/:id', AdminProductDetailPage, missing), /상품을 찾을 수 없어요/)
  } finally { missing.clear() }
})

test('a failed refetch keeps the last product and the form instead of replacing the page', () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
  client.getQueryCache().build(client, { queryKey: ['admin', 'product', id] })
    .setState({ status: 'error', fetchStatus: 'idle', data: detail, dataUpdatedAt: 1, error: apiError(ssr.ApiError, 500, 'INTERNAL_ERROR') })
  client.setQueryData(['admin', 'logs', id, 0], { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
  try {
    const html = render(`/admin/products/${id}`, '/admin/products/:id', AdminProductDetailPage, client)
    assert.match(html, /최신 상태를 불러오지 못했어요/)
    assert.match(html, /다시 불러오기/)
    assert.match(html, /로고 디자인/)
    assert.match(html, /moderation-reason/)
    assert.doesNotMatch(html, /상품을 불러오지 못했어요/)
  } finally { client.clear() }
})

test('back link only returns to an admin list URL', () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
  client.setQueryData(['admin', 'product', id], detail)
  client.setQueryData(['admin', 'logs', id, 0], { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 })
  try {
    for (const [from, expected] of [['/admin/products?isHidden=true', '/admin/products?isHidden=true'], ['//evil.example', '/admin/products'], ['/mypage', '/admin/products']]) {
      const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [{ pathname: `/admin/products/${id}`, state: { from } }] },
        createElement(QueryClientProvider, { client }, createElement(Routes, null,
          createElement(Route, { path: '/admin/products/:id', element: createElement(AdminProductDetailPage) })))))
      assert.match(html, new RegExp(`href="${expected.replace('?', '\\?')}"`))
      assert.doesNotMatch(html, /evil\.example/)
    }
  } finally { client.clear() }
})

test('header shows the admin link only for an ADMIN session', () => {
  for (const [role, shown] of [['ADMIN', true], ['USER', false]]) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
    client.setQueryData(['auth', 'me'], { id: '1', email: 'a@b.com', displayName: '회원', role, hasSellerProfile: false })
    try {
      const html = renderToStaticMarkup(createElement(MemoryRouter, null, createElement(QueryClientProvider, { client },
        createElement(MemberNav, { signupClassName: 's', buttonClassName: 'b' }))))
      assert.equal(/href="\/admin\/products"/.test(html), shown, role)
      assert.match(html, /회원님/)
    } finally { client.clear() }
  }
})
