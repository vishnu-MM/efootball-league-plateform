import { ListOrdered, MinusCircle } from 'lucide-react'
import { useState, type ReactNode } from 'react'
import { toast } from 'sonner'
import { Button } from '../../components/ui/Button'
import { SelectField, TextField } from '../../components/ui/Field'
import { FormDialog, TextArea } from '../../components/ui/FormDialog'
import { ErrorState, Skeleton } from '../../components/ui/States'
import { IconTile, Tile } from '../../components/ui/Tile'
import { cn } from '../../lib/cn'
import { useDeductions, useStandings, type Deduction, type FixtureSide } from './useSeason'

type StandingsTileProps = {
  slug: string
  viewerId?: string
  /** Owners and admins can deduct points (and give them back) while the season is active. */
  canManage?: boolean
  players?: FixtureSide[]
  className?: string
}

/** The league table: recalculated by the server from final results (minus deductions) on every load. */
export function StandingsTile({ slug, viewerId, canManage, players = [], className }: StandingsTileProps) {
  const { data, isPending, isError, refetch } = useStandings(slug)
  const showDeducted = Boolean(data?.rows.some((r) => r.deducted > 0))
  const canChangePoints = Boolean(canManage && data?.season?.active)

  return (
    <Tile as="section" aria-labelledby="table-heading" className={cn('flex flex-col gap-4', className)}>
      <div className="flex items-center gap-4">
        <IconTile>
          <ListOrdered className="size-6" />
        </IconTile>
        <h2 id="table-heading" className="type-title">
          Table
        </h2>
        {canChangePoints && <DeductDialog slug={slug} players={players} />}
      </div>

      {isPending && <Skeleton className="h-48 rounded-inner" />}
      {isError && <ErrorState message="The table couldn't be loaded." onRetry={() => refetch()} />}
      {data && data.rows.length > 0 && (
        <div className="-mx-2 overflow-x-auto">
          <table className="w-full min-w-[20rem] border-collapse text-sm">
            <thead>
              <tr className="type-label text-tile-muted">
                <th scope="col" className="w-8 px-2 py-2 text-right font-normal">
                  #
                </th>
                <th scope="col" className="px-2 py-2 text-left font-normal">
                  Team
                </th>
                <Num head>P</Num>
                <Num head wide>W</Num>
                <Num head wide>D</Num>
                <Num head wide>L</Num>
                <Num head wide>GF</Num>
                <Num head wide>GA</Num>
                <Num head>GD</Num>
                {showDeducted && (
                  <Num head>
                    <abbr title="Points deducted">Ded.</abbr>
                  </Num>
                )}
                <Num head>Pts</Num>
              </tr>
            </thead>
            <tbody className="divide-y divide-tile-soft">
              {data.rows.map((row, index) => (
                <tr key={row.userId} className={cn(row.userId === viewerId && 'bg-tile-soft')}>
                  <td className="px-2 py-3 text-right tabular-nums text-tile-muted">{index + 1}</td>
                  <th scope="row" className="max-w-0 truncate px-2 py-3 text-left font-normal">
                    {row.name}
                  </th>
                  <Num>{row.played}</Num>
                  <Num wide>{row.won}</Num>
                  <Num wide>{row.drawn}</Num>
                  <Num wide>{row.lost}</Num>
                  <Num wide>{row.goalsFor}</Num>
                  <Num wide>{row.goalsAgainst}</Num>
                  <Num>{row.goalDifference > 0 ? `+${row.goalDifference}` : row.goalDifference}</Num>
                  {showDeducted && <Num>{row.deducted > 0 ? `−${row.deducted}` : ''}</Num>}
                  <Num strong>{row.points}</Num>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
      {data && data.deductions.length > 0 && (
        <section aria-labelledby="deductions-heading" className="flex flex-col gap-2">
          <h3 id="deductions-heading" className="type-label px-1 text-tile-muted">
            Point deductions
          </h3>
          <ul className="flex flex-col gap-2">
            {data.deductions.map((d) => (
              <DeductionRow key={d.id} slug={slug} deduction={d} canRevoke={canChangePoints} />
            ))}
          </ul>
        </section>
      )}
      {data && data.rows.length === 0 && (
        <p className="rounded-inner bg-tile-soft p-5 text-tile-muted">The table appears once a season starts.</p>
      )}
    </Tile>
  )
}

function DeductDialog({ slug, players }: { slug: string; players: FixtureSide[] }) {
  const { deduct } = useDeductions(slug)
  const [open, setOpen] = useState(false)
  const [userId, setUserId] = useState('')
  const [points, setPoints] = useState('3')
  const [reason, setReason] = useState('')

  function reset(next: boolean) {
    setOpen(next)
    if (!next) {
      setUserId('')
      setPoints('3')
      setReason('')
      deduct.reset()
    }
  }

  return (
    <FormDialog
      open={open}
      onOpenChange={reset}
      trigger={
        <Button variant="secondary" size="sm" className="ml-auto">
          <MinusCircle className="size-4" aria-hidden />
          Deduct points
        </Button>
      }
      title="Deduct points"
      description="The table subtracts them straight away. The whole league sees the reason in the log."
      submitLabel={deduct.isPending ? 'Deducting…' : 'Deduct points'}
      pending={deduct.isPending}
      onSubmit={() =>
        deduct.mutate(
          { userId, points: Number(points), reason: reason.trim() },
          {
            onSuccess: () => {
              toast.success('Points deducted')
              reset(false)
            },
          },
        )
      }
    >
      <SelectField label="Team" required value={userId} onChange={(e) => setUserId(e.target.value)}>
        <option value="" disabled>
          Pick a team
        </option>
        {players.map((p) => (
          <option key={p.userId} value={p.userId}>
            {p.name}
          </option>
        ))}
      </SelectField>
      <TextField label="Points" type="number" min={1} max={99} required value={points} onChange={(e) => setPoints(e.target.value)} />
      <TextArea id="deduct-reason" label="Reason" required maxLength={280} value={reason} onChange={(e) => setReason(e.target.value)} />
      {deduct.isError && <ErrorState message={deduct.error.message} />}
    </FormDialog>
  )
}

function DeductionRow({ slug, deduction, canRevoke }: { slug: string; deduction: Deduction; canRevoke: boolean }) {
  const { revoke } = useDeductions(slug)
  const [open, setOpen] = useState(false)
  const [reason, setReason] = useState('')

  return (
    <li className="flex flex-wrap items-center justify-between gap-2 rounded-inner bg-tile-soft px-4 py-3 text-sm">
      <span>
        <span className="font-medium">
          {deduction.teamName} −{deduction.points}
        </span>
        <span className="text-tile-muted"> · {deduction.reason}</span>
      </span>
      {canRevoke && (
        <FormDialog
          open={open}
          onOpenChange={(next) => {
            setOpen(next)
            if (!next) setReason('')
          }}
          trigger={
            <Button variant="ghost" size="sm">
              Revoke
            </Button>
          }
          title={`Give ${deduction.points} back to ${deduction.teamName}?`}
          submitLabel={revoke.isPending ? 'Revoking…' : 'Revoke deduction'}
          pending={revoke.isPending}
          onSubmit={() =>
            revoke.mutate(
              { id: deduction.id, reason: reason.trim() },
              {
                onSuccess: () => {
                  toast.success('Deduction revoked')
                  setOpen(false)
                },
              },
            )
          }
        >
          <TextArea id={`revoke-${deduction.id}`} label="Reason" required maxLength={280} value={reason} onChange={(e) => setReason(e.target.value)} />
          {revoke.isError && <ErrorState message={revoke.error.message} />}
        </FormDialog>
      )}
    </li>
  )
}

/** A numeric column: right-aligned tabular figures. "wide" columns only show on larger screens. */
function Num({ children, head, wide, strong }: { children: ReactNode; head?: boolean; wide?: boolean; strong?: boolean }) {
  const className = cn(
    'w-10 px-2 py-3 text-right tabular-nums',
    head && 'py-2 font-normal',
    wide && 'hidden sm:table-cell',
    strong && 'font-metric text-base font-semibold',
  )
  return head ? (
    <th scope="col" className={className}>
      {children}
    </th>
  ) : (
    <td className={className}>{children}</td>
  )
}
