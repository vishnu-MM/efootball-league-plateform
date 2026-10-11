import { Trophy } from 'lucide-react'
import { Link } from 'react-router'
import { Button } from '../../components/ui/Button'
import { ErrorState, Skeleton } from '../../components/ui/States'
import { IconTile, Tile } from '../../components/ui/Tile'
import { cn } from '../../lib/cn'
import { FixtureRow } from './FixtureRow'
import type { Season } from './useSeason'

type SeasonTileProps = {
  slug: string
  season: Season | null | undefined
  isPending: boolean
  isError: boolean
  onRetry: () => void
  canManage: boolean
  isMember: boolean
  viewerId?: string
  className?: string
}

/** The season at a glance: progress, and the matches waiting on you. Or, before one exists, how to start it. */
export function SeasonTile({ slug, season, isPending, isError, onRetry, canManage, isMember, viewerId, className }: SeasonTileProps) {
  const startLink = (label: string) => (
    <Button asChild className="self-start">
      <Link to={`/leagues/${slug}/seasons/new`}>{label}</Link>
    </Button>
  )

  return (
    <Tile as="section" tone="violet" aria-labelledby="season-heading" className={cn('flex flex-col gap-5', className)}>
      <div className="flex items-center gap-4">
        <IconTile>
          <Trophy className="size-6" />
        </IconTile>
        <h2 id="season-heading" className="type-title min-w-0 break-words">
          {season ? season.name : 'Season'}
        </h2>
      </div>

      {isPending && <Skeleton className="h-28 rounded-inner" />}
      {isError && <ErrorState message="The season couldn't be loaded." onRetry={onRetry} />}

      {season === null && (
        <div className="flex flex-col gap-4 rounded-inner bg-tile-soft p-5">
          <p className="type-heading">No season yet</p>
          <p className="text-sm">
            {canManage
              ? 'Pick who plays and the format. Every fixture is made for you.'
              : 'Fixtures and the table appear here once the owner starts a season.'}
          </p>
          {canManage && startLink('Start a season')}
        </div>
      )}

      {season && <SeasonSummary season={season} slug={slug} isMember={isMember} viewerId={viewerId} />}
      {season?.status === 'COMPLETED' && canManage && startLink('Start the next season')}
    </Tile>
  )
}

function SeasonSummary({ season, slug, isMember, viewerId }: { season: Season; slug: string; isMember: boolean; viewerId?: string }) {
  const played = season.fixtures.filter((f) => f.status === 'CONFIRMED').length
  const needsYou = season.fixtures.filter((f) => f.actions.length > 0)
  const formatLabel = season.format === 'SINGLE' ? 'Single round-robin' : 'Home and away'

  return (
    <>
      <div className="flex flex-wrap items-end gap-3 rounded-inner bg-tile-soft p-5">
        <dl className="flex flex-col gap-1">
          <dt className="type-heading">{season.status === 'COMPLETED' ? 'Season complete' : 'Matches played'}</dt>
          <dd className="type-metric">
            {played}
            <span className="text-xl opacity-70"> / {season.fixtures.length}</span>
          </dd>
        </dl>
        <p className="ml-auto text-right text-xs">
          {season.players.length} players
          <br />
          {formatLabel}
        </p>
      </div>

      {isMember && season.status === 'ACTIVE' && (
        <div className="flex flex-col gap-2">
          <h3 className="type-heading">Needs you</h3>
          {needsYou.length === 0 ? (
            <p className="rounded-inner bg-tile-soft p-4 text-sm">Nothing right now.</p>
          ) : (
            <ul className="flex flex-col gap-1 rounded-inner bg-tile-soft p-1">
              {needsYou.slice(0, 4).map((fixture) => (
                <li key={fixture.id}>
                  <FixtureRow fixture={fixture} slug={slug} viewerId={viewerId} />
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </>
  )
}
