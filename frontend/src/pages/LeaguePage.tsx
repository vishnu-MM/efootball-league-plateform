import { CalendarDays, Globe, Lock } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { ApiError } from '../api/client'
import { Bento } from '../components/layout/Page'
import { Button } from '../components/ui/Button'
import { Crest } from '../components/ui/Crest'
import { RoleBadge } from '../components/ui/RoleBadge'
import { EmptyState, ErrorState, Skeleton } from '../components/ui/States'
import { Tile } from '../components/ui/Tile'
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

  return (
    <Bento>
      <Tile as="header" tone="coral" className="flex min-h-[24rem] flex-col justify-between gap-8 md:col-span-6 xl:col-span-7">
        <div className="flex items-start justify-between gap-4">
          <span className="grid size-24 shrink-0 place-items-center rounded-inner bg-tile-soft">
            <Crest seed={league.slug} name={league.name} size={60} />
          </span>
          <dl className="flex flex-wrap justify-end gap-2">
            <div>
              <dt className="sr-only">Visibility</dt>
              <dd className="inline-flex h-7 items-center gap-1.5 rounded-pill bg-chip px-3 text-xs text-chip-fg">
                {isPublic ? <Globe className="size-3.5" aria-hidden /> : <Lock className="size-3.5" aria-hidden />}
                {isPublic ? 'Public' : 'Private'}
              </dd>
            </div>
            {league.myRole && (
              <div>
                <dt className="sr-only">Your role</dt>
                <dd>
                  <RoleBadge role={league.myRole} className="h-7 bg-ink px-3 text-on-dark" />
                </dd>
              </div>
            )}
          </dl>
        </div>
        <div className="flex flex-col gap-6">
          <h1 className="type-poster break-words">{league.name}</h1>
          <dl className="flex w-fit min-w-44 flex-col gap-2 rounded-inner bg-tile-soft p-5">
            <dt className="type-heading">Members</dt>
            <dd className="type-metric">{league.memberCount}</dd>
          </dl>
        </div>
      </Tile>

      <Tile as="section" aria-labelledby="fixtures-heading" className="flex flex-col gap-5 md:col-span-6 xl:col-span-5">
        <h2 id="fixtures-heading" className="type-title">
          Fixtures and table
        </h2>
        <EmptyState nested icon={<CalendarDays className="size-6" />} title="No fixtures yet" className="flex-1">
          Fixtures, results and the league table will appear here once the season is set up.
        </EmptyState>
      </Tile>
    </Bento>
  )
}

function LeagueHeaderSkeleton() {
  return (
    <Bento role="status" aria-label="Loading league">
      <Tile className="flex min-h-[24rem] flex-col justify-between md:col-span-6 xl:col-span-7">
        <Skeleton className="size-24 rounded-inner" />
        <div className="flex flex-col gap-4">
          <Skeleton className="h-16 w-3/4 rounded-inner" />
          <Skeleton className="h-24 w-44 rounded-inner" />
        </div>
      </Tile>
      <Tile className="flex flex-col gap-5 md:col-span-6 xl:col-span-5">
        <Skeleton className="h-7 w-1/2" />
        <Skeleton className="min-h-40 flex-1 rounded-inner" />
      </Tile>
    </Bento>
  )
}

function LeagueNotFound() {
  return (
    <Bento>
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
    </Bento>
  )
}
