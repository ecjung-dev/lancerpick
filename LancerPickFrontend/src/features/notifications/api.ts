import { apiRequest } from '../../shared/api/client.ts'
import type { PageResponse } from '../../shared/api/types'
import type { NotificationItem, UnreadCount } from './types.ts'

function notificationPath(id: string) {
  if (!/^[0-9]+$/.test(id) || BigInt(id) < 1n || BigInt(id) > 9223372036854775807n)
    throw new Error('알림 ID를 확인해 주세요.')
  return `/notifications/${id}`
}

export const getNotifications = (page: number, signal?: AbortSignal) =>
  apiRequest<PageResponse<NotificationItem>>(`/notifications?page=${page}&size=20`, { signal })

export const getUnreadNotificationCount = (signal?: AbortSignal) =>
  apiRequest<UnreadCount>('/notifications/unread-count', { signal })

export const markNotificationRead = (id: string) =>
  apiRequest<void>(`${notificationPath(id)}/read`, { method: 'POST' })

export const markAllNotificationsRead = () =>
  apiRequest<void>('/notifications/read-all', { method: 'POST' })
