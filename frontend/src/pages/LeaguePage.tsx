import { Globe, Lock, MessageSquare } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { ApiError } from '../api/client'
import { LogTile } from '../features/activity/LogTile'
import { Bento } from '../components/layout/Page'
import { Button } from '../components/ui/Button'
import { Crest } from '../components/ui/Crest'
import { RoleBadge } from '../components/ui/RoleBadge'
import { EmptyState, ErrorState, Skeleton } from '../components/ui/States'
import { Tile } from '../components/ui/Tile'
import { useMe } from '../features/auth/useAuth'
import { ClubTile } from '../features/league/ClubTile'
import { InviteTile } from '../features/league/InviteTile'
import { MembersTile } from '../features/league/MembersTile'
import { useMembers } from '../features/league/useInvites'
import { useLeague } from '../features/league/useLeagues'
import { FixturesTile } from '../features/match/FixturesTile'
import { SeasonTile } from '../features/match/SeasonTile'
import { StandingsTile } from '../features/match/StandingsTile'
import { useSeason } from '../features/match/useSeason'
import { SquadAdminTile } from '../features/roster/SquadAdminTile'
import { LeaderboardsTile } from '../features/stats/LeaderboardsTile'
import { SquadTile } from '../features/roster/SquadTile'

export function LeaguePage() {
  const { slug = '' } = useParams()
  const { data: league, isPending, isError, error, refetch } = useLeague(slug)
  const { data: me } = useMe()
  const season = useSeason(slug)
  const members = useMembers(slug, Boolean(league?.myRole))

  if (isPending) return <LeagueHeaderSkeleton />
  if (isError) {
    if (error instanceof ApiError && error.status === 404) return <LeagueNotFound />
    return <ErrorState message="This league couldn't be loaded." onRetry={() => refetch()} />
  }

  const isPublic = league.visibility === 'PUBLIC'
  const canManage = league.myRole === 'OWNER' || league.myRole === 'ADMIN'
  const myClub = members.data?.find((m) => m.userId === me?.id)?.clubName ?? null

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
          <div className="flex flex-wrap items-end gap-3">
            <dl className="flex w-fit min-w-44 flex-col gap-2 rounded-inner bg-tile-soft p-5">
              <dt className="type-heading">Members</dt>
              <dd className="type-metric">{league.memberCount}</dd>
            </dl>
            {league.myRole === 'OWNER' && (
              <Button asChild variant="secondary">
                <Link to={`/leagues/${league.slug}/settings/discord`}>
                  <MessageSquare className="size-4" aria-hidden />
                  Discord
                </Link>
              </Button>
            )}
          </div>
        </div>
      </Tile>

      <SeasonTile
        slug={league.slug}
        season={season.data}
        isPending={season.isPending}
        isError={season.isError}
        onRetry={() => season.refetch()}
        canManage={canManage}
        isMember={Boolean(league.myRole)}
        viewerId={me?.id}
        className="md:col-span-6 xl:col-span-5"
      />

      {season.data && (
        <>
          <StandingsTile
            slug={league.slug}
            viewerId={me?.id}
            canManage={canManage}
            players={season.data.players}
            className="md:col-span-6 xl:col-span-7"
          />
          <FixturesTile
            key={season.data.id}
            slug={league.slug}
            season={season.data}
            viewerId={me?.id}
            className="md:col-span-6 xl:col-span-5"
          />
        </>
      )}

      {season.data && <LeaderboardsTile slug={league.slug} className="md:col-span-6 xl:col-span-7" />}
      <LogTile slug={league.slug} className="md:col-span-6 xl:col-span-5" />

      {league.myRole && <SquadTile slug={league.slug} className="md:col-span-6 xl:col-span-7" />}
      {canManage && <SquadAdminTile slug={league.slug} className="md:col-span-6 xl:col-span-5" />}

      {league.myRole && <MembersTile slug={league.slug} isOwner={league.myRole === 'OWNER'} className="md:col-span-6 xl:col-span-7" />}
      {league.myRole && (
        <div className="flex flex-col gap-3 md:col-span-6 md:gap-6 xl:col-span-5">
          <ClubTile key={myClub ?? ''} slug={league.slug} currentName={myClub} />
          {canManage && <InviteTile slug={league.slug} />}
        </div>
      )}
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
