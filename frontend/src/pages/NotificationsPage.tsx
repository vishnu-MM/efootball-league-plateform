import { Bell } from 'lucide-react'
import { useNavigate } from 'react-router'
import { Bento, PageHeader } from '../components/layout/Page'
import { Button } from '../components/ui/Button'
import { EmptyState, ErrorState, ListSkeleton } from '../components/ui/States'
import { Tile } from '../components/ui/Tile'
import { formatWhen } from '../features/activity/useLog'
import { useMarkRead, useNotifications, type NotificationItem } from '../features/notify/useNotifications'
import { cn } from '../lib/cn'

/** Your notifications, newest first. Opening one marks it read and goes to where it points. */
export function NotificationsPage() {
  const { data, isPending, isError, refetch, fetchNextPage, hasNextPage, isFetchingNextPage } = useNotifications()
  const markRead = useMarkRead()
  const items = data?.pages.flatMap((p) => p.items) ?? []
  const anyUnread = items.some((n) => !n.read)

  return (
    <section className="flex flex-col gap-6">
      <PageHeader
        title="Notifications"
        actions={
          anyUnread && (
            <Button variant="secondary" disabled={markRead.isPending} onClick={() => markRead.mutate(undefined)}>
              Mark all read
            </Button>
          )
        }
      >
        Results to confirm, disputes, admin decisions and season news from your leagues.
      </PageHeader>
      {isPending && <ListSkeleton rows={3} label="Loading notifications" />}
      {isError && <ErrorState message="Your notifications couldn't be loaded." onRetry={() => refetch()} />}
      {data && items.length === 0 && (
        <Bento>
          <EmptyState title="All quiet" icon={<Bell className="size-6" />}>
            When something in your leagues needs you, it shows up here.
          </EmptyState>
        </Bento>
      )}
      {items.length > 0 && (
        <Bento>
          <Tile className="flex flex-col gap-4 md:col-span-6 xl:col-span-8">
            <ul className="flex flex-col gap-2">
              {items.map((item) => (
                <NotificationRow key={item.id} item={item} onOpen={() => !item.read && markRead.mutate([item.id])} />
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

function NotificationRow({ item, onOpen }: { item: NotificationItem; onOpen: () => void }) {
  const navigate = useNavigate()
  return (
    <li>
      <button
        type="button"
        onClick={() => {
          onOpen()
          navigate(item.link)
        }}
        className={cn(
          'press flex w-full items-start gap-3 rounded-inner px-4 py-3 text-left',
          item.read ? 'bg-transparent hover:bg-tile-soft' : 'bg-tile-soft',
        )}
      >
        <span aria-hidden className={cn('mt-1.5 size-2 shrink-0 rounded-full', item.read ? 'bg-transparent' : 'bg-coral')} />
        <span className="flex min-w-0 flex-col gap-1">
          <span className={cn('text-sm', !item.read && 'font-medium')}>
            {!item.read && <span className="sr-only">Unread: </span>}
            {item.text}
          </span>
          <span className="text-xs text-tile-muted">
            {item.leagueName && `${item.leagueName} · `}
            <time dateTime={item.createdAt}>{formatWhen(item.createdAt)}</time>
          </span>
        </span>
      </button>
    </li>
  )
}
