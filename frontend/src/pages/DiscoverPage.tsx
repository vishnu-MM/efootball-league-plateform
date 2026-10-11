import { Compass } from 'lucide-react'
import { Bento, PageHeader } from '../components/layout/Page'
import { EmptyState, ErrorState, ListSkeleton } from '../components/ui/States'
import { LeagueList } from '../features/league/LeagueList'
import { usePublicLeagues } from '../features/league/useLeagues'

export function DiscoverPage() {
  const { data, isPending, isError, refetch } = usePublicLeagues()

  return (
    <section className="flex flex-col gap-6">
      <PageHeader title="Discover">Public leagues anyone can follow.</PageHeader>

      {isPending && <ListSkeleton label="Loading public leagues" />}
      {isError && <ErrorState message="Public leagues couldn't be loaded." onRetry={() => refetch()} />}
      {data && data.length === 0 && (
        <Bento>
          <EmptyState icon={<Compass className="size-6" />} title="No public leagues yet">
            When an owner makes their league public, it shows up here.
          </EmptyState>
        </Bento>
      )}
      {data && data.length > 0 && <LeagueList label="Public leagues" leagues={data} />}
    </section>
  )
}
