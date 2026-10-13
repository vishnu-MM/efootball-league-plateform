import { ScrollText } from 'lucide-react'
import { Link } from 'react-router'
import { ErrorState, Skeleton } from '../../components/ui/States'
import { IconTile, OpenArrow, Tile } from '../../components/ui/Tile'
import { cn } from '../../lib/cn'
import { LogEntryItem } from './LogEntryItem'
import { useRecentLog } from './useLog'

const SHOWN = 5

/** The last few things that happened in the league, with a way into the full log. */
export function LogTile({ slug, className }: { slug: string; className?: string }) {
  const { data, isPending, isError, refetch } = useRecentLog(slug)

  return (
    <Tile as="section" tone="sage" aria-labelledby="log-heading" className={cn('flex flex-col gap-4', className)}>
      <Link to={`/leagues/${slug}/log`} className="group flex items-center gap-4">
        <IconTile>
          <ScrollText className="size-6" />
        </IconTile>
        <h2 id="log-heading" className="type-title flex-1">
          League log
        </h2>
        <OpenArrow />
      </Link>
      {isPending && <Skeleton className="h-32 rounded-inner" />}
      {isError && <ErrorState message="The log couldn't be loaded." onRetry={() => refetch()} />}
      {data && data.entries.length === 0 && (
        <p className="rounded-inner bg-tile-soft p-5 text-tile-muted">
          Admin decisions, corrections and season milestones show up here for everyone to see.
        </p>
      )}
      {data && data.entries.length > 0 && (
        <ul className="flex flex-col gap-2">
          {data.entries.slice(0, SHOWN).map((entry) => (
            <LogEntryItem key={entry.id} slug={slug} entry={entry} />
          ))}
        </ul>
      )}
    </Tile>
  )
}
