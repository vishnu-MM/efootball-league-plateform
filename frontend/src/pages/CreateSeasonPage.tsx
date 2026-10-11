import { Check } from 'lucide-react'
import { RadioGroup } from 'radix-ui'
import { useState, type FormEvent, type ReactNode } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { Bento, PageHeader } from '../components/layout/Page'
import { Button } from '../components/ui/Button'
import { TextField } from '../components/ui/Field'
import { ErrorState, ListSkeleton } from '../components/ui/States'
import { Tile } from '../components/ui/Tile'
import { useMembers } from '../features/league/useInvites'
import { useLeague } from '../features/league/useLeagues'
import { formatPlayBy, useCreateSeason, useSeason, type SeasonFormat } from '../features/match/useSeason'
import { cn } from '../lib/cn'

type Timing = 'NONE' | 'DATES'

const today = () => new Date().toISOString().slice(0, 10)

export function CreateSeasonPage() {
  const { slug = '' } = useParams()
  const league = useLeague(slug)
  const canManage = league.data?.myRole === 'OWNER' || league.data?.myRole === 'ADMIN'
  const members = useMembers(slug, canManage)
  const season = useSeason(slug)

  if (league.isPending || season.isPending || (canManage && members.isPending)) return <ListSkeleton rows={2} label="Loading" />
  if (league.isError || !canManage) return <Navigate to={`/leagues/${slug}`} replace />
  if (season.data?.status === 'ACTIVE') {
    return (
      <Bento>
        <Tile className="flex flex-col items-start gap-4 md:col-span-6 xl:col-span-7">
          <h1 className="type-display">A season is already running</h1>
          <p className="text-tile-muted">You can start the next one when {season.data.name} is complete.</p>
          <Button asChild variant="secondary">
            <Link to={`/leagues/${slug}`}>Back to the league</Link>
          </Button>
        </Tile>
      </Bento>
    )
  }
  if (members.isError) return <ErrorState message="Members couldn't be loaded." onRetry={() => members.refetch()} />

  return (
    <SeasonForm
      slug={slug}
      leagueName={league.data.name}
      nextNumber={(season.data?.number ?? 0) + 1}
      members={members.data ?? []}
    />
  )
}

type SeasonFormProps = {
  slug: string
  leagueName: string
  nextNumber: number
  members: { userId: string; displayName: string; clubName: string | null }[]
}

function SeasonForm({ slug, leagueName, nextNumber, members }: SeasonFormProps) {
  const navigate = useNavigate()
  const createSeason = useCreateSeason(slug)
  const [name, setName] = useState('')
  const [players, setPlayers] = useState<Set<string>>(() => new Set(members.map((m) => m.userId)))
  const [format, setFormat] = useState<SeasonFormat>('SINGLE')
  const [pointsWin, setPointsWin] = useState(3)
  const [pointsDraw, setPointsDraw] = useState(1)
  const [timing, setTiming] = useState<Timing>('NONE')
  const [startDate, setStartDate] = useState(today)
  const [daysPerRound, setDaysPerRound] = useState(7)

  const count = players.size
  const roundsPerLeg = count < 2 ? 0 : count % 2 === 0 ? count - 1 : count
  const rounds = format === 'HOME_AND_AWAY' ? roundsPerLeg * 2 : roundsPerLeg
  const matches = (count * (count - 1)) / 2 * (format === 'HOME_AND_AWAY' ? 2 : 1)
  const datesValid = timing === 'NONE' || (Boolean(startDate) && daysPerRound >= 1 && daysPerRound <= 60)
  const pointsValid = pointsWin >= 1 && pointsWin <= 10 && pointsDraw >= 0 && pointsDraw <= pointsWin
  const lastPlayBy = timing === 'DATES' && startDate && rounds > 0 ? addDays(startDate, daysPerRound * rounds - 1) : null

  function toggle(userId: string) {
    setPlayers((current) => {
      const next = new Set(current)
      if (next.has(userId)) next.delete(userId)
      else next.add(userId)
      return next
    })
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    createSeason.mutate(
      {
        ...(name.trim() ? { name: name.trim() } : {}),
        participantIds: [...players],
        format,
        pointsWin,
        pointsDraw,
        ...(timing === 'DATES' ? { startDate, daysPerRound } : {}),
      },
      {
        onSuccess: (season) => {
          toast.success(`${season.name} started`)
          navigate(`/leagues/${slug}`)
        },
      },
    )
  }

  return (
    <section className="flex flex-col gap-6">
      <PageHeader title="New season">{leagueName}. Every fixture is made when you start it.</PageHeader>

      <Bento>
        <Tile className="md:col-span-6 xl:col-span-7">
          <form onSubmit={submit} className="flex flex-col gap-8" noValidate>
            <TextField
              label="Season name"
              placeholder={`Season ${nextNumber}`}
              maxLength={60}
              value={name}
              autoComplete="off"
              hint="Leave empty to use the placeholder."
              onChange={(e) => setName(e.target.value)}
            />

            <fieldset className="flex flex-col gap-3">
              <legend className="type-label mb-2 px-1 text-tile-muted">
                Who plays ({count} picked)
              </legend>
              <ul className="grid gap-2 sm:grid-cols-2">
                {members.map((member) => {
                  const checked = players.has(member.userId)
                  return (
                    <li key={member.userId}>
                      <button
                        type="button"
                        role="checkbox"
                        aria-checked={checked}
                        onClick={() => toggle(member.userId)}
                        className={cn(
                          'press flex w-full items-center gap-3 rounded-inner p-3 text-left',
                          checked ? 'bg-aqua text-ink' : 'bg-tile-soft text-tile-fg',
                        )}
                      >
                        <span
                          aria-hidden
                          className={cn(
                            'grid size-6 shrink-0 place-items-center rounded-[8px] border-2',
                            checked ? 'border-ink bg-ink text-aqua' : 'border-current',
                          )}
                        >
                          {checked && <Check className="size-4" />}
                        </span>
                        <span className="flex min-w-0 flex-col">
                          <span className="truncate text-sm">{member.displayName}</span>
                          {member.clubName && <span className="truncate text-xs opacity-80">{member.clubName}</span>}
                        </span>
                      </button>
                    </li>
                  )
                })}
              </ul>
              {count < 2 && <p className="px-1 text-xs text-field-error">Pick at least two players.</p>}
            </fieldset>

            <Choice
              legend="Format"
              value={format}
              onChange={(v) => setFormat(v as SeasonFormat)}
              options={[
                { value: 'SINGLE', title: 'Single round-robin', body: 'Everyone plays everyone once.' },
                { value: 'HOME_AND_AWAY', title: 'Home and away', body: 'Everyone plays everyone twice.' },
              ]}
            />

            <fieldset className="flex flex-col gap-2">
              <legend className="type-label mb-2 px-1 text-tile-muted">Points</legend>
              <div className="grid grid-cols-2 gap-3">
                <TextField label="For a win" type="number" min={1} max={10} value={pointsWin} onChange={(e) => setPointsWin(toInt(e.target.value))} />
                <TextField label="For a draw" type="number" min={0} max={10} value={pointsDraw} onChange={(e) => setPointsDraw(toInt(e.target.value))} />
              </div>
              {!pointsValid && <p className="px-1 text-xs text-field-error">A win is worth 1–10 points, and a draw can't be worth more.</p>}
            </fieldset>

            <Choice
              legend="Match dates"
              value={timing}
              onChange={(v) => setTiming(v as Timing)}
              options={[
                { value: 'NONE', title: 'No dates', body: 'Just rounds. Play whenever.' },
                { value: 'DATES', title: 'Play-by dates', body: 'Each round gets a deadline.' },
              ]}
            />
            {timing === 'DATES' && (
              <div className="grid grid-cols-2 gap-3">
                <TextField label="Start date" type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} />
                <TextField
                  label="Days per round"
                  type="number"
                  min={1}
                  max={60}
                  value={daysPerRound}
                  onChange={(e) => setDaysPerRound(toInt(e.target.value))}
                />
              </div>
            )}

            {createSeason.isError && <ErrorState message={createSeason.error.message} />}

            <div className="flex flex-wrap gap-3">
              <Button type="submit" size="lg" disabled={createSeason.isPending || count < 2 || !datesValid || !pointsValid}>
                {createSeason.isPending ? 'Starting season…' : 'Start season'}
              </Button>
              <Button size="lg" variant="secondary" onClick={() => navigate(-1)}>
                Cancel
              </Button>
            </div>
          </form>
        </Tile>

        {/* Live summary of what will be created. */}
        <Tile tone="coral" aria-live="polite" className="order-first flex flex-col gap-6 md:col-span-6 xl:order-none xl:col-span-5 xl:self-start">
          <p className="type-display">{name.trim() || `Season ${nextNumber}`}</p>
          <dl className="grid grid-cols-2 gap-3">
            <Stat label="Players" value={count} />
            <Stat label="Rounds" value={rounds} />
            <Stat label="Matches" value={matches} />
            <Stat label="Win / draw" value={`${pointsWin} / ${pointsDraw}`} />
          </dl>
          {lastPlayBy && (
            <p className="text-sm">
              Round 1 by {formatPlayBy(addDays(startDate, daysPerRound - 1))}. Last round by {formatPlayBy(lastPlayBy)}.
            </p>
          )}
        </Tile>
      </Bento>
    </section>
  )
}

function Stat({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="flex flex-col gap-1 rounded-inner bg-tile-soft p-4">
      <dt className="type-heading">{label}</dt>
      <dd className="type-metric">{value}</dd>
    </div>
  )
}

type ChoiceProps = {
  legend: string
  value: string
  onChange: (value: string) => void
  options: { value: string; title: string; body: string }[]
}

function Choice({ legend, value, onChange, options }: ChoiceProps) {
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="type-label mb-2 px-1 text-tile-muted">{legend}</legend>
      <RadioGroup.Root value={value} onValueChange={onChange} className="grid gap-3 sm:grid-cols-2">
        {options.map((option) => (
          <RadioGroup.Item
            key={option.value}
            value={option.value}
            className="press group flex flex-col items-start gap-2 rounded-inner bg-tile-soft p-4 text-left text-tile-fg data-[state=checked]:bg-aqua data-[state=checked]:text-ink"
          >
            <span className="flex w-full items-center justify-between gap-2">
              <span className="type-heading">{option.title}</span>
              <span className="grid size-5 place-items-center rounded-full border-2 border-current">
                <RadioGroup.Indicator className="size-2.5 rounded-full bg-ink" />
              </span>
            </span>
            <span className="text-xs opacity-80 group-data-[state=checked]:opacity-100">{option.body}</span>
          </RadioGroup.Item>
        ))}
      </RadioGroup.Root>
    </fieldset>
  )
}

function toInt(value: string) {
  const n = Number.parseInt(value, 10)
  return Number.isNaN(n) ? 0 : n
}

function addDays(isoDate: string, days: number) {
  const date = new Date(`${isoDate}T00:00:00Z`)
  date.setUTCDate(date.getUTCDate() + days)
  return date.toISOString().slice(0, 10)
}
