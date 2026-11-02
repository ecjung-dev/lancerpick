export interface WalletBalance {
  balance: number
  currency: 'KRW'
  isTest: boolean
}

export type WalletEntryType = 'INITIAL_GRANT' | 'PAYMENT' | 'REFUND'

export interface WalletEntry {
  id: string
  type: WalletEntryType
  amountDelta: number
  balanceAfter: number
  orderId: string | null
  createdAt: string
}
