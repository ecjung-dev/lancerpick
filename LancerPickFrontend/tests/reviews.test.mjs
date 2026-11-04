import assert from 'node:assert/strict'
import { after, afterEach, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-reviews', server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { getProductReviews, writeReview, prepareReview } = await server.ssrLoadModule('/src/features/reviews/api.ts')
const { clearCsrfToken } = await server.ssrLoadModule('/src/shared/api/client.ts')
const { ProductReviews } = await server.ssrLoadModule('/src/features/reviews/components/ProductReviews.tsx')
const { ReviewForm } = await server.ssrLoadModule('/src/features/reviews/components/ReviewForm.tsx')
const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch; clearCsrfToken() })
after(() => server.close())

test('review normalization counts Unicode points and preserves optional null', () => {
  assert.deepEqual(prepareReview({ rating: 5, content: null }), { rating: 5, content: null })
  assert.equal(prepareReview({ rating: 1, content: '　a\r\nb　' }).content, 'a\nb')
  assert.equal(prepareReview({ rating: 4, content: '😀'.repeat(2000) }).content.length, 4000)
  for (const rating of [0, 6, 1.5, NaN, '5', null]) assert.throws(() => prepareReview({ rating, content: null }))
  for (const content of ['', '　', '😀'.repeat(2001)]) assert.throws(() => prepareReview({ rating: 5, content }))
})
test('public review query preserves BIGINT IDs, cancellation and pagination', async () => {
  const controller = new AbortController()
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/products/9007199254740993/reviews?page=2&size=20')
    assert.equal(options.credentials, 'same-origin')
    assert.equal(options.signal, controller.signal)
    return Response.json({ items: [{ id: '9007199254740995' }] })
  }
  assert.equal((await getProductReviews('9007199254740993', 2, controller.signal)).items[0].id, '9007199254740995')
})
test('invalid identifiers and pages never send a request', () => {
  globalThis.fetch = () => { throw new Error('must not request') }
  for (const id of ['0', '-1', '1/other', '9223372036854775808']) assert.throws(() => getProductReviews(id, 0))
  for (const page of [-1, 1.2, 2147483648, NaN]) assert.throws(() => getProductReviews('1', page))
})
test('review write uses CSRF and cookie, no member identity, and never replays a failed POST', async () => {
  let posts = 0
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return Response.json({ token: 'csrf-review', headerName: 'X-CSRF-TOKEN' })
    posts++
    assert.equal(url, '/api/orders/9007199254740993/review')
    assert.equal(options.method, 'POST')
    assert.equal(options.headers.get('X-CSRF-TOKEN'), 'csrf-review')
    assert.equal(options.credentials, 'same-origin')
    assert.deepEqual(JSON.parse(options.body), { rating: 5, content: '좋아요' })
    return Response.json({ code: 'REVIEW_ALREADY_EXISTS', message: '이미 작성', fieldErrors: [] }, { status: 409 })
  }
  await assert.rejects(writeReview('9007199254740993', { rating: 5, content: ' 좋아요 ' }), error => error.status === 409)
  assert.equal(posts, 1)
})
function render(page, data) {
  const client = new QueryClient()
  if (data) client.setQueryData(['products', 'reviews', '7', page], data)
  try { return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [`/products/7?page=${page}`] }, createElement(QueryClientProvider, { client }, createElement(ProductReviews, { productId: '7' })))) }
  finally { client.clear() }
}
test('public review markup escapes text, shows rating/null content and range recovery', () => {
  const html = render(1, { items: [{ id: '1', authorDisplayName: '<script>name</script>', rating: 5, content: '<script>text</script>', createdAt: '2026-11-04T00:00:00Z' }, { id: '2', authorDisplayName: '작성자', rating: 1, content: null, createdAt: '2026-11-04T00:00:00Z' }], totalElements: 21, totalPages: 2 })
  assert.match(html, /&lt;script&gt;text/)
  assert.doesNotMatch(html, /<script>/)
  assert.match(html, /별점 5 \/ 5/)
  assert.match(html, /2페이지/)
  assert.match(render(10, { items: [], totalElements: 1, totalPages: 1 }), /이 페이지에 후기가 없습니다/)
  assert.match(render(0, { items: [], totalElements: 0, totalPages: 0 }), /아직 작성된 후기가 없습니다/)
  assert.match(render(0), /불러오고 있어요/)
})
test('write form explains one-time policy and optional text', () => {
  const client = new QueryClient()
  try {
    const html = renderToStaticMarkup(createElement(QueryClientProvider, { client }, createElement(ReviewForm, { orderId: '1', productId: '7' })))
    assert.match(html, /수정·삭제할 수 없습니다/)
    assert.match(html, /후기 글 \(선택\)/)
    assert.match(html, /후기 등록/)
  } finally { client.clear() }
})
