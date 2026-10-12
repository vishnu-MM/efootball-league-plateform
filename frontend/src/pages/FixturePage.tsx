import { Check, Scale } from 'lucide-react'
import { RadioGroup } from 'radix-ui'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { toast } from 'sonner'
import { ApiError } from '../api/client'
import { Bento } from '../components/layout/Page'
import { Button } from '../components/ui/Button'
import { EmptyState, ErrorState, Skeleton } from '../components/ui/States'
import { IconTile, Tile } from '../components/ui/Tile'
import { useMe } from '../features/auth/useAuth'
import { ScoreText, StatusChip } from '../features/match/FixtureRow'
import { ScoreForm } from '../features/match/ScoreForm'
import {
  formatPlayBy,
  useFixture,
  useFixtureAction,
  type FixtureDetail,
  type Report,
  type ScoreInput,
} from '../features/match/useSeason'
import { useMemberSquad, type SquadPlayer } from '../features/roster/useRoster'

export function FixturePage() {
  const { slug = '', id = '' } = useParams()
  const { data, isPending, isError, error, refetch } = useFixture(slug, id)
  const { data: me } = useMe()

  if (isPending) return <FixtureSkeleton />
  if (isError) {
    if (error instanceof ApiError && error.status === 404) {
      return (
        <Bento>
          <EmptyState
            title="Match not found"
            actions={
              <Button asChild variant="secondary">
                <Link to={`/leagues/${slug}`}>Back to the league</Link>
              </Button>
            }
          >
            The link may be wrong, or the league is private.
          </EmptyState>
        </Bento>
      )
    }
    return <ErrorState message="This match couldn't be loaded." onRetry={() => refetch()} />
  }

  const { fixture } = data
  return (
    <Bento>
      <Tile as="header" tone="coral" className="flex min-h-[22rem] flex-col justify-between gap-8 md:col-span-6 xl:col-span-7">
        <div className="flex flex-wrap items-center justify-between gap-3">
          <p className="type-heading">
            {data.seasonName}, round {fixture.round}
            {fixture.playBy && <span className="font-mono text-xs normal-case">, play by {formatPlayBy(fixture.playBy)}</span>}
          </p>
          <StatusChip fixture={fixture} />
        </div>
        <div className="grid grid-cols-[1fr_auto_1fr] items-center gap-4">
          <h1 className="type-display min-w-0 break-words text-right">{fixture.home.name}</h1>
          <p className="type-poster tabular-nums" aria-label={scoreLabel(data)}>
            <ScoreText fixture={fixture} />
          </p>
          <p className="type-display min-w-0 break-words" aria-hidden>
            {fixture.away.name}
          </p>
          <span className="sr-only">against {fixture.away.name}</span>
        </div>
        <p className="text-sm">{statusLine(data, me?.id)}</p>
      </Tile>

      <ActionTile slug={slug} detail={data} className="md:col-span-6 xl:col-span-5" />

      {data.reports.length > 0 && (
        <Tile as="section" tone="sage" aria-labelledby="history-heading" className="flex flex-col gap-4 md:col-span-6 xl:col-span-7">
          <h2 id="history-heading" className="type-title">
            Score history
          </h2>
          <ol className="flex flex-col gap-3">
            {data.reports.map((report) => (
              <ReportCard key={report.id} report={report} detail={data} />
            ))}
          </ol>
        </Tile>
      )}
    </Bento>
  )
}

const KIND_LABEL: Record<Report['kind'], string> = {
  REPORT: 'reported',
  DISPUTE: 'disputed with',
  RESOLUTION: 'settled it at',
}

function ReportCard({ report, detail }: { report: Report; detail: FixtureDetail }) {
  const { fixture } = detail
  const isFinal = report.id === detail.finalReportId
  const scorers = (side: 'HOME' | 'AWAY') => report.goals.filter((g) => g.side === side)

  return (
    <li className="flex flex-col gap-3 rounded-inner bg-tile-soft p-4">
      <div className="flex flex-wrap items-baseline justify-between gap-2">
        <p className="text-sm">
          {report.reportedByName} {KIND_LABEL[report.kind]}{' '}
          <span className="font-metric text-lg font-semibold tabular-nums">
            {report.homeGoals}–{report.awayGoals}
          </span>
        </p>
        {isFinal && (
          <span className="inline-flex h-6 items-center gap-1 rounded-pill bg-ink px-2.5 text-[0.6875rem] text-on-dark">
            <Check className="size-3.5" aria-hidden />
            Final
          </span>
        )}
      </div>
      {report.goals.length > 0 && (
        <div className="grid grid-cols-2 gap-3 text-xs">
          {(['HOME', 'AWAY'] as const).map((side) => (
            <ul key={side} className="flex flex-col gap-1">
              <li className="type-label text-tile-muted">{side === 'HOME' ? fixture.home.name : fixture.away.name}</li>
              {scorers(side).map((goal, i) => (
                <li key={i}>
                  {goal.scorer}
                  {goal.assist && <span className="text-tile-muted"> (assist {goal.assist})</span>}
                </li>
              ))}
            </ul>
          ))}
        </div>
      )}
      {report.note && <p className="text-xs">“{report.note}”</p>}
    </li>
  )
}

function ActionTile({ slug, detail, className }: { slug: string; detail: FixtureDetail; className?: string }) {
  const { fixture } = detail
  const act = useFixtureAction(slug, fixture.id)
  const [disputing, setDisputing] = useState(false)
  const homeSquad = useMemberSquad(slug, fixture.home.userId)
  const awaySquad = useMemberSquad(slug, fixture.away.userId)
  const squads = { HOME: homeSquad.data?.cards ?? [], AWAY: awaySquad.data?.cards ?? [] }
  const opponentName = fixture.reportedBy === fixture.home.userId ? fixture.away.name : fixture.home.name
  const pending = detail.reports.filter((r) => r.kind === 'REPORT').at(-1)

  const run = (input: Parameters<typeof act.mutate>[0], message: string) =>
    act.mutate(input, { onSuccess: () => toast.success(message) })

  let body
  if (fixture.actions.includes('REPORT')) {
    body = (
      <ScoreForm
        homeName={fixture.home.name}
        awayName={fixture.away.name}
        submitLabel="Report score"
        pendingLabel="Reporting…"
        pending={act.isPending}
        squads={squads}
        onSubmit={(score) => run({ action: 'report', score }, 'Score reported. Waiting for confirmation.')}
      />
    )
  } else if (fixture.actions.includes('CONFIRM') && !disputing) {
    body = (
      <div className="flex flex-col gap-4">
        <p>
          {pending?.reportedByName ?? 'A player'} reported{' '}
          <span className="font-metric text-xl font-semibold tabular-nums">
            {fixture.homeGoals}–{fixture.awayGoals}
          </span>
          . Is that right?
        </p>
        <div className="flex flex-wrap gap-3">
          <Button size="lg" disabled={act.isPending} onClick={() => run({ action: 'confirm' }, 'Result confirmed')}>
            {act.isPending ? 'Confirming…' : 'Confirm score'}
          </Button>
          {fixture.actions.includes('DISPUTE') && (
            <Button size="lg" variant="secondary" onClick={() => setDisputing(true)}>
              Dispute
            </Button>
          )}
        </div>
        {!fixture.actions.includes('DISPUTE') && (
          <p className="text-xs text-tile-muted">You're confirming as an owner or admin, on {opponentName}'s behalf.</p>
        )}
      </div>
    )
  } else if (disputing) {
    body = (
      <div className="flex flex-col gap-4">
        <p className="text-sm text-tile-muted">Enter the score you think is right. An owner or admin will decide.</p>
        <ScoreForm
          homeName={fixture.home.name}
          awayName={fixture.away.name}
          withNote
          submitLabel="Send dispute"
          pendingLabel="Sending…"
          pending={act.isPending}
          squads={squads}
          onSubmit={(score) => run({ action: 'dispute', score }, 'Dispute sent to the league admins')}
        />
        <Button variant="ghost" className="self-start" onClick={() => setDisputing(false)}>
          Back
        </Button>
      </div>
    )
  } else if (fixture.actions.includes('RESOLVE')) {
    body = (
      <Resolve
        detail={detail}
        squads={squads}
        pending={act.isPending}
        onSubmit={(score) => run({ action: 'resolve', score }, 'Result settled')}
      />
    )
  } else {
    return null
  }

  return (
    <Tile as="section" aria-labelledby="action-heading" className={className}>
      <div className="flex flex-col gap-5">
        <h2 id="action-heading" className="type-title">
          {fixture.actions.includes('REPORT')
            ? 'Report the score'
            : fixture.actions.includes('RESOLVE')
              ? 'Settle the dispute'
              : disputing
                ? 'Dispute the score'
                : 'Confirm the score'}
        </h2>
        {act.isError && <ErrorState message={act.error.message} />}
        {body}
      </div>
    </Tile>
  )
}

/** Pick either player's version (pre-fills the form), or enter the right score. */
type ResolveProps = {
  detail: FixtureDetail
  squads: Record<'HOME' | 'AWAY', SquadPlayer[]>
  pending: boolean
  onSubmit: (score: ScoreInput) => void
}

function Resolve({ detail, squads, pending, onSubmit }: ResolveProps) {
  const { fixture } = detail
  const versions = detail.reports.filter((r) => r.kind !== 'RESOLUTION')
  const [choice, setChoice] = useState(versions.at(-1)?.id ?? '')
  const chosen = versions.find((r) => r.id === choice)

  return (
    <div className="flex flex-col gap-5">
      <div className="flex items-center gap-3">
        <IconTile variant="mustard" className="size-10">
          <Scale className="size-5" />
        </IconTile>
        <p className="text-sm text-tile-muted">The players disagree. Start from one version and adjust if needed.</p>
      </div>
      <RadioGroup.Root value={choice} onValueChange={setChoice} className="grid gap-3 sm:grid-cols-2">
        {versions.map((report) => (
          <RadioGroup.Item
            key={report.id}
            value={report.id}
            className="press flex flex-col items-start gap-1 rounded-inner bg-tile-soft p-4 text-left data-[state=checked]:bg-aqua data-[state=checked]:text-ink"
          >
            <span className="text-xs">{report.reportedByName}'s version</span>
            <span className="type-metric">
              {report.homeGoals}–{report.awayGoals}
            </span>
          </RadioGroup.Item>
        ))}
      </RadioGroup.Root>
      <ScoreForm
        key={choice}
        homeName={fixture.home.name}
        awayName={fixture.away.name}
        initial={chosen && { homeGoals: chosen.homeGoals, awayGoals: chosen.awayGoals, goals: chosen.goals }}
        submitLabel="Settle result"
        pendingLabel="Settling…"
        pending={pending}
        squads={squads}
        onSubmit={onSubmit}
      />
    </div>
  )
}

function scoreLabel({ fixture }: FixtureDetail) {
  if (fixture.homeGoals === null || fixture.awayGoals === null) return 'Not played yet'
  return `${fixture.homeGoals} to ${fixture.awayGoals}`
}

/** One plain sentence about where this match stands, from the viewer's point of view. */
function statusLine({ fixture }: FixtureDetail, viewerId?: string) {
  switch (fixture.status) {
    case 'SCHEDULED':
      return 'Not played yet. Either player can report the score.'
    case 'AWAITING_CONFIRMATION':
      return fixture.reportedBy === viewerId
        ? 'You reported this score. Waiting for your opponent, or an owner or admin, to confirm it.'
        : 'Reported score, waiting for confirmation.'
    case 'DISPUTED':
      return 'The players disagree on the score. An owner or admin will decide.'
    case 'CONFIRMED':
      return fixture.decidedByAdmin ? 'Final result, decided by an admin.' : 'Final result.'
  }
}

function FixtureSkeleton() {
  return (
    <Bento role="status" aria-label="Loading match">
      <Tile className="flex min-h-[22rem] flex-col justify-between md:col-span-6 xl:col-span-7">
        <Skeleton className="h-6 w-1/3" />
        <Skeleton className="h-20 rounded-inner" />
        <Skeleton className="h-5 w-1/2" />
      </Tile>
    </Bento>
  )
}
