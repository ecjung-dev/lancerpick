import assert from 'node:assert/strict'
import { after, afterEach, test } from 'node:test'
import { createElement } from 'react'
import { renderToStaticMarkup } from 'react-dom/server'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { createServer } from 'vite'
import {
  apiPdfRequest,
  ApiError,
  clearCsrfToken,
} from '../src/shared/api/client.ts'
import {
  getContract,
  generateContract,
  downloadContract,
  verifyContractPdf,
  prepareContract,
  signContract,
} from '../src/features/contracts/api.ts'

const originalFetch = globalThis.fetch
afterEach(() => {
  globalThis.fetch = originalFetch
  clearCsrfToken()
})
const server = await createServer({
  configFile: false,
  cacheDir: 'node_modules/.vite-test-contracts',
  server: { middlewareMode: true, ws: false, watch: null },
  appType: 'custom',
})
const { ContractPanel } = await server.ssrLoadModule(
  '/src/features/contracts/components/ContractPanel.tsx',
)
const { ApiError: RenderApiError } = await server.ssrLoadModule('/src/shared/api/client.ts')
after(async () => {
  await server.close()
})

test('private PDF sends credentials, bypasses cache and pins the exact version', async () => {
  globalThis.fetch = async (url, init) => {
    assert.equal(url, '/api/orders/9007199254740993/contract/file?version=2')
    assert.equal(init.credentials, 'same-origin')
    assert.equal(init.cache, 'no-store')
    assert.match(init.headers.get('Accept'), /application\/pdf/)
    return new Response('%PDF fixture', {
      headers: { 'Content-Type': 'application/pdf' },
    })
  }
  const file = await downloadContract('9007199254740993', 2)
  assert.equal(await file.text(), '%PDF fixture')
})
test('PDF errors stay structured and an HTML success cannot become a download', async () => {
  globalThis.fetch = async () =>
    new Response(
      JSON.stringify({
        code: 'AUTHENTICATION_REQUIRED',
        message: '로그인이 필요합니다.',
        fieldErrors: [],
        traceId: 'test',
      }),
      { status: 401 },
    )
  await assert.rejects(
    apiPdfRequest('/orders/1/contract/file'),
    (e) => e instanceof ApiError && e.status === 401,
  )
  globalThis.fetch = async () =>
    new Response('<html>proxy</html>', {
      headers: { 'Content-Type': 'text/html' },
    })
  await assert.rejects(
    apiPdfRequest('/orders/1/contract/file'),
    (e) => e.code === 'INVALID_RESPONSE',
  )
})
test('generation sends only version with CSRF and never retries an uncertain mutation', async () => {
  const calls = []
  globalThis.fetch = async (url, init) => {
    calls.push(url)
    if (url === '/api/auth/csrf')
      return Response.json({
        token: 'csrf-fixture',
        headerName: 'X-CSRF-TOKEN',
      })
    assert.deepEqual(JSON.parse(init.body), { version: 3 })
    assert.equal(init.headers.get('X-CSRF-TOKEN'), 'csrf-fixture')
    throw new TypeError('network')
  }
  await assert.rejects(
    generateContract('11', 3),
    (e) => e.code === 'NETWORK_ERROR',
  )
  assert.equal(calls.filter((url) => url.endsWith('/generate')).length, 1)
})
test('bad IDs and fractional or overflowing versions are rejected before fetch', async () => {
  globalThis.fetch = async () => {
    throw new Error('must not fetch')
  }
  for (const id of ['0', '../1', '9223372036854775808'])
    assert.throws(() => getContract(id))
  for (const version of [0, 1.5, 2147483648])
    assert.throws(() => downloadContract('1', version))
})
test('download hash must equal the fixed server metadata', async () => {
  const file = new Blob(['%PDF fixture'])
  const hash = Buffer.from(
    await crypto.subtle.digest('SHA-256', await file.arrayBuffer()),
  ).toString('hex')
  await verifyContractPdf(file, hash)
  await assert.rejects(verifyContractPdf(new Blob(['tampered']), hash), /변경/)
  await assert.rejects(verifyContractPdf(file, ''), /해시/)
})

function render(status, memberId = '7', fileHash = null, denied = false, panelProps = {}) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, retryOnMount: false } },
  })
  client.setQueryData(['wallet', 'contract-member'], { id: memberId })
  client.setQueryData(['wallet', 'contract', memberId, '11', 'current'], {
    id: '3',
    version: 1,
    status,
    fileHash,
    canonicalContent: JSON.stringify({
      standardBody: '<script>unsafe</script>',
      total: 100001,
    }),
    signatures: [],
    specialTerms: '',
  })
  if (denied) client.getQueryCache().find({ queryKey: ['wallet', 'contract', memberId, '11', 'current'] }).setState({ status: 'error', error: new Error('접근할 수 없습니다.') })
  try {
    return renderToStaticMarkup(
      createElement(
        QueryClientProvider,
        { client },
        createElement(ContractPanel, {
          orderId: '11',
          sellerId: '7',
          orderStatus: 'AWAITING_CONTRACT',
          ...panelProps,
        }),
      ),
    )
  } finally {
    client.clear()
  }
}

function renderContractError(orderStatus, {
  status = 404,
  code = 'RESOURCE_NOT_FOUND',
  memberId = '7',
  hasContractHistory = false,
  cached = false,
} = {}) {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, retryOnMount: false } },
  })
  client.setQueryData(['wallet', 'contract-member'], { id: memberId })
  const key = ['wallet', 'contract', memberId, '11', 'current']
  if (cached) {
    client.setQueryData(key, {
      version: 1, status: 'READY', fileHash: 'a'.repeat(64), signatures: [],
      canonicalContent: JSON.stringify({ standardBody: 'cached-private-contract' }),
    })
  }
  const query = client.getQueryCache().build(client, { queryKey: key })
  query.setState({ status: 'error', fetchStatus: 'idle', error: new RenderApiError(status, {
    code, message: '계약 조회 실패', fieldErrors: [],
  }) })
  try {
    return renderToStaticMarkup(createElement(QueryClientProvider, { client },
      createElement(ContractPanel, { orderId: '11', sellerId: '7', orderStatus, hasContractHistory })))
  } finally {
    client.clear()
  }
}

test('unwritten contracts in pre-contract states are normal notices, not alerts', () => {
  for (const orderStatus of ['REQUESTED', 'AWAITING_CONTRACT', 'REJECTED', 'CANCELLED']) {
    const html = renderContractError(orderStatus)
    assert.match(html, /role="status"/)
    assert.match(html, /아직 작성된 계약서가 없습니다/)
    assert.doesNotMatch(html, /role="alert"|PDF 다운로드/)
  }
  assert.match(renderContractError('AWAITING_CONTRACT'), /특약으로 계약 초안 저장/)
  const buyer = renderContractError('AWAITING_CONTRACT', { memberId: '8' })
  assert.match(buyer, /판매자가 계약 초안을 작성하면/)
  assert.doesNotMatch(buyer, /특약으로 계약 초안 저장/)
})

test('a missing expected or previously known contract stays an error without exposing cached content', () => {
  for (const orderStatus of ['AWAITING_PAYMENT', 'IN_PROGRESS', 'REVISION_IN_PROGRESS', 'AWAITING_CONFIRMATION', 'COMPLETED']) {
    assert.match(renderContractError(orderStatus), /role="alert"/)
  }
  for (const options of [{ hasContractHistory: true }, { cached: true }]) {
    const html = renderContractError('AWAITING_CONTRACT', options)
    assert.match(html, /role="alert"/)
    assert.doesNotMatch(html, /아직 작성된 계약서가 없습니다|cached-private-contract|PDF 다운로드|특약으로 계약 초안 저장/)
  }
})

test('authentication, server, network and unrecognized 404 failures never become empty notices', () => {
  for (const status of [0, 401, 403, 500, 503]) {
    const html = renderContractError('AWAITING_CONTRACT', { status, code: 'REQUEST_FAILED' })
    assert.match(html, /role="alert"/)
    assert.doesNotMatch(html, /아직 작성된 계약서가 없습니다|특약으로 계약 초안 저장/)
  }
  assert.match(renderContractError('AWAITING_CONTRACT', { code: 'UNKNOWN_404' }), /role="alert"/)
})
test('signed contract asks for payment only while the order is awaiting payment', () => {
  const v3 = (orderStatus) =>
    render('SIGNED', '8', 'a'.repeat(64), false, { orderStatus, policyVersion: 'CONTRACT_V3' })
  assert.match(v3('AWAITING_PAYMENT'), /전액 적립금 결제를 진행해 주세요/)
  for (const orderStatus of ['IN_PROGRESS', 'AWAITING_CONFIRMATION', 'COMPLETED', 'CANCELLED']) {
    const html = v3(orderStatus)
    assert.match(html, /계약이 성립했습니다/)
    assert.doesNotMatch(html, /결제를 진행해 주세요/, orderStatus)
  }
  const v2 = (orderStatus) => render('SIGNED', '8', 'a'.repeat(64), false, { orderStatus })
  assert.match(v2('AWAITING_PAYMENT'), /선금 결제 기능 연결 전까지/)
  assert.doesNotMatch(v2('CANCELLED'), /선금 결제 기능 연결 전까지/)
})
test('seller sees draft generation and retry while buyer cannot generate', () => {
  assert.match(render('DRAFT'), /PDF 생성<\/button>/)
  assert.match(render('FAILED'), /생성 재시도/)
  assert.doesNotMatch(render('DRAFT', '8'), /PDF 생성<\/button>/)
  assert.match(render('GENERATING'), /5초마다/)
})
test('ready file escapes contract text and keeps signing disabled before PDF review', () => {
  const html = render('READY', '8', 'a'.repeat(64))
  assert.match(html, /PDF 다운로드/)
  assert.match(html, /&lt;script&gt;/)
  assert.doesNotMatch(html, /<script>/)
  assert.match(html, /<button disabled="">현재 계약에 서명<\/button>/)
})

test('contract panel has no second accept button; acceptance uses the order detail ACCEPT action', () => {
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, retryOnMount: false } },
  })
  client.setQueryData(['wallet', 'contract-member'], { id: '7' })
  try {
    const html = renderToStaticMarkup(
      createElement(
        QueryClientProvider,
        { client },
        createElement(ContractPanel, {
          orderId: '11',
          sellerId: '7',
          orderStatus: 'REQUESTED',
        }),
      ),
    )
    assert.doesNotMatch(html, /수락/)
  } finally {
    client.clear()
  }
})

test('draft and signing commands send the expected version and fixed document hashes with consent', async () => {
  const writes=[]
  globalThis.fetch=async (url,init) => {
    if(url==='/api/auth/csrf') return Response.json({ token:'csrf-test',headerName:'X-CSRF-TOKEN' })
    assert.equal(init.headers.get('X-CSRF-TOKEN'),'csrf-test')
    writes.push([url,JSON.parse(init.body)])
    return Response.json({ status:'READY' })
  }
  await prepareContract('11',2,'특약')
  await signContract('11',{ version:3,contentHash:'a'.repeat(64),fileHash:'b'.repeat(64) })
  assert.deepEqual(writes,[['/api/orders/11/contract/draft',{expectedVersion:2,specialTerms:'특약'}],['/api/orders/11/contract/sign',{version:3,contentHash:'a'.repeat(64),fileHash:'b'.repeat(64),agreed:true}]])
})

test('a failed access recheck hides previously cached contract content and download', () => {
  const html = render('READY', '8', 'a'.repeat(64), true)
  assert.doesNotMatch(html, /PDF 다운로드|unsafe/)
  assert.match(html, /접근할 수 없습니다/)
})
