import assert from 'node:assert/strict'
import { afterEach, test } from 'node:test'
import { QueryClient, QueryObserver, skipToken } from '@tanstack/react-query'
import {
  beginChatSend,
  confirmChatSendFromHistory,
  pendingChatKey,
  settleChatSend,
} from '../src/features/order-chat/pending.ts'
import {
  memberQueryKey,
  resetAccountState,
} from '../src/features/auth/session.ts'

const clients = []
const account = {
  id: '7',
  email: 'fixture@example.com',
  displayName: 'fixture',
  role: 'USER',
  hasSellerProfile: false,
}
function client() {
  const result = new QueryClient({
    defaultOptions: { queries: { gcTime: 0, retry: false } },
  })
  result.setQueryDefaults(memberQueryKey, { gcTime: Infinity })
  result.setQueryData(memberQueryKey, account)
  clients.push(result)
  return result
}
afterEach(() => {
  clients.splice(0).forEach((client) => client.clear())
})

test('leaving a room does not expire its unconfirmed UUID and a retry keeps the original body', async () => {
  const cache = client()
  const first = beginChatSend(cache, '7', '11', '  원래 메시지  ')
  assert.equal(beginChatSend(cache, '7', '11', '빠른 중복 클릭'), undefined)
  await new Promise((resolve) => setTimeout(resolve, 10))
  assert.equal(
    cache.getQueryData(pendingChatKey('7', '11')).input.clientMessageId,
    first.input.clientMessageId,
  )
  settleChatSend(cache, '7', '11', first, 'uncertain')
  const second = beginChatSend(cache, '7', '11', '다른 본문으로 바꾸지 않음')
  assert.deepEqual(second.input, first.input)
  assert.equal(second.input.content, '원래 메시지')
  assert.equal(second.revision, 2)
  assert.equal(
    beginChatSend(cache, '7', '12', '다른 주문').input.clientMessageId ===
      first.input.clientMessageId,
    false,
  )
  assert.equal(beginChatSend(cache, '8', '11', '타인 계정'), undefined)
})

test('late failure cannot overwrite a retry, and a late success settles only its own UUID', () => {
  const cache = client()
  const first = beginChatSend(cache, '7', '11', '메시지')
  settleChatSend(cache, '7', '11', first, 'uncertain')
  const retry = beginChatSend(cache, '7', '11', '')
  settleChatSend(cache, '7', '11', first, 'rejected')
  assert.equal(cache.getQueryData(pendingChatKey('7', '11')).phase, 'sending')
  settleChatSend(cache, '7', '11', first, 'confirmed')
  assert.equal(cache.getQueryData(pendingChatKey('7', '11')), null)
  const next = beginChatSend(cache, '7', '11', '새 메시지')
  assert.notEqual(next.input.clientMessageId, first.input.clientMessageId)
  settleChatSend(cache, '7', '11', retry, 'uncertain')
  settleChatSend(cache, '7', '11', retry, 'confirmed')
  assert.equal(
    cache.getQueryData(pendingChatKey('7', '11')).input.clientMessageId,
    next.input.clientMessageId,
  )
})

test('history acknowledgment clears only a matching pending message; definite rejection permits editing', () => {
  const cache = client()
  const first = beginChatSend(cache, '7', '11', '메시지')
  confirmChatSendFromHistory(cache, '7', '11', 'different-id')
  assert.ok(cache.getQueryData(pendingChatKey('7', '11')))
  confirmChatSendFromHistory(cache, '7', '11', first.input.clientMessageId)
  assert.equal(cache.getQueryData(pendingChatKey('7', '11')), null)
  const rejected = beginChatSend(cache, '7', '11', '거절될 본문')
  settleChatSend(cache, '7', '11', rejected, 'rejected')
  assert.equal(cache.getQueryData(pendingChatKey('7', '11')), null)
  assert.notEqual(
    beginChatSend(cache, '7', '11', '수정한 본문').input.clientMessageId,
    rejected.input.clientMessageId,
  )
})

test('cache invalidation neither discards the UUID nor triggers an automatic resend', async () => {
  const cache = client()
  const first = beginChatSend(cache, '7', '11', '메시지')
  const observer = new QueryObserver(cache, {
    queryKey: pendingChatKey('7', '11'),
    queryFn: skipToken,
    gcTime: Infinity,
  })
  const unsubscribe = observer.subscribe(() => {})
  try {
    await cache.invalidateQueries()
    assert.equal(
      observer.getCurrentResult().data.input.clientMessageId,
      first.input.clientMessageId,
    )
    assert.equal(observer.getCurrentResult().fetchStatus, 'idle')
  } finally {
    unsubscribe()
  }
})

test('account cleanup removes pending text and late completions cannot restore it even after relogin', async () => {
  const cache = client()
  const first = beginChatSend(cache, '7', '11', '개인 메시지')
  await resetAccountState(cache, { ...account, id: '8' })
  assert.equal(cache.getQueryData(pendingChatKey('7', '11')), undefined)
  settleChatSend(cache, '7', '11', first, 'uncertain')
  assert.equal(cache.getQueryData(pendingChatKey('7', '11')), undefined)
  await resetAccountState(cache, account)
  const next = beginChatSend(cache, '7', '11', '다시 로그인한 메시지')
  settleChatSend(cache, '7', '11', first, 'confirmed')
  assert.equal(
    cache.getQueryData(pendingChatKey('7', '11')).input.clientMessageId,
    next.input.clientMessageId,
  )
})

test('a rejected retry cannot prove the first request failed, so a later retry still uses the same UUID', () => {
  const cache = client()
  const first = beginChatSend(cache, '7', '11', '최초 결과 불명확')
  settleChatSend(cache, '7', '11', first, 'uncertain')
  const retry = beginChatSend(cache, '7', '11', '')
  assert.equal(settleChatSend(cache, '7', '11', retry, 'rejected'), 'uncertain')
  const third = beginChatSend(cache, '7', '11', '본문 교체 금지')
  assert.deepEqual(third.input, first.input)
  settleChatSend(cache, '7', '11', third, 'confirmed')
  assert.equal(cache.getQueryData(pendingChatKey('7', '11')), null)
})
