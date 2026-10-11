import { useQuery } from '@tanstack/react-query'
import { getJson } from '../../api/client'

export type Status = {
  app: string
  version: string
  databaseTime: string
}

export function useStatus() {
  return useQuery({ queryKey: ['status'], queryFn: () => getJson<Status>('/api/public/status') })
}
