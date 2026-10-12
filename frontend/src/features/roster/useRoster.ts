import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getJson, send } from '../../api/client'

export type CardType = 'STANDARD' | 'FEATURED' | 'HIGHLIGHT' | 'POTW' | 'EPIC' | 'BIG_TIME' | 'SHOW_TIME' | 'LEGEND' | 'TREND'
export type Position = 'GK' | 'CB' | 'LB' | 'RB' | 'DMF' | 'CMF' | 'LMF' | 'RMF' | 'AMF' | 'LWF' | 'RWF' | 'SS' | 'CF'
export type CapType = 'NONE' | 'PER_CARD' | 'SQUAD_TOTAL' | 'TOP_11_TOTAL'
export type SquadCardStatus = 'PENDING' | 'APPROVED' | 'REJECTED'

export const CARD_TYPES: Record<CardType, string> = {
  STANDARD: 'Standard',
  FEATURED: 'Featured',
  HIGHLIGHT: 'Highlight',
  POTW: 'POTW',
  EPIC: 'Epic',
  BIG_TIME: 'Big Time',
  SHOW_TIME: 'Show Time',
  LEGEND: 'Legend',
  TREND: 'Trend',
}

export const POSITIONS: Position[] = ['GK', 'CB', 'LB', 'RB', 'DMF', 'CMF', 'LMF', 'RMF', 'AMF', 'LWF', 'RWF', 'SS', 'CF']

export const CAP_TYPES: Record<CapType, { title: string; body: string; usage: string }> = {
  NONE: { title: 'No cap', body: 'Any ratings.', usage: '' },
  PER_CARD: { title: 'Max per card', body: 'No card rated above the cap.', usage: 'Best card' },
  SQUAD_TOTAL: { title: 'Squad total', body: 'All ratings added up.', usage: 'Squad total' },
  TOP_11_TOTAL: { title: 'Top-11 total', body: 'The 11 best ratings added up.', usage: 'Top-11 total' },
}

export const RATING_MIN = 40
export const RATING_MAX = 110

export type Card = { id: string; playerName: string; cardType: CardType; rating: number; position: Position }

export type SquadCard = { id: string; status: SquadCardStatus; rejectReason: string | null; card: Card }

export type SquadRules = {
  maxSquadSize: number | null
  capType: CapType
  capValue: number | null
  bannedCardTypes: CardType[]
}

export type FlaggedSquad = { userId: string; name: string; problems: string[] }

export type Roster = {
  rules: SquadRules
  windowOpen: boolean
  canManage: boolean
  isOwner: boolean
  mySquad: { cards: SquadCard[]; size: number; capUsage: number | null; problems: string[] }
  flagged: FlaggedSquad[]
  /** Owners and admins only. */
  pendingCount: number | null
}

export type PendingCard = { id: string; userId: string; teamName: string; card: Card }

/** An approved card as squad pages and scorer pickers show it. */
export type SquadPlayer = {
  squadCardId: string
  playerName: string
  cardType: CardType
  rating: number
  position: Position
}

export type MemberSquad = { userId: string; name: string; cards: SquadPlayer[] }

export type NewCard =
  | { cardId: string }
  | { playerName: string; cardType: CardType; rating: number; position: Position }

const base = (slug: string) => `/api/leagues/${encodeURIComponent(slug)}`

/** Rules, window, your squad and flagged squads. Members only. */
export function useRoster(slug: string, enabled = true) {
  return useQuery({
    queryKey: ['leagues', 'roster', slug],
    queryFn: () => getJson<Roster>(`${base(slug)}/roster`),
    enabled,
  })
}

/** Approved catalogue cards matching the text; nothing is fetched under 2 characters. */
export function useCardSearch(slug: string, text: string) {
  const q = text.trim()
  return useQuery({
    queryKey: ['leagues', 'cards', slug, q.toLowerCase()],
    queryFn: () => getJson<Card[]>(`${base(slug)}/cards?q=${encodeURIComponent(q)}`),
    enabled: q.length >= 2,
    placeholderData: keepPreviousData,
  })
}

/** A member's approved squad; works for anyone who can see the league. */
export function useMemberSquad(slug: string, userId: string | undefined) {
  return useQuery({
    queryKey: ['leagues', 'squad', slug, userId],
    queryFn: () =>
      getJson<MemberSquad>(`/api/public/leagues/${encodeURIComponent(slug)}/squads/${encodeURIComponent(userId ?? '')}`),
    enabled: Boolean(userId),
  })
}

export function useApprovals(slug: string, enabled: boolean) {
  return useQuery({
    queryKey: ['leagues', 'approvals', slug],
    queryFn: () => getJson<PendingCard[]>(`${base(slug)}/squad/approvals`),
    enabled,
  })
}

/** Any squad change refreshes everything under the league (roster, squads, approvals, search). */
function useLeagueMutation<Input, Output = void>(fn: (input: Input) => Promise<Output>) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: fn,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['leagues'] }),
  })
}

export function useAddCard(slug: string) {
  return useLeagueMutation((card: NewCard) => send<SquadCard>('POST', `${base(slug)}/squad/cards`, card))
}

export function useRemoveCard(slug: string) {
  return useLeagueMutation((id: string) => send('DELETE', `${base(slug)}/squad/cards/${encodeURIComponent(id)}`))
}

export function useDecideCard(slug: string) {
  return useLeagueMutation(({ id, decision, reason }: { id: string; decision: 'approve' | 'reject'; reason?: string }) =>
    send('POST', `${base(slug)}/squad/cards/${encodeURIComponent(id)}/${decision}`, decision === 'reject' ? { reason } : undefined),
  )
}

/** Owner only: which squads these rules would flag, without saving. */
export function useCheckRules(slug: string) {
  return useMutation({
    mutationFn: (rules: SquadRules) => send<{ flagged: FlaggedSquad[] }>('POST', `${base(slug)}/squad-rules/check`, rules),
  })
}

export function useSaveRules(slug: string) {
  return useLeagueMutation((rules: SquadRules) => send<{ flagged: FlaggedSquad[] }>('PUT', `${base(slug)}/squad-rules`, rules))
}

export function useSetWindow(slug: string) {
  return useLeagueMutation((open: boolean) => send('PUT', `${base(slug)}/transfer-window`, { open }))
}

/** One line describing the rules, e.g. "Up to 25 cards · top-11 total 1,000 · banned: Epic". */export function rulesSummary(rules: SquadRules) {
  const parts = [
    rules.maxSquadSize !== null ? `Up to ${rules.maxSquadSize} cards` : 'Any squad size',
    rules.capType === 'NONE' ? 'no rating cap' : `${CAP_TYPES[rules.capType].title.toLowerCase()} ${rules.capValue?.toLocaleString()}`,
  ]
  if (rules.bannedCardTypes.length > 0) {
    parts.push(`banned: ${rules.bannedCardTypes.map((t) => CARD_TYPES[t]).join(', ')}`)
  }
  return parts.join(' · ')
}

export function cardLine(card: { cardType: CardType; position: Position }) {
  return `${CARD_TYPES[card.cardType]} · ${card.position}`
}
