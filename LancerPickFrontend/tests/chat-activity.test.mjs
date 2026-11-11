import assert from 'node:assert/strict'
import { test } from 'node:test'
import { hasNewChat } from '../src/features/order-chat/activity.ts'
import { chatEnterAction } from '../src/features/order-chat/keyboard.ts'

test('session hint excludes history, own messages and messages already viewed', () => {
  const state = { startedAt: '2026-11-11T01:00:00Z', seen: {} }
  const thread = { orderId: '1', lastSenderId: '2', lastMessageAt: '2026-11-11T10:01:00+09:00' }
  assert.equal(hasNewChat(thread, '1', state), true)
  assert.equal(hasNewChat(thread, '2', state), false)
  assert.equal(hasNewChat({ ...thread, lastMessageAt: '2026-11-11T00:59:00Z' }, '1', state), false)
  assert.equal(hasNewChat(thread, '1', { ...state, seen: { 1: '2026-11-11T01:01:00Z' } }), false)
  assert.equal(hasNewChat({ ...thread, lastMessageAt: null }, '1', state), false)
})
test('Enter sends, Alt+Enter inserts a newline, IME and held Enter never send', () => {
  const key = { key: 'Enter', altKey: false, isComposing: false, keyCode: 13, repeat: false }
  assert.equal(chatEnterAction(key), 'send')
  assert.equal(chatEnterAction({ ...key, altKey: true }), 'newline')
  assert.equal(chatEnterAction({ ...key, isComposing: true }), 'ignore')
  assert.equal(chatEnterAction({ ...key, keyCode: 229 }), 'ignore')
  assert.equal(chatEnterAction({ ...key, repeat: true }), 'ignore')
})
