import { Compass } from 'lucide-react'
import { EmptyState, ErrorState, ListSkeleton } from '../components/ui/States'
import { LeagueList } from '../features/league/LeagueList'
import { usePublicLeagues } from '../features/league/useLeagues'

export function DiscoverPage() {
  const { data, isPending, isError, refetch } = usePublicLeagues()

  return (
    <section className="flex flex-col gap-6">
      <div className="flex flex-col gap-2">
        <h1 className="type-title">Discover</h1>
        <p className="text-ink-muted">Public leagues anyone can follow.</p>
      </div>

      {isPending && <ListSkeleton label="Loading public leagues" />}
      {isError && <ErrorState message="Public leagues couldn't be loaded." onRetry={() => refetch()} />}
      {data && data.length === 0 && (
        <EmptyState icon={<Compass className="size-7" />} title="No public leagues yet">
          When an owner makes their league public, it shows up here.
        </EmptyState>
      )}
      {data && data.length > 0 && <LeagueList label="Public leagues" leagues={data} />}
    </section>
  )
}
