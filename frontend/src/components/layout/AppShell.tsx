import { ArrowLeft, Compass, Home, Plus, UserRound } from 'lucide-react'
import { Tooltip } from 'radix-ui'
import { useEffect, type ComponentType } from 'react'
import { Link, NavLink, Outlet, useLocation, useMatch, useNavigate } from 'react-router'
import { useMe } from '../../features/auth/useAuth'
import { useLeague, useMyLeagues } from '../../features/league/useLeagues'
import { APP_NAME } from '../../lib/app'
import { cn } from '../../lib/cn'
import { initials } from '../../lib/initials'
import { takeNext } from '../../lib/signInNext'
import { Button } from '../ui/Button'
import { Crest } from '../ui/Crest'
import { Skeleton } from '../ui/States'
import { Tip } from '../ui/Tip'
import { OfflineBanner } from './OfflineBanner'
import { UserMenu } from './UserMenu'

type NavItem = { to: string; label: string; icon: ComponentType<{ className?: string }>; end?: boolean }

const PAGE_LABELS: Record<string, string> = {
  '/': 'Home',
  '/discover': 'Discover',
  '/signin': 'Sign in',
  '/leagues/new': 'New league',
  '/account': 'Account',
}

/** The current page's name for the breadcrumb; a league page shows the league's name. */
function useCurrentPageLabel() {
  const { pathname } = useLocation()
  const slug = useMatch('/leagues/:slug')?.params.slug
  const isLeague = Boolean(slug) && !(pathname in PAGE_LABELS)
  // Same query as the league page, so this reads the cached result rather than fetching twice.
  const { data: league } = useLeague(slug ?? '', isLeague)
  if (pathname in PAGE_LABELS) return PAGE_LABELS[pathname]
  if (isLeague) return league?.name ?? ''
  if (pathname.startsWith('/join/')) return 'Join league'
  if (/^\/leagues\/[^/]+\/seasons\/new$/.test(pathname)) return 'New season'
  if (/^\/leagues\/[^/]+\/fixtures\/[^/]+$/.test(pathname)) return 'Match'
  if (/^\/leagues\/[^/]+\/squad\/add$/.test(pathname)) return 'Add card'
  if (/^\/leagues\/[^/]+\/squad\/approvals$/.test(pathname)) return 'Approvals'
  if (/^\/leagues\/[^/]+\/squad\/rules$/.test(pathname)) return 'Squad rules'
  if (/^\/leagues\/[^/]+\/squads\/[^/]+$/.test(pathname)) return 'Squad'
  return '404'
}

function LogoBadge() {
  return (
    <Link
      to="/"
      aria-label={`${APP_NAME} home`}
      className="press grid size-11 shrink-0 place-items-center rounded-full bg-sage font-display text-lg font-semibold text-ink"
    >
      {initials(APP_NAME)}
    </Link>
  )
}

const railItem = (isActive: boolean) =>
  cn(
    'press grid size-11 place-items-center rounded-tile',
    isActive ? 'bg-aqua text-ink' : 'text-on-dark/80 hover:bg-surface-dark-2 hover:text-on-dark',
  )

function RailLink({ item }: { item: NavItem }) {
  const Icon = item.icon
  return (
    <Tip label={item.label}>
      <NavLink to={item.to} end={item.end} aria-label={item.label} className={({ isActive }) => railItem(isActive)}>
        <Icon className="size-6" />
      </NavLink>
    </Tip>
  )
}

function RailLeagues() {
  const { data, isPending } = useMyLeagues()
  if (isPending) {
    return (
      <div className="flex flex-col gap-3">
        <Skeleton className="size-11 rounded-tile bg-surface-dark-2" />
        <Skeleton className="size-11 rounded-tile bg-surface-dark-2" />
      </div>
    )
  }
  if (!data?.length) return null
  return (
    <ul className="flex flex-col items-center gap-2">
      {data.map((league) => (
        <li key={league.id}>
          <Tip label={league.name}>
            <NavLink
              to={`/leagues/${league.slug}`}
              aria-label={league.name}
              className={({ isActive }) => railItem(isActive)}
            >
              <Crest seed={league.slug} name={league.name} size={28} />
            </NavLink>
          </Tip>
        </li>
      ))}
    </ul>
  )
}

function Rail({ signedIn }: { signedIn: boolean }) {
  const items: NavItem[] = [
    { to: '/', label: 'Home', icon: Home, end: true },
    { to: '/discover', label: 'Discover', icon: Compass },
  ]
  return (
    <aside className="hidden w-(--sidebar-w) shrink-0 flex-col items-center gap-6 border-r border-hairline-dark py-6 md:flex">
      <LogoBadge />
      <nav aria-label="Main" className="flex flex-col items-center gap-4">
        {items.map((item) => (
          <RailLink key={item.to} item={item} />
        ))}
      </nav>
      {signedIn && (
        <>
          <span aria-hidden className="h-px w-3 shrink-0 bg-hairline-dark" />
          <nav aria-label="Your leagues" className="flex min-h-0 flex-1 flex-col items-center overflow-y-auto px-2 py-1">
            <RailLeagues />
          </nav>
          <div className="flex flex-col items-center gap-2 rounded-pill bg-paper p-1.5">
            <Tip label="New league">
              <Link
                to="/leagues/new"
                aria-label="New league"
                className="press grid size-11 place-items-center rounded-full bg-coral text-ink"
              >
                <Plus className="size-6" />
              </Link>
            </Tip>
            <UserMenu />
          </div>
        </>
      )}
    </aside>
  )
}

function TopBar({ signedIn }: { signedIn: boolean }) {
  const label = useCurrentPageLabel()
  return (
    <header className="hidden h-24 shrink-0 items-center justify-between gap-6 px-6 md:flex">
      <nav aria-label="Breadcrumb" className="min-w-0">
        <ol className="flex items-baseline font-crumb text-xl">
          <li className="shrink-0">
            <Link to="/" className="rounded-chip text-muted-dark hover:text-on-dark">
              {APP_NAME}
            </Link>
          </li>
          <li aria-hidden className="px-2 text-muted-dark">
            /
          </li>
          <li aria-current="page" className="truncate text-on-dark">
            {label}
          </li>
        </ol>
      </nav>
      {!signedIn && (
        <Button asChild variant="secondary">
          <Link to="/signin">Sign in</Link>
        </Button>
      )}
    </header>
  )
}

const TAB_ROOTS = new Set(Object.keys(PAGE_LABELS))

function MobileHeader() {
  const { pathname } = useLocation()
  const navigate = useNavigate()
  const isRoot = TAB_ROOTS.has(pathname)
  // Go back within the app when there is somewhere to go; otherwise go home.
  const back = () => ((window.history.state?.idx ?? 0) > 0 ? navigate(-1) : navigate('/'))

  return (
    <header className="flex items-center gap-3 px-4 pt-4 md:hidden">
      {isRoot ? (
        <LogoBadge />
      ) : (
        <button type="button" onClick={back} aria-label="Back" className="press grid size-11 place-items-center rounded-full bg-paper text-ink">
          <ArrowLeft className="size-5" />
        </button>
      )}
      <span className="font-crumb text-lg text-muted-dark">{APP_NAME}</span>
    </header>
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
      className="fixed inset-x-4 bottom-[calc(1rem+env(safe-area-inset-bottom))] z-20 rounded-pill bg-surface-dark p-2 md:hidden"
    >
      <ul className="flex justify-around">
        {items.map(({ to, label, icon: Icon, end }) => (
          <li key={to}>
            <NavLink
              to={to}
              end={end}
              className={({ isActive }) =>
                cn(
                  'press grid size-12 place-items-center rounded-tile',
                  isActive ? 'bg-aqua text-ink' : 'text-on-dark/80',
                )
              }
            >
              <Icon className="size-6" />
              <span className="sr-only">{label}</span>
            </NavLink>
          </li>
        ))}
      </ul>
    </nav>
  )
}

/** After a Google/Discord round-trip, continue to the page the user was heading to. */
function useContinueAfterSignIn(signedIn: boolean) {
  const navigate = useNavigate()
  useEffect(() => {
    if (!signedIn) return
    const next = takeNext()
    if (next && next !== window.location.pathname) navigate(next, { replace: true })
  }, [signedIn, navigate])
}

export function AppShell() {
  const { data: me } = useMe()
  const signedIn = Boolean(me)
  useContinueAfterSignIn(signedIn)

  return (
    <Tooltip.Provider delayDuration={250}>
      <div className="min-h-dvh md:flex md:h-dvh md:p-6">
        <div className="flex min-h-dvh flex-1 bg-ink md:min-h-0 md:overflow-hidden md:rounded-frame md:border-2 md:border-(--frame-border)">
          <Rail signedIn={signedIn} />
          <div className="flex min-w-0 flex-1 flex-col">
            <MobileHeader />
            <TopBar signedIn={signedIn} />
            <OfflineBanner />
            <main className="flex-1 px-4 pt-6 pb-32 md:overflow-y-auto md:px-6 md:pt-0 md:pb-6">
              <Outlet />
            </main>
          </div>
        </div>
      </div>
      <TabBar signedIn={signedIn} />
    </Tooltip.Provider>
  )
}
