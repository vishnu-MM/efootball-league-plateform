import { Link } from 'react-router'
import { cn } from '../../lib/cn'
import type { FixtureSummary } from './useSeason'

const STATUS: Record<string, { label: string; dot: string } | undefined> = {
  AWAITING_CONFIRMATION: { label: 'Awaiting confirmation', dot: 'bg-mustard' },
  DISPUTED: { label: 'Disputed', dot: 'bg-coral' },
}

/** Fixture state as an ink pill with a coloured dot; readable on any tile. */
export function StatusChip({ fixture, className }: { fixture: FixtureSummary; className?: string }) {
  const status = fixture.decidedByAdmin
    ? { label: 'Decided by an admin', dot: 'bg-aqua' }
    : STATUS[fixture.status]
  if (!status) return null
  return (
    <span className={cn('inline-flex h-6 items-center gap-1.5 rounded-pill bg-ink px-2.5 text-[0.6875rem] text-on-dark', className)}>
      <span aria-hidden className={cn('size-1.5 rounded-full', status.dot)} />
      {status.label}
    </span>
  )
}

/** "2 – 1" for a final or reported score, "v" before one is in. */
export function ScoreText({ fixture }: { fixture: FixtureSummary }) {
  if (fixture.homeGoals === null || fixture.awayGoals === null) return <>v</>
  return (
    <>
      {fixture.homeGoals}
      <span aria-hidden className="px-1 opacity-60">
        –
      </span>
      <span className="sr-only"> to </span>
      {fixture.awayGoals}
    </>
  )
}

/** One fixture as a row: home, score, away. Links to the fixture page. */
export function FixtureRow({ fixture, slug, viewerId }: { fixture: FixtureSummary; slug: string; viewerId?: string }) {
  const mine = (userId: string) => userId === viewerId && 'font-medium underline decoration-2 underline-offset-4'
  return (
    <Link
      to={`/leagues/${slug}/fixtures/${fixture.id}`}
      className="press flex flex-col gap-1.5 rounded-inner px-3 py-2.5 hover:bg-tile-soft"
    >
      <span className="grid grid-cols-[1fr_auto_1fr] items-center gap-3">
        <span className={cn('truncate text-right', mine(fixture.home.userId))}>{fixture.home.name}</span>
        <span
          className={cn(
            'min-w-14 rounded-pill bg-tile-soft px-3 py-1 text-center font-metric text-lg font-semibold tabular-nums',
            fixture.status === 'AWAITING_CONFIRMATION' && 'opacity-70',
          )}
        >
          <ScoreText fixture={fixture} />
        </span>
        <span className={cn('truncate', mine(fixture.away.userId))}>{fixture.away.name}</span>
      </span>
      {(fixture.status === 'AWAITING_CONFIRMATION' || fixture.status === 'DISPUTED' || fixture.decidedByAdmin) && (
        <StatusChip fixture={fixture} className="self-center" />
      )}
    </Link>
  )
}
