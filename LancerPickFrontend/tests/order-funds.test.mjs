import assert from 'node:assert/strict'
import { after, afterEach, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import { getOrderFunds } from '../src/features/orders/api.ts'

const originalFetch = globalThis.fetch
afterEach(() => {
  globalThis.fetch = originalFetch
})
const server = await createServer({
  configFile: false,
  cacheDir: 'node_modules/.vite-test-order-funds',
  server: { middlewareMode: true, ws: false, watch: null },
  appType: 'custom',
})
const { OrderFundsPanel } = await server.ssrLoadModule(
  '/src/features/orders/components/OrderFundsPanel.tsx',
)
after(async () => {
  await server.close()
})

test('funds lookup keeps large IDs intact, sends session and abort signal, bypasses browser cache', async () => {
  const controller = new AbortController()
  globalThis.fetch = async (url, init) => {
    assert.equal(url, '/api/orders/9007199254740993/funds')
    assert.equal(init.credentials, 'same-origin')
    assert.equal(init.cache, 'no-store')
    assert.equal(init.signal, controller.signal)
    return Response.json({ status: 'HELD', amount: 600001 })
  }
  assert.equal(
    (await getOrderFunds('9007199254740993', controller.signal)).amount,
    600001,
  )
})

test('invalid order IDs never reach the server', () => {
  globalThis.fetch = async () => {
    throw new Error('must not fetch')
  }
  for (const id of ['0', '../1', '-1', '1.2', '9223372036854775808'])
    assert.throws(() => getOrderFunds(id))
})

function render(status, denied = false, orderStatus = 'AWAITING_PAYMENT') {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, retryOnMount: false } },
  })
  const queryKey = ['wallet', 'order-funds', '11']
  client.setQueryData(queryKey, {
    status,
    amount: 600001,
    heldAmount: status === 'HELD' ? 600001 : 0,
    refundedAmount: status === 'REFUNDED' ? 600001 : 0,
    feeAmount: status === 'RELEASED' ? 60000 : null,
    sellerPayout: status === 'RELEASED' ? 540001 : null,
    resolvedAt: null,
    paidAt: null,
  })
  if (denied)
    client
      .getQueryCache()
      .find({ queryKey })
      .setState({ status: 'error', error: new Error('다시 로그인해 주세요.') })
  try {
    return renderToStaticMarkup(
      createElement(
        QueryClientProvider,
        { client },
        createElement(OrderFundsPanel, { orderId: '11', orderStatus }),
      ),
    )
  } finally {
    client.clear()
  }
}

test('held money is distinguished from completed seller income and buyer refund', () => {
  assert.match(render('NOT_PAID'), /전액 결제 대기/)
  assert.match(render('HELD'), /주문 대금 보관 중/)
  assert.doesNotMatch(render('HELD'), /판매자 적립금 지급/)
  assert.match(render('RELEASED'), /판매자 지급 완료/)
  assert.match(render('RELEASED'), /540,001/)
  assert.match(render('RELEASED'), /60,000/)
  assert.match(render('REFUNDED'), /구매자 전액 반환 완료/)
})

test('failed permission recheck hides previously cached financial data', () => {
  const html = render('RELEASED', true)
  assert.match(html, /다시 로그인/)
  assert.doesNotMatch(html, /600,001|540,001|판매자 지급 완료/)
})

test('unpaid terminal orders show closure while paid refunds retain their money status', () => {
  for (const state of ['CANCELLED', 'REJECTED']) {
    const html = render('NOT_PAID', false, state)
    assert.match(html, /결제 없이 종료된 거래/)
    assert.match(html, /현재 보관금.*0원/)
    assert.doesNotMatch(html, /전액 결제 대기|구매자 전액 반환 완료/)
  }
  assert.match(render('NOT_PAID', false, 'AWAITING_CONTRACT'), /전액 결제 대기/)
  const refunded = render('REFUNDED', false, 'CANCELLED')
  assert.match(refunded, /구매자 전액 반환 완료/)
  assert.doesNotMatch(refunded, /결제 없이 종료된 거래/)
})
