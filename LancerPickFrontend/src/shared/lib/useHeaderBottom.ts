import { useSyncExternalStore } from 'react'

function header() {
  return document.querySelector('[data-app-header]')
}

function snapshot() {
  return Math.max(0, Math.min(window.innerHeight, header()?.getBoundingClientRect().bottom ?? 0))
}

function subscribe(changed: () => void) {
  const observer = new ResizeObserver(changed)
  const element = header()
  if (element) observer.observe(element)
  window.addEventListener('resize', changed)
  window.addEventListener('scroll', changed, { passive: true })
  return () => {
    observer.disconnect()
    window.removeEventListener('resize', changed)
    window.removeEventListener('scroll', changed)
  }
}

/** Portaled drawers start below the visible header, including responsive wrapping. */
export function useHeaderBottom() {
  return useSyncExternalStore(subscribe, snapshot, () => 0)
}
