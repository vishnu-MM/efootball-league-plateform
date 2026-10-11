import { ListOrdered } from 'lucide-react'
import type { ReactNode } from 'react'
import { ErrorState, Skeleton } from '../../components/ui/States'
import { IconTile, Tile } from '../../components/ui/Tile'
import { cn } from '../../lib/cn'
import { useStandings } from './useSeason'

/** The league table: recalculated by the server from final results on every load. */
export function StandingsTile({ slug, viewerId, className }: { slug: string; viewerId?: string; className?: string }) {
  const { data, isPending, isError, refetch } = useStandings(slug)

  return (
    <Tile as="section" aria-labelledby="table-heading" className={cn('flex flex-col gap-4', className)}>
      <div className="flex items-center gap-4">
        <IconTile>
          <ListOrdered className="size-6" />
        </IconTile>
        <h2 id="table-heading" className="type-title">
          Table
        </h2>
      </div>

      {isPending && <Skeleton className="h-48 rounded-inner" />}
      {isError && <ErrorState message="The table couldn't be loaded." onRetry={() => refetch()} />}
      {data && data.rows.length > 0 && (
        <div className="-mx-2 overflow-x-auto">
          <table className="w-full min-w-[20rem] border-collapse text-sm">
            <thead>
              <tr className="type-label text-tile-muted">
                <th scope="col" className="w-8 px-2 py-2 text-right font-normal">
                  #
                </th>
                <th scope="col" className="px-2 py-2 text-left font-normal">
                  Team
                </th>
                <Num head>P</Num>
                <Num head wide>W</Num>
                <Num head wide>D</Num>
                <Num head wide>L</Num>
                <Num head wide>GF</Num>
                <Num head wide>GA</Num>
                <Num head>GD</Num>
                <Num head>Pts</Num>
              </tr>
            </thead>
            <tbody className="divide-y divide-tile-soft">
              {data.rows.map((row, index) => (
                <tr key={row.userId} className={cn(row.userId === viewerId && 'bg-tile-soft')}>
                  <td className="px-2 py-3 text-right tabular-nums text-tile-muted">{index + 1}</td>
                  <th scope="row" className="max-w-0 truncate px-2 py-3 text-left font-normal">
                    {row.name}
                  </th>
                  <Num>{row.played}</Num>
                  <Num wide>{row.won}</Num>
                  <Num wide>{row.drawn}</Num>
                  <Num wide>{row.lost}</Num>
                  <Num wide>{row.goalsFor}</Num>
                  <Num wide>{row.goalsAgainst}</Num>
                  <Num>{row.goalDifference > 0 ? `+${row.goalDifference}` : row.goalDifference}</Num>
                  <Num strong>{row.points}</Num>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && data.rows.length === 0 && (
        <p className="rounded-inner bg-tile-soft p-5 text-tile-muted">The table appears once a season starts.</p>
      )}
    </Tile>
  )
}

/** A numeric column: right-aligned tabular figures. "wide" columns only show on larger screens. */
function Num({ children, head, wide, strong }: { children: ReactNode; head?: boolean; wide?: boolean; strong?: boolean }) {
  const className = cn(
    'w-10 px-2 py-3 text-right tabular-nums',
    head && 'py-2 font-normal',
    wide && 'hidden sm:table-cell',
    strong && 'font-metric text-base font-semibold',
  )
  return head ? (
    <th scope="col" className={className}>
      {children}
    </th>
  ) : (
    <td className={className}>{children}</td>
  )
}
