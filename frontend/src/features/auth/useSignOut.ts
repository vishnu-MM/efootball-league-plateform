import { useNavigate } from 'react-router'
import { useLogout } from './useAuth'

/** Signs out, then returns to the home page. */
export function useSignOut() {
  const logout = useLogout()
  const navigate = useNavigate()
  return {
    pending: logout.isPending,
    run: () => logout.mutate(undefined, { onSuccess: () => navigate('/') }),
    navigateToAccount: () => navigate('/account'),
  }
}
