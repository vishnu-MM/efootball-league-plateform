import { Bell } from 'lucide-react'
import { Link } from 'react-router'
import { cn } from '../../lib/cn'
import { useUnreadCount } from './useNotifications'

/** The header bell: unread count badge, links to the notifications page. */
export function NotificationBell({ className }: { className?: string }) {
  const { data: unread = 0 } = useUnreadCount(true)
  const label = unread > 0 ? `Notifications, ${unread} unread` : 'Notifications'
  return (
    <Link
      to="/notifications"
      aria-label={label}
      className={cn('press relative grid size-11 shrink-0 place-items-center rounded-full bg-paper text-ink', className)}
    >
      <Bell className="size-5" aria-hidden />
      {unread > 0 && (
        <span
          aria-hidden
          className="absolute -top-1 -right-1 grid h-5 min-w-5 place-items-center rounded-pill bg-coral px-1 font-metric text-[0.6875rem] font-semibold text-ink tabular-nums"
        >
          {unread > 99 ? '99+' : unread}
        </span>
      )}
    </Link>
  )
}
