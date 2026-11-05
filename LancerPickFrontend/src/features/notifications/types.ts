export interface NotificationItem {
  id: string
  type: string
  orderId: string | null
  productTitle: string | null
  read: boolean
  createdAt: string
}

export interface UnreadCount {
  count: number
}
