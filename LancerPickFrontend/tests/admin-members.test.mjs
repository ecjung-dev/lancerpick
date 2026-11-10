import assert from 'node:assert/strict'
import { after, afterEach, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Route, Routes } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import { getAdminMember, getAdminMembers, getMemberSanctions, executeMemberSanction } from '../src/features/admin/memberApi.ts'
import { pendingMemberCommandKey, submitMemberSanction } from '../src/features/admin/memberCommands.ts'
import { koreaEndTime, readMemberFilter } from '../src/features/admin/memberValidation.ts'
import { captureAccountScope, memberQueryKey, resetAccountState } from '../src/features/auth/session.ts'
import { clearCsrfToken } from '../src/shared/api/client.ts'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-admin-members', server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { AdminMemberListPage } = await server.ssrLoadModule('/src/features/admin/pages/AdminMemberListPage.tsx')
const { AdminMemberDetailPage } = await server.ssrLoadModule('/src/features/admin/pages/AdminMemberDetailPage.tsx')
const { adminMemberKeys } = await server.ssrLoadModule('/src/features/admin/memberQueries.ts')
const { ApiError } = await server.ssrLoadModule('/src/shared/api/client.ts')
after(async () => { await server.close() })
const original = globalThis.fetch
const clients = []
afterEach(() => { globalThis.fetch = original; clearCsrfToken(); clients.splice(0).forEach(client => client.clear()) })
const json = (body, status = 200) => new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } })
const failure = (status, code = 'INTERNAL_ERROR') => json({ code, message: code, fieldErrors: [], traceId: 'fixture' }, status)
const empty = { items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }
const id = '9007199254740993'
const summary = { id, email: 'fixture@example.com', displayName: '회원 <script>', role: 'USER', restriction: 'NONE', createdAt: '2026-11-09T06:00:00Z' }
const detail = { member: summary, emailVerifiedAt: null, serverTime: '2026-11-09T06:00:00Z', canManage: true, activeSanctions: [] }
const command = { action: 'apply', kind: 'GENERAL_RESTRICTION', reason: '사유', endsAt: '2026-11-10T06:00:00Z', requestKey: 'a81bfe88-4745-4ed8-bda1-14a8e5a1431e' }

function client() {
  const cache = new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false, gcTime: 0 } } })
  cache.setQueryDefaults(memberQueryKey, { gcTime: Infinity })
  cache.setQueryData(memberQueryKey, { id: '1', role: 'ADMIN', displayName: '관리자', email: 'admin@example.com' })
  clients.push(cache)
  return cache
}

test('member reads preserve large IDs, literal keyword, pagination, abort and no-store without caller identity', async () => {
  const sent = [], abort = new AbortController()
  globalThis.fetch = async (url, options) => { sent.push([url, options]); return json(empty) }
  await getAdminMembers({ q: '_%!', restriction: 'SECURITY_LOCK' }, 2, abort.signal)
  const url = new URL(sent[0][0], 'http://fixture')
  assert.equal(url.pathname, '/api/admin/members')
  assert.equal(url.searchParams.get('q'), '_%!')
  assert.equal(url.searchParams.get('restriction'), 'SECURITY_LOCK')
  assert.equal(url.searchParams.get('page'), '2')
  assert.equal(url.searchParams.size, 4)
  assert.equal(sent[0][1].signal, abort.signal)
  assert.equal(sent[0][1].cache, 'no-store')
  await getAdminMember(id)
  await getMemberSanctions(id, 1)
  assert.equal(sent[1][0], `/api/admin/members/${id}`)
  assert.equal(sent[2][0], `/api/admin/members/${id}/sanctions?page=1&size=20`)
  assert.throws(() => getAdminMember('9223372036854775808'))
  assert.throws(() => getMemberSanctions(id, -1))
})

test('Korean date input rejects impossible/past dates and does not depend on browser local timezone', () => {
  assert.deepEqual(koreaEndTime('2026-11-10T15:00', detail.serverTime), { endsAt: '2026-11-10T06:00:00.000Z' })
  assert.ok(koreaEndTime('2026-02-30T15:00', detail.serverTime).error)
  assert.ok(koreaEndTime('2026-11-09T15:00', detail.serverTime).error)
  assert.ok(koreaEndTime('', detail.serverTime).error)
  assert.deepEqual(readMemberFilter(new URLSearchParams('q=++이름++&restriction=bogus')), { q: '이름', restriction: '' })
})

test('sanction and revoke send exact JSON with CSRF and no actor ID or role', async () => {
  const bodies = []
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
    bodies.push([url, JSON.parse(options.body)])
    assert.equal(options.headers.get('X-CSRF-TOKEN'), 'csrf')
    return url.endsWith('/revoke') ? new Response(null, { status: 204 }) : json({ memberId: id, sanctionId: '9' })
  }
  await executeMemberSanction(id, command)
  await executeMemberSanction(id, { action: 'revoke', sanctionId: '9', reason: '해제 사유' })
  assert.deepEqual(bodies[0][1], { kind: command.kind, reason: command.reason, endsAt: command.endsAt, requestKey: command.requestKey })
  assert.deepEqual(bodies[1], [`/api/admin/members/${id}/sanctions/9/revoke`, { reason: '해제 사유' }])
})

test('an unknown apply survives route departure and retries the exact requestKey/body even after another rejection', async () => {
  const cache = client(), bodies = []
  globalThis.fetch = async (url, options) => {
    if (url === '/api/auth/csrf') return json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
    bodies.push(JSON.parse(options.body))
    return bodies.length === 1 ? failure(503) : bodies.length === 2 ? failure(429, 'RATE_LIMITED') : json({ memberId: id, sanctionId: '9' })
  }
  await assert.rejects(submitMemberSanction(cache, id, command))
  await new Promise(resolve => setTimeout(resolve, 10))
  assert.equal(cache.getQueryData(pendingMemberCommandKey(id)).phase, 'uncertain')
  await assert.rejects(submitMemberSanction(cache, id, { ...command, reason: '바뀌면 안 됨', requestKey: crypto.randomUUID() }))
  assert.equal(cache.getQueryData(pendingMemberCommandKey(id)).phase, 'uncertain')
  await submitMemberSanction(cache, id)
  assert.deepEqual(bodies, [bodies[0], bodies[0], bodies[0]])
  assert.equal(cache.getQueryData(pendingMemberCommandKey(id)), null)
})

test('first definite rejection allows editing and two simultaneous clicks issue only one POST', async () => {
  const cache = client()
  globalThis.fetch = async url => url === '/api/auth/csrf' ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }) : failure(400, 'VALIDATION_FAILED')
  await assert.rejects(submitMemberSanction(cache, id, command))
  assert.equal(cache.getQueryData(pendingMemberCommandKey(id)), null)
  let entered, release, writes = 0
  const started = new Promise(resolve => { entered = resolve })
  globalThis.fetch = async () => { writes++; entered(); return new Promise(resolve => { release = () => resolve(json({ memberId: id, sanctionId: '9' })) }) }
  const pending = submitMemberSanction(cache, id, command)
  await started
  await assert.rejects(submitMemberSanction(cache, id, command), /이미 처리 중/)
  release(); await pending
  assert.equal(writes, 1)
})

test('account switch while CSRF is pending prevents POST and cannot restore private command state', async () => {
  const cache = client()
  let entered, release, csrfCalls = 0, writes = 0
  const started = new Promise(resolve => { entered = resolve })
  globalThis.fetch = async url => {
    if (url === '/api/auth/csrf') {
      if (++csrfCalls === 1) { entered(); return new Promise(resolve => { release = () => resolve(json({ token: 'old', headerName: 'X-CSRF-TOKEN' })) }) }
      return json({ token: 'new', headerName: 'X-CSRF-TOKEN' })
    }
    writes++; return json({ memberId: id, sanctionId: '9' })
  }
  const pending = submitMemberSanction(cache, id, command, captureAccountScope(cache))
  const rejected = assert.rejects(pending, error => error.name === 'AbortError')
  await started
  await resetAccountState(cache, null)
  release(); await rejected
  assert.equal(writes, 0)
  assert.equal(cache.getQueryData(pendingMemberCommandKey(id)), undefined)
})

test('late success after a posted command cannot change the next account cache', async () => {
  const cache = client()
  let entered, release
  const started = new Promise(resolve => { entered = resolve })
  globalThis.fetch = async url => {
    if (url === '/api/auth/csrf') return json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' })
    entered(); return new Promise(resolve => { release = () => resolve(json({ memberId: id, sanctionId: '9' })) })
  }
  const pending = submitMemberSanction(cache, id, command)
  await started
  await resetAccountState(cache, { id: '2', role: 'ADMIN' })
  const key = adminMemberKeys.detail(id)
  cache.setQueryData(key, { marker: 'next-account' })
  release(); await pending
  assert.deepEqual(cache.getQueryData(key), { marker: 'next-account' })
  assert.equal(cache.getQueryData(pendingMemberCommandKey(id)), undefined)
})

function render(cache, detailPage = false) {
  return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [detailPage ? `/admin/members/${id}` : '/admin/members'] },
    createElement(QueryClientProvider, { client: cache }, createElement(Routes, null,
      createElement(Route, { path: detailPage ? '/admin/members/:id' : '/admin/members', element: createElement(detailPage ? AdminMemberDetailPage : AdminMemberListPage) })))))
}

test('member list escapes personal text and links to detail without exposing credential fields', () => {
  const cache = client()
  cache.setQueryData(adminMemberKeys.list({ q: '', restriction: '' }, 0), { ...empty, items: [summary], totalElements: 1, totalPages: 1 })
  const html = render(cache)
  assert.match(html, /회원 &lt;script&gt;/)
  assert.match(html, new RegExp(`/admin/members/${id}`))
  assert.match(html, /일반 회원/)
  assert.doesNotMatch(html, /passwordHash|authVersion/)
})

test('current permission failure hides previously cached member email and mutation controls', () => {
  const cache = client(), key = adminMemberKeys.detail(id)
  cache.setQueryData(key, detail)
  cache.getQueryCache().find({ queryKey: key }).setState({ status: 'error', fetchStatus: 'idle', error: new ApiError(403, { code: 'FORBIDDEN', message: '권한 없음', fieldErrors: [] }) })
  const html = render(cache, true)
  assert.match(html, /접근 권한이 없어요/)
  assert.doesNotMatch(html, /fixture@example.com|제재 적용·해제/)
})

test('protected administrator detail is read-only and history states remain distinct', () => {
  const cache = client()
  cache.setQueryData(adminMemberKeys.detail(id), { ...detail, member: { ...summary, role: 'ADMIN' }, canManage: false })
  cache.setQueryData(adminMemberKeys.history(id, 0), { ...empty, items: [{ id: '9', kind: 'GENERAL_RESTRICTION', state: 'EXPIRED', reason: '<b>사유</b>', appliedBy: '1', startsAt: '2026-11-01T00:00:00Z', endsAt: '2026-11-02T00:00:00Z', revokedAt: null, revokedBy: null, revokeReason: null, timerStopDeliveredAt: null }], totalElements: 1, totalPages: 1 })
  const html = render(cache, true)
  assert.match(html, /관리자 계정과 본인 계정은/)
  assert.match(html, /기간 만료/)
  assert.match(html, /&lt;b&gt;사유&lt;\/b&gt;/)
  assert.doesNotMatch(html, /member-sanction-end/)
})
