import { Compass, Home, Plus, UserRound } from 'lucide-react'
import type { ComponentType } from 'react'
import { Link, NavLink, Outlet } from 'react-router'
import { useMe } from '../../features/auth/useAuth'
import { useMyLeagues } from '../../features/league/useLeagues'
import { APP_NAME } from '../../lib/app'
import { cn } from '../../lib/cn'
import { Button } from '../ui/Button'
import { Crest } from '../ui/Crest'
import { Skeleton } from '../ui/States'
import { OfflineBanner } from './OfflineBanner'
import { UserMenu } from './UserMenu'

type NavItem = { to: string; label: string; icon: ComponentType<{ className?: string }>; end?: boolean }

function Brand() {
  return (
    <Link to="/" className="flex items-center gap-2.5 rounded-md">
      <svg viewBox="0 0 40 46" width="26" height="30" aria-hidden>
        <path d="M20 1.5 37.5 7v15.5c0 11-7.4 18.6-17.5 22-10.1-3.4-17.5-11-17.5-22V7z" fill="var(--turf)" />
        <path d="M8 24h24M20 10v28" stroke="var(--on-turf)" strokeWidth="2.5" strokeLinecap="round" opacity="0.85" />
      </svg>
      <span className="text-[1.0625rem] font-extrabold [font-stretch:120%]">{APP_NAME}</span>
    </Link>
  )
}

function SidebarLink({ item }: { item: NavItem }) {
  const Icon = item.icon
  return (
    <NavLink
      to={item.to}
      end={item.end}
      className={({ isActive }) =>
        cn(
          'flex h-10 items-center gap-3 rounded-lg px-3 font-medium text-ink-muted hover:bg-sunken hover:text-ink',
          isActive && 'bg-turf-soft text-turf-strong hover:bg-turf-soft hover:text-turf-strong',
        )
      }
    >
      <Icon className="size-[18px]" />
      {item.label}
    </NavLink>
  )
}

function SidebarLeagues() {
  const { data, isPending } = useMyLeagues()
  if (isPending) {
    return (
      <div className="flex flex-col gap-2 px-3">
        <Skeleton className="h-6 w-4/5" />
        <Skeleton className="h-6 w-3/5" />
      </div>
    )
  }
  if (!data?.length) return null
  return (
    <ul className="flex flex-col gap-0.5">
      {data.map((league) => (
        <li key={league.id}>
          <NavLink
            to={`/leagues/${league.slug}`}
            className={({ isActive }) =>
              cn(
                'flex h-10 items-center gap-2.5 rounded-lg px-3 text-sm hover:bg-sunken',
                isActive ? 'bg-sunken font-semibold' : 'text-ink-muted hover:text-ink',
              )
            }
          >
            <Crest seed={league.slug} name={league.name} size={20} />
            <span className="truncate">{league.name}</span>
          </NavLink>
        </li>
      ))}
    </ul>
  )
}

function Sidebar({ signedIn }: { signedIn: boolean }) {
  const items: NavItem[] = [
    { to: '/', label: 'Home', icon: Home, end: true },
    { to: '/discover', label: 'Discover', icon: Compass },
  ]
  return (
    <aside className="sticky top-0 hidden h-dvh w-64 shrink-0 flex-col gap-6 border-r border-line bg-surface px-3 py-5 md:flex">
      <div className="px-3">
        <Brand />
      </div>
      <nav aria-label="Main" className="flex flex-col gap-0.5">
        {items.map((item) => (
          <SidebarLink key={item.to} item={item} />
        ))}
      </nav>
      {signedIn && (
        <nav aria-label="Your leagues" className="flex min-h-0 flex-1 flex-col gap-2 overflow-y-auto">
          <h2 className="px-3 text-sm font-semibold text-ink-muted">Your leagues</h2>
          <SidebarLeagues />
          <Button asChild variant="ghost" size="sm" className="justify-start gap-3 px-3 text-ink-muted">
            <Link to="/leagues/new">
              <Plus className="size-[18px]" />
              New league
            </Link>
          </Button>
        </nav>
      )}
      <div className="mt-auto">{signedIn ? <UserMenu /> : <SignInButton className="w-full" />}</div>
    </aside>
  )
}

function SignInButton({ className }: { className?: string }) {
  return (
    <Button asChild className={className}>
      <Link to="/signin">Sign in</Link>
    </Button>
  )
}

function TabBar({ signedIn }: { signedIn: boolean }) {
  const items: NavItem[] = [
    { to: '/', label: 'Home', icon: Home, end: true },
    { to: '/discover', label: 'Discover', icon: Compass },
    ...(signedIn ? [{ to: '/leagues/new', label: 'New league', icon: Plus }] : []),
    { to: signedIn ? '/account' : '/signin', label: signedIn ? 'Account' : 'Sign in', icon: UserRound },
  ]
  return (
    <nav
      aria-label="Main"
      className="fixed inset-x-0 bottom-0 z-20 border-t border-line bg-surface/95 pb-[env(safe-area-inset-bottom)] backdrop-blur md:hidden"
    >
      <ul className="flex">
        {items.map(({ to, label, icon: Icon, end }) => (
          <li key={to} className="flex-1">
            <NavLink
              to={to}
              end={end}
              className={({ isActive }) =>
                cn(
                  'flex h-16 flex-col items-center justify-center gap-1 text-xs font-medium',
                  isActive ? 'text-turf-strong' : 'text-ink-muted',
                )
              }
            >
              <Icon className="size-[22px]" />
              {label}
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}

export function AppShell() {
  const { data: me } = useMe()
  const signedIn = Boolean(me)

  return (
    <div className="flex min-h-dvh">
      <Sidebar signedIn={signedIn} />
      <div className="flex min-w-0 flex-1 flex-col">
        <header className="sticky top-0 z-20 flex h-14 items-center border-b border-line bg-surface/95 px-4 backdrop-blur md:hidden">
          <Brand />
        </header>
        <OfflineBanner />
        <main className="w-full max-w-4xl flex-1 px-4 pt-6 pb-28 sm:px-6 md:px-10 md:pt-10 md:pb-16">
          <Outlet />
        </main>
      </div>
      <TabBar signedIn={signedIn} />
    </div>
  )
}
