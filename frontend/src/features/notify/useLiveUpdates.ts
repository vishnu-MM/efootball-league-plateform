import { useQueryClient } from '@tanstack/react-query'
import { useEffect } from 'react'
import { UNREAD_KEY } from './useNotifications'

/**
 * While signed in, keeps one Server-Sent Events stream open. A "notification" event updates the bell straight away;
 * a "league" event refetches every query about that league (season, table, fixtures, log, stats, squads).
 * EventSource reconnects by itself after the server ends a stream or the network drops.
 */
export function useLiveUpdates(signedIn: boolean) {
  const queryClient = useQueryClient()

  useEffect(() => {
    if (!signedIn || typeof EventSource === 'undefined') return
    const source = new EventSource('/api/stream', { withCredentials: true })

    source.addEventListener('notification', (event) => {
      const { unread } = JSON.parse((event as MessageEvent<string>).data) as { unread: number }
      queryClient.setQueryData(UNREAD_KEY, unread)
      void queryClient.invalidateQueries({ queryKey: ['notifications', 'list'] })
    })
    source.addEventListener('league', (event) => {
      const { slug } = JSON.parse((event as MessageEvent<string>).data) as { slug: string }
      void queryClient.invalidateQueries({
        predicate: (query) => query.queryKey[0] === 'leagues' && query.queryKey.includes(slug),
      })
    })

    return () => source.close()
  }, [signedIn, queryClient])
}
