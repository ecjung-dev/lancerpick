import assert from 'node:assert/strict'
import { after, afterEach, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import { updateMe } from '../src/features/auth/api.ts'
import { saveDisplayName } from '../src/features/auth/profile.ts'
import { isDisplayNameChangeLocked } from '../src/features/auth/nameChangePolicy.ts'
import { memberQueryKey } from '../src/features/auth/session.ts'
import { validateDisplayName, validateSignup } from '../src/features/auth/validation.ts'
import { clearCsrfToken } from '../src/shared/api/client.ts'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-profile',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { MyProfilePage } = await server.ssrLoadModule('/src/features/auth/pages/MyProfilePage.tsx')
const { AccountLayout } = await server.ssrLoadModule('/src/features/account/components/AccountLayout.tsx')
const ssr = await server.ssrLoadModule('/src/shared/api/client.ts')
after(async () => { await server.close() })

const original = globalThis.fetch
afterEach(() => { globalThis.fetch = original; clearCsrfToken() })
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
const failure = (code, status) => json({ code, message: '실패', fieldErrors: [], traceId: 't' }, status)
const member = { id: '9007199254740993', email: 'a@example.com', displayName: '예전 이름', role: 'USER', hasSellerProfile: false, displayNameChangeAvailableAt: null }
const renamed = { ...member, displayName: '새 이름' }
const other = { ...member, id: '9007199254740995', email: 'b@example.com', displayName: '다른 계정' }

/** GET /api/me 응답, CSRF, PATCH 응답을 차례로 흉내 낸다. PATCH 횟수와 본문을 기록한다. */
function mockServer({ me, patch }) {
  const calls = { patch: 0, bodies: [] }
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return json({ token: 'csrf-1', headerName: 'X-CSRF-TOKEN' })
    if (url === '/api/me' && options.method === 'GET') return me()
    assert.equal(url, '/api/me')
    assert.equal(options.method, 'PATCH')
    assert.equal(options.headers.get('X-CSRF-TOKEN'), 'csrf-1')
    calls.patch++
    calls.bodies.push(JSON.parse(options.body))
    return patch()
  }
  return calls
}

// ---------- API·입력 규칙 ----------

test('updateMe sends PATCH /api/me with CSRF and only displayName, without retrying a failure', async () => {
  const calls = mockServer({ me: () => json(member), patch: () => json(renamed) })
  assert.equal((await updateMe({ displayName: '새 이름' })).displayName, '새 이름')
  assert.deepEqual(calls.bodies, [{ displayName: '새 이름' }])

  const failed = mockServer({ me: () => json(member), patch: () => failure('INTERNAL_ERROR', 500) })
  await assert.rejects(updateMe({ displayName: '새 이름' }), (e) => e.status === 500)
  assert.equal(failed.patch, 1)
})

test('display name is normalized and must be 1 to 30 code points, the same rule as signup', () => {
  assert.deepEqual(validateDisplayName('  새 이름 \n'), { displayName: '새 이름', error: undefined })
  assert.match(validateDisplayName('   ').error, /1~30자/)
  assert.equal(validateDisplayName('😀'.repeat(30)).error, undefined)
  assert.match(validateDisplayName('😀'.repeat(31)).error, /1~30자/)
  const signup = validateSignup({ email: 'a@example.com', password: 'abcd1234', passwordConfirm: 'abcd1234', displayName: '😀'.repeat(31) })
  assert.equal(signup.errors.displayName, validateDisplayName('😀'.repeat(31)).error)
})

// ---------- 저장 순서·캐시 ----------

test('saving checks the session first, updates the header cache and a late /me cannot restore the old name', async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(memberQueryKey, member)
  client.setQueryData(['products', 'list'], { items: [] })
  let finishOld
  const late = client.fetchQuery({ queryKey: memberQueryKey, staleTime: 0,
    queryFn: () => new Promise((resolve) => { finishOld = () => resolve(member) }) }).catch(() => undefined)
  const calls = mockServer({ me: () => json(member), patch: () => json(renamed) })
  const outcome = await saveDisplayName(client, member.id, '새 이름')
  finishOld() // 저장 전에 출발한 조회가 옛 이름으로 늦게 도착
  await late
  assert.equal(outcome.kind, 'saved')
  assert.equal(calls.patch, 1)
  assert.deepEqual(client.getQueryData(memberQueryKey), renamed)
  assert.equal(client.getQueryCache().find({ queryKey: ['products', 'list'] }).state.isInvalidated, true)
  client.clear()
})

test('a different account in another tab or an ended session never sends PATCH and clears the old account caches', async () => {
  for (const [me, kind, expected] of [[() => json(other), 'accountChanged', other], [() => failure('AUTHENTICATION_REQUIRED', 401), 'signedOut', null]]) {
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    client.setQueryData(memberQueryKey, member)
    client.setQueryData(['wallet', 'balance'], { balance: 1000 })
    const calls = mockServer({ me, patch: () => json(renamed) })
    const outcome = await saveDisplayName(client, member.id, '새 이름')
    assert.equal(outcome.kind, kind)
    assert.equal(calls.patch, 0)
    assert.deepEqual(client.getQueryData(memberQueryKey), expected)
    assert.equal(client.getQueryData(['wallet', 'balance']), undefined)
    client.clear()
  }
})

test('a response for another member is not shown as saved, and failures propagate once without retry', async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(memberQueryKey, member)
  mockServer({ me: () => json(member), patch: () => json(other) })
  assert.equal((await saveDisplayName(client, member.id, '새 이름')).kind, 'accountChanged')
  assert.deepEqual(client.getQueryData(memberQueryKey), other)

  client.setQueryData(memberQueryKey, member)
  for (const [status, code] of [[400, 'VALIDATION_FAILED'], [403, 'CSRF_INVALID'], [500, 'INTERNAL_ERROR']]) {
    const calls = mockServer({ me: () => json(member), patch: () => failure(code, status) })
    await assert.rejects(saveDisplayName(client, member.id, '새 이름'), (e) => e.status === status)
    assert.equal(calls.patch, 1)
    clearCsrfToken()
  }
  assert.deepEqual(client.getQueryData(memberQueryKey), member)
  client.clear()
})

// ---------- 화면 ----------

test('name cooldown allows the exact server deadline and never guesses calendar months in the browser', () => {
  const at = Date.parse('2027-05-09T06:00:00.123Z')
  assert.equal(isDisplayNameChangeLocked(null, at), false)
  assert.equal(isDisplayNameChangeLocked('2027-05-09T06:00:00.123Z', at - 1), true)
  assert.equal(isDisplayNameChangeLocked('2027-05-09T06:00:00.123Z', at), false)
  assert.equal(isDisplayNameChangeLocked('2027-05-09T06:00:00.123Z', at + 1), false)
  assert.equal(isDisplayNameChangeLocked('invalid', at), true)
})

test('name cooldown shows the next date and disables only rename, leaving password change available', () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
  client.setQueryData(memberQueryKey, { ...member, displayNameChangeAvailableAt: '2999-05-09T06:00:00Z' })
  try {
    const html = render(client)
    assert.match(html, /다음 변경 가능일/)
    assert.match(html, /<button[^>]*disabled=""[^>]*>표시 이름 저장<\/button>/)
    assert.doesNotMatch(html, /<button[^>]*disabled=""[^>]*>비밀번호 변경<\/button>/)
  } finally { client.clear() }
})

function render(client) {
  return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/mypage/profile'] },
    createElement(QueryClientProvider, { client }, createElement(MyProfilePage))))
}

test('logged-out visitors get a login link that returns here, without a form', () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
  client.setQueryData(memberQueryKey, null)
  try {
    const html = render(client)
    assert.match(html, /로그인 후 내 정보를 볼 수 있어요/)
    assert.match(html, /href="\/login\?next=%2Fmypage%2Fprofile"/)
    assert.doesNotMatch(html, /profile-display-name/)
  } finally { client.clear() }
})

test('the form shows email and the current name, escapes text and leaves the route back to the mypage layout', () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
  client.setQueryData(memberQueryKey, { ...member, displayName: '<b>굵게</b>' })
  try {
    const html = render(client)
    assert.match(html, /a@example\.com/)
    assert.match(html, /value="&lt;b&gt;굵게&lt;\/b&gt;"/)
    assert.doesNotMatch(html, /<b>굵게/)
    assert.match(html, /표시 이름 저장/)
    assert.match(html, /\/ 30자/)
    assert.match(html, /<h1>내 정보<\/h1>/)
    assert.doesNotMatch(html, /aria-label="현재 위치"/)
  } finally { client.clear() }
})

test('a failed session check offers retry instead of a login form or stale data', () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
  client.getQueryCache().build(client, { queryKey: memberQueryKey })
    .setState({ status: 'error', fetchStatus: 'idle', error: new ssr.ApiError(500, { code: 'INTERNAL_ERROR', message: '서버 오류', fieldErrors: [], traceId: 't' }) })
  try {
    const html = render(client)
    assert.match(html, /로그인 상태를 확인하지 못했어요/)
    assert.match(html, /다시 불러오기/)
    assert.doesNotMatch(html, /profile-display-name/)
  } finally { client.clear() }
})

test('mypage menu links to the profile screen instead of a coming-soon label', () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(['auth', 'me'], null)
  const html = renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: ['/mypage'] },
    createElement(QueryClientProvider, { client }, createElement(AccountLayout))))
  client.clear()
  assert.match(html, /href="\/mypage\/profile"/)
  assert.doesNotMatch(html, /내 정보 <small>준비 중/)
})
