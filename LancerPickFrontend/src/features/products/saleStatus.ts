import { apiRequest } from '../../shared/api/client.ts'
import type { OwnedProduct } from './management.ts'

export type SaleStatus = 'ON_SALE' | 'STOPPED'
export interface SaleStatusChange { saleStatus: SaleStatus; expectedVersion: number }

export function changeProductSaleStatus(id: string, input: SaleStatusChange) {
  if (!/^[0-9]{1,19}$/.test(id) || BigInt(id) <= 0n || BigInt(id) > 9223372036854775807n) throw new Error('상품 번호를 확인해 주세요.')
  if (!['ON_SALE', 'STOPPED'].includes(input.saleStatus) || !Number.isSafeInteger(input.expectedVersion) || input.expectedVersion < 0) throw new Error('판매 상태와 상품 버전을 확인해 주세요.')
  return apiRequest<OwnedProduct>(`/products/${id}/sale-status`, {
    method: 'PATCH', json: { saleStatus: input.saleStatus, expectedVersion: input.expectedVersion },
  })
}
