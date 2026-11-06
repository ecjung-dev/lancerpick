import { formatWon } from '../../shared/lib/format'
import type { ProductDetail } from './types'

export function orderMinimumNotice(product: ProductDetail) {
  const minimum = product.minimumOrderAmount
  if (minimum == null || product.priceAmount >= minimum) return null
  return `계약 주문은 ${formatWon(minimum)} 이상인 상품부터 신청할 수 있어요.`
}
