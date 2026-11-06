import { getMemberSession } from '../auth/api.ts'
import { ApiError, apiRequest } from '../../shared/api/client.ts'
import type { SellerProfile, SellerProfileInput } from './types.ts'

export async function getPublicSellerProfile(id: string, signal?: AbortSignal) {
  if (!/^[0-9]{1,19}$/.test(id) || BigInt(id) <= 0n || BigInt(id) > 9223372036854775807n) {
    throw new Error('판매자 번호를 확인해 주세요.')
  }
  return apiRequest<SellerProfile>(`/sellers/${id}`, { signal })
}

export function getSellerSession(signal?: AbortSignal) {
  return getMemberSession(signal)
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
