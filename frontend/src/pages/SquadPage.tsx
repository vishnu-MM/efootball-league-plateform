import { Shirt } from 'lucide-react'
import { Link, useParams } from 'react-router'
import { ApiError } from '../api/client'
import { Bento, PageHeader } from '../components/layout/Page'
import { Button } from '../components/ui/Button'
import { EmptyState, ErrorState, ListSkeleton } from '../components/ui/States'
import { Tile } from '../components/ui/Tile'
import { CardRow } from '../features/roster/CardRow'
import { useMemberSquad } from '../features/roster/useRoster'

/** A member's approved cards, for anyone who can see the league. */
export function SquadPage() {
  const { slug = '', userId = '' } = useParams()
  const { data, isPending, isError, error, refetch } = useMemberSquad(slug, userId)

  if (isPending) return <ListSkeleton rows={1} label="Loading squad" />
  if (isError) {
    if (error instanceof ApiError && error.status === 404) {
      return (
        <Bento>
          <EmptyState
            title="Squad not found"
            actions={
              <Button asChild variant="secondary">
                <Link to={`/leagues/${slug}`}>Back to the league</Link>
              </Button>
            }
          >
            They may have left the league, or it's private.
          </EmptyState>
        </Bento>
      )
    }
    return <ErrorState message="This squad couldn't be loaded." onRetry={() => refetch()} />
  }

  return (
    <section className="flex flex-col gap-6">
      <PageHeader
        title={data.name}
        actions={
          <Button asChild variant="secondary">
            <Link to={`/leagues/${slug}`}>Back to the league</Link>
          </Button>
        }
      >
        {data.cards.length} approved {data.cards.length === 1 ? 'card' : 'cards'}.
      </PageHeader>
      <Bento>
        {data.cards.length === 0 ? (
          <EmptyState title="No approved cards yet" icon={<Shirt className="size-6" />} />
        ) : (
          <Tile tone="violet" className="md:col-span-6 xl:col-span-7">
            <ul className="flex flex-col divide-y divide-tile-soft">
              {data.cards.map((card) => (
                <li key={card.squadCardId} className="py-3">
                  <CardRow card={card} />
                </li>
              ))}
            </ul>
          </Tile>
        )}
      </Bento>
    </section>
  )
}
