import assert from 'node:assert/strict'
import { after, afterEach, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import { requestPasswordReset, resetPassword } from '../src/features/auth/api.ts'
import { readResetToken, resetFailureKind, submitPasswordReset } from '../src/features/auth/passwordReset.ts'
import { memberQueryKey } from '../src/features/auth/session.ts'
import { validatePasswordReset, validatePasswordResetRequest } from '../src/features/auth/validation.ts'
import { ApiError, clearCsrfToken } from '../src/shared/api/client.ts'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-password-reset',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { PasswordResetPage } = await server.ssrLoadModule('/src/features/auth/pages/PasswordResetPage.tsx')
const { PasswordResetRequestPage } = await server.ssrLoadModule('/src/features/auth/pages/PasswordResetRequestPage.tsx')
const { LoginPage } = await server.ssrLoadModule('/src/features/auth/pages/LoginPage.tsx')
after(async () => { await server.close() })

const original = globalThis.fetch
afterEach(() => { globalThis.fetch = original; clearCsrfToken() })
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
const failure = (code, status, fieldErrors = []) => json({ code, message: '실패', fieldErrors, traceId: 't' }, status)
const noContent = () => new Response(null, { status: 204 })
const accepted = () => json({ message: '입력한 주소로 안내할 수 있는 경우 비밀번호 재설정 메일을 보냅니다.' }, 202)
const TOKEN = 'A'.repeat(42) + 'g'
const member = { id: '9007199254740993', email: 'a@example.com', displayName: '회원', role: 'USER', hasSellerProfile: false }
const other = { ...member, id: '9007199254740995', email: 'b@example.com' }

/** CSRF·재설정 POST·GET /api/me를 흉내 낸다. 호출 순서·본문·POST 횟수를 기록한다. */
function mockServer({ post, me = () => failure('AUTHENTICATION_REQUIRED', 401) }) {
  const calls = { csrf: 0, post: 0, bodies: [], order: [] }
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') {
      calls.csrf++
      return json({ token: `csrf-${calls.csrf}`, headerName: 'X-CSRF-TOKEN' })
    }
    if (url === '/api/me') {
      calls.order.push('GET /api/me')
      return me()
    }
    assert.ok(url === '/api/auth/password-reset-requests' || url === '/api/auth/password-resets', url)
    assert.equal(options.method, 'POST')
    assert.equal(options.headers.get('X-CSRF-TOKEN'), `csrf-${calls.csrf}`)
    calls.order.push(`POST ${url}`)
    calls.post++
    calls.bodies.push(JSON.parse(options.body))
    return post()
  }
  return calls
}

// ---------- API ----------

test('requestPasswordReset posts the email once with CSRF and accepts the fixed 202 body', async () => {
  const calls = mockServer({ post: accepted })
  const result = await requestPasswordReset('a@example.com')
  assert.match(result.message, /안내할 수 있는 경우/)
  assert.deepEqual(calls.bodies, [{ email: 'a@example.com' }])

  clearCsrfToken()
  const failed = mockServer({ post: () => failure('INTERNAL_ERROR', 500) })
  await assert.rejects(requestPasswordReset('a@example.com'), (e) => e.status === 500)
  assert.equal(failed.post, 1) // 자동 재전송 없음
})

test('resetPassword posts the raw token and password once and never retries', async () => {
  const calls = mockServer({ post: noContent })
  assert.equal(await resetPassword({ token: TOKEN, newPassword: ' new pass1 ' }), undefined)
  assert.deepEqual(calls.bodies, [{ token: TOKEN, newPassword: ' new pass1 ' }])

  for (const [status, code] of [[400, 'PASSWORD_RESET_TOKEN_INVALID'], [403, 'CSRF_INVALID'], [500, 'INTERNAL_ERROR']]) {
    clearCsrfToken()
    const failed = mockServer({ post: () => failure(code, status) })
    await assert.rejects(resetPassword({ token: TOKEN, newPassword: 'reset5678x' }), (e) => e.code === code)
    assert.equal(failed.post, 1, code)
  }
})

// ---------- 토큰·입력 규칙 ----------

test('readResetToken only accepts the 43-character URL-safe token from the fragment', () => {
  assert.equal(readResetToken(`#token=${TOKEN}`), TOKEN)
  assert.equal(readResetToken(`token=${TOKEN}`), TOKEN)
  assert.equal(readResetToken(`#utm=x&token=${TOKEN}`), TOKEN)
  for (const hash of ['', '#', '#token=', '#token=short', `#token=${TOKEN}A`, `#token=${'+'.repeat(43)}`, `#other=${TOKEN}`]) {
    assert.equal(readResetToken(hash), null, hash)
  }
})

test('reset request email uses the signup rule and new passwords are never trimmed', () => {
  assert.deepEqual(validatePasswordResetRequest({ email: '  A@Example.COM ' }), { email: 'a@example.com', error: undefined, valid: true })
  assert.match(validatePasswordResetRequest({ email: '' }).error, /입력해/)
  assert.match(validatePasswordResetRequest({ email: 'a@b' }).error, /형식/)

  const ok = validatePasswordReset({ newPassword: 'reset5678x', newPasswordConfirm: 'reset5678x' })
  assert.equal(ok.valid, true)
  assert.equal(ok.newPassword, 'reset5678x')
  for (const bad of ['abc1234', 'abcdefgh', 'abcd 1234', ' abcd5678', '비밀번호1234a']) {
    assert.match(validatePasswordReset({ newPassword: bad, newPasswordConfirm: bad }).errors.newPassword, /8~64자/, bad)
  }
  assert.match(validatePasswordReset({ newPassword: 'reset5678x', newPasswordConfirm: 'reset5678y' }).errors.newPasswordConfirm, /서로 달라요/)
  assert.deepEqual(Object.keys(validatePasswordReset({ newPassword: '', newPasswordConfirm: '' }).errors).sort(),
    ['newPassword', 'newPasswordConfirm'])
})

// ---------- 재설정 순서·캐시 ----------

function clientWith(current) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(memberQueryKey, current)
  client.setQueryData(['wallet', 'balance'], { balance: 1000 })
  return client
}

test('a successful reset posts once, then rechecks the session and clears the revoked account', async () => {
  const client = clientWith(member)
  const calls = mockServer({ post: noContent }) // 같은 계정 세션은 서버가 폐기 → /api/me 401
  await submitPasswordReset(client, { token: TOKEN, newPassword: 'reset5678x' })

  assert.deepEqual(calls.order, ['POST /api/auth/password-resets', 'GET /api/me'])
  assert.equal(client.getQueryData(memberQueryKey), null)
  assert.equal(client.getQueryData(['wallet', 'balance']), undefined)
  client.clear()
})

test('a reset does not log out a different account that is signed in on this browser', async () => {
  const client = clientWith(other)
  mockServer({ post: noContent, me: () => json(other) })
  await submitPasswordReset(client, { token: TOKEN, newPassword: 'reset5678x' })
  assert.deepEqual(client.getQueryData(memberQueryKey), other)
  client.clear()
})

test('a failed reset is not followed by a session check and keeps local data', async () => {
  const client = clientWith(member)
  const calls = mockServer({ post: () => failure('PASSWORD_RESET_TOKEN_INVALID', 400) })
  await assert.rejects(submitPasswordReset(client, { token: TOKEN, newPassword: 'reset5678x' }))
  assert.deepEqual(calls.order, ['POST /api/auth/password-resets'])
  assert.deepEqual(client.getQueryData(memberQueryKey), member)
  client.clear()
})

test('reset failures are classified by code, and network or 5xx failures are unclear', () => {
  const error = (status, code) => new ApiError(status, { code, message: 'x', fieldErrors: [] })
  assert.equal(resetFailureKind(error(400, 'PASSWORD_RESET_TOKEN_INVALID')), 'invalidLink')
  assert.equal(resetFailureKind(error(400, 'VALIDATION_FAILED')), 'fields')
  assert.equal(resetFailureKind(error(403, 'CSRF_INVALID')), 'csrf')
  assert.equal(resetFailureKind(error(0, 'NETWORK_ERROR')), 'unclear')
  assert.equal(resetFailureKind(error(502, 'HTTP_ERROR')), 'unclear')
  assert.equal(resetFailureKind(error(409, 'SOMETHING_ELSE')), 'other')
})

// ---------- 화면 ----------

function render(element, entry, current = null) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
  client.setQueryData(memberQueryKey, current)
  try {
    return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [entry] },
      createElement(QueryClientProvider, { client }, createElement(element))))
  } finally { client.clear() }
}

test('the reset page shows the new-password form for a valid link and never prints the token', () => {
  const html = render(PasswordResetPage, `/password-reset#token=${TOKEN}`)
  assert.match(html, /새 비밀번호 설정/)
  assert.equal((html.match(/autocomplete="new-password"/gi) ?? []).length, 2)
  assert.doesNotMatch(html, new RegExp(TOKEN))
})

test('the reset page explains a missing or malformed link and offers a new request', () => {
  for (const entry of ['/password-reset', '/password-reset#token=short', `/password-reset?token=${TOKEN}`]) {
    const html = render(PasswordResetPage, entry)
    assert.match(html, /유효하지 않거나 만료된 링크/, entry)
    assert.match(html, /href="\/password-reset\/request"/, entry)
    assert.doesNotMatch(html, /reset-new-password/, entry)
  }
})

test('the request page asks only for the email and the login page links to it', () => {
  const request = render(PasswordResetRequestPage, '/password-reset/request')
  assert.match(request, /id="reset-email"/)
  assert.match(request, /재설정 메일 받기/)

  const login = render(LoginPage, '/login')
  assert.match(login, /href="\/password-reset\/request"[^>]*>비밀번호를 잊으셨나요\?/)
})

test('the login page explains a finished or unclear password reset', () => {
  assert.match(render(LoginPage, { pathname: '/login', state: { passwordReset: 'done' } }), /비밀번호를 다시 설정했어요/)
  assert.match(render(LoginPage, { pathname: '/login', state: { passwordReset: 'unclear' } }), /새 비밀번호로 먼저 로그인해 보고/)
  assert.doesNotMatch(render(LoginPage, '/login'), /다시 설정했어요|재설정 결과/)
})
