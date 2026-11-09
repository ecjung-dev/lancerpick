import { useQuery } from '@tanstack/react-query'
import { getAdminInquiries, getAdminInquiry } from './inquiryApi'
import type { AdminInquiryFilter } from './inquiryTypes'

/**
 * 관리자 조회 캐시는 ['admin'] 아래에 둔다(로그인·로그아웃 때 resetAccountState()가 지운다).
 * 문의 상태는 판매자·구매자·다른 관리자가 바꿀 수 있어 매번 최신으로 조회하고 보관하지 않는다.
 */
export const adminInquiryKeys = {
  list: (filter: AdminInquiryFilter, page: number) => ['admin', 'inquiries', filter, page] as const,
  detail: (id: string) => ['admin', 'inquiry', id] as const,
}

const fresh = { retry: false, staleTime: 0, gcTime: 0 } as const

export function useAdminInquiries(filter: AdminInquiryFilter, page: number) {
  return useQuery({ queryKey: adminInquiryKeys.list(filter, page), queryFn: ({ signal }) => getAdminInquiries(filter, page, signal), ...fresh })
}

export function useAdminInquiry(id: string) {
  return useQuery({ queryKey: adminInquiryKeys.detail(id), queryFn: ({ signal }) => getAdminInquiry(id, signal), ...fresh })
}

/** URL 쿼리에서 목록 필터를 읽는다. 허용하지 않는 값은 "조건 없음"으로 바꿔 서버 400을 일으키지 않는다. */
export function readInquiryFilter(params: URLSearchParams): AdminInquiryFilter {
  const status = params.get('status')
  const type = params.get('type')
  return {
    status: status === 'OPEN' || status === 'IN_REVIEW' || status === 'CLOSED' ? status : '',
    type: type === 'CANCELLATION_REVIEW' || type === 'GENERAL' ? type : '',
  }
}

export function inquiryTypeLabel(type: string) {
  return type === 'CANCELLATION_REVIEW' ? '취소 검토' : '일반 문의'
}
