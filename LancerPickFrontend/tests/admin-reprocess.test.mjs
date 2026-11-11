import assert from 'node:assert/strict'
import { after, afterEach, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Route, Routes } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import {
  executeReprocess, getActionLogs, pendingReprocessKey, resultLabel, submitReprocess,
} from '../src/features/admin/reprocessApi.ts'
import { clearCsrfToken } from '../src/shared/api/client.ts'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-admin-reprocess',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { AdminFinancePage } = await server.ssrLoadModule('/src/features/admin/pages/AdminFinancePage.tsx')
const { AdminReprocessPanel } = await server.ssrLoadModule('/src/features/admin/components/AdminReprocessPanel.tsx')
after(async () => { await server.close() })

const original = globalThis.fetch
afterEach(() => { globalThis.fetch = original; clearCsrfToken() })
const json = (value, status = 200) => new Response(JSON.stringify(value), { status, headers: { 'Content-Type': 'application/json' } })
const KEY = '6f1c2b9e-4d3a-4f6b-8c1d-2e3f4a5b6c7d'
const receipt = (over = {}) => ({
  id: '10', adminId: '1', action: 'TOPUP_RECHECK', targetType: 'WALLET_TOPUP', targetId: '9', orderId: null, reason: '확인',
  requestKey: KEY, status: 'SUCCEEDED', resultCode: 'TOPUP_CREDITED', requestedAt: '2026-11-10T00:00:00Z',
  leaseUntil: '2026-11-10T00:10:00Z', completedAt: '2026-11-10T00:00:01Z', ...over,
})
const csrf = (url) => url === '/api/auth/csrf' ? json({ token: 'csrf', headerName: 'X-CSRF-TOKEN' }) : undefined
const newClient = () => new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })

// ---------- API ----------

test('commands are single CSRF POSTs with only reason/requestKey (and version for PDF)', async () => {
  const sent = []
  globalThis.fetch = async (url, options) => {
    const token = csrf(url); if (token) return token
    sent.push([url, options.method, options.headers.get('X-CSRF-TOKEN'), options.body])
    return json(receipt(), 202)
  }
  await executeReprocess({ action: 'TOPUP_RECHECK', target: '9', reason: '확인', requestKey: KEY })
  await executeReprocess({ action: 'TOPUP_RECOVERY_RESTART', target: '31', version: 3, reason: '재시작', requestKey: KEY })
  await executeReprocess({ action: 'CONTRACT_PDF_REGENERATE', target: '9007199254740993', version: 2, reason: '재생성', requestKey: KEY })
  assert.deepEqual(sent, [
    ['/api/admin/finance/topups/9/recheck', 'POST', 'csrf', JSON.stringify({ reason: '확인', requestKey: KEY })],
    ['/api/admin/finance/recovery-jobs/31/restart', 'POST', 'csrf', JSON.stringify({ reason: '재시작', requestKey: KEY })],
    ['/api/admin/orders/9007199254740993/contract-pdf/regenerate', 'POST', 'csrf',
      JSON.stringify({ reason: '재생성', requestKey: KEY, version: 2 })],
  ])
  assert.throws(() => executeReprocess({ action: 'TOPUP_RECHECK', target: '0', reason: '확인', requestKey: KEY }))
})

test('log queries send targetId only together with an action', async () => {
  const sent = []
  globalThis.fetch = async (url) => { sent.push(url); return json({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 }) }
  await getActionLogs({ action: 'TOPUP_RECHECK', targetId: '9' }, 1)
  await getActionLogs({ targetId: '9' }, 0)
  await getActionLogs({ orderId: '7' }, 0)
  assert.deepEqual(sent, [
    '/api/admin/action-logs?page=1&size=20&action=TOPUP_RECHECK&targetId=9',
    '/api/admin/action-logs?page=0&size=20',
    '/api/admin/action-logs?page=0&size=20&orderId=7',
  ])
})

test('an unclear failure keeps the request and the retry resends the same key and body', async () => {
  const client = newClient()
  const bodies = []
  let fail = true
  globalThis.fetch = async (url, options) => {
    const token = csrf(url); if (token) return token
    bodies.push(options.body)
    if (fail) throw new TypeError('network failed')
    return json(receipt(), 200)
  }
  const command = { action: 'TOPUP_RECHECK', target: '9', reason: '확인', requestKey: KEY }
  await assert.rejects(submitReprocess(client, 'TOPUP_RECHECK', '9', command, () => true))
  assert.equal(client.getQueryData(pendingReprocessKey('TOPUP_RECHECK', '9')).phase, 'uncertain')
  // 재전송은 새 입력을 무시하고 저장한 요청을 그대로 보낸다.
  fail = false
  const other = { ...command, reason: '다른 사유', requestKey: crypto.randomUUID() }
  await submitReprocess(client, 'TOPUP_RECHECK', '9', other, () => true)
  assert.equal(bodies.length, 2)
  assert.equal(bodies[1], bodies[0])
  const stored = client.getQueryData(pendingReprocessKey('TOPUP_RECHECK', '9'))
  assert.equal(stored.phase, 'done')
  assert.equal(stored.receipt.resultCode, 'TOPUP_CREDITED')
  client.clear()
})

test('a clear first rejection forgets the request; a rejected retry stays uncertain', async () => {
  const client = newClient()
  const conflict = () => json({ code: 'ADMIN_ACTION_IN_PROGRESS', message: '진행 중인 재처리 요청이 있어요.', fieldErrors: [], traceId: 't' }, 409)
  globalThis.fetch = async (url) => csrf(url) ?? conflict()
  const command = { action: 'TOPUP_RECHECK', target: '9', reason: '확인', requestKey: KEY }
  await assert.rejects(submitReprocess(client, 'TOPUP_RECHECK', '9', command, () => true), /진행 중인 재처리 요청/)
  assert.equal(client.getQueryData(pendingReprocessKey('TOPUP_RECHECK', '9')), null)

  // 첫 요청 결과가 불명확했다면, 재전송 거절로 첫 요청이 기록되지 않았다고 단정하지 않는다.
  client.setQueryData(pendingReprocessKey('TOPUP_RECHECK', '9'), { command, phase: 'uncertain', operationId: 'x' })
  await assert.rejects(submitReprocess(client, 'TOPUP_RECHECK', '9', undefined, () => true))
  assert.equal(client.getQueryData(pendingReprocessKey('TOPUP_RECHECK', '9')).phase, 'uncertain')
  client.clear()
})

test('nothing is sent while sending or after the account changed', async () => {
  const client = newClient()
  let calls = 0
  globalThis.fetch = async () => { calls++; return json(receipt()) }
  const command = { action: 'TOPUP_RECHECK', target: '9', reason: '확인', requestKey: KEY }
  await assert.rejects(submitReprocess(client, 'TOPUP_RECHECK', '9', command, () => false), (e) => e.name === 'AbortError')
  client.setQueryData(pendingReprocessKey('TOPUP_RECHECK', '9'), { command, phase: 'sending', operationId: 'x' })
  await assert.rejects(submitReprocess(client, 'TOPUP_RECHECK', '9', command, () => true), /이미 처리 중/)
  assert.equal(calls, 0)
  client.clear()
})

test('result labels never turn an accepted restart or unknown result into success', () => {
  assert.match(resultLabel({ status: 'SUCCEEDED', resultCode: 'BATCH_RESTART_ACCEPTED' }), /끝났다는 뜻은 아니/)
  assert.match(resultLabel({ status: 'SUCCEEDED', resultCode: 'TOPUP_RESOLVED_FAILED' }), /충전되지 않았어요/)
  assert.match(resultLabel({ status: 'UNKNOWN', resultCode: 'LEASE_EXPIRED' }), /직접 확인/)
  assert.match(resultLabel({ status: 'REQUESTED', resultCode: null }), /아직 결과를 기록하지 않았어요/)
  assert.equal(resultLabel({ status: 'FAILED', resultCode: 'SOMETHING_NEW' }), 'SOMETHING_NEW')
})

// ---------- 화면 ----------

function render(url, path, element, client) {
  return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [url] },
    createElement(QueryClientProvider, { client }, createElement(Routes, null, createElement(Route, { path, element })))))
}

test('panel hides for non-candidates and shows only "same request" for an uncertain one', () => {
  const client = newClient()
  try {
    const panel = (eligible) => createElement(AdminReprocessPanel, { action: 'TOPUP_RECOVERY_RESTART', target: '31', eligible })
    assert.equal(render('/', '/', panel(false), client), '')
    client.setQueryData(pendingReprocessKey('TOPUP_RECOVERY_RESTART', '31'), {
      command: { action: 'TOPUP_RECOVERY_RESTART', target: '31', reason: '이전 사유', requestKey: KEY }, phase: 'uncertain', operationId: 'x' })
    // 후보에서 빠졌어도 불명확한 요청은 확인할 수 있어야 한다.
    const html = render('/', '/', panel(false), client)
    assert.match(html, /<details[^>]*open=""/)
    assert.match(html, /서버가 이미 처리했을 수 있어요/)
    assert.match(html, /이전 사유/)
    assert.match(html, /<button type="submit">같은 요청으로 결과 확인<\/button>/)
    assert.doesNotMatch(html, /재시작 요청/)
  } finally { client.clear() }
})

test('a finished request shows the receipt, not a success claim for an accepted restart', () => {
  const client = newClient()
  try {
    client.setQueryData(pendingReprocessKey('TOPUP_RECOVERY_RESTART', '31'), {
      command: { action: 'TOPUP_RECOVERY_RESTART', target: '31', reason: '재시작', requestKey: KEY }, phase: 'done', operationId: 'x',
      receipt: receipt({ action: 'TOPUP_RECOVERY_RESTART', targetType: 'BATCH_JOB_EXECUTION', targetId: '31', reason: '재시작',
        resultCode: 'BATCH_RESTART_ACCEPTED' }) })
    const html = render('/', '/', createElement(AdminReprocessPanel, { action: 'TOPUP_RECOVERY_RESTART', target: '31', eligible: true }), client)
    assert.match(html, /충전 복구 작업 재시작 · 처리됨/)
    assert.match(html, /재시작을 접수했어요\. 작업이 끝났다는 뜻은 아니/)
    assert.match(html, /<button type="button">새 요청 작성<\/button>/)
  } finally { client.clear() }
})

test('log tab lists receipts with Korean status, reason and target', () => {
  const client = newClient()
  try {
    client.setQueryData(['admin', 'action-logs', { action: 'CONTRACT_PDF_REGENERATE' }, 0], {
      items: [receipt({ action: 'CONTRACT_PDF_REGENERATE', targetType: 'CONTRACT', targetId: '901', orderId: '55', reason: '고객 요청',
        status: 'UNKNOWN', resultCode: 'PDF_RESULT_UNCONFIRMED' })], page: 0, size: 20, totalElements: 1, totalPages: 1 })
    const html = render('/admin/finance?tab=logs&action=CONTRACT_PDF_REGENERATE', '/admin/finance', createElement(AdminFinancePage), client)
    assert.match(html, /aria-current="page"[^>]*>관리자 작업 이력</)
    assert.match(html, /value="CONTRACT_PDF_REGENERATE" selected=""/)
    assert.match(html, /data-label="작업"><strong>계약 PDF 다시 만들기<\/strong>/)
    assert.match(html, /data-label="대상">계약 #901 · 주문 #55</)
    assert.match(html, /결과 확인 필요/)
    assert.match(html, /사유: 고객 요청/)
    assert.doesNotMatch(html, new RegExp(KEY), '요청 키는 보여 주지 않는다')
  } finally { client.clear() }
})
