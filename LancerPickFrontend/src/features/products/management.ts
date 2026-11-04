import { apiRequest } from '../../shared/api/client.ts'
import { normalizeText, codePointLength } from '../../shared/lib/text.ts'
import type { ProductSummary } from './types.ts'

export interface ProductInput { categoryId: string; title: string; description: string; scopeDescription: string; preparationDescription: string; priceAmount: number; durationDays: number }
export interface OwnedProduct extends ProductSummary { description: string; scopeDescription: string; preparationDescription: string; version: number; saleStatus: string; isHidden: boolean; createdAt: string; updatedAt: string }
export const textLimits = { title: 100, description: 10000, scopeDescription: 3000, preparationDescription: 3000 } as const
const fieldNames = { title: '상품명', description: '상품 설명', scopeDescription: '작업 범위', preparationDescription: '준비사항' }
export function validateProduct(input: ProductInput): ProductInput {
  if (!/^[0-9]{1,19}$/.test(input.categoryId) || BigInt(input.categoryId) <= 0n || BigInt(input.categoryId) > 9223372036854775807n) throw new Error('카테고리를 선택해 주세요.')
  const normalized = { ...input }
  for (const field of Object.keys(textLimits) as (keyof typeof textLimits)[]) {
    normalized[field] = normalizeText(input[field])
    if (codePointLength(normalized[field]) < 1 || codePointLength(normalized[field]) > textLimits[field]) throw new Error(`${fieldNames[field]}은 필수 입력이며 최대 ${textLimits[field]}자입니다.`)
  }
  if (!Number.isInteger(input.priceAmount) || input.priceAmount < 1 || input.priceAmount > 1000000) throw new Error('가격은 1~1,000,000원 정수로 입력해 주세요.')
  if (!Number.isInteger(input.durationDays) || input.durationDays < 1 || input.durationDays > 30) throw new Error('작업 기간은 1~30일 정수로 입력해 주세요.')
  return { categoryId: normalized.categoryId, title: normalized.title, description: normalized.description, scopeDescription: normalized.scopeDescription, preparationDescription: normalized.preparationDescription, priceAmount: normalized.priceAmount, durationDays: normalized.durationDays }
}
function idPath(id: string) { if (!/^[0-9]{1,19}$/.test(id) || BigInt(id) <= 0n || BigInt(id) > 9223372036854775807n) throw new Error('상품 번호를 확인해 주세요.'); return `/products/${id}` }
export const getOwnedProduct = (id: string, signal?: AbortSignal) => apiRequest<OwnedProduct>(`/me${idPath(id)}`, { signal })
export const createProduct = (input: ProductInput) => apiRequest<OwnedProduct>('/products', { method: 'POST', json: validateProduct(input) })
export function patchProduct(id: string, patch: Partial<ProductInput>, expectedVersion: number) {
  if (!Number.isSafeInteger(expectedVersion) || expectedVersion < 0) throw new Error('상품 버전을 확인해 주세요.')
  return apiRequest<OwnedProduct>(idPath(id), { method: 'PATCH', json: { ...patch, expectedVersion } })
}
