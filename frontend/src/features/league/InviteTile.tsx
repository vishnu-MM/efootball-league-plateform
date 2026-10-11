import { Link2 } from 'lucide-react'
import { toast } from 'sonner'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { ErrorState, Skeleton } from '../../components/ui/States'
import { IconTile, Tile } from '../../components/ui/Tile'
import { inviteUrl, useChangeInvite, useLeagueInvite } from './useInvites'

/** Owners and admins share the league's invite link from here. */
export function InviteTile({ slug, className }: { slug: string; className?: string }) {
  const { data, isPending, isError, refetch } = useLeagueInvite(slug, true)
  const create = useChangeInvite(slug, 'create')
  const rotate = useChangeInvite(slug, 'rotate')
  const url = data?.code ? inviteUrl(data.code) : null
  const changeError = create.error ?? rotate.error

  async function copy(link: string) {
    try {
      await navigator.clipboard.writeText(link)
      toast.success('Invite link copied')
    } catch {
      toast.error("Couldn't copy. Select the link and copy it instead.")
    }
  }

  return (
    <Tile as="section" tone="aqua" aria-labelledby="invite-heading" className={className}>
      <div className="flex flex-col gap-5">
        <div className="flex items-center gap-4">
          <IconTile>
            <Link2 className="size-6" />
          </IconTile>
          <h2 id="invite-heading" className="type-title">
            Invite managers
          </h2>
        </div>
        <p>Anyone with this link can join as a manager.</p>

        {isPending && <Skeleton className="h-13" />}
        {isError && <ErrorState message="The invite link couldn't be loaded." onRetry={() => refetch()} />}
        {data && !url && (
          <Button className="self-start" disabled={create.isPending} onClick={() => create.mutate()}>
            {create.isPending ? 'Creating link…' : 'Create invite link'}
          </Button>
        )}
        {url && (
          <>
            <input
              readOnly
              value={url}
              aria-label="Invite link"
              onFocus={(e) => e.currentTarget.select()}
              className="h-13 w-full truncate rounded-pill bg-tile-soft px-5 font-mono text-sm text-ink"
            />
            <div className="flex flex-wrap gap-3">
              <Button onClick={() => copy(url)}>Copy link</Button>
              <ConfirmDialog
                trigger={
                  <Button variant="secondary" disabled={rotate.isPending}>
                    {rotate.isPending ? 'Making link…' : 'New link'}
                  </Button>
                }
                title="Make a new link?"
                confirmLabel="Make new link"
                onConfirm={() => rotate.mutate(undefined, { onSuccess: () => toast.success('New invite link ready') })}
              >
                The current link will stop working. Anyone who already joined stays in the league.
              </ConfirmDialog>
            </div>
          </>
        )}
        {changeError && <ErrorState message={changeError.message} />}
      </div>
    </Tile>
  )
}
