import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getJson, send } from '../../api/client'

export type DiscordTopic = 'RESULTS' | 'DISPUTES' | 'CORRECTIONS' | 'DEDUCTIONS' | 'SEASONS' | 'TRANSFER_WINDOW'

export const DISCORD_TOPICS: { key: DiscordTopic; label: string }[] = [
  { key: 'RESULTS', label: 'Results confirmed' },
  { key: 'DISPUTES', label: 'Disputes opened and settled' },
  { key: 'CORRECTIONS', label: 'Overrides and forfeits' },
  { key: 'DEDUCTIONS', label: 'Point deductions' },
  { key: 'SEASONS', label: 'Seasons starting and finishing' },
  { key: 'TRANSFER_WINDOW', label: 'Transfer window opening and closing' },
]

export type DiscordSettings = {
  configured: boolean
  /** e.g. https://discord.com/api/webhooks/1234…/****. The full URL never comes back from the server. */
  maskedUrl: string | null
  topics: DiscordTopic[]
  enabled: boolean
  lastError: string | null
  lastErrorAt: string | null
}

const path = (slug: string) => `/api/leagues/${encodeURIComponent(slug)}/discord`

export function useDiscordSettings(slug: string) {
  return useQuery({ queryKey: ['leagues', 'discord', slug], queryFn: () => getJson<DiscordSettings>(path(slug)) })
}

export function useDiscordActions(slug: string) {
  const queryClient = useQueryClient()
  const onSuccess = () => queryClient.invalidateQueries({ queryKey: ['leagues', 'discord', slug] })
  return {
    save: useMutation({
      mutationFn: (input: { url?: string; topics: DiscordTopic[] }) => send<DiscordSettings>('PUT', path(slug), input),
      onSuccess,
    }),
    remove: useMutation({ mutationFn: () => send('DELETE', path(slug)), onSuccess }),
    test: useMutation({ mutationFn: () => send('POST', `${path(slug)}/test`), onSettled: onSuccess }),
  }
}
