import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getJson, send } from '../../api/client'

export type SeasonFormat = 'SINGLE' | 'HOME_AND_AWAY'
export type FixtureStatus = 'SCHEDULED' | 'AWAITING_CONFIRMATION' | 'DISPUTED' | 'CONFIRMED'
export type FixtureAction = 'REPORT' | 'CONFIRM' | 'DISPUTE' | 'RESOLVE'
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

export type Goal = { side: Side; scorer: string; assist: string | null }

export type Report = {
  id: string
  kind: 'REPORT' | 'DISPUTE' | 'RESOLUTION'
  reportedBy: string
  reportedByName: string
  homeGoals: number
  awayGoals: number
  note: string | null
  createdAt: string
  goals: Goal[]
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
  points: number
}

export type Standings = { season: { id: string; name: string; active: boolean } | null; rows: StandingsRow[] }

export type ScoreInput = { homeGoals: number; awayGoals: number; goals: Goal[]; note?: string }

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
