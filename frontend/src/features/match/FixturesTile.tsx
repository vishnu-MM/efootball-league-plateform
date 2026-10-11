import { CalendarDays, ChevronLeft, ChevronRight } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { IconTile, Tile } from '../../components/ui/Tile'
import { cn } from '../../lib/cn'
import { FixtureRow } from './FixtureRow'
import { currentRound, formatPlayBy, roundsOf, type Season } from './useSeason'

/** Fixtures one round at a time, opening on the first round that still has matches to play. */
export function FixturesTile({ slug, season, viewerId, className }: { slug: string; season: Season; viewerId?: string; className?: string }) {
  const rounds = roundsOf(season.fixtures)
  const [selected, setSelected] = useState(() => currentRound(season.fixtures))
  const index = Math.max(0, rounds.findIndex((r) => r.round === selected))
  const round = rounds[index]

  return (
    <Tile as="section" tone="aqua" aria-labelledby="fixtures-heading" className={cn('flex flex-col gap-4', className)}>
      <div className="flex flex-wrap items-center justify-between gap-3">
        <div className="flex items-center gap-4">
          <IconTile>
            <CalendarDays className="size-6" />
          </IconTile>
          <h2 id="fixtures-heading" className="type-title">
            Fixtures
          </h2>
        </div>
        <div className="flex items-center gap-2">
          <RoundButton label="Previous round" disabled={index === 0} onClick={() => setSelected(rounds[index - 1].round)}>
            <ChevronLeft className="size-5" />
          </RoundButton>
          <span className="min-w-24 text-center text-xs" aria-live="polite">
            Round {round?.round} of {rounds.length}
          </span>
          <RoundButton label="Next round" disabled={index >= rounds.length - 1} onClick={() => setSelected(rounds[index + 1].round)}>
            <ChevronRight className="size-5" />
          </RoundButton>
        </div>
      </div>

      {round?.playBy && <p className="text-xs">Play by {formatPlayBy(round.playBy)}</p>}
      <ul className="flex flex-col gap-1">
        {round?.fixtures.map((fixture) => (
          <li key={fixture.id}>
            <FixtureRow fixture={fixture} slug={slug} viewerId={viewerId} />
          </li>
        ))}
      </ul>
    </Tile>
  )
}

function RoundButton({ label, disabled, onClick, children }: { label: string; disabled: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className="press grid size-11 place-items-center rounded-full bg-tile-soft text-ink disabled:opacity-40"
    >
      {children}
    </button>
  )
}
