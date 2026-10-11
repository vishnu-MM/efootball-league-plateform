import { Plus, Trophy } from 'lucide-react'
import { Link } from 'react-router'
import { Button } from '../components/ui/Button'
import { EmptyState, ErrorState, ListSkeleton } from '../components/ui/States'
import { RoleBadge } from '../components/ui/RoleBadge'
import { useMe } from '../features/auth/useAuth'
import { LeagueList } from '../features/league/LeagueList'
import { useMyLeagues } from '../features/league/useLeagues'

export function HomePage() {
  const { data: me, isPending } = useMe()
  if (isPending) return <ListSkeleton label="Loading" />
  return me ? <YourLeagues /> : <Welcome />
}

function Welcome() {
  return (
    <section className="flex flex-col gap-6 pt-4 md:pt-12">
      <h1 className="type-display max-w-[14ch]">Run your league without the spreadsheet</h1>
      <p className="max-w-[48ch] text-lg text-ink-muted">
        Create a league for your community and keep the managers, fixtures and table in one place.
      </p>
      <div className="flex flex-wrap gap-3">
        <Button asChild size="lg">
          <Link to="/signin">Sign in to get started</Link>
        </Button>
        <Button asChild size="lg" variant="secondary">
          <Link to="/discover">Browse public leagues</Link>
        </Button>
      </div>
    </section>
  )
}

function YourLeagues() {
  const { data, isPending, isError, refetch } = useMyLeagues()

  return (
    <section className="flex flex-col gap-6">
      <div className="flex flex-wrap items-end justify-between gap-4">
        <h1 className="type-title">Your leagues</h1>
        {data && data.length > 0 && (
          <Button asChild className="hidden md:inline-flex">
            <Link to="/leagues/new">
              <Plus className="size-[18px]" aria-hidden />
              New league
            </Link>
          </Button>
        )}
      </div>

      {isPending && <ListSkeleton label="Loading your leagues" />}
      {isError && <ErrorState message="Your leagues couldn't be loaded." onRetry={() => refetch()} />}
      {data && data.length === 0 && (
        <EmptyState
          icon={<Trophy className="size-7" />}
          title="You're not in a league yet"
          actions={
            <>
              <Button asChild>
                <Link to="/leagues/new">Create a league</Link>
              </Button>
              <Button asChild variant="secondary">
                <Link to="/discover">Browse public leagues</Link>
              </Button>
            </>
          }
        >
          Create one for your community. You'll be its owner and can set it up the way you play.
        </EmptyState>
      )}
      {data && data.length > 0 && (
        <LeagueList
          label="Your leagues"
          leagues={data.map((league) => ({ ...league, aside: <RoleBadge role={league.role} /> }))}
        />
      )}
    </section>
  )
}
