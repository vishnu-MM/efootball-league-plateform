import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { Toaster } from 'sonner'
import { ApiError } from './api/client'
import { AppShell } from './components/layout/AppShell'
import { RequireAuth } from './features/auth/RequireAuth'
import { AccountPage } from './pages/AccountPage'
import { AddCardPage } from './pages/AddCardPage'
import { ApprovalsPage } from './pages/ApprovalsPage'
import { CreateLeaguePage } from './pages/CreateLeaguePage'
import { CreateSeasonPage } from './pages/CreateSeasonPage'
import { DiscordSettingsPage } from './pages/DiscordSettingsPage'
import { DiscoverPage } from './pages/DiscoverPage'
import { FixturePage } from './pages/FixturePage'
import { HomePage } from './pages/HomePage'
import { JoinPage } from './pages/JoinPage'
import { LeaguePage } from './pages/LeaguePage'
import { LogPage } from './pages/LogPage'
import { NotFoundPage } from './pages/NotFoundPage'
import { NotificationsPage } from './pages/NotificationsPage'
import { SignInPage } from './pages/SignInPage'
import { SquadPage } from './pages/SquadPage'
import { SquadRulesPage } from './pages/SquadRulesPage'
import { StatsPage } from './pages/StatsPage'
import './styles.css'

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      // Don't retry "not found" or "not signed in"; retry network hiccups once.
      retry: (failureCount, error) =>
        !(error instanceof ApiError && error.status >= 400 && error.status < 500) && failureCount < 1,
    },
  },
})

const router = createBrowserRouter([
  {
    element: <AppShell />,
    children: [
      { index: true, element: <HomePage /> },
      { path: 'discover', element: <DiscoverPage /> },
      { path: 'signin', element: <SignInPage /> },
      { path: 'leagues/:slug', element: <LeaguePage /> },
      { path: 'leagues/:slug/fixtures/:id', element: <FixturePage /> },
      { path: 'leagues/:slug/squads/:userId', element: <SquadPage /> },
      { path: 'leagues/:slug/log', element: <LogPage /> },
      { path: 'leagues/:slug/stats', element: <StatsPage /> },
      { path: 'join/:code', element: <JoinPage /> },
      {
        element: <RequireAuth />,
        children: [
          { path: 'leagues/new', element: <CreateLeaguePage /> },
          { path: 'leagues/:slug/seasons/new', element: <CreateSeasonPage /> },
          { path: 'leagues/:slug/squad/add', element: <AddCardPage /> },
          { path: 'leagues/:slug/squad/approvals', element: <ApprovalsPage /> },
          { path: 'leagues/:slug/squad/rules', element: <SquadRulesPage /> },
          { path: 'leagues/:slug/settings/discord', element: <DiscordSettingsPage /> },
          { path: 'account', element: <AccountPage /> },
          { path: 'notifications', element: <NotificationsPage /> },
        ],
      },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
])

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <RouterProvider router={router} />
      <Toaster
        position="top-center"
        closeButton
        icons={{
          success: <span className="size-2.5 rounded-full bg-aqua" />,
          error: <span className="size-2.5 rounded-full bg-coral" />,
          warning: <span className="size-2.5 rounded-full bg-mustard" />,
          info: <span className="size-2.5 rounded-full bg-paper" />,
        }}
        toastOptions={{
          unstyled: true,
          classNames: {
            toast: 'flex w-full animate-pop items-center gap-3 rounded-pill bg-surface-dark-2 py-2 pr-2 pl-5 font-mono text-[0.8125rem] text-on-dark',
            icon: 'flex shrink-0 items-center',
            content: 'min-w-0 flex-1',
            closeButton: 'order-last grid size-9 shrink-0 place-items-center rounded-full bg-paper text-ink [&_svg]:size-4',
          },
        }}
      />
    </QueryClientProvider>
  </StrictMode>,
)
