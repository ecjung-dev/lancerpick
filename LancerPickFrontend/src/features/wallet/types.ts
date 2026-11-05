export interface WalletBalance {
  balance: number
  currency: 'KRW'
  isTest: boolean
}

export type WalletEntryType = 'INITIAL_GRANT' | 'PAYMENT' | 'REFUND' | 'TOPUP' | 'SELLER_PAYOUT'

export interface WalletEntry {
  id: string
  type: WalletEntryType
  amountDelta: number
  balanceAfter: number
  orderId: string | null
  createdAt: string
}

export type TopupStatus = 'READY' | 'PROCESSING' | 'UNKNOWN' | 'COMPLETED' | 'FAILED'

export interface WalletTopup {
  id: string
  tossOrderId: string
  amount: number
  currency: 'KRW'
  isTest: boolean
  status: TopupStatus
  createdAt: string
}

export interface TopupConfirmation { paymentKey: string; orderId: string; amount: number }
