import { Image, Lock, LockOpen, Plus, Shirt, X } from 'lucide-react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { Button } from '../../components/ui/Button'
import { ErrorState, Skeleton } from '../../components/ui/States'
import { IconTile, Tile } from '../../components/ui/Tile'
import { cn } from '../../lib/cn'
import { CardRow, StatusChip } from './CardRow'
import { CAP_TYPES, proofSrc, useRemoveCard, useRoster, type Roster } from './useRoster'

/** The signed-in member's squad: cards and their status, how it measures against the rules, and what's wrong. */
export function SquadTile({ slug, className }: { slug: string; className?: string }) {
  const { data, isPending, isError, refetch } = useRoster(slug)
  const removeCard = useRemoveCard(slug)

  function remove(id: string, name: string) {
    removeCard.mutate(id, {
      onSuccess: () => toast.success(`${name} left your squad`),
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <Tile as="section" tone="violet" aria-labelledby="squad-heading" className={cn('flex flex-col gap-5', className)}>
      <div className="flex flex-wrap items-center gap-4">
        <IconTile>
          <Shirt className="size-6" />
        </IconTile>
        <h2 id="squad-heading" className="type-title min-w-0 flex-1">
          Your squad
        </h2>
        {data && <WindowChip open={data.windowOpen} />}
      </div>

      {isPending && (
        <div role="status" aria-label="Loading your squad" className="flex flex-col gap-3">
          <Skeleton className="h-20 rounded-inner" />
          <Skeleton className="h-12" />
        </div>
      )}
      {isError && <ErrorState message="Your squad couldn't be loaded." onRetry={() => refetch()} />}

      {data && (
        <>
          <SquadNumbers roster={data} />

          {data.mySquad.problems.length > 0 && (
            <div role="alert" className="flex flex-col gap-2 rounded-inner bg-coral p-4 text-ink">
              <p className="type-heading">Your squad breaks the rules</p>
              <ul className="list-disc pl-5 text-sm">
                {data.mySquad.problems.map((problem) => (
                  <li key={problem}>{problem}</li>
                ))}
              </ul>
              <p className="text-xs">
                Remove cards until it's back within the rules. You can't add cards or play a new season until then.
              </p>
            </div>
          )}

          {data.mySquad.cards.length === 0 ? (
            <p className="text-tile-muted">
              No cards yet. Add the cards you own in eFootball; an owner or admin checks each one.
            </p>
          ) : (
            <ul className="flex flex-col divide-y divide-tile-soft">
              {data.mySquad.cards.map((entry) => {
                const canRemove = entry.status === 'REJECTED' || data.windowOpen
                return (
                  <li key={entry.id} className="flex flex-col gap-1 py-3">
                    <CardRow card={entry.card}>
                      {entry.hasProof && (
                        <a
                          href={proofSrc(slug, entry.id)}
                          target="_blank"
                          rel="noreferrer"
                          aria-label={`Screenshot of ${entry.card.playerName}`}
                          className="press grid size-9 shrink-0 place-items-center rounded-full bg-tile-soft"
                        >
                          <Image className="size-4" aria-hidden />
                        </a>
                      )}
                      <StatusChip status={entry.status} />
                      {canRemove && (
                        <button
                          type="button"
                          aria-label={`Remove ${entry.card.playerName}`}
                          disabled={removeCard.isPending}
                          onClick={() => remove(entry.id, entry.card.playerName)}
                          className="press grid size-9 shrink-0 place-items-center rounded-full bg-tile-soft disabled:opacity-50"
                        >
                          <X className="size-4" />
                        </button>
                      )}
                    </CardRow>
                    {entry.status === 'REJECTED' && entry.rejectReason && (
                      <p className="pl-15 text-xs text-tile-muted">Reason: {entry.rejectReason}</p>
                    )}
                  </li>
                )
              })}
            </ul>
          )}

          {data.windowOpen && data.mySquad.problems.length === 0 && (
            <Button asChild variant="secondary" className="self-start">
              <Link to={`/leagues/${slug}/squad/add`}>
                <Plus className="size-4" aria-hidden />
                Add card
              </Link>
            </Button>
          )}
          {!data.windowOpen && (
            <p className="text-xs text-tile-muted">The transfer window is closed. Squads change between seasons.</p>
          )}
        </>
      )}
    </Tile>
  )
}

function SquadNumbers({ roster }: { roster: Roster }) {
  const { rules, mySquad } = roster
  const cap = CAP_TYPES[rules.capType]
  return (
    <dl className="grid grid-cols-2 gap-3">
      <div className="flex flex-col gap-1 rounded-inner bg-tile-soft p-4">
        <dt className="type-heading">Cards</dt>
        <dd className="type-metric">
          {mySquad.size}
          {rules.maxSquadSize !== null && <span className="text-tile-muted"> / {rules.maxSquadSize}</span>}
        </dd>
      </div>
      {rules.capType !== 'NONE' && (
        <div className="flex flex-col gap-1 rounded-inner bg-tile-soft p-4">
          <dt className="type-heading">{cap.usage}</dt>
          <dd className="type-metric">
            {(mySquad.capUsage ?? 0).toLocaleString()}
            <span className="text-tile-muted"> / {rules.capValue?.toLocaleString()}</span>
          </dd>
        </div>
      )}
    </dl>
  )
}

export function WindowChip({ open }: { open: boolean }) {
  return (
    <span
      className={cn(
        'inline-flex h-7 items-center gap-1.5 rounded-pill px-3 text-xs',
        open ? 'bg-aqua text-ink' : 'bg-ink text-on-dark',
      )}
    >
      {open ? <LockOpen className="size-3.5" aria-hidden /> : <Lock className="size-3.5" aria-hidden />}
      {open ? 'Window open' : 'Window closed'}
    </span>
  )
}
