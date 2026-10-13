import { Minus, Plus, X } from 'lucide-react'
import { useState, type FormEvent, type ReactNode } from 'react'
import { Button } from '../../components/ui/Button'
import { cn } from '../../lib/cn'
import type { SquadPlayer } from '../roster/useRoster'
import type { Goal, ScoreInput, Side } from './useSeason'

type ScoreFormProps = {
  homeName: string
  awayName: string
  initial?: ScoreInput
  withNote?: boolean
  /** Label for the note; with `noteRequired` the form can't be sent without one (e.g. an admin's reason). */
  noteLabel?: string
  noteRequired?: boolean
  submitLabel: string
  pendingLabel: string
  pending: boolean
  onSubmit: (score: ScoreInput) => void
  /** Each side's approved cards. A side with cards picks scorers from them; a side without types names. */
  squads?: Partial<Record<Side, SquadPlayer[]>>
}

const MAX_GOALS = 99

/** Goals per side with steppers, optional scorers (and assists) picked from squads or typed, and an optional note. */
export function ScoreForm({
  homeName,
  awayName,
  initial,
  withNote,
  noteLabel = 'Note for the admin (optional)',
  noteRequired,
  submitLabel,
  pendingLabel,
  pending,
  onSubmit,
  squads,
}: ScoreFormProps) {
  const [home, setHome] = useState(initial?.homeGoals ?? 0)
  const [away, setAway] = useState(initial?.awayGoals ?? 0)
  const [goals, setGoals] = useState<Goal[]>(initial?.goals ?? [])
  const [note, setNote] = useState(initial?.note ?? '')

  const count = (side: Side) => goals.filter((g) => g.side === side).length
  const squadOf = (side: Side) => squads?.[side] ?? []

  function setSideGoals(side: Side, value: number) {
    const next = Math.max(0, Math.min(MAX_GOALS, value))
    if (side === 'HOME') setHome(next)
    else setAway(next)
    // Drop scorers beyond the new goal count, newest first.
    setGoals((current) => {
      let extra = current.filter((g) => g.side === side).length - next
      if (extra <= 0) return current
      return [...current].reverse().filter((g) => !(g.side === side && extra-- > 0)).reverse()
    })
  }

  function updateGoal(index: number, patch: Partial<Goal>) {
    setGoals((current) => current.map((g, i) => (i === index ? { ...g, ...patch } : g)))
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    onSubmit({
      homeGoals: home,
      awayGoals: away,
      goals: goals.flatMap((g): Goal[] => {
        if (squadOf(g.side).length > 0) {
          // Picked from the squad: send card ids only (old typed scorers without an id are dropped).
          return g.scorerCardId ? [{ side: g.side, scorer: g.scorer, assist: null, scorerCardId: g.scorerCardId, assistCardId: g.assistCardId || null }] : []
        }
        return g.scorer.trim() ? [{ side: g.side, scorer: g.scorer.trim(), assist: g.assist?.trim() || null }] : []
      }),
      ...(withNote && note.trim() ? { note: note.trim() } : {}),
    })
  }

  return (
    <form onSubmit={submit} className="flex flex-col gap-6">
      <div className="grid gap-3 sm:grid-cols-2">
        <GoalStepper label={homeName} value={home} onChange={(v) => setSideGoals('HOME', v)} />
        <GoalStepper label={awayName} value={away} onChange={(v) => setSideGoals('AWAY', v)} />
      </div>

      <fieldset className="flex flex-col gap-3">
        <legend className="type-label mb-2 px-1 text-tile-muted">Scorers (optional)</legend>
        {goals.map((goal, index) => (
          <div key={index} className="flex flex-col gap-2 rounded-inner bg-tile-soft p-3">
            <div className="flex items-center justify-between gap-2">
              <span className="text-xs text-tile-muted">{goal.side === 'HOME' ? homeName : awayName}</span>
              <button
                type="button"
                aria-label="Remove scorer"
                onClick={() => setGoals((current) => current.filter((_, i) => i !== index))}
                className="press grid size-8 place-items-center rounded-full bg-paper text-ink"
              >
                <X className="size-4" />
              </button>
            </div>
            {squadOf(goal.side).length > 0 ? (
              <>
                <CardPicker
                  label="Scorer"
                  empty="Pick the scorer"
                  cards={squadOf(goal.side)}
                  value={goal.scorerCardId ?? ''}
                  onChange={(card) => updateGoal(index, { scorerCardId: card?.squadCardId ?? null, scorer: card?.playerName ?? '' })}
                />
                <CardPicker
                  label="Assist"
                  empty="No assist"
                  cards={squadOf(goal.side).filter((c) => c.squadCardId !== goal.scorerCardId)}
                  value={goal.assistCardId ?? ''}
                  onChange={(card) => updateGoal(index, { assistCardId: card?.squadCardId ?? null, assist: card?.playerName ?? null })}
                />
              </>
            ) : (
              <>
                <input
                  aria-label="Scorer"
                  placeholder="Scorer"
                  maxLength={60}
                  value={goal.scorer}
                  onChange={(e) => updateGoal(index, { scorer: e.target.value })}
                  className="h-11 rounded-pill bg-field px-4 text-sm text-field-fg placeholder:text-field-placeholder"
                />
                <input
                  aria-label="Assist"
                  placeholder="Assist (optional)"
                  maxLength={60}
                  value={goal.assist ?? ''}
                  onChange={(e) => updateGoal(index, { assist: e.target.value })}
                  className="h-11 rounded-pill bg-field px-4 text-sm text-field-fg placeholder:text-field-placeholder"
                />
              </>
            )}
          </div>
        ))}
        <div className="flex flex-wrap gap-2">
          {(['HOME', 'AWAY'] as const).map((side) => (
            <Button
              key={side}
              size="sm"
              variant="secondary"
              disabled={count(side) >= (side === 'HOME' ? home : away)}
              onClick={() => setGoals((current) => [...current, { side, scorer: '', assist: null }])}
            >
              <Plus className="size-4" aria-hidden />
              <span className="max-w-32 truncate">{side === 'HOME' ? homeName : awayName}</span> scorer
            </Button>
          ))}
        </div>
      </fieldset>

      {withNote && (
        <div className="flex flex-col gap-2">
          <label htmlFor="dispute-note" className="type-label px-1 text-tile-muted">
            {noteLabel}
          </label>
          <textarea
            id="dispute-note"
            rows={3}
            maxLength={280}
            required={noteRequired}
            value={note}
            onChange={(e) => setNote(e.target.value)}
            className="rounded-inner bg-field px-5 py-3 text-sm text-field-fg"
          />
        </div>
      )}

      <Button type="submit" size="lg" className="self-start" disabled={pending}>
        {pending ? pendingLabel : submitLabel}
      </Button>
    </form>
  )
}

type CardPickerProps = {
  label: string
  empty: string
  cards: SquadPlayer[]
  value: string
  onChange: (card: SquadPlayer | null) => void
}

/** A native select of a side's approved cards (best rated first). */
function CardPicker({ label, empty, cards, value, onChange }: CardPickerProps) {
  return (
    <select
      aria-label={label}
      value={value}
      onChange={(e) => onChange(cards.find((c) => c.squadCardId === e.target.value) ?? null)}
      className="h-11 rounded-pill bg-field px-4 text-sm text-field-fg"
    >
      <option value="">{empty}</option>
      {cards.map((card) => (
        <option key={card.squadCardId} value={card.squadCardId}>
          {card.playerName} · {card.rating} {card.position}
        </option>
      ))}
    </select>
  )
}

function GoalStepper({ label, value, onChange }: { label: string; value: number; onChange: (value: number) => void }) {
  return (
    // Phones: one row per side (name left, stepper right). Wider screens: two columns, name above.
    <div className="flex items-center justify-between gap-3 rounded-inner bg-tile-soft p-4 sm:flex-col">
      <span className="type-heading min-w-0 truncate sm:w-full sm:text-center">{label}</span>
      <div className="flex shrink-0 items-center gap-2 sm:gap-3">
        <StepButton label={`One goal fewer for ${label}`} disabled={value <= 0} onClick={() => onChange(value - 1)}>
          <Minus className="size-5" />
        </StepButton>
        <input
          type="number"
          inputMode="numeric"
          min={0}
          max={MAX_GOALS}
          aria-label={`${label} goals`}
          value={value}
          onChange={(e) => onChange(Number.parseInt(e.target.value || '0', 10))}
          className="type-metric w-14 bg-transparent text-center [appearance:textfield] [&::-webkit-inner-spin-button]:appearance-none"
        />
        <StepButton label={`One more goal for ${label}`} disabled={value >= MAX_GOALS} onClick={() => onChange(value + 1)}>
          <Plus className="size-5" />
        </StepButton>
      </div>
    </div>
  )
}

function StepButton({ label, disabled, onClick, children }: { label: string; disabled: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className={cn('press grid size-11 place-items-center rounded-full bg-paper text-ink', 'disabled:opacity-40')}
    >
      {children}
    </button>
  )
}
