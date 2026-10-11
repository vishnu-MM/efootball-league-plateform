import { Compass, Plus, Trophy } from 'lucide-react'
import { Link } from 'react-router'
import { Bento, PageHeader } from '../components/layout/Page'
import { Button } from '../components/ui/Button'
import { RoleBadge } from '../components/ui/RoleBadge'
import { EmptyState, ErrorState, ListSkeleton } from '../components/ui/States'
import { IconTile, OpenArrow, Tile } from '../components/ui/Tile'
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
    <Bento>
      <Tile as="section" tone="coral" className="flex min-h-[26rem] flex-col justify-between gap-10 md:col-span-6 xl:col-span-8">
        <h1 className="type-poster max-w-[14ch]">Run your league without the spreadsheet</h1>
        <div className="flex flex-col items-start gap-6">
          <p className="max-w-[48ch] text-sm md:text-base">
            Create a league for your community and keep the managers, fixtures and table in one place.
          </p>
          <Button asChild size="lg">
            <Link to="/signin">Sign in to get started</Link>
          </Button>
        </div>
      </Tile>
      <Link
        to="/discover"
        className="group tone-violet flex min-h-60 flex-col justify-between gap-8 rounded-card bg-tile p-5 text-tile-fg md:col-span-6 md:p-6 xl:col-span-4"
      >
        <span className="flex items-start justify-between">
          <IconTile>
            <Compass className="size-6" />
          </IconTile>
          <OpenArrow />
        </span>
        <span className="type-display max-w-[12ch]">Browse public leagues</span>
      </Link>
    </Bento>
  )
}

function YourLeagues() {
  const { data, isPending, isError, refetch } = useMyLeagues()

  return (
    <section className="flex flex-col gap-6">
      <PageHeader
        title="Your leagues"
        actions={
          data &&
          data.length > 0 && (
            <Button asChild className="hidden md:inline-flex">
              <Link to="/leagues/new">
                <Plus className="size-[18px]" aria-hidden />
                New league
              </Link>
            </Button>
          )
        }
      />

      {isPending && <ListSkeleton label="Loading your leagues" />}
      {isError && <ErrorState message="Your leagues couldn't be loaded." onRetry={() => refetch()} />}
      {data && data.length === 0 && (
        <Bento>
          <EmptyState
            icon={<Trophy className="size-6" />}
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
        </Bento>
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
