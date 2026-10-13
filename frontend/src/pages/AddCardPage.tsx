import { Search } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate, useParams } from 'react-router'
import { toast } from 'sonner'
import { Bento, PageHeader } from '../components/layout/Page'
import { Button } from '../components/ui/Button'
import { SelectField, TextField } from '../components/ui/Field'
import { ErrorState, ListSkeleton } from '../components/ui/States'
import { Tile } from '../components/ui/Tile'
import { useLeague } from '../features/league/useLeagues'
import { ScreenshotPicker } from '../features/files/ScreenshotPicker'
import { CardRow } from '../features/roster/CardRow'
import {
  CARD_TYPES,
  POSITIONS,
  RATING_MAX,
  RATING_MIN,
  rulesSummary,
  useAddCard,
  useCardSearch,
  useRoster,
  type Card,
  type CardType,
  type Position,
} from '../features/roster/useRoster'

export function AddCardPage() {
  const { slug = '' } = useParams()
  const league = useLeague(slug)
  const roster = useRoster(slug, Boolean(league.data?.myRole))

  if (league.isPending || (league.data?.myRole && roster.isPending)) return <ListSkeleton rows={2} label="Loading" />
  if (league.isError || !league.data.myRole) return <Navigate to={`/leagues/${slug}`} replace />
  if (roster.isError) return <ErrorState message="Your squad couldn't be loaded." onRetry={() => roster.refetch()} />

  const data = roster.data!
  if (!data.windowOpen || data.mySquad.problems.length > 0) {
    return (
      <Bento>
        <Tile className="flex flex-col items-start gap-4 md:col-span-6 xl:col-span-7">
          <h1 className="type-display">{data.windowOpen ? 'Fix your squad first' : 'The transfer window is closed'}</h1>
          <p className="text-tile-muted">
            {data.windowOpen
              ? "Your squad breaks the league's rules. Remove cards until it's within them, then add new ones."
              : 'Squads change between seasons, or when an owner or admin opens the window.'}
          </p>
          <Button asChild variant="secondary">
            <Link to={`/leagues/${slug}`}>Back to the league</Link>
          </Button>
        </Tile>
      </Bento>
    )
  }

  return (
    <AddCardForm
      slug={slug}
      leagueName={league.data.name}
      rules={rulesSummary(data.rules)}
      requireProof={Boolean(data.rules.requireCardProof)}
    />
  )
}

type AddCardFormProps = { slug: string; leagueName: string; rules: string; requireProof: boolean }

function AddCardForm({ slug, leagueName, rules, requireProof }: AddCardFormProps) {
  const navigate = useNavigate()
  const addCard = useAddCard(slug)
  const [proof, setProof] = useState<string | undefined>()
  const missingProof = requireProof && !proof
  const [query, setQuery] = useState('')
  const search = useCardSearch(slug, query)
  const [cardType, setCardType] = useState<CardType>('STANDARD')
  const [position, setPosition] = useState<Position>('CF')
  const [rating, setRating] = useState('')

  const name = query.trim().replace(/\s+/g, ' ')
  const ratingNumber = Number.parseInt(rating, 10)
  const ratingValid = ratingNumber >= RATING_MIN && ratingNumber <= RATING_MAX
  const nameValid = name.length >= 2 && name.length <= 60

  function done(card: { playerName: string }) {
    toast.success(`${card.playerName} added. Waiting for approval.`)
    navigate(`/leagues/${slug}`)
  }

  function pick(card: Card) {
    addCard.mutate({ cardId: card.id, proofUploadId: proof }, { onSuccess: () => done(card) })
  }

  function submit(event: FormEvent) {
    event.preventDefault()
    const card = { playerName: name, cardType, rating: ratingNumber, position }
    addCard.mutate({ ...card, proofUploadId: proof }, { onSuccess: () => done(card) })
  }

  const results = query.trim().length >= 2 ? (search.data ?? []) : []

  return (
    <section className="flex flex-col gap-6">
      <PageHeader title="Add a card">
        {leagueName}. Add a card you own; an owner or admin approves it before it counts for scorers.
      </PageHeader>

      <Bento>
        <Tile className="flex flex-col gap-6 md:col-span-6 xl:col-span-7">
          <TextField
            label="Player name"
            placeholder="e.g. Lionel Messi"
            maxLength={60}
            value={query}
            autoComplete="off"
            hint="Pick a match from the league's cards, or fill in the rest below."
            onChange={(e) => {
              setQuery(e.target.value)
              addCard.reset()
            }}
          />

          <ScreenshotPicker
            slug={slug}
            purpose="CARD_PROOF"
            label={requireProof ? 'Screenshot of the card' : 'Screenshot of the card (optional)'}
            hint={
              requireProof
                ? 'This league needs one: a screenshot from your club in the game, showing the card.'
                : 'Helps the admins approve it: a screenshot from your club in the game, showing the card.'
            }
            max={1}
            required={requireProof}
            onChange={(ids) => setProof(ids[0])}
          />

          {results.length > 0 && (
            <div className="flex flex-col gap-2" aria-live="polite">
              <p className="type-label px-1 text-tile-muted">Already in this league</p>
              <ul className="flex flex-col gap-2">
                {results.map((card) => (
                  <li key={card.id}>
                    <button
                      type="button"
                      disabled={addCard.isPending || missingProof}
                      onClick={() => pick(card)}
                      className="press w-full rounded-inner bg-tile-soft p-3 text-left disabled:opacity-50"
                    >
                      <CardRow card={card}>
                        <span className="text-xs text-tile-muted">Add</span>
                      </CardRow>
                    </button>
                  </li>
                ))}
              </ul>
            </div>
          )}
          {query.trim().length >= 2 && !search.isFetching && results.length === 0 && (
            <p className="flex items-center gap-2 px-1 text-xs text-tile-muted">
              <Search className="size-3.5" aria-hidden />
              No approved card by that name yet. Enter it as a new card.
            </p>
          )}

          {addCard.isError && <ErrorState message={addCard.error.message} />}

          <form onSubmit={submit} className="flex flex-col gap-6 border-t border-tile-soft pt-6" noValidate>
            <p className="type-heading">Or enter a new card</p>
            <div className="grid gap-3 sm:grid-cols-3">
              <SelectField label="Card type" value={cardType} onChange={(e) => setCardType(e.target.value as CardType)}>
                {Object.entries(CARD_TYPES).map(([value, label]) => (
                  <option key={value} value={value}>
                    {label}
                  </option>
                ))}
              </SelectField>
              <SelectField label="Position" value={position} onChange={(e) => setPosition(e.target.value as Position)}>
                {POSITIONS.map((p) => (
                  <option key={p} value={p}>
                    {p}
                  </option>
                ))}
              </SelectField>
              <TextField
                label="Rating"
                type="number"
                inputMode="numeric"
                min={RATING_MIN}
                max={RATING_MAX}
                value={rating}
                error={rating && !ratingValid ? `${RATING_MIN}–${RATING_MAX}` : undefined}
                onChange={(e) => setRating(e.target.value)}
              />
            </div>

            <div className="flex flex-wrap gap-3">
              <Button type="submit" size="lg" disabled={addCard.isPending || !nameValid || !ratingValid || missingProof}>
                {addCard.isPending ? 'Adding…' : 'Add new card'}
              </Button>
              <Button size="lg" variant="secondary" onClick={() => navigate(-1)}>
                Cancel
              </Button>
            </div>
          </form>
        </Tile>

        <Tile tone="violet" className="order-first flex flex-col gap-3 md:col-span-6 xl:order-none xl:col-span-5 xl:self-start">
          <p className="type-heading">League rules</p>
          <p>{rules}</p>
          <p className="text-sm opacity-80">Pending cards count towards these rules too.</p>
        </Tile>
      </Bento>
    </section>
  )
}
