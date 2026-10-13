import { Link, useParams } from 'react-router'
import { ApiError } from '../api/client'
import { Bento, PageHeader } from '../components/layout/Page'
import { Button } from '../components/ui/Button'
import { EmptyState, ErrorState, ListSkeleton } from '../components/ui/States'
import { Tile } from '../components/ui/Tile'
import { Leaderboards } from '../features/stats/Leaderboard'
import { useStats } from '../features/stats/useStats'

/** The full leaderboards (top 20 each) for the active or latest season. */
export function StatsPage() {
  const { slug = '' } = useParams()
  const { data, isPending, isError, error, refetch } = useStats(slug)

  return (
    <section className="flex flex-col gap-6">
      <PageHeader
        title="Leaderboards"
        actions={
          <Button asChild variant="secondary">
            <Link to={`/leagues/${slug}`}>Back to the league</Link>
          </Button>
        }
      >
        {data?.season ? `${data.season.name}, from final results.` : 'Top scorers, assists and MVPs, from final results.'}
      </PageHeader>
      {isPending && <ListSkeleton rows={3} label="Loading leaderboards" />}
      {isError &&
        (error instanceof ApiError && error.status === 404 ? (
          <Bento>
            <EmptyState title="League not found">The link may be wrong, or the league is private.</EmptyState>
          </Bento>
        ) : (
          <ErrorState message="The leaderboards couldn't be loaded." onRetry={() => refetch()} />
        ))}
      {data && !data.season && (
        <Bento>
          <EmptyState title="No season yet">Leaderboards start with the first season.</EmptyState>
        </Bento>
      )}
      {data?.season && (
        <Bento>
          <Tile tone="aqua" className="md:col-span-6 xl:col-span-8">
            <Leaderboards stats={data} limit={20} />
          </Tile>
        </Bento>
      )}
    </section>
  )
}
