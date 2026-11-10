import assert from 'node:assert/strict'
import { after, afterEach, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { MemoryRouter, Route, Routes } from 'react-router'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import { getAdminDashboard } from '../src/features/admin/dashboardApi.ts'

const server = await createServer({ configFile: false, cacheDir: 'node_modules/.vite-test-admin-dashboard',
  server: { middlewareMode: true, ws: false, watch: null }, appType: 'custom' })
const { AdminDashboardPage } = await server.ssrLoadModule('/src/features/admin/pages/AdminDashboardPage.tsx')
const { AdminNav } = await server.ssrLoadModule('/src/features/admin/pages/AdminNav.tsx')
after(async () => { await server.close() })

const original = globalThis.fetch
afterEach(() => { globalThis.fetch = original })

test('summary is a single GET without parameters', async () => {
  const sent = []
  globalThis.fetch = async (url, options) => {
    sent.push([url, options.method, options.body])
    return new Response(JSON.stringify({}), { status: 200, headers: { 'Content-Type': 'application/json' } })
  }
  await getAdminDashboard()
  assert.deepEqual(sent, [['/api/admin/dashboard', 'GET', undefined]])
})

const newClient = () => new QueryClient({ defaultOptions: { queries: { retry: false, retryOnMount: false } } })
function render(url, element, client) {
  return renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [url] },
    createElement(QueryClientProvider, { client }, createElement(Routes, null, createElement(Route, { path: url.split('?')[0], element })))))
}

test('cards link to the filtered admin screens, put items needing attention first and offer no command', () => {
  const client = newClient()
  try {
    client.setQueryData(['admin', 'dashboard'], {
      inquiries: { open: 0, inReview: 1 }, reviewTimers: { active: 7, expiringWithin24Hours: 0 },
      notifications: { failed: 1234, retrying: 0 }, topups: { unknown: 0 }, checkedAt: '2026-11-09T03:00:00Z',
    })
    const html = render('/admin', createElement(AdminDashboardPage), client)
    assert.match(html, /href="\/admin\/inquiries\?status=OPEN"/)
    assert.match(html, /href="\/admin\/inquiries\?status=IN_REVIEW"/)
    assert.match(html, /href="\/admin\/review-timers"/)
    assert.match(html, /href="\/admin\/outbox\?status=FAILED"/)
    assert.match(html, /href="\/admin\/outbox\?status=RETRYING"/)
    assert.match(html, /href="\/admin\/finance\?tab=topups&amp;status=UNKNOWN"/)
    assert.match(html, /1,234건/)
    assert.match(html, /진행 중 타이머 7건/)
    // 0이 아닌 항목(검토 중 문의·멈춘 알림)이 0건 항목보다 앞에 온다.
    const attention = html.indexOf('처리가 멈춘 알림')
    assert.ok(html.indexOf('검토 중인 문의') < html.indexOf('접수된 운영 문의'))
    assert.ok(attention < html.indexOf('접수된 운영 문의'))
    assert.equal(html.match(/class="[^"]*attention[^"]*"/g)?.length, 2, '건수가 있는 항목만 강조')
    assert.doesNotMatch(html, /<button[^>]*>(승인|해제|재처리|다시 보내기)/, '운영 현황에는 실행 버튼이 없다')
    // 서버 렌더링 시점에는 화면 진입 조회가 진행 중이라 "확인 중…"으로 막혀 있다.
    assert.match(html, /<button[^>]*>(다시 확인|확인 중…)<\/button>/)
  } finally { client.clear() }
})

test('admin menu marks the dashboard only on /admin itself', () => {
  const at = (url) => renderToStaticMarkup(createElement(MemoryRouter, { initialEntries: [url] }, createElement(AdminNav)))
  assert.match(at('/admin'), /aria-current="page"[^>]*href="\/admin"|href="\/admin"[^>]*aria-current="page"/)
  assert.doesNotMatch(at('/admin/outbox'), /href="\/admin"[^>]*aria-current="page"|aria-current="page"[^>]*href="\/admin"/)
})
