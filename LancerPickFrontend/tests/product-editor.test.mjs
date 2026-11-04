import assert from 'node:assert/strict'
import { after, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Routes, Route } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-product-editor', server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { ProductEditorPage } = await server.ssrLoadModule('/src/features/products/pages/ProductEditorPage.tsx')
const { ApiError } = await server.ssrLoadModule('/src/shared/api/client.ts')
after(() => server.close())
function render(client, url, path) {
  try { return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [url] }, createElement(QueryClientProvider, { client }, createElement(Routes, null, createElement(Route, { path, element: createElement(ProductEditorPage) }))))) }
  finally { client.clear() }
}
test('signed out editor returns to the same product route after login and hides private fields', () => {
  const client = new QueryClient({ defaultOptions: { queries: { retryOnMount: false } } })
  client.getQueryCache().build(client, { queryKey: ['product-editor-session'] }).setState({ status: 'error', fetchStatus: 'idle', error: new ApiError(401, { code: 'AUTHENTICATION_REQUIRED', message: '로그인', fieldErrors: [] }) })
  const html = render(client, '/mypage/products/123/edit', '/mypage/products/:id/edit')
  assert.match(html, /\/login\?next=%2Fmypage%2Fproducts%2F123%2Fedit/)
  assert.doesNotMatch(html, /textarea|상품 저장/)
})
test('owner editor loads the private version and values while escaping text', () => {
  const client = new QueryClient()
  client.setQueryData(['product-editor-session'], { id: '3' })
  client.setQueryData(['categories'], [{ id: '2', name: '분류', displayOrder: 0 }])
  client.setQueryData(['product-editor', '3', '123'], { id: '123', category: { id: '2', name: '분류' }, title: '<script>test</script>', description: '설명', scopeDescription: '범위', preparationDescription: '준비', priceAmount: 50000, durationDays: 3, version: 4, saleStatus: 'STOPPED', isHidden: true })
  const html = render(client, '/mypage/products/123/edit', '/mypage/products/:id/edit')
  assert.match(html, /관리자 숨김/)
  assert.match(html, /판매 중지/)
  assert.match(html, /&lt;script&gt;test&lt;\/script&gt;/)
  assert.match(html, /value="50000"/)
  assert.match(html, /상품 저장/)
})
