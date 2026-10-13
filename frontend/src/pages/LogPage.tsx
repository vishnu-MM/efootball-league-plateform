import { ScrollText } from 'lucide-react'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError } from '../api/client'
import { Bento, PageHeader } from '../components/layout/Page'
import { Button } from '../components/ui/Button'
import { EmptyState, ErrorState, ListSkeleton } from '../components/ui/States'
import { Tile } from '../components/ui/Tile'
import { LogEntryItem } from '../features/activity/LogEntryItem'
import { useLog } from '../features/activity/useLog'

/** The full league log, newest first, optionally only what owners and admins did. */
export function LogPage() {
  const { slug = '' } = useParams()
  const [adminOnly, setAdminOnly] = useState(false)
  const { data, isPending, isError, error, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } = useLog(slug, adminOnly)
  const entries = data?.pages.flatMap((p) => p.entries) ?? []

  return (
    <section className="flex flex-col gap-6">
      <PageHeader
        title="League log"
        actions={
          <Button asChild variant="secondary">
            <Link to={`/leagues/${slug}`}>Back to the league</Link>
          </Button>
        }
      >
        Every admin decision and correction, with the reason given, for the whole league to see.
      </PageHeader>

      <label className="flex w-fit items-center gap-3 text-sm">
        <input type="checkbox" checked={adminOnly} onChange={(e) => setAdminOnly(e.target.checked)} className="size-5 accent-ink" />
        Admin actions only
      </label>

      {isPending && <ListSkeleton rows={3} label="Loading the log" />}
      {isError &&
        (error instanceof ApiError && error.status === 404 ? (
          <Bento>
            <EmptyState title="League not found">The link may be wrong, or the league is private.</EmptyState>
          </Bento>
        ) : (
          <ErrorState message="The log couldn't be loaded." onRetry={() => refetch()} />
        ))}
      {data && entries.length === 0 && (
        <Bento>
          <EmptyState title="Nothing here yet" icon={<ScrollText className="size-6" />}>
            Admin decisions, corrections and season milestones will show up here.
          </EmptyState>
        </Bento>
      )}
      {entries.length > 0 && (
        <Bento>
          <Tile tone="sage" className="flex flex-col gap-4 md:col-span-6 xl:col-span-8">
            <ul className="flex flex-col gap-2">
              {entries.map((entry) => (
                <LogEntryItem key={entry.id} slug={slug} entry={entry} />
              ))}
            </ul>
            {hasNextPage && (
              <Button variant="secondary" className="self-start" disabled={isFetchingNextPage} onClick={() => fetchNextPage()}>
                {isFetchingNextPage ? 'Loading…' : 'Show older'}
              </Button>
            )}
          </Tile>
        </Bento>
      )}
    </section>
  )
}
