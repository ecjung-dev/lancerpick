import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { QueryClient } from '@tanstack/react-query'
import { getCurrentMember, login, logout, signup } from '../src/features/auth/api.ts'
import { isUnclearResult, memberQueryKey, recheckAfterUnauthorized, recheckSession, resetAccountState, safeNextPath, loginPath } from '../src/features/auth/session.ts'
import { validateLogin, validateSignup } from '../src/features/auth/validation.ts'
import { ApiError, apiRequest, clearCsrfToken } from '../src/shared/api/client.ts'

const original = globalThis.fetch
afterEach(() => { globalThis.fetch = original; clearCsrfToken() })
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
const error = (code, status, fieldErrors = []) => json({ code, message: '실패', fieldErrors, traceId: 't' }, status)
const member = { id: '9007199254740993', email: 'a@example.com', displayName: '회원', role: 'USER', hasSellerProfile: false }

test('current member is null only when the session is missing (401)', async () => {
  globalThis.fetch = async (url, options) => {
    assert.equal(url, '/api/me')
    assert.equal(options.credentials, 'same-origin')
    return json(member)
  }
  assert.equal((await getCurrentMember()).id, '9007199254740993')
  globalThis.fetch = async () => error('AUTHENTICATION_REQUIRED', 401)
  assert.equal(await getCurrentMember(), null)
  for (const status of [403, 500]) {
    globalThis.fetch = async () => error('ERROR', status)
    await assert.rejects(getCurrentMember(), (e) => e.status === status)
  }
  globalThis.fetch = async () => { throw new TypeError('network failed') }
  await assert.rejects(getCurrentMember(), (e) => e.code === 'NETWORK_ERROR')
})

test('login sends CSRF and the raw password once, without retrying a failure', async () => {
  let writes = 0
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return json({ token: 'anon-csrf', headerName: 'X-CSRF-TOKEN' })
    writes++
    assert.equal(url, '/api/auth/login')
    assert.equal(options.method, 'POST')
    assert.equal(options.headers.get('X-CSRF-TOKEN'), 'anon-csrf')
    assert.deepEqual(JSON.parse(options.body), { email: 'a@example.com', password: ' pass 1234 ' })
    return error('INVALID_CREDENTIALS', 401)
  }
  await assert.rejects(login({ email: 'a@example.com', password: ' pass 1234 ' }), (e) => e.code === 'INVALID_CREDENTIALS')
  assert.equal(writes, 1)
})

test('signup and logout use the documented paths and methods', async () => {
  const calls = []
  const bodies = []
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
    calls.push(`${options.method} ${url}`)
    if (options.body) bodies.push(JSON.parse(options.body))
    if (url === '/api/auth/logout') return new Response(null, { status: 204 })
    return json(member, 201)
  }
  const proof = 'P'.repeat(43)
  assert.equal((await signup({ email: 'a@example.com', password: 'abcd1234', displayName: '회원', emailVerificationProof: proof })).email, 'a@example.com')
  assert.equal(await logout(), undefined)
  assert.deepEqual(calls, ['POST /api/members', 'POST /api/auth/logout'])
  // 가입 본문에는 이메일 인증 증명이 함께 간다(2026-11-06부터 필수).
  assert.deepEqual(bodies[0], { email: 'a@example.com', password: 'abcd1234', displayName: '회원', emailVerificationProof: proof })
})

test('reset after login drops the old CSRF token and previous account caches but keeps the auth query', async () => {
  let csrfCalls = 0
  globalThis.fetch = async (url) => {
    if (url === '/api/auth/csrf') { csrfCalls++; return json({ token: `csrf-${csrfCalls}`, headerName: 'X-CSRF-TOKEN' }) }
    return json({})
  }
  await apiRequest('/x', { method: 'POST' })
  assert.equal(csrfCalls, 1)

  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(['wallet', 'old', 'balance'], { balance: 1 })
  client.setQueryData(['seller-profile', 'old'], { id: 'old' })
  client.setQueryData(memberQueryKey, null)
  await resetAccountState(client, member)

  assert.equal(client.getQueryData(['wallet', 'old', 'balance']), undefined)
  assert.equal(client.getQueryData(['seller-profile', 'old']), undefined)
  assert.deepEqual(client.getQueryData(memberQueryKey), member)
  await apiRequest('/x', { method: 'POST' })
  assert.equal(csrfCalls, 2, '로그인 후에는 새 CSRF 토큰을 받아야 한다')
  client.clear()
})

test('signup validation matches the server email, password and display name rules', () => {
  const ok = validateSignup({ email: '  User@Example.COM ', password: 'abcd1234', passwordConfirm: 'abcd1234', displayName: '　 창재 ' })
  assert.equal(ok.valid, true)
  assert.equal(ok.input.email, 'user@example.com')
  assert.equal(ok.input.displayName, '창재')
  assert.equal(ok.input.password, 'abcd1234')

  for (const email of ['review@domain..com', '.a@b.com', 'a@-b.com', 'a@b', 'a@b.c', 'a..b@c.com', 'a@b.com.']) {
    assert.equal(validateSignup({ email, password: 'abcd1234', passwordConfirm: 'abcd1234', displayName: '이름' }).errors.email !== undefined, true, email)
  }
  for (const password of ['abc1234', 'abcdefgh', '12345678', 'abcd 1234', ' abcd1234', '비밀번호1234a', 'a1'.repeat(32) + 'b']) {
    assert.equal(validateSignup({ email: 'a@b.com', password, passwordConfirm: password, displayName: '이름' }).errors.password !== undefined, true, password)
  }
  assert.equal(validateSignup({ email: 'a@b.com', password: 'a1'.repeat(32), passwordConfirm: 'a1'.repeat(32), displayName: '이름' }).valid, true)
  assert.equal(validateSignup({ email: 'a@b.com', password: 'abcd1234', passwordConfirm: 'abcd1235', displayName: '이름' }).errors.passwordConfirm !== undefined, true)
  assert.equal(validateSignup({ email: 'a@b.com', password: 'abcd1234', passwordConfirm: 'abcd1234', displayName: '😀'.repeat(30) }).valid, true)
  assert.equal(validateSignup({ email: 'a@b.com', password: 'abcd1234', passwordConfirm: 'abcd1234', displayName: '😀'.repeat(31) }).valid, false)
  assert.equal(validateSignup({ email: 'a@b.com', password: 'abcd1234', passwordConfirm: 'abcd1234', displayName: '   ' }).valid, false)
})

test('login validation only checks empty values and never trims the password', () => {
  const result = validateLogin({ email: ' A@B.com ', password: ' x ' })
  assert.equal(result.valid, true)
  assert.deepEqual(result.input, { email: 'a@b.com', password: ' x ' })
  assert.equal(validateLogin({ email: ' ', password: '' }).valid, false)
})

test('next path only allows same-site paths', () => {
  assert.equal(safeNextPath('/products/1'), '/products/1')
  for (const next of [null, '', 'https://evil.com', '//evil.com', '/\\evil.com', '/\t/evil.com', '/\n/evil.com', '/a\\b', 'products', '/login', '/signup?x=1']) {
    assert.equal(safeNextPath(next), '/', String(next))
  }
})

test('a late /me response that started before login cannot overwrite the new account state', async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  let finishOld
  const pending = client.fetchQuery({
    queryKey: memberQueryKey,
    queryFn: () => new Promise((resolve) => { finishOld = () => resolve(null) }),
  }).catch(() => undefined)
  await resetAccountState(client, member)
  finishOld() // 로그인 전에 보낸 조회가 이제야 '로그인 안 함'으로 도착
  await pending
  assert.deepEqual(client.getQueryData(memberQueryKey), member)

  let finishStale
  const stale = client.fetchQuery({
    queryKey: memberQueryKey, staleTime: 0,
    queryFn: () => new Promise((resolve) => { finishStale = () => resolve(member) }),
  }).catch(() => undefined)
  await resetAccountState(client, null) // 로그아웃
  finishStale() // 로그아웃 전에 보낸 조회가 이전 회원으로 도착
  await stale
  assert.equal(client.getQueryData(memberQueryKey), null)
  client.clear()
})

test('network or server failure is treated as unclear, client errors are not', () => {
  const api = (status, code) => new ApiError(status, { code, message: 'x', fieldErrors: [] })
  assert.equal(isUnclearResult(api(0, 'NETWORK_ERROR')), true)
  assert.equal(isUnclearResult(api(500, 'INTERNAL_ERROR')), true)
  assert.equal(isUnclearResult(api(502, 'HTTP_ERROR')), true)
  assert.equal(isUnclearResult(new TypeError('x')), true)
  for (const [status, code] of [[400, 'VALIDATION_FAILED'], [401, 'INVALID_CREDENTIALS'], [403, 'CSRF_INVALID'], [409, 'EMAIL_ALREADY_EXISTS']]) {
    assert.equal(isUnclearResult(api(status, code)), false, code)
  }
})

test('logout response lost but /me is 401: recheck clears previous wallet, order and profile caches without replaying logout', async () => {
  let csrfCalls = 0
  const commands = []
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') { csrfCalls++; return json({ token: `csrf-${csrfCalls}`, headerName: 'X-CSRF-TOKEN' }) }
    if (url === '/api/me') return error('AUTHENTICATION_REQUIRED', 401)
    commands.push(`${options.method} ${url}`)
    return json({})
  }
  await apiRequest('/x', { method: 'POST' })
  commands.length = 0
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(memberQueryKey, member)
  client.setQueryData(['wallet', 'session'], member)
  client.setQueryData(['wallet', member.id, 'balance'], { balance: 1000000 })
  client.setQueryData(['seller-profile', member.id], { id: member.id })
  client.setQueryData(['seller-profile-session'], member)

  assert.equal(await recheckSession(client), 'signedOut')
  assert.equal(client.getQueryData(memberQueryKey), null)
  for (const key of [['wallet', 'session'], ['wallet', member.id, 'balance'], ['seller-profile', member.id], ['seller-profile-session']]) {
    assert.equal(client.getQueryData(key), undefined, JSON.stringify(key))
  }
  assert.deepEqual(commands, [], '로그아웃을 다시 보내지 않는다')
  await apiRequest('/x', { method: 'POST' })
  assert.equal(csrfCalls, 2, '버린 토큰 대신 새 CSRF 토큰을 받는다')
  client.clear()
})

test('recheck keeps caches when the same member is still logged in and resets them for a different member', async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(memberQueryKey, member)
  client.setQueryData(['wallet', member.id, 'balance'], { balance: 1 })
  globalThis.fetch = async () => json(member)
  assert.equal(await recheckSession(client), 'same')
  assert.deepEqual(client.getQueryData(['wallet', member.id, 'balance']), { balance: 1 })

  const other = { ...member, id: '42', displayName: '다른 회원' }
  globalThis.fetch = async () => json(other)
  assert.equal(await recheckSession(client), 'changed')
  assert.deepEqual(client.getQueryData(memberQueryKey), other)
  assert.equal(client.getQueryData(['wallet', member.id, 'balance']), undefined)
  client.clear()
})

test('if the recheck itself fails, previous personal caches are still removed and the session is marked for another check', async () => {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(memberQueryKey, member)
  client.setQueryData(['wallet', member.id, 'balance'], { balance: 1 })
  globalThis.fetch = async () => { throw new TypeError('network failed') }
  assert.equal(await recheckSession(client), 'unknown')
  assert.equal(client.getQueryData(['wallet', member.id, 'balance']), undefined)
  assert.equal(client.getQueryState(memberQueryKey).isInvalidated, true)
  client.clear()
})

test('a query 401 while the header still shows a member rechecks the session once and signs the header out', async () => {
  let meCalls = 0
  globalThis.fetch = async (url) => {
    if (url === '/api/me') { meCalls++; return error('AUTHENTICATION_REQUIRED', 401) }
    return json({})
  }
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(memberQueryKey, member)
  client.setQueryData(['notifications', member.id, 'unread-count'], { count: 3 })
  const expired = new ApiError(401, { code: 'AUTHENTICATION_REQUIRED', message: '로그인이 필요합니다.', fieldErrors: [] })

  await Promise.all([recheckAfterUnauthorized(client, expired), recheckAfterUnauthorized(client, expired)])
  assert.equal(meCalls, 1, '동시에 받은 401은 재확인 한 번으로 묶는다')
  assert.equal(client.getQueryData(memberQueryKey), null, '헤더가 로그인 안 함으로 바뀐다')
  assert.equal(client.getQueryData(['notifications', member.id, 'unread-count']), undefined)

  await recheckAfterUnauthorized(client, expired)
  assert.equal(meCalls, 1, '이미 로그아웃 상태면 다시 묻지 않는다')
  client.clear()
})

test('only a 401 rechecks the session after a query failure', async () => {
  let meCalls = 0
  globalThis.fetch = async () => { meCalls++; return json(member) }
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(memberQueryKey, member)
  for (const status of [0, 403, 404, 409, 500]) {
    await recheckAfterUnauthorized(client, new ApiError(status, { code: 'X', message: '실패', fieldErrors: [] }))
  }
  await recheckAfterUnauthorized(client, new Error('plain'))
  assert.equal(meCalls, 0)
  await recheckAfterUnauthorized(client, new ApiError(401, { code: 'AUTHENTICATION_REQUIRED', message: '로그인이 필요합니다.', fieldErrors: [] }))
  assert.equal(meCalls, 1)
  assert.deepEqual(client.getQueryData(memberQueryKey), member, '같은 계정이 그대로면 유지한다')
  client.clear()
})

test('when the session recheck itself fails, later 401s do not keep asking with the same login state', async () => {
  let meCalls = 0
  globalThis.fetch = async () => { meCalls++; throw new TypeError('network failed') }
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  client.setQueryData(memberQueryKey, member)
  const expired = new ApiError(401, { code: 'AUTHENTICATION_REQUIRED', message: '로그인이 필요합니다.', fieldErrors: [] })
  assert.equal(await recheckAfterUnauthorized(client, expired), 'unknown')
  assert.equal(await recheckAfterUnauthorized(client, expired), undefined)
  assert.equal(meCalls, 1, '캐시 정리 뒤 재조회의 401로 /api/me를 반복 호출하지 않는다')
  client.clear()
})

test('a late recheck response after another login or logout does not overwrite the newer account state', async () => {
  const other = { ...member, id: '42', displayName: '다른 회원' }
  const expired = new ApiError(401, { code: 'AUTHENTICATION_REQUIRED', message: '로그인이 필요합니다.', fieldErrors: [] })
  for (const [label, late] of [['로그인 안 함 응답', () => error('AUTHENTICATION_REQUIRED', 401)], ['통신 실패', () => { throw new TypeError('network failed') }]]) {
    let release
    const waiting = new Promise((resolve) => { release = resolve })
    globalThis.fetch = async () => { await waiting; return late() }
    const client = new QueryClient({ defaultOptions: { queries: { retry: false } } })
    client.setQueryData(memberQueryKey, member)

    const recheck = recheckAfterUnauthorized(client, expired)
    await new Promise((resolve) => setTimeout(resolve, 0))
    await resetAccountState(client, other) // 재확인 응답을 기다리는 동안 다른 계정으로 로그인 완료
    client.setQueryData(['wallet', other.id, 'balance'], { balance: 7 })
    release()

    assert.equal(await recheck, 'superseded', label)
    assert.deepEqual(client.getQueryData(memberQueryKey), other, `${label}: 새 계정 상태 유지`)
    assert.deepEqual(client.getQueryData(['wallet', other.id, 'balance']), { balance: 7 }, `${label}: 새 계정 캐시 유지`)
    client.clear()
  }
})

test('loginPath는 돌아올 경로를 인코딩해 safeNextPath가 그대로 되돌릴 수 있게 한다', () => {
  const next = '/orders/9007199254740993?side=BUY&page=2'
  const path = loginPath(next)
  assert.equal(path, `/login?next=${encodeURIComponent(next)}`)
  assert.equal(safeNextPath(new URL(path, 'http://localhost').searchParams.get('next')), next)
})
