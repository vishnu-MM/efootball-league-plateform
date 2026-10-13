import { useQuery } from '@tanstack/react-query'
import { getJson } from '../../api/client'
import { CARD_TYPES, type CardType, type Position } from '../roster/useRoster'

/** One player on a leaderboard. Card details are null for typed names. */
export type StatRow = {
  rank: number
  name: string
  userId: string
  teamName: string
  cardType: CardType | null
  rating: number | null
  position: Position | null
  count: number
}

export type Stats = {
  season: { id: string; name: string; active: boolean } | null
  scorers: StatRow[]
  assists: StatRow[]
  mvps: StatRow[]
}

export type Board = 'scorers' | 'assists' | 'mvps'

export const BOARDS: { key: Board; label: string; unit: [string, string] }[] = [
  { key: 'scorers', label: 'Goals', unit: ['goal', 'goals'] },
  { key: 'assists', label: 'Assists', unit: ['assist', 'assists'] },
  { key: 'mvps', label: 'MVP', unit: ['award', 'awards'] },
]

/** Top scorers, assists and MVPs of the active (or latest) season, from final results. */
export function useStats(slug: string) {
  return useQuery({
    queryKey: ['leagues', 'stats', slug],
    queryFn: () => getJson<Stats>(`/api/public/leagues/${encodeURIComponent(slug)}/stats`),
  })
}

/** "Featured · 98 CF" for a card; empty for a typed name. */
export function cardLine(row: StatRow) {
  if (!row.cardType) return ''
  return `${CARD_TYPES[row.cardType]} · ${row.rating} ${row.position}`
}
