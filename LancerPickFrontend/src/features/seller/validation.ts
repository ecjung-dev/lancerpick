import { codePointLength, normalizeText } from '../../shared/lib/text.ts'
import type { SellerProfileInput } from './types.ts'

export type ProfileErrors = Partial<Record<keyof SellerProfileInput, string>>

export function isSafePortfolioUrl(value: string) {
  try {
    const url = new URL(value)
    return /^https:\/\/[^/?#]+(?:[/?#]|$)/i.test(value)
      && url.protocol === 'https:' && !!url.hostname && !url.username && !url.password
      && !/^https:\/\/[^/]*@/i.test(value) && !/\s/.test(value)
  } catch {
    return false
  }
}

export function validateSellerProfile(draft: { introduction: string; specialty: string; portfolioUrl: string }) {
  const input: SellerProfileInput = {
    introduction: normalizeText(draft.introduction),
    specialty: normalizeText(draft.specialty),
    portfolioUrl: normalizeText(draft.portfolioUrl) || null,
  }
  const errors: ProfileErrors = {}
  if (!input.introduction || codePointLength(input.introduction) > 2000) errors.introduction = '소개는 공백 정리 후 1~2,000자여야 해요.'
  if (!input.specialty || codePointLength(input.specialty) > 100) errors.specialty = '전문 분야는 공백 정리 후 1~100자여야 해요.'
  if (input.portfolioUrl) {
    if (codePointLength(input.portfolioUrl) > 2048) errors.portfolioUrl = '주소는 최대 2,048자까지 입력할 수 있어요.'
    else if (!isSafePortfolioUrl(input.portfolioUrl)) {
      errors.portfolioUrl = '사용자 정보가 없는 HTTPS 주소를 입력해 주세요.'
    }
  }
  return { input, errors, valid: Object.keys(errors).length === 0 }
}
