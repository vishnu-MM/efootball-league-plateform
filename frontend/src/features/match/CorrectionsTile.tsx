import { Gavel } from 'lucide-react'
import { RadioGroup } from 'radix-ui'
import { useState } from 'react'
import { toast } from 'sonner'
import { Button } from '../../components/ui/Button'
import { TextArea } from '../../components/ui/FormDialog'
import { ErrorState } from '../../components/ui/States'
import { IconTile, Tile } from '../../components/ui/Tile'
import { cn } from '../../lib/cn'
import type { SquadPlayer } from '../roster/useRoster'
import { ScoreForm } from './ScoreForm'
import { useCorrection, type FixtureDetail, type Forfeit } from './useSeason'

type Mode = 'override' | 'forfeit'

/** Owner/admin: override a final result or record a forfeit, always with a reason the league will see in the log. */
export function CorrectionsTile({
  slug,
  detail,
  squads,
  className,
}: {
  slug: string
  detail: FixtureDetail
  squads: Record<'HOME' | 'AWAY', SquadPlayer[]>
  className?: string
}) {
  const { fixture } = detail
  const canOverride = fixture.actions.includes('OVERRIDE')
  const canForfeit = fixture.actions.includes('FORFEIT')
  const [mode, setMode] = useState<Mode | null>(null)
  const correct = useCorrection(slug, fixture.id)
  if (!canOverride && !canForfeit) return null

  const done = (message: string) => () => {
    toast.success(message)
    setMode(null)
  }
  const final = detail.reports.find((r) => r.id === detail.finalReportId)

  return (
    <Tile as="section" tone="violet" aria-labelledby="corrections-heading" className={cn('flex flex-col gap-5', className)}>
      <div className="flex items-center gap-4">
        <IconTile>
          <Gavel className="size-6" />
        </IconTile>
        <div>
          <h2 id="corrections-heading" className="type-title">
            Admin corrections
          </h2>
          <p className="text-sm text-tile-muted">The reason you give appears in the league log.</p>
        </div>
      </div>

      <div className="flex flex-wrap gap-3">
        {canOverride && (
          <Button variant={mode === 'override' ? 'primary' : 'secondary'} onClick={() => setMode(mode === 'override' ? null : 'override')}>
            Override result
          </Button>
        )}
        {canForfeit && (
          <Button variant={mode === 'forfeit' ? 'primary' : 'secondary'} onClick={() => setMode(mode === 'forfeit' ? null : 'forfeit')}>
            Record forfeit
          </Button>
        )}
      </div>

      {correct.isError && <ErrorState message={correct.error.message} />}

      {mode === 'override' && (
        <ScoreForm
          homeName={fixture.home.name}
          awayName={fixture.away.name}
          initial={final && fixture.forfeit === null ? { homeGoals: final.homeGoals, awayGoals: final.awayGoals, goals: final.goals } : undefined}
          withNote
          noteRequired
          noteLabel="Reason for the change"
          submitLabel="Override result"
          pendingLabel="Saving…"
          pending={correct.isPending}
          squads={squads}
          onSubmit={(score) => correct.mutate({ action: 'override', score }, { onSuccess: done('Result overridden') })}
        />
      )}

      {mode === 'forfeit' && (
        <ForfeitForm
          homeName={fixture.home.name}
          awayName={fixture.away.name}
          pending={correct.isPending}
          onSubmit={(forfeit) => correct.mutate({ action: 'forfeit', forfeit }, { onSuccess: done('Forfeit recorded') })}
        />
      )}
    </Tile>
  )
}

type ForfeitFormProps = {
  homeName: string
  awayName: string
  pending: boolean
  onSubmit: (input: { forfeiting: Forfeit; homeGoals?: number; awayGoals?: number; reason: string }) => void
}

/** Who forfeits; for one side, a fixed 3–0 or a score the admin enters; both sides is always 0–0. */
function ForfeitForm({ homeName, awayName, pending, onSubmit }: ForfeitFormProps) {
  const [who, setWho] = useState<Forfeit>('HOME')
  const [scoring, setScoring] = useState<'fixed' | 'custom'>('fixed')
  const [home, setHome] = useState(0)
  const [away, setAway] = useState(3)
  const [reason, setReason] = useState('')

  const options: { value: Forfeit; label: string }[] = [
    { value: 'HOME', label: `${homeName} forfeits` },
    { value: 'AWAY', label: `${awayName} forfeits` },
    { value: 'BOTH', label: 'Both forfeit' },
  ]

  function pickWho(value: Forfeit) {
    setWho(value)
    // Start a custom score from the 3–0 the forfeiting side would get.
    setHome(value === 'AWAY' ? 3 : 0)
    setAway(value === 'HOME' ? 3 : 0)
  }

  const forfeiterLoses = who === 'HOME' ? home < away : away < home
  const outcome =
    who === 'BOTH'
      ? 'Counts as a 0–0 draw.'
      : scoring === 'fixed'
        ? `Counts as ${who === 'HOME' ? '0–3' : '3–0'}.`
        : forfeiterLoses
          ? `Counts as ${home}–${away}.`
          : 'The side that forfeits has to lose.'

  return (
    <form
      className="flex flex-col gap-5"
      onSubmit={(event) => {
        event.preventDefault()
        onSubmit({
          forfeiting: who,
          ...(who !== 'BOTH' && scoring === 'custom' ? { homeGoals: home, awayGoals: away } : {}),
          reason: reason.trim(),
        })
      }}
    >
      <Choice label="Who forfeits" value={who} onChange={(v) => pickWho(v as Forfeit)} options={options} />
      {who !== 'BOTH' && (
        <Choice
          label="Score"
          value={scoring}
          onChange={(v) => setScoring(v as 'fixed' | 'custom')}
          options={[
            { value: 'fixed', label: '3–0' },
            { value: 'custom', label: 'Enter score' },
          ]}
        />
      )}
      {who !== 'BOTH' && scoring === 'custom' && (
        <div className="grid grid-cols-2 gap-3">
          <GoalInput label={homeName} value={home} onChange={setHome} />
          <GoalInput label={awayName} value={away} onChange={setAway} />
        </div>
      )}
      <p className="text-sm" aria-live="polite">
        {outcome}
      </p>
      <TextArea
        id="forfeit-reason"
        label="Reason"
        required
        maxLength={280}
        value={reason}
        onChange={(e) => setReason(e.target.value)}
      />
      <Button type="submit" size="lg" className="self-start" disabled={pending || (who !== 'BOTH' && scoring === 'custom' && !forfeiterLoses)}>
        {pending ? 'Saving…' : 'Record forfeit'}
      </Button>
    </form>
  )
}

function Choice({
  label,
  value,
  onChange,
  options,
}: {
  label: string
  value: string
  onChange: (value: string) => void
  options: { value: string; label: string }[]
}) {
  return (
    <div className="flex flex-col gap-2">
      <span className="type-label px-1 text-tile-muted">{label}</span>
      <RadioGroup.Root value={value} onValueChange={onChange} aria-label={label} className="flex flex-wrap gap-2">
        {options.map((option) => (
          <RadioGroup.Item
            key={option.value}
            value={option.value}
            className="press h-10 rounded-pill bg-tile-soft px-4 text-sm data-[state=checked]:bg-ink data-[state=checked]:text-on-dark"
          >
            {option.label}
          </RadioGroup.Item>
        ))}
      </RadioGroup.Root>
    </div>
  )
}

function GoalInput({ label, value, onChange }: { label: string; value: number; onChange: (value: number) => void }) {
  return (
    <label className="flex flex-col gap-2">
      <span className="type-label truncate px-1 text-tile-muted">{label}</span>
      <input
        type="number"
        min={0}
        max={99}
        value={value}
        onChange={(e) => onChange(Math.max(0, Math.min(99, Number(e.target.value) || 0)))}
        className="h-13 rounded-pill bg-field px-5 font-metric text-lg text-field-fg tabular-nums"
      />
    </label>
  )
}
