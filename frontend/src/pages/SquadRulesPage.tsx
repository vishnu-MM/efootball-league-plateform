import { Check } from 'lucide-react'
import { RadioGroup } from 'radix-ui'
import { useState, type FormEvent } from 'react'
import { Navigate, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { Bento, PageHeader } from '../components/layout/Page'
import { Button } from '../components/ui/Button'
import { ConfirmDialog } from '../components/ui/ConfirmDialog'
import { TextField } from '../components/ui/Field'
import { ErrorState, ListSkeleton } from '../components/ui/States'
import { Tile } from '../components/ui/Tile'
import { useLeague } from '../features/league/useLeagues'
import {
  CAP_TYPES,
  CARD_TYPES,
  useCheckRules,
  useRoster,
  useSaveRules,
  type CapType,
  type CardType,
  type FlaggedSquad,
  type SquadRules,
} from '../features/roster/useRoster'
import { cn } from '../lib/cn'

export function SquadRulesPage() {
  const { slug = '' } = useParams()
  const league = useLeague(slug)
  const isOwner = league.data?.myRole === 'OWNER'
  const roster = useRoster(slug, isOwner)

  if (league.isPending || (isOwner && roster.isPending)) return <ListSkeleton rows={2} label="Loading" />
  if (league.isError || !isOwner) return <Navigate to={`/leagues/${slug}`} replace />
  if (roster.isError) return <ErrorState message="The rules couldn't be loaded." onRetry={() => roster.refetch()} />

  return <RulesForm slug={slug} leagueName={league.data.name} initial={roster.data!.rules} />
}

function RulesForm({ slug, leagueName, initial }: { slug: string; leagueName: string; initial: SquadRules }) {
  const navigate = useNavigate()
  const check = useCheckRules(slug)
  const save = useSaveRules(slug)
  const [maxSize, setMaxSize] = useState(initial.maxSquadSize?.toString() ?? '')
  const [capType, setCapType] = useState<CapType>(initial.capType)
  const [capValue, setCapValue] = useState(initial.capValue?.toString() ?? '')
  const [banned, setBanned] = useState<Set<CardType>>(() => new Set(initial.bannedCardTypes))
  const [requireProof, setRequireProof] = useState(Boolean(initial.requireCardProof))
  const [flagged, setFlagged] = useState<FlaggedSquad[] | null>(null)

  const size = maxSize.trim() ? Number.parseInt(maxSize, 10) : null
  const cap = capValue.trim() ? Number.parseInt(capValue, 10) : null
  const sizeValid = size === null || (size >= 1 && size <= 100)
  const capValid = capType === 'NONE' || (cap !== null && cap >= 1 && cap <= 100_000)
  const rules: SquadRules = {
    maxSquadSize: size,
    capType,
    capValue: capType === 'NONE' ? null : cap,
    bannedCardTypes: [...banned],
    requireCardProof: requireProof,
  }

  function toggle(type: CardType) {
    setBanned((current) => {
      const next = new Set(current)
      if (next.has(type)) next.delete(type)
      else next.add(type)
      return next
    })
  }

  function doSave() {
    save.mutate(rules, {
      onSuccess: () => {
        toast.success('Squad rules saved')
        navigate(`/leagues/${slug}`)
      },
    })
  }

  /** Ask first if the new rules would flag anyone's squad. */
  function submit(event: FormEvent) {
    event.preventDefault()
    check.mutate(rules, {
      onSuccess: (result) => (result.flagged.length > 0 ? setFlagged(result.flagged) : doSave()),
    })
  }

  const error = check.error ?? save.error

  return (
    <section className="flex flex-col gap-6">
      <PageHeader title="Squad rules">{leagueName}. Adding a card that breaks these rules is refused.</PageHeader>
      <Bento>
        <Tile className="md:col-span-6 xl:col-span-7">
          <form onSubmit={submit} className="flex flex-col gap-8" noValidate>
            <TextField
              label="Max squad size"
              type="number"
              inputMode="numeric"
              min={1}
              max={100}
              placeholder="No limit"
              value={maxSize}
              error={sizeValid ? undefined : 'Between 1 and 100, or empty for no limit'}
              hint="Pending and approved cards both count."
              onChange={(e) => setMaxSize(e.target.value)}
            />

            <fieldset className="flex flex-col gap-2">
              <legend className="type-label mb-2 px-1 text-tile-muted">Rating cap</legend>
              <RadioGroup.Root value={capType} onValueChange={(v) => setCapType(v as CapType)} className="grid gap-3 sm:grid-cols-2">
                {(Object.keys(CAP_TYPES) as CapType[]).map((type) => (
                  <RadioGroup.Item
                    key={type}
                    value={type}
                    className="press group flex flex-col items-start gap-2 rounded-inner bg-tile-soft p-4 text-left text-tile-fg data-[state=checked]:bg-aqua data-[state=checked]:text-ink"
                  >
                    <span className="flex w-full items-center justify-between gap-2">
                      <span className="type-heading">{CAP_TYPES[type].title}</span>
                      <span className="grid size-5 place-items-center rounded-full border-2 border-current">
                        <RadioGroup.Indicator className="size-2.5 rounded-full bg-ink" />
                      </span>
                    </span>
                    <span className="text-xs opacity-80 group-data-[state=checked]:opacity-100">{CAP_TYPES[type].body}</span>
                  </RadioGroup.Item>
                ))}
              </RadioGroup.Root>
            </fieldset>
            {capType !== 'NONE' && (
              <TextField
                label="Cap"
                type="number"
                inputMode="numeric"
                min={1}
                placeholder={capType === 'PER_CARD' ? 'e.g. 95' : capType === 'TOP_11_TOTAL' ? 'e.g. 1000' : 'e.g. 2000'}
                value={capValue}
                error={capValue && !capValid ? 'Enter a number from 1 to 100,000' : undefined}
                onChange={(e) => setCapValue(e.target.value)}
              />
            )}

            <fieldset className="flex flex-col gap-3">
              <legend className="type-label mb-2 px-1 text-tile-muted">Banned card types</legend>
              <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3">
                {(Object.keys(CARD_TYPES) as CardType[]).map((type) => {
                  const checked = banned.has(type)
                  return (
                    <li key={type}>
                      <button
                        type="button"
                        role="checkbox"
                        aria-checked={checked}
                        onClick={() => toggle(type)}
                        className={cn(
                          'press flex w-full items-center gap-3 rounded-inner p-3 text-left text-sm',
                          checked ? 'bg-coral text-ink' : 'bg-tile-soft text-tile-fg',
                        )}
                      >
                        <span
                          aria-hidden
                          className={cn(
                            'grid size-6 shrink-0 place-items-center rounded-[8px] border-2',
                            checked ? 'border-ink bg-ink text-coral' : 'border-current',
                          )}
                        >
                          {checked && <Check className="size-4" />}
                        </span>
                        {CARD_TYPES[type]}
                      </button>
                    </li>
                  )
                })}
              </ul>
            </fieldset>

            <label className="flex items-start gap-3 rounded-inner bg-tile-soft p-4 text-sm">
              <input
                type="checkbox"
                checked={requireProof}
                onChange={(e) => setRequireProof(e.target.checked)}
                className="mt-0.5 size-5 shrink-0 accent-ink"
              />
              <span>
                Require a screenshot for new cards
                <span className="block text-xs text-tile-muted">Cards already in squads aren't affected.</span>
              </span>
            </label>

            {error && <ErrorState message={error.message} />}

            <div className="flex flex-wrap gap-3">
              <Button type="submit" size="lg" disabled={check.isPending || save.isPending || !sizeValid || !capValid}>
                {check.isPending || save.isPending ? 'Saving…' : 'Save rules'}
              </Button>
              <Button size="lg" variant="secondary" onClick={() => navigate(-1)}>
                Cancel
              </Button>
            </div>
          </form>
        </Tile>
      </Bento>

      <ConfirmDialog
        open={flagged !== null}
        onOpenChange={(open) => !open && setFlagged(null)}
        title="Some squads would break these rules"
        confirmLabel="Save anyway"
        onConfirm={doSave}
      >
        {flagged?.map((s) => s.name).join(', ')} would need to remove cards. They can't add cards or start a new season
        until their squads are within the rules.
      </ConfirmDialog>
    </section>
  )
}
