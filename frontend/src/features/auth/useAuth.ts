import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { ApiError, getJson, send } from '../../api/client'

export type Me = {
  id: string
  displayName: string
  email: string | null
}

/** The signed-in user, or null when signed out. */
export function useMe() {
  return useQuery({
    queryKey: ['me'],
    queryFn: async () => {
      try {
        return await getJson<Me>('/api/me')
      } catch (error) {
        if (error instanceof ApiError && error.status === 401) return null
        throw error
      }
    },
  })
}

/** Which sign-in buttons to show: "google", "discord", and "dev" (local only). */
export function useProviders() {
  return useQuery({ queryKey: ['auth', 'providers'], queryFn: () => getJson<string[]>('/api/public/auth/providers') })
}

export function useDevLogin() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (displayName: string) => send('POST', '/api/dev/login', { displayName }),
    onSuccess: () => queryClient.invalidateQueries(),
  })
}

export function useLogout() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: () => send('POST', '/api/auth/logout'),
    onSuccess: () => queryClient.invalidateQueries(),
  })
}
