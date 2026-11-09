import { useQuery } from '@tanstack/react-query'
import { getMyInquiries } from './api'

/**
 * 주문별 내 문의. 주문 명령 뒤 ['wallet'] 무효화로 함께 다시 조회되도록 wallet 하위 키를 쓴다.
 * 로그인·로그아웃 때 resetAccountState()가 지우므로 다른 계정에 남지 않는다.
 */
export const inquiryKeys = {
  forOrder: (orderId: string) => ['wallet', 'order-inquiries', orderId] as const,
}

export function useOrderInquiries(orderId: string, enabled: boolean) {
  return useQuery({
    queryKey: inquiryKeys.forOrder(orderId),
    queryFn: ({ signal }) => getMyInquiries(orderId, signal),
    enabled,
    retry: false,
  })
}
