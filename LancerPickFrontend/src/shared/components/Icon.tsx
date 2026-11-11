import type { CSSProperties } from 'react'

export type IconName = 'refresh' | 'search' | 'design' | 'code' | 'video' | 'marketing' | 'translate' | 'document' | 'arrow' | 'check' | 'bell' | 'message'
const paths: Record<IconName, React.ReactNode> = {
  refresh: <><path d="M20 7v5h-5M4 17v-5h5" /><path d="M6 7a7 7 0 0 1 12-1l2 6M4 12l2 6a7 7 0 0 0 12-1" /></>,
  message: <path d="M4 3h16a2 2 0 0 1 2 2v11a2 2 0 0 1-2 2H9l-6 4V5a2 2 0 0 1 1-2Z" />,
  bell: <><path d="M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9ZM10 21h4" /></>,
  search: <><circle cx="10.5" cy="10.5" r="6.5" /><path d="m16 16 4.5 4.5" /></>,
  design: <><path d="m5 15 10-11 5 5L9 19l-6 2 2-6Z M13 6l5 5 M5 15l4 4" /></>,
  code: <path d="m8 6-6 6 6 6m8-12 6 6-6 6m-3-15-2 18" />,
  video: <><rect x="3" y="5" width="18" height="14" rx="3" /><path d="m10 9 5 3-5 3V9Z" /></>,
  marketing: <><path d="m3 9 15-5v16L3 15V9Zm15 0h3v6h-3M6 16l1 5h4l-2-4" /></>,
  translate: <><path d="M3 5h12M9 2v3M5 5c0 6 5 9 9 10M13 5c0 6-5 9-10 11m11 5 4-11 4 11m-6-4h4" /></>,
  document: <><path d="M5 2h9l5 5v15H5V2Zm9 0v6h5M8 12h8M8 16h8" /></>,
  arrow: <path d="M4 12h16m-6-6 6 6-6 6" />,
  check: <><circle cx="12" cy="12" r="9" /><path d="m8 12 3 3 5-6" /></>,
}

export function Icon({ name, size = 24, style }: { name: IconName; size?: number; style?: CSSProperties }) {
  return <svg aria-hidden="true" width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" style={style}>{paths[name]}</svg>
}
