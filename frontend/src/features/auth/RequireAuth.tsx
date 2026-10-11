import { Navigate, Outlet, useLocation } from 'react-router'
import { ListSkeleton } from '../../components/ui/States'
import { useMe } from './useAuth'

/** Sends signed-out visitors to the sign-in page, then back here afterwards. */
export function RequireAuth() {
  const { data: me, isPending } = useMe()
  const location = useLocation()

  if (isPending) return <ListSkeleton rows={2} label="Loading" />
  if (!me) {
    const next = encodeURIComponent(location.pathname + location.search)
    return <Navigate to={`/signin?next=${next}`} replace />
  }
  return <Outlet />
}
