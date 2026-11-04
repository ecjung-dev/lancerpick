/** API 명세 9절. ID는 큰 숫자 정밀도 손실을 막기 위해 문자열이다. */
export type SaleStatus = 'ON_SALE' | 'STOPPED'

export interface AdminProductSummary {
  id: string
  title: string
  priceAmount: number
  durationDays: number
  category: { id: string; name: string }
  seller: { id: string; displayName: string }
  saleStatus: SaleStatus
  isHidden: boolean
  version: number
  createdAt: string
  updatedAt: string
}

export interface AdminProductDetail extends AdminProductSummary {
  description: string
  scopeDescription: string
  preparationDescription: string
}

export interface ModerationLog {
  id: string
  adminId: string
  action: 'HIDE' | 'RESTORE'
  reason: string
  createdAt: string
}

/** 목록 필터. 빈 문자열은 "조건 없음"이다. */
export interface AdminProductFilter {
  keyword: string
  isHidden: '' | 'true' | 'false'
  saleStatus: '' | SaleStatus
}

export interface VisibilityInput {
  hidden: boolean
  reason: string
  expectedVersion: number
}
