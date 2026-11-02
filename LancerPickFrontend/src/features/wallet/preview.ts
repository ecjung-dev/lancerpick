import type { WalletBalance, WalletEntry } from './types'

// 화면 검토 전용 데이터. API 실패 시 대체 데이터로 사용하지 않는다.
export const previewBalance: WalletBalance = { balance: 870000, currency: 'KRW', isTest: true }
export const previewEntries: WalletEntry[] = [
  { id: '6', type: 'REFUND', amountDelta: 50000, balanceAfter: 870000, orderId: '1003', createdAt: '2026-11-02T05:30:00Z' },
  { id: '5', type: 'PAYMENT', amountDelta: -50000, balanceAfter: 820000, orderId: '1003', createdAt: '2026-11-02T02:00:00Z' },
  { id: '4', type: 'PAYMENT', amountDelta: -130000, balanceAfter: 870000, orderId: '1002', createdAt: '2026-11-01T04:20:00Z' },
  { id: '3', type: 'REFUND', amountDelta: 130000, balanceAfter: 1000000, orderId: '1001', createdAt: '2026-10-31T07:00:00Z' },
  { id: '2', type: 'PAYMENT', amountDelta: -130000, balanceAfter: 870000, orderId: '1001', createdAt: '2026-10-30T03:00:00Z' },
  { id: '1', type: 'INITIAL_GRANT', amountDelta: 1000000, balanceAfter: 1000000, orderId: null, createdAt: '2026-10-28T01:00:00Z' },
]
