import { CalendarDays, Globe, Lock, Users } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { ApiError } from '../api/client'
import { Button } from '../components/ui/Button'
import { Crest } from '../components/ui/Crest'
import { RoleBadge } from '../components/ui/RoleBadge'
import { EmptyState, ErrorState, Skeleton } from '../components/ui/States'
import { useLeague } from '../features/league/useLeagues'

export function LeaguePage() {
  const { slug = '' } = useParams()
  const { data: league, isPending, isError, error, refetch } = useLeague(slug)

  if (isPending) return <LeagueHeaderSkeleton />
  if (isError) {
    if (error instanceof ApiError && error.status === 404) return <LeagueNotFound />
    return <ErrorState message="This league couldn't be loaded." onRetry={() => refetch()} />
  }

  const isPublic = league.visibility === 'PUBLIC'
  const members = league.memberCount === 1 ? '1 member' : `${league.memberCount} members`

  return (
    <article className="flex flex-col gap-10">
      <header className="flex flex-col gap-5 sm:flex-row sm:items-center sm:gap-7">
        <Crest seed={league.slug} name={league.name} size={88} />
        <div className="flex min-w-0 flex-col gap-3">
          <h1 className="type-display break-words">{league.name}</h1>
          <dl className="flex flex-wrap items-center gap-x-5 gap-y-2 text-ink-muted">
            <div className="flex items-center gap-1.5">
              <dt className="sr-only">Visibility</dt>
              {isPublic ? <Globe className="size-4" aria-hidden /> : <Lock className="size-4" aria-hidden />}
              <dd>{isPublic ? 'Public' : 'Private'}</dd>
            </div>
            <div className="flex items-center gap-1.5">
              <dt className="sr-only">Members</dt>
              <Users className="size-4" aria-hidden />
              <dd className="type-tabular">{members}</dd>
            </div>
            {league.myRole && (
              <div className="flex items-center gap-1.5">
                <dt className="sr-only">Your role</dt>
                <dd>
                  <RoleBadge role={league.myRole} />
                </dd>
              </div>
            )}
          </dl>
        </div>
      </header>

      <section className="flex flex-col gap-4" aria-labelledby="fixtures-heading">
        <h2 id="fixtures-heading" className="type-heading">
          Fixtures and table
        </h2>
        <EmptyState icon={<CalendarDays className="size-7" />} title="No fixtures yet">
          Fixtures, results and the league table will appear here once the season is set up.
        </EmptyState>
      </section>
    </article>
  )
}

function LeagueHeaderSkeleton() {
  return (
    <div role="status" aria-label="Loading league" className="flex flex-col gap-5 sm:flex-row sm:items-center sm:gap-7">
      <Skeleton className="h-[101px] w-[88px] rounded-2xl" />
      <div className="flex flex-1 flex-col gap-3">
        <Skeleton className="h-10 w-3/4" />
        <Skeleton className="h-5 w-1/3" />
      </div>
    </div>
  )
}

function LeagueNotFound() {
  return (
    <EmptyState
      title="League not found"
      actions={
        <Button asChild variant="secondary">
          <Link to="/">Go to your leagues</Link>
        </Button>
      }
    >
      The link may be wrong, or the league is private and you're not a member.
    </EmptyState>
  )
}
