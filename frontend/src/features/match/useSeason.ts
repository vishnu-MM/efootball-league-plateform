import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getJson, send } from '../../api/client'

export type SeasonFormat = 'SINGLE' | 'HOME_AND_AWAY'
export type FixtureStatus = 'SCHEDULED' | 'AWAITING_CONFIRMATION' | 'DISPUTED' | 'CONFIRMED'
export type FixtureAction = 'REPORT' | 'CONFIRM' | 'DISPUTE' | 'RESOLVE' | 'OVERRIDE' | 'FORFEIT'
/** Which side forfeited a fixture. */
export type Forfeit = 'HOME' | 'AWAY' | 'BOTH'
export type Side = 'HOME' | 'AWAY'

export type FixtureSide = { userId: string; name: string }

export type FixtureSummary = {
  id: string
  round: number
  playBy: string | null
  status: FixtureStatus
  home: FixtureSide
  away: FixtureSide
  /** Final score when confirmed; the reported score while awaiting confirmation. */
  homeGoals: number | null
  awayGoals: number | null
  reportedBy: string | null
  decidedByAdmin: boolean
  forfeit: Forfeit | null
  overridden: boolean
  actions: FixtureAction[]
}

export type Season = {
  id: string
  number: number
  name: string
  format: SeasonFormat
  status: 'ACTIVE' | 'COMPLETED'
  pointsWin: number
  pointsDraw: number
  startDate: string | null
  daysPerRound: number | null
  players: FixtureSide[]
  fixtures: FixtureSummary[]
  canManage: boolean
}

/** A scorer. With squads, the card ids point at approved squad cards and the names are their player names. */
export type Goal = {
  side: Side
  scorer: string
  assist: string | null
  scorerCardId?: string | null
  assistCardId?: string | null
}

/** The match MVP: an approved card of that side's manager, or a typed name for a side without a squad. */
export type Mvp = { side: Side; cardId?: string | null; name?: string | null }

export type Report = {
  id: string
  kind: 'REPORT' | 'DISPUTE' | 'RESOLUTION' | 'OVERRIDE' | 'FORFEIT'
  reportedBy: string
  reportedByName: string
  homeGoals: number
  awayGoals: number
  note: string | null
  createdAt: string
  goals: Goal[]
  mvp: Mvp | null
}

export type FixtureDetail = {
  fixture: FixtureSummary
  seasonName: string
  reports: Report[]
  finalReportId: string | null
}

export type StandingsRow = {
  userId: string
  name: string
  played: number
  won: number
  drawn: number
  lost: number
  goalsFor: number
  goalsAgainst: number
  goalDifference: number
  /** Points taken away by an admin; already subtracted from `points`. */
  deducted: number
  points: number
}

export type Deduction = { id: string; userId: string; teamName: string; points: number; reason: string; createdAt: string }

export type Standings = {
  season: { id: string; name: string; active: boolean } | null
  rows: StandingsRow[]
  deductions: Deduction[]
}

export type ScoreInput = { homeGoals: number; awayGoals: number; goals: Goal[]; note?: string; mvp?: Mvp | null }

export type NewSeason = {
  name?: string
  participantIds: string[]
  format: SeasonFormat
  pointsWin: number
  pointsDraw: number
  startDate?: string
  daysPerRound?: number
}

const base = (slug: string) => encodeURIComponent(slug)

/** The active season, or the latest finished one; null if the league hasn't had one. */
export function useSeason(slug: string) {
  return useQuery({
    queryKey: ['leagues', 'season', slug],
    queryFn: async () => (await getJson<{ season: Season | null }>(`/api/public/leagues/${base(slug)}/season`)).season,
  })
}

export function useStandings(slug: string) {
  return useQuery({
    queryKey: ['leagues', 'standings', slug],
    queryFn: () => getJson<Standings>(`/api/public/leagues/${base(slug)}/standings`),
  })
}

export function useFixture(slug: string, id: string) {
  return useQuery({
    queryKey: ['leagues', 'fixture', slug, id],
    queryFn: () => getJson<FixtureDetail>(`/api/public/leagues/${base(slug)}/fixtures/${encodeURIComponent(id)}`),
  })
}

export function useCreateSeason(slug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (season: NewSeason) => send<{ id: string; name: string }>('POST', `/api/leagues/${base(slug)}/seasons`, season),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['leagues'] }),
  })
}

type ActionInput = { action: 'report' | 'dispute' | 'resolve'; score: ScoreInput } | { action: 'confirm' }

/** Report, confirm, dispute or resolve; refreshes the fixture, season and table afterwards. */
export function useFixtureAction(slug: string, id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: ActionInput) =>
      send(
        'POST',
        `/api/leagues/${base(slug)}/fixtures/${encodeURIComponent(id)}/${input.action}`,
        input.action === 'confirm' ? undefined : input.score,
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['leagues'] }),
  })
}

export type ForfeitInput = { forfeiting: Forfeit; homeGoals?: number; awayGoals?: number; reason: string }

/** Owner/admin corrections to a fixture: override a final result, or record a forfeit. */
export function useCorrection(slug: string, id: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (input: { action: 'override'; score: ScoreInput } | { action: 'forfeit'; forfeit: ForfeitInput }) =>
      send(
        'POST',
        `/api/leagues/${base(slug)}/fixtures/${encodeURIComponent(id)}/${input.action}`,
        input.action === 'override'
          ? {
              homeGoals: input.score.homeGoals,
              awayGoals: input.score.awayGoals,
              goals: input.score.goals,
              mvp: input.score.mvp,
              reason: input.score.note,
            }
          : input.forfeit,
      ),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['leagues'] }),
  })
}

/** Owner/admin: take points from a participant in the active season, or give them back. */
export function useDeductions(slug: string) {
  const queryClient = useQueryClient()
  const onSuccess = () => queryClient.invalidateQueries({ queryKey: ['leagues'] })
  return {
    deduct: useMutation({
      mutationFn: (input: { userId: string; points: number; reason: string }) =>
        send<{ id: string }>('POST', `/api/leagues/${base(slug)}/deductions`, input),
      onSuccess,
    }),
    revoke: useMutation({
      mutationFn: (input: { id: string; reason: string }) =>
        send('POST', `/api/leagues/${base(slug)}/deductions/${encodeURIComponent(input.id)}/revoke`, { reason: input.reason }),
      onSuccess,
    }),
  }
}

/** Rounds in order, each with its fixtures and (when the season has dates) its play-by date. */
export function roundsOf(fixtures: FixtureSummary[]) {
  const rounds = new Map<number, FixtureSummary[]>()
  for (const fixture of fixtures) {
    rounds.set(fixture.round, [...(rounds.get(fixture.round) ?? []), fixture])
  }
  return [...rounds.entries()].map(([round, list]) => ({ round, playBy: list[0]?.playBy ?? null, fixtures: list }))
}

/** The first round that still has unfinished matches, else the last round. */
export function currentRound(fixtures: FixtureSummary[]) {
  const open = fixtures.find((f) => f.status !== 'CONFIRMED')
  return open?.round ?? fixtures.at(-1)?.round ?? 1
}

export function formatPlayBy(date: string) {
  return new Date(`${date}T00:00:00`).toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
}
