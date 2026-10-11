import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getJson, send } from '../../api/client'

export type Visibility = 'PUBLIC' | 'PRIVATE'
export type Role = 'OWNER' | 'ADMIN' | 'MANAGER'

export type LeagueSummary = {
  id: string
  name: string
  slug: string
}

export type MyLeague = LeagueSummary & { role: Role }

export type LeagueDetail = LeagueSummary & {
  visibility: Visibility
  myRole: Role | null
  memberCount: number
}

export function usePublicLeagues() {
  return useQuery({
    queryKey: ['leagues', 'public'],
    queryFn: () => getJson<LeagueSummary[]>('/api/public/leagues'),
  })
}

export function useMyLeagues(enabled = true) {
  return useQuery({
    queryKey: ['leagues', 'mine'],
    queryFn: () => getJson<MyLeague[]>('/api/leagues/mine'),
    enabled,
  })
}

export function useLeague(slug: string) {
  return useQuery({
    queryKey: ['leagues', 'detail', slug],
    queryFn: () => getJson<LeagueDetail>(`/api/public/leagues/${encodeURIComponent(slug)}`),
  })
}

export function useCreateLeague() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { name: string; visibility: Visibility }) =>
      send<LeagueSummary>('POST', '/api/leagues', input),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['leagues'] }),
  })
}
