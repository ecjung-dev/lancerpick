import assert from 'node:assert/strict'
import { after, afterEach, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-favorites',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { getFavorite, getFavorites, setFavorite } = await server.ssrLoadModule('/src/features/products/favoritesApi.ts')
const { ApiError, clearCsrfToken } = await server.ssrLoadModule('/src/shared/api/client.ts')
const { ProductFavoriteButton } = await server.ssrLoadModule('/src/features/products/components/ProductFavoriteButton.tsx')
const { MyFavoritesPage } = await server.ssrLoadModule('/src/features/products/pages/MyFavoritesPage.tsx')
const originalFetch = globalThis.fetch
afterEach(() => { globalThis.fetch = originalFetch; clearCsrfToken() })
after(() => server.close())

test('favorite reads use session, no-store, pagination and the original string ID', async () => {
  const signal = new AbortController().signal
  const paths = []
  globalThis.fetch = async (path, options) => {
    paths.push(path)
    assert.equal(options.credentials, 'same-origin')
    assert.equal(options.cache, 'no-store')
    assert.equal(options.signal, signal)
    return Response.json({ favorited: true, items: [] })
  }
  assert.equal((await getFavorite('9007199254740993', signal)).favorited, true)
  await getFavorites(2, signal)
  assert.deepEqual(paths, ['/api/me/favorites/9007199254740993', '/api/me/favorites?page=2&size=20'])
  assert.throws(() => getFavorite('1/2'))
  assert.throws(() => getFavorites(-1))
})

test('registration and removal send explicit states with CSRF and never retry automatically', async () => {
  const methods = []
  globalThis.fetch = async (path, options) => {
    if (path === '/api/auth/csrf') return Response.json({ token: 'test-token', headerName: 'X-CSRF-TOKEN' })
    assert.equal(path, '/api/me/favorites/7')
    assert.equal(options.credentials, 'same-origin')
    assert.equal(options.headers.get('X-CSRF-TOKEN'), 'test-token')
    assert.equal(options.body, undefined)
    methods.push(options.method)
    return new Response(null, { status: options.method === 'PUT' ? 204 : 500 })
  }
  await setFavorite('7', true)
  await assert.rejects(setFavorite('7', false), error => error.status === 500)
  assert.deepEqual(methods, ['PUT', 'DELETE'])
})

function render(component, path, setup) {
  const client = new QueryClient({ defaultOptions: { queries: { refetchOnMount: false, retryOnMount: false } } })
  setup(client)
  try {
    return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [path] },
      createElement(QueryClientProvider, { client }, component)))
  } finally { client.clear() }
}

test('signed out product links back after login and saved product shows the removal action', () => {
  const button = createElement(ProductFavoriteButton, { productId: '7' })
  const signedOut = render(button, '/products/7?rating=5', client => {
    client.getQueryCache().build(client, { queryKey: ['my-products-session'] }).setState({
      status: 'error', fetchStatus: 'idle',
      error: new ApiError(401, { code: 'AUTHENTICATION_REQUIRED', message: '로그인이 필요합니다.', fieldErrors: [] }),
    })
  })
  assert.match(signedOut, /\/login\?next=%2Fproducts%2F7%3Frating%3D5/)
  const saved = render(button, '/products/7', client => {
    client.setQueryData(['my-products-session'], { id: '1' })
    client.setQueryData(['favorites', '1', 'product', '7'], { favorited: true })
  })
  assert.match(saved, /aria-pressed="true"/)
  assert.match(saved, /관심 상품 해제/)
})

test('list uses the current account and page and lets an empty later page recover', () => {
  const html = render(createElement(MyFavoritesPage), '/mypage/favorites?page=2', client => {
    client.setQueryData(['my-products-session'], { id: '2' })
    client.setQueryData(['favorites', '1', 'list', 2], {
      items: [{ id: '7', title: '이전 계정 상품', category: {}, seller: {} }], totalElements: 1, totalPages: 1,
    })
    client.setQueryData(['favorites', '2', 'list', 2], { items: [], totalElements: 0, totalPages: 0 })
  })
  assert.match(html, /첫 페이지로/)
  assert.doesNotMatch(html, /이전 계정 상품/)
})
