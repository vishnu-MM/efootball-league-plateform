import { useState } from 'react'
import { Navigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { Bento, PageHeader } from '../components/layout/Page'
import { Button } from '../components/ui/Button'
import { TextField } from '../components/ui/Field'
import { EmptyState, ErrorState, ListSkeleton } from '../components/ui/States'
import { Tile } from '../components/ui/Tile'
import { Thumbnail } from '../features/files/Thumbnail'
import { useLeague } from '../features/league/useLeagues'
import { CardRow } from '../features/roster/CardRow'
import { proofSrc, useApprovals, useDecideCard, type PendingCard } from '../features/roster/useRoster'
import { toneAt } from '../lib/tones'

/** Owners and admins approve or reject the cards managers have added, grouped by squad. */
export function ApprovalsPage() {
  const { slug = '' } = useParams()
  const league = useLeague(slug)
  const canManage = league.data?.myRole === 'OWNER' || league.data?.myRole === 'ADMIN'
  const approvals = useApprovals(slug, canManage)

  if (league.isPending || (canManage && approvals.isPending)) return <ListSkeleton rows={2} label="Loading approvals" />
  if (league.isError || !canManage) return <Navigate to={`/leagues/${slug}`} replace />
  if (approvals.isError) return <ErrorState message="Approvals couldn't be loaded." onRetry={() => approvals.refetch()} />

  const groups = new Map<string, PendingCard[]>()
  for (const card of approvals.data ?? []) {
    groups.set(card.userId, [...(groups.get(card.userId) ?? []), card])
  }

  return (
    <section className="flex flex-col gap-6">
      <PageHeader title="Card approvals">
        {league.data.name}. Approve cards the manager really owns. Only approved cards can be picked as scorers.
      </PageHeader>
      <Bento>
        {groups.size === 0 && <EmptyState title="All caught up">No cards are waiting for approval.</EmptyState>}
        {[...groups.values()].map((cards, index) => (
          <Tile
            key={cards[0].userId}
            as="section"
            tone={toneAt(index + 1)}
            className="flex flex-col gap-4 md:col-span-6 xl:col-span-6"
          >
            <h2 className="type-title">{cards[0].teamName}</h2>
            <ul className="flex flex-col divide-y divide-tile-soft">
              {cards.map((card) => (
                <PendingRow key={card.id} slug={slug} pending={card} />
              ))}
            </ul>
          </Tile>
        ))}
      </Bento>
    </section>
  )
}

function PendingRow({ slug, pending }: { slug: string; pending: PendingCard }) {
  const decide = useDecideCard(slug)
  const [rejecting, setRejecting] = useState(false)
  const [reason, setReason] = useState('')
  const name = pending.card.playerName

  function run(decision: 'approve' | 'reject') {
    decide.mutate(
      { id: pending.id, decision, reason: reason.trim() || undefined },
      {
        onSuccess: () => toast.success(decision === 'approve' ? `${name} approved` : `${name} rejected`),
        onError: (error) => toast.error(error.message),
      },
    )
  }

  return (
    <li className="flex flex-col gap-3 py-3">
      <div className="flex items-start gap-3">
        <CardRow card={pending.card} className="min-w-0 flex-1" />
        {pending.hasProof ? (
          <Thumbnail src={proofSrc(slug, pending.id)} alt={`Screenshot of ${name}`} />
        ) : (
          <span className="shrink-0 self-center text-xs text-tile-muted">No screenshot</span>
        )}
      </div>
      {rejecting ? (
        <div className="flex flex-col gap-3">
          <TextField
            label="Reason (optional)"
            placeholder="e.g. Rating doesn't match the card"
            maxLength={200}
            value={reason}
            onChange={(e) => setReason(e.target.value)}
          />
          <div className="flex flex-wrap gap-2">
            <Button size="sm" variant="danger" disabled={decide.isPending} onClick={() => run('reject')}>
              Reject card
            </Button>
            <Button size="sm" variant="ghost" onClick={() => setRejecting(false)}>
              Back
            </Button>
          </div>
        </div>
      ) : (
        <div className="flex flex-wrap gap-2">
          <Button size="sm" disabled={decide.isPending} onClick={() => run('approve')}>
            Approve
          </Button>
          <Button size="sm" variant="secondary" disabled={decide.isPending} onClick={() => setRejecting(true)}>
            Reject
          </Button>
        </div>
      )}
    </li>
  )
}
