import assert from 'node:assert/strict'
import { after, afterEach, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import { requestAccountGuide } from '../src/features/auth/api.ts'
import { clearCsrfToken } from '../src/shared/api/client.ts'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-account-guide',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { AccountGuidePage } = await server.ssrLoadModule('/src/features/auth/pages/AccountGuidePage.tsx')
const { LoginPage } = await server.ssrLoadModule('/src/features/auth/pages/LoginPage.tsx')
after(async () => { await server.close() })

const original = globalThis.fetch
afterEach(() => { globalThis.fetch = original; clearCsrfToken() })
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })

test('account guide request posts only the email once with CSRF and accepts the fixed 202 body', async () => {
  const calls = []
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
    calls.push([url, options.method, options.headers.get('X-CSRF-TOKEN'), options.body])
    return json({ message: '입력한 주소가 가입한 이메일이면 아이디 안내 메일을 보냅니다.' }, 202)
  }
  const result = await requestAccountGuide('a@example.com')
  assert.deepEqual(calls, [['/api/auth/account-guide-requests', 'POST', 'csrf', JSON.stringify({ email: 'a@example.com' })]])
  assert.match(result.message, /아이디 안내/)
})

test('a network failure is not retried', async () => {
  let posts = 0
  globalThis.fetch = async (url) => {
    if (url === '/api/auth/csrf') return json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
    posts++
    throw new TypeError('network failed')
  }
  await assert.rejects(requestAccountGuide('a@example.com'), (e) => e.code === 'NETWORK_ERROR')
  assert.equal(posts, 1)
})

function render(element) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(['auth', 'me'], null)
  try {
    return renderToStaticMarkup(createElement(MemoryRouter, null, createElement(QueryClientProvider, { client }, element)))
  } finally { client.clear() }
}

test('guide page explains that the email is the ID, asks only for the email and links to reset and login', () => {
  const html = render(createElement(AccountGuidePage))
  assert.match(html, /아이디 안내/)
  assert.match(html, /가입한 이메일 주소가 아이디/)
  assert.match(html, /autoComplete="username"|autocomplete="username"/)
  assert.equal(html.match(/<input/g)?.length, 1, '이메일 입력 하나만 받는다')
  assert.match(html, /href="\/password-reset\/request"/)
  assert.match(html, /href="\/login"/)
  assert.doesNotMatch(html, /이메일을 찾아드려요|이메일 찾기/, '잊어버린 이메일을 찾아 주는 것처럼 표현하지 않는다')
})

test('login page links to the account guide next to password reset', () => {
  const html = render(createElement(LoginPage))
  assert.match(html, /href="\/account-guide"/)
  assert.match(html, /href="\/password-reset\/request"/)
})
