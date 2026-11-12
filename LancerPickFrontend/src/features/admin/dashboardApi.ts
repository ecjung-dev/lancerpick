import { useQuery } from '@tanstack/react-query'
import { apiRequest } from '../../shared/api/client.ts'

/**
 * 관리자 운영 현황(K3, 조회만). 각 숫자는 조회 순간의 건수이며 서로 다른 순간에 센 값일 수 있다. 시각은 UTC다.
 * 자세한 내용은 각 관리자 화면(문의·타이머·알림 처리·금융 조회)에서 본다.
 */
export interface AdminDashboard {
  inquiries: { open: number; inReview: number }
  reviewTimers: { active: number; expiringWithin24Hours: number }
  notifications: { failed: number; retrying: number }
  topups: { unknown: number }
  checkedAt: string
}

export const getAdminDashboard = (signal?: AbortSignal) => apiRequest<AdminDashboard>('/admin/dashboard', { signal })

/** ['admin'] 아래라 계정 전환 때 지워진다. 매번 최신으로 조회하고 보관하지 않는다. */
const dashboardKey = ['admin', 'dashboard'] as const

export const useAdminDashboard = () =>
  useQuery({ queryKey: dashboardKey, queryFn: ({ signal }) => getAdminDashboard(signal), retry: false, staleTime: 0, gcTime: 0 })
