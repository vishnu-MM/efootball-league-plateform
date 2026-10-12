import { ClipboardCheck } from 'lucide-react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { Button } from '../../components/ui/Button'
import { OpenArrow, IconTile, Tile } from '../../components/ui/Tile'
import { cn } from '../../lib/cn'
import { WindowChip } from './SquadTile'
import { rulesSummary, useRoster, useSetWindow } from './useRoster'

/** Owners and admins: cards waiting for approval, the transfer window, the rules and flagged squads. */
export function SquadAdminTile({ slug, className }: { slug: string; className?: string }) {
  const { data } = useRoster(slug)
  const setWindow = useSetWindow(slug)
  if (!data?.canManage) return null

  const pending = data.pendingCount ?? 0

  function toggleWindow(open: boolean) {
    setWindow.mutate(open, {
      onSuccess: () => toast.success(open ? 'Transfer window opened' : 'Transfer window closed'),
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <Tile as="section" tone="aqua" aria-labelledby="squads-admin-heading" className={cn('flex flex-col gap-5', className)}>
      <div className="flex items-center gap-4">
        <IconTile>
          <ClipboardCheck className="size-6" />
        </IconTile>
        <h2 id="squads-admin-heading" className="type-title">
          Squads
        </h2>
      </div>

      <Link
        to={`/leagues/${slug}/squad/approvals`}
        className="press group flex items-center justify-between gap-4 rounded-inner bg-tile-soft p-4"
      >
        <span className="flex flex-col gap-1">
          <span className="type-heading">Waiting for approval</span>
          <span className="type-metric">{pending}</span>
        </span>
        <OpenArrow />
      </Link>

      <div className="flex flex-wrap items-center justify-between gap-3">
        <WindowChip open={data.windowOpen} />
        <Button size="sm" variant="secondary" disabled={setWindow.isPending} onClick={() => toggleWindow(!data.windowOpen)}>
          {data.windowOpen ? 'Close window' : 'Open window'}
        </Button>
      </div>

      <div className="flex flex-col gap-2">
        <p className="type-label px-1 text-tile-muted">Rules</p>
        <p className="text-sm">{rulesSummary(data.rules)}</p>
        {data.isOwner && (
          <Button asChild size="sm" variant="secondary" className="self-start">
            <Link to={`/leagues/${slug}/squad/rules`}>Change rules</Link>
          </Button>
        )}
      </div>

      {data.flagged.length > 0 && (
        <div role="alert" className="flex flex-col gap-2 rounded-inner bg-coral p-4 text-ink">
          <p className="type-heading">Squads breaking the rules</p>
          <ul className="flex flex-col gap-1 text-sm">
            {data.flagged.map((squad) => (
              <li key={squad.userId}>
                <Link to={`/leagues/${slug}/squads/${squad.userId}`} className="underline">
                  {squad.name}
                </Link>
                : {squad.problems.join('; ')}
              </li>
            ))}
          </ul>
        </div>
      )}
    </Tile>
  )
}
