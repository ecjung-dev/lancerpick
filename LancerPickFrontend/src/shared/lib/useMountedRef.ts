import { useEffect, useRef, type RefObject } from 'react'

/**
 * 마운트 표시 effect 본문. 설정 때 true, 정리 때 false로 둔다.
 * StrictMode의 설정 → 정리 → 재설정에서도 마지막 설정이 true로 되돌린다.
 */
export function markMounted(ref: RefObject<boolean>) {
  ref.current = true
  return () => {
    ref.current = false
  }
}

/** 비동기 응답이 돌아왔을 때 컴포넌트가 아직 화면에 있는지 확인하는 ref. */
export function useMountedRef(): RefObject<boolean> {
  const ref = useRef(true)
  useEffect(() => markMounted(ref), [])
  return ref
}
