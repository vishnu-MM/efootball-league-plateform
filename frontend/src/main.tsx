import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router'
import { Toaster } from 'sonner'
import { ApiError } from './api/client'
import { AppShell } from './components/layout/AppShell'
import { RequireAuth } from './features/auth/RequireAuth'
import { AccountPage } from './pages/AccountPage'
import { CreateLeaguePage } from './pages/CreateLeaguePage'
import { DiscoverPage } from './pages/DiscoverPage'
import { HomePage } from './pages/HomePage'
import { LeaguePage } from './pages/LeaguePage'
import { NotFoundPage } from './pages/NotFoundPage'
import { SignInPage } from './pages/SignInPage'
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
      {
        element: <RequireAuth />,
        children: [
          { path: 'leagues/new', element: <CreateLeaguePage /> },
          { path: 'account', element: <AccountPage /> },
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
      <Toaster position="top-center" richColors closeButton />
    </QueryClientProvider>
  </StrictMode>,
)
