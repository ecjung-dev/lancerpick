// API 명세에 맞춘 요청·응답 타입을 구현할 때 정의합니다.
export interface Category { id: string; name: string; displayOrder: number }
export interface ProductSummary {
  id: string
  title: string
  priceAmount: number
  durationDays: number
  category: Category
  seller: { id: string; displayName: string }
}
export interface ProductSearch { keyword: string; categoryId: string; page: number; size: number }
export interface ProductDetail extends ProductSummary {
  description: string
  scopeDescription: string
  preparationDescription: string
  version: number
  saleStatus: 'ON_SALE'
  createdAt: string
  updatedAt: string
}
