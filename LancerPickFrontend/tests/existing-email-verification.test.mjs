import assert from 'node:assert/strict'
import { after, afterEach, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Route, Routes } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import { requestExistingEmailVerification, verifyExistingEmail } from '../src/features/auth/api.ts'
import { clearCsrfToken } from '../src/shared/api/client.ts'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-existing-email',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { EmailVerificationCard } = await server.ssrLoadModule('/src/features/auth/components/EmailVerificationCard.tsx')
const { EmailVerificationPage } = await server.ssrLoadModule('/src/features/auth/pages/EmailVerificationPage.tsx')
after(async () => { await server.close() })

const original = globalThis.fetch
afterEach(() => { globalThis.fetch = original; clearCsrfToken() })
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
const TOKEN = 'A'.repeat(42) + 'g'
const member = { id: '9007199254740993', email: 'old@example.com', displayName: '기존 회원', role: 'USER',
  hasSellerProfile: false, displayNameChangeAvailableAt: null, emailVerified: false }

test('request and verify are single CSRF POSTs to the documented paths', async () => {
  const calls = []
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
    calls.push([url, options.method, options.headers.get('X-CSRF-TOKEN'), options.body])
    return url.endsWith('requests') ? json({ message: 'ok' }, 202) : new Response(null, { status: 204 })
  }
  await requestExistingEmailVerification()
  await verifyExistingEmail(TOKEN)
  assert.deepEqual(calls, [
    ['/api/me/email-verification-requests', 'POST', 'csrf', undefined],
    ['/api/me/email-verifications', 'POST', 'csrf', JSON.stringify({ token: TOKEN })],
  ])
})

test('a network failure during verification is not retried', async () => {
  let posts = 0
  globalThis.fetch = async (url) => {
    if (url === '/api/auth/csrf') return json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
    posts++
    throw new TypeError('network failed')
  }
  await assert.rejects(verifyExistingEmail(TOKEN), (e) => e.code === 'NETWORK_ERROR')
  assert.equal(posts, 1)
})

test('commands are not sent when the account changed after the click', async () => {
  let calls = 0
  globalThis.fetch = async () => { calls++; return json({}) }
  await assert.rejects(requestExistingEmailVerification(() => false), (e) => e.name === 'AbortError')
  await assert.rejects(verifyExistingEmail(TOKEN, () => false), (e) => e.name === 'AbortError')
  assert.equal(calls, 0)
})

function render(url, path, element, me) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
  client.setQueryData(['auth', 'me'], me)
  try {
    return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [url] },
      createElement(QueryClientProvider, { client }, createElement(Routes, null, createElement(Route, { path, element })))))
  } finally { client.clear() }
}

test('profile card offers the mail only to unverified members and keeps trading available', () => {
  const unverified = render('/mypage/profile', '/mypage/profile', createElement(EmailVerificationCard, { member }), member)
  assert.match(unverified, /아직 인증하지 않았어요/)
  assert.match(unverified, /로그인과 거래는 그대로/)
  assert.match(unverified, /<button[^>]*>인증 메일 받기<\/button>/)

  const verified = render('/mypage/profile', '/mypage/profile',
    createElement(EmailVerificationCard, { member: { ...member, emailVerified: true } }), member)
  assert.match(verified, /인증 완료/)
  assert.doesNotMatch(verified, /<button/)
})

test('link page needs a click to verify, asks to sign in when logged out and never prints the token', () => {
  const withToken = `/mypage/email-verification#token=${TOKEN}`
  const signedIn = render(withToken, '/mypage/email-verification', createElement(EmailVerificationPage), member)
  assert.match(signedIn, /<button[^>]*>이메일 인증하기<\/button>/)
  assert.match(signedIn, /old@example\.com/)
  assert.doesNotMatch(signedIn, new RegExp(TOKEN))

  const signedOut = render(withToken, '/mypage/email-verification', createElement(EmailVerificationPage), null)
  assert.match(signedOut, /로그인한 뒤 메일의 링크를 다시 열어/)
  assert.match(signedOut, /href="\/login"/)
  assert.doesNotMatch(signedOut, /<button/)

  const broken = render('/mypage/email-verification#token=short', '/mypage/email-verification', createElement(EmailVerificationPage), member)
  assert.match(broken, /이 링크로는 인증할 수 없어요/)
  assert.match(broken, /href="\/mypage\/profile"/)
})
