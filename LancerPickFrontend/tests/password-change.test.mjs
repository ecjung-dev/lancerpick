import assert from 'node:assert/strict'
import { after, afterEach, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import { changePassword } from '../src/features/auth/api.ts'
import { submitPasswordChange } from '../src/features/auth/password.ts'
import { memberQueryKey } from '../src/features/auth/session.ts'
import { validatePasswordChange } from '../src/features/auth/validation.ts'
import { clearCsrfToken } from '../src/shared/api/client.ts'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-password',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { MyProfilePage } = await server.ssrLoadModule('/src/features/auth/pages/MyProfilePage.tsx')
const { LoginPage } = await server.ssrLoadModule('/src/features/auth/pages/LoginPage.tsx')
after(async () => { await server.close() })

const original = globalThis.fetch
afterEach(() => { globalThis.fetch = original; clearCsrfToken() })
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
const failure = (code, status, fieldErrors = []) => json({ code, message: '실패', fieldErrors, traceId: 't' }, status)
const noContent = () => new Response(null, { status: 204 })
const member = { id: '9007199254740993', email: 'a@example.com', displayName: '회원', role: 'USER', hasSellerProfile: false }
const other = { ...member, id: '9007199254740995', email: 'b@example.com', displayName: '다른 계정' }
const input = { currentPassword: ' abcd1234', newPassword: 'newpass5678' }

/** GET /api/me, CSRF, POST /api/me/password를 흉내 낸다. CSRF 발급 횟수·POST 횟수·본문을 기록한다. */
function mockServer({ me, post }) {
  const calls = { csrf: 0, post: 0, bodies: [], order: [] }
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') {
      calls.csrf++
      return json({ token: `csrf-${calls.csrf}`, headerName: 'X-CSRF-TOKEN' })
    }
    if (url === '/api/me' && options.method === 'GET') {
      calls.order.push('GET /api/me')
      return me()
    }
    assert.equal(url, '/api/me/password')
    assert.equal(options.method, 'POST')
    assert.equal(options.headers.get('X-CSRF-TOKEN'), `csrf-${calls.csrf}`)
    calls.order.push('POST /api/me/password')
    calls.post++
    calls.bodies.push(JSON.parse(options.body))
    return post()
  }
  return calls
}

function clientWithAccountData() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(memberQueryKey, member)
  client.setQueryData(['wallet', 'balance'], { balance: 1000 })
  client.setQueryData(['orders', 'buyer'], { items: [] })
  return client
}

// ---------- API·입력 규칙 ----------

test('changePassword posts the raw passwords once with CSRF and returns nothing on 204', async () => {
  const calls = mockServer({ me: () => json(member), post: noContent })
  assert.equal(await changePassword(input), undefined)
  assert.deepEqual(calls.bodies, [{ currentPassword: ' abcd1234', newPassword: 'newpass5678' }])

  clearCsrfToken() // 아래 가짜 서버가 CSRF 발급부터 다시 세도록 저장된 토큰을 버린다
  const failed = mockServer({ me: () => json(member), post: () => failure('INTERNAL_ERROR', 500) })
  await assert.rejects(changePassword(input), (e) => e.status === 500)
  assert.equal(failed.post, 1)
})

test('password change input uses the signup rule, never trims and rejects the same password', () => {
  const ok = validatePasswordChange({ currentPassword: ' old 1 ', newPassword: 'newpass5678', newPasswordConfirm: 'newpass5678' })
  assert.equal(ok.valid, true)
  assert.deepEqual(ok.input, { currentPassword: ' old 1 ', newPassword: 'newpass5678' })

  const empty = validatePasswordChange({ currentPassword: '', newPassword: '', newPasswordConfirm: '' })
  assert.deepEqual(Object.keys(empty.errors).sort(), ['currentPassword', 'newPassword', 'newPasswordConfirm'])

  for (const bad of ['abc1234', 'abcdefgh', '12345678', 'abcd 1234', ' abcd5678', '비밀번호1234a', 'a1'.repeat(33)]) {
    const result = validatePasswordChange({ currentPassword: 'abcd1234', newPassword: bad, newPasswordConfirm: bad })
    assert.match(result.errors.newPassword, /8~64자/, bad)
  }
  assert.equal(validatePasswordChange({ currentPassword: 'x', newPassword: 'a1'.repeat(32), newPasswordConfirm: 'a1'.repeat(32) }).valid, true)

  const same = validatePasswordChange({ currentPassword: 'abcd1234', newPassword: 'abcd1234', newPasswordConfirm: 'abcd1234' })
  assert.match(same.errors.newPassword, /현재 비밀번호와 다른/)

  const mismatch = validatePasswordChange({ currentPassword: 'abcd1234', newPassword: 'newpass5678', newPasswordConfirm: 'newpass5679' })
  assert.match(mismatch.errors.newPasswordConfirm, /서로 달라요/)
})

// ---------- 변경 순서·캐시 ----------

test('a successful change checks the session first, then ends the local session and clears every account cache', async () => {
  const client = clientWithAccountData()
  const calls = mockServer({ me: () => json(member), post: noContent })
  const outcome = await submitPasswordChange(client, member.id, input)

  assert.deepEqual(outcome, { kind: 'changed' })
  assert.deepEqual(calls.order, ['GET /api/me', 'POST /api/me/password'])
  assert.equal(calls.post, 1)
  assert.equal(client.getQueryData(memberQueryKey), null)
  assert.equal(client.getQueryData(['wallet', 'balance']), undefined)
  assert.equal(client.getQueryData(['orders', 'buyer']), undefined)

  // 서버가 세션을 끝냈으므로 이전 CSRF 토큰을 버렸어야 한다. 다음 변경 요청은 토큰을 새로 받는다.
  const next = mockServer({ me: () => json(member), post: noContent })
  client.setQueryData(memberQueryKey, member)
  await submitPasswordChange(client, member.id, input)
  assert.equal(next.csrf, 1)
  client.clear()
})

test('a different account in another tab or an ended session never sends the change', async () => {
  for (const [me, kind, expected] of [[() => json(other), 'accountChanged', other], [() => failure('AUTHENTICATION_REQUIRED', 401), 'signedOut', null]]) {
    const client = clientWithAccountData()
    const calls = mockServer({ me, post: noContent })
    const outcome = await submitPasswordChange(client, member.id, input)
    assert.equal(outcome.kind, kind)
    assert.equal(calls.post, 0)
    assert.deepEqual(client.getQueryData(memberQueryKey), expected)
    assert.equal(client.getQueryData(['wallet', 'balance']), undefined)
    client.clear()
  }
})

test('rejected changes are sent once, keep the session data and surface the error code', async () => {
  for (const [status, code] of [[400, 'CURRENT_PASSWORD_INCORRECT'], [400, 'PASSWORD_UNCHANGED'], [400, 'VALIDATION_FAILED'], [401, 'AUTHENTICATION_REQUIRED'], [403, 'CSRF_INVALID'], [500, 'INTERNAL_ERROR']]) {
    const client = clientWithAccountData()
    const calls = mockServer({ me: () => json(member), post: () => failure(code, status) })
    await assert.rejects(submitPasswordChange(client, member.id, input), (e) => e.status === status && e.code === code)
    assert.equal(calls.post, 1, code)
    // 실패 처리(401 정리·결과 재확인)는 화면이 한다. 여기서는 캐시를 지우지 않는다.
    assert.deepEqual(client.getQueryData(memberQueryKey), member)
    client.clear()
    clearCsrfToken()
  }
})

test('a session check that fails for an unclear reason does not send the change', async () => {
  const client = clientWithAccountData()
  const calls = mockServer({ me: () => failure('INTERNAL_ERROR', 500), post: noContent })
  await assert.rejects(submitPasswordChange(client, member.id, input), (e) => e.status === 500)
  assert.equal(calls.post, 0)
  client.clear()
})

// ---------- 화면 ----------

function renderProfile(client) {
  return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/mypage/profile'] },
    createElement(QueryClientProvider, { client }, createElement(MyProfilePage))))
}

function renderLogin(state) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
  client.setQueryData(memberQueryKey, null)
  try {
    return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [{ pathname: '/login', state }] },
      createElement(QueryClientProvider, { client }, createElement(LoginPage))))
  } finally { client.clear() }
}

test('the profile screen offers a password form with password-manager hints and empty fields', () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
  client.setQueryData(memberQueryKey, member)
  try {
    const html = renderProfile(client)
    assert.match(html, /비밀번호 변경/)
    assert.match(html, /id="password-current"[^>]*type="password"|type="password"[^>]*id="password-current"/)
    assert.match(html, /autoComplete="current-password"|autocomplete="current-password"/)
    assert.equal((html.match(/autocomplete="new-password"/gi) ?? []).length, 2)
    assert.match(html, /autocomplete="username"[^>]*value="a@example\.com"|value="a@example\.com"[^>]*autocomplete="username"/i)
    assert.match(html, /모든 기기에서 로그아웃/)
    assert.doesNotMatch(html, /이메일과 비밀번호는 이 화면에서 바꿀 수 없어요/)
    // 표시 이름 폼은 그대로 있다.
    assert.match(html, /표시 이름 저장/)
  } finally { client.clear() }
})

test('logged-out visitors do not see the password form', () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
  client.setQueryData(memberQueryKey, null)
  try {
    const html = renderProfile(client)
    assert.doesNotMatch(html, /password-current/)
    assert.match(html, /로그인 후 내 정보를 볼 수 있어요/)
  } finally { client.clear() }
})

test('the login screen explains a finished or unclear password change', () => {
  assert.match(renderLogin({ passwordChanged: 'done' }), /비밀번호를 바꿨어요/)
  const unclear = renderLogin({ passwordChanged: 'unclear' })
  assert.match(unclear, /새 비밀번호로 먼저 로그인해 보고/)
  assert.doesNotMatch(renderLogin(null), /비밀번호를 바꿨어요|비밀번호 변경 결과/)
})
