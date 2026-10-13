import { useInfiniteQuery, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getJson, send } from '../../api/client'

export type NotificationItem = {
  id: string
  type: string
  text: string
  link: string
  createdAt: string
  read: boolean
  leagueName: string | null
}

type NotificationPage = { items: NotificationItem[]; hasMore: boolean }

export const UNREAD_KEY = ['notifications', 'unread'] as const

export function useUnreadCount(enabled: boolean) {
  return useQuery({
    queryKey: UNREAD_KEY,
    queryFn: async () => (await getJson<{ unread: number }>('/api/notifications/unread-count')).unread,
    enabled,
  })
}

export function useNotifications() {
  return useInfiniteQuery({
    queryKey: ['notifications', 'list'],
    queryFn: ({ pageParam }) =>
      getJson<NotificationPage>(`/api/notifications${pageParam ? `?before=${encodeURIComponent(pageParam)}` : ''}`),
    initialPageParam: '',
    getNextPageParam: (last) => (last.hasMore ? last.items.at(-1)?.createdAt : undefined),
  })
}

/** Marks some notifications read, or all of them when no ids are given. */
export function useMarkRead() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (ids?: string[]) => send('POST', '/api/notifications/read', { ids: ids ?? [] }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['notifications'] }),
  })
}
