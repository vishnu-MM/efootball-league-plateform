import { Trophy } from 'lucide-react'
import { Link } from 'react-router'
import { ErrorState, Skeleton } from '../../components/ui/States'
import { IconTile, OpenArrow, Tile } from '../../components/ui/Tile'
import { cn } from '../../lib/cn'
import { Leaderboards } from './Leaderboard'
import { useStats } from './useStats'

/** Top 5 scorers, assists and MVPs, linking to the full stats page. */
export function LeaderboardsTile({ slug, className }: { slug: string; className?: string }) {
  const { data, isPending, isError, refetch } = useStats(slug)

  return (
    <Tile as="section" tone="aqua" aria-labelledby="leaders-heading" className={cn('flex flex-col gap-4', className)}>
      <Link to={`/leagues/${slug}/stats`} className="group flex items-center gap-4">
        <IconTile>
          <Trophy className="size-6" />
        </IconTile>
        <h2 id="leaders-heading" className="type-title flex-1">
          Leaderboards
        </h2>
        <OpenArrow />
      </Link>
      {isPending && <Skeleton className="h-40 rounded-inner" />}
      {isError && <ErrorState message="The leaderboards couldn't be loaded." onRetry={() => refetch()} />}
      {data && <Leaderboards stats={data} limit={5} />}
    </Tile>
  )
}
