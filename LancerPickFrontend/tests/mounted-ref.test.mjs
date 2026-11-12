import assert from 'node:assert/strict'
import { test } from 'node:test'
import { markMounted } from '../src/shared/lib/useMountedRef.ts'

test('마운트 표시는 설정 때 true, 정리 때 false이고 StrictMode 재설정 뒤 다시 true다', () => {
  const ref = { current: false }
  const cleanup = markMounted(ref)
  assert.equal(ref.current, true)
  cleanup()
  assert.equal(ref.current, false)
  // StrictMode 개발 모드: 설정 → 정리 → 재설정
  const again = markMounted(ref)
  assert.equal(ref.current, true)
  again()
  assert.equal(ref.current, false)
})
