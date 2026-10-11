import { Link, Navigate, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { ApiError } from '../api/client'
import { Bento } from '../components/layout/Page'
import { Button } from '../components/ui/Button'
import { Crest } from '../components/ui/Crest'
import { EmptyState, ErrorState, Skeleton } from '../components/ui/States'
import { Tile } from '../components/ui/Tile'
import { useMe } from '../features/auth/useAuth'
import { useAcceptInvite, useInvitePreview } from '../features/league/useInvites'

/** Where invite links land: shows the league, then joins it as a manager. */
export function JoinPage() {
  const { code = '' } = useParams()
  const { data: me, isPending: mePending } = useMe()
  const { data: invite, isPending, isError, error, refetch } = useInvitePreview(code)
  const accept = useAcceptInvite()
  const navigate = useNavigate()

  if (isPending || mePending) return <JoinSkeleton />
  if (isError) {
    if (error instanceof ApiError && error.status === 404) return <InviteNotValid />
    return <ErrorState message="This invite couldn't be loaded." onRetry={() => refetch()} />
  }
  if (invite.myRole) return <Navigate to={`/leagues/${invite.slug}`} replace />

  const members = invite.memberCount === 1 ? '1 member' : `${invite.memberCount} members`

  function join() {
    accept.mutate(code, {
      onSuccess: (joined) => {
        toast.success(`Joined ${joined.name}`)
        navigate(`/leagues/${joined.slug}`, { replace: true })
      },
    })
  }

  return (
    <Bento>
      <Tile as="section" tone="coral" className="flex min-h-[26rem] flex-col justify-between gap-8 md:col-span-6 xl:col-span-8">
        <span className="grid size-24 place-items-center rounded-inner bg-tile-soft">
          <Crest seed={invite.slug} name={invite.name} size={60} />
        </span>
        <div className="flex flex-col items-start gap-5">
          <p className="type-heading">You're invited to join</p>
          <h1 className="type-poster break-words">{invite.name}</h1>
          <p className="type-tabular">{members}. You'll join as a manager.</p>
          {accept.isError && <ErrorState message={accept.error.message} />}
          {me ? (
            <Button size="lg" disabled={accept.isPending} onClick={join}>
              {accept.isPending ? 'Joining…' : 'Join league'}
            </Button>
          ) : (
            <Button asChild size="lg">
              <Link to={`/signin?next=${encodeURIComponent(`/join/${code}`)}`}>Sign in to join</Link>
            </Button>
          )}
        </div>
      </Tile>
    </Bento>
  )
}

function JoinSkeleton() {
  return (
    <Bento role="status" aria-label="Loading invite">
      <Tile className="flex min-h-[26rem] flex-col justify-between md:col-span-6 xl:col-span-8">
        <Skeleton className="size-24 rounded-inner" />
        <div className="flex flex-col gap-4">
          <Skeleton className="h-6 w-1/3" />
          <Skeleton className="h-16 w-3/4 rounded-inner" />
          <Skeleton className="h-12 w-40" />
        </div>
      </Tile>
    </Bento>
  )
}

function InviteNotValid() {
  return (
    <Bento>
      <EmptyState
        title="This invite link doesn't work"
        actions={
          <Button asChild variant="secondary">
            <Link to="/">Go to home</Link>
          </Button>
        }
      >
        It may have been replaced with a new one. Ask the league owner for a fresh link.
      </EmptyState>
    </Bento>
  )
}
