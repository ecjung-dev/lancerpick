import assert from 'node:assert/strict'
import { after, afterEach, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import { requestSignupEmail, verifySignupEmail } from '../src/features/auth/api.ts'
import { readLinkToken } from '../src/features/auth/linkToken.ts'
import { readResetToken } from '../src/features/auth/passwordReset.ts'
import { memberQueryKey } from '../src/features/auth/session.ts'
import { signupFailureKind, verifyFailureKind } from '../src/features/auth/signupEmail.ts'
import { validateSignup } from '../src/features/auth/validation.ts'
import { ApiError, clearCsrfToken } from '../src/shared/api/client.ts'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-signup-email',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { SignupPage } = await server.ssrLoadModule('/src/features/auth/pages/SignupPage.tsx')
const { SignupVerifyPage } = await server.ssrLoadModule('/src/features/auth/pages/SignupVerifyPage.tsx')
const { PasswordResetRequestPage } = await server.ssrLoadModule('/src/features/auth/pages/PasswordResetRequestPage.tsx')
after(async () => { await server.close() })

const original = globalThis.fetch
afterEach(() => { globalThis.fetch = original; clearCsrfToken() })
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
const failure = (code, status) => json({ code, message: '실패', fieldErrors: [], traceId: 't' }, status)
const TOKEN = 'A'.repeat(42) + 'g'
const verification = { email: 'a@example.com', proof: 'P'.repeat(43), proofExpiresAt: '2026-11-06T01:30:00Z', alreadyRegistered: false }

/** CSRF와 가입 인증 POST를 흉내 낸다. POST 횟수·본문을 기록한다. */
function mockServer(path, respond) {
  const calls = { csrf: 0, post: 0, bodies: [] }
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') {
      calls.csrf++
      return json({ token: `csrf-${calls.csrf}`, headerName: 'X-CSRF-TOKEN' })
    }
    assert.equal(url, path)
    assert.equal(options.method, 'POST')
    assert.equal(options.headers.get('X-CSRF-TOKEN'), `csrf-${calls.csrf}`)
    calls.post++
    calls.bodies.push(JSON.parse(options.body))
    return respond()
  }
  return calls
}

// ---------- API ----------

test('requestSignupEmail posts only the email once with CSRF and accepts the fixed 202 body', async () => {
  const calls = mockServer('/api/auth/signup-email-requests', () => json({ message: '입력한 주소로 가입 인증 메일을 보냅니다.' }, 202))
  const result = await requestSignupEmail('a@example.com')
  assert.match(result.message, /인증 메일/)
  assert.deepEqual(calls.bodies, [{ email: 'a@example.com' }])

  clearCsrfToken()
  const failed = mockServer('/api/auth/signup-email-requests', () => failure('INTERNAL_ERROR', 500))
  await assert.rejects(requestSignupEmail('a@example.com'), (e) => e.status === 500)
  assert.equal(failed.post, 1) // 자동 재전송 없음
})

test('verifySignupEmail posts the link token once and returns the proof, never retrying', async () => {
  const calls = mockServer('/api/auth/signup-email-verifications', () => json(verification))
  assert.deepEqual(await verifySignupEmail(TOKEN), verification)
  assert.deepEqual(calls.bodies, [{ token: TOKEN }])

  for (const [status, code] of [[400, 'SIGNUP_EMAIL_TOKEN_INVALID'], [403, 'CSRF_INVALID'], [502, 'HTTP_ERROR']]) {
    clearCsrfToken()
    const failed = mockServer('/api/auth/signup-email-verifications', () => failure(code, status))
    await assert.rejects(verifySignupEmail(TOKEN), (e) => e.status === status)
    assert.equal(failed.post, 1, code)
  }
})

// ---------- 토큰·입력·실패 분류 ----------

test('signup and reset links share the same 43-character token rule', () => {
  assert.equal(readLinkToken(`#token=${TOKEN}`), TOKEN)
  assert.equal(readResetToken, readLinkToken)
  for (const hash of ['', '#token=short', `#token=${TOKEN}A`, `?token=${TOKEN}`.replace('?', '#x')]) {
    assert.equal(readLinkToken(hash), null, hash)
  }
})

test('signup input carries the proof untouched and keeps the existing password and name rules', () => {
  const ok = validateSignup({ email: 'a@example.com', password: 'abcd1234', passwordConfirm: 'abcd1234', displayName: ' 창재 ', emailVerificationProof: verification.proof })
  assert.equal(ok.valid, true)
  assert.deepEqual(ok.input, { email: 'a@example.com', password: 'abcd1234', displayName: '창재', emailVerificationProof: verification.proof })
  assert.equal(validateSignup({ email: 'a@example.com', password: 'abcdefgh', passwordConfirm: 'abcdefgh', displayName: '이름', emailVerificationProof: 'p' }).valid, false)
})

test('verification and signup failures are classified by code, and network or 5xx failures are unclear', () => {
  const error = (status, code) => new ApiError(status, { code, message: 'x', fieldErrors: [] })
  assert.equal(verifyFailureKind(error(400, 'SIGNUP_EMAIL_TOKEN_INVALID')), 'invalidLink')
  assert.equal(verifyFailureKind(error(403, 'CSRF_INVALID')), 'csrf')
  assert.equal(verifyFailureKind(error(0, 'NETWORK_ERROR')), 'unclear')
  assert.equal(verifyFailureKind(error(500, 'INTERNAL_ERROR')), 'unclear')

  assert.equal(signupFailureKind(error(400, 'EMAIL_VERIFICATION_INVALID')), 'proofInvalid')
  assert.equal(signupFailureKind(error(409, 'EMAIL_ALREADY_EXISTS')), 'alreadyRegistered')
  assert.equal(signupFailureKind(error(400, 'VALIDATION_FAILED')), 'fields')
  assert.equal(signupFailureKind(error(403, 'CSRF_INVALID')), 'csrf')
  assert.equal(signupFailureKind(error(503, 'HTTP_ERROR')), 'unclear')
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

test('the signup page asks only for the email first and never for a password', () => {
  const html = render(SignupPage, '/signup')
  assert.match(html, /id="signup-email"/)
  assert.match(html, /인증 메일 받기/)
  assert.doesNotMatch(html, /type="password"/)
  assert.match(html, /href="\/login"/)
})

test('a signed-in visitor is told to log out before creating another account', () => {
  const member = { id: '1', email: 'a@example.com', displayName: '회원', role: 'USER', hasSellerProfile: false }
  assert.match(render(SignupPage, '/signup', member), /이미 로그인되어 있어요/)
})

test('opening the verification link shows only a verify button and never prints the token', () => {
  const html = render(SignupVerifyPage, `/signup/verify#token=${TOKEN}`)
  assert.match(html, /이메일 인증/)
  assert.match(html, /인증하기/)
  assert.doesNotMatch(html, /type="password"/)
  assert.doesNotMatch(html, new RegExp(TOKEN))
})

test('a missing or malformed verification link offers a new verification mail', () => {
  for (const entry of ['/signup/verify', '/signup/verify#token=short', `/signup/verify?token=${TOKEN}`]) {
    const html = render(SignupVerifyPage, entry)
    assert.match(html, /링크를 쓸 수 없어요/, entry)
    assert.match(html, /href="\/signup"/, entry)
  }
})

test('the shared request form keeps the reset page fields and password-manager hint', () => {
  const html = render(PasswordResetRequestPage, '/password-reset/request')
  assert.match(html, /id="reset-email"/)
  assert.match(html, /autocomplete="username"/i)
  assert.match(html, /재설정 메일 받기/)
})
