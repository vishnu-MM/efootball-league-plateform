import { useQuery } from '@tanstack/react-query'
import { getJson } from '../../api/client'

export type LeagueSummary = {
  id: string
  name: string
  slug: string
}

export function usePublicLeagues() {
  return useQuery({
    queryKey: ['leagues', 'public'],
    queryFn: () => getJson<LeagueSummary[]>('/api/public/leagues'),
  })
}
