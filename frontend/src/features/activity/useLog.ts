import { useInfiniteQuery, useQuery } from '@tanstack/react-query'
import { getJson } from '../../api/client'

export type LogEntry = {
  id: string
  createdAt: string
  type: string
  /** Done by an owner or admin (as opposed to, say, a season finishing). */
  adminAction: boolean
  summary: string
  reason: string | null
  fixtureId: string | null
  subjectUserId: string | null
}

type LogPage = { entries: LogEntry[]; hasMore: boolean }

const path = (slug: string) => `/api/public/leagues/${encodeURIComponent(slug)}/log`

/** The latest few entries, for the league page. */
export function useRecentLog(slug: string) {
  return useQuery({
    queryKey: ['leagues', 'log', slug, 'recent'],
    queryFn: () => getJson<LogPage>(path(slug)),
  })
}

/** The whole log, newest first, a page at a time. */
export function useLog(slug: string, adminOnly: boolean) {
  return useInfiniteQuery({
    queryKey: ['leagues', 'log', slug, { adminOnly }],
    queryFn: ({ pageParam }) => {
      const params = new URLSearchParams()
      if (pageParam) params.set('before', pageParam)
      if (adminOnly) params.set('adminOnly', 'true')
      return getJson<LogPage>(`${path(slug)}?${params}`)
    },
    initialPageParam: '',
    getNextPageParam: (last) => (last.hasMore ? last.entries.at(-1)?.createdAt : undefined),
  })
}

/** "Today 14:05", "Yesterday 09:12" or "3 Oct 18:40". */
export function formatWhen(iso: string) {
  const date = new Date(iso)
  const time = date.toLocaleTimeString(undefined, { hour: '2-digit', minute: '2-digit' })
  const days = Math.floor((startOfDay(new Date()) - startOfDay(date)) / 86_400_000)
  if (days === 0) return `Today ${time}`
  if (days === 1) return `Yesterday ${time}`
  return `${date.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })} ${time}`
}

function startOfDay(date: Date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
}
