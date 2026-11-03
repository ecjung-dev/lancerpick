import { ApiError, apiRequest } from '../../shared/api/client.ts'
import type { SellerProfile, SellerProfileInput, SellerSession } from './types.ts'

export function getSellerSession(signal?: AbortSignal) {
  return apiRequest<SellerSession>('/me', { signal })
}

export async function getMySellerProfile(signal?: AbortSignal): Promise<SellerProfile | null> {
  try {
    return await apiRequest<SellerProfile>('/me/seller-profile', { signal })
  } catch (error) {
    if (error instanceof ApiError && error.status === 404 && error.code === 'RESOURCE_NOT_FOUND') return null
    throw error
  }
}

export function saveSellerProfile(input: SellerProfileInput) {
  // 전체 교체. 회원 ID를 보내지 않고 서버 인증 정보로 대상을 결정한다.
  return apiRequest<SellerProfile>('/me/seller-profile', { method: 'PUT', json: input })
}
