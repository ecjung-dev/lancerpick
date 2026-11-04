import { normalizeText, codePointLength } from '../../shared/lib/text.ts'
import type { CreateOrderInput } from './types'


/** 상품 상세에서 받은 버전과 같은 입력을 재전송할 때 같은 키를 유지한다. */
export function prepareOrderInput(input: CreateOrderInput): CreateOrderInput {
  if (!/^[0-9]+$/.test(input.productId) || BigInt(input.productId) < 1n || BigInt(input.productId) > 9223372036854775807n) throw new Error('상품 ID를 확인해 주세요.')
  if (!Number.isSafeInteger(input.expectedProductVersion) || input.expectedProductVersion < 0) throw new Error('상품 버전을 확인해 주세요.')
  const requirements = normalizeText(input.requirements)
  if (codePointLength(requirements) < 1 || codePointLength(requirements) > 5000) throw new Error('요구사항은 1~5,000자로 입력해 주세요.')
  if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(input.requestKey)) throw new Error('주문 요청 키를 확인해 주세요.')
  return { productId: input.productId, expectedProductVersion: input.expectedProductVersion, requirements, requestKey: input.requestKey }
}
