import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { getJson, send } from '../../api/client'
import type { Role } from './useLeagues'

export type InvitePreview = {
  id: string
  name: string
  slug: string
  memberCount: number
  myRole: Role | null
}

export type Member = {
  userId: string
  displayName: string
  clubName: string | null
  role: Role
  joinedAt: string
}

type Invite = { code: string | null }

export function inviteUrl(code: string) {
  return `${window.location.origin}/join/${code}`
}

/** What an invite link leads to; works signed out. */
export function useInvitePreview(code: string) {
  return useQuery({
    queryKey: ['invites', code],
    queryFn: () => getJson<InvitePreview>(`/api/public/invites/${encodeURIComponent(code)}`),
  })
}

export function useAcceptInvite() {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (code: string) =>
      send<{ name: string; slug: string; role: Role }>('POST', `/api/invites/${encodeURIComponent(code)}/accept`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['leagues'] }),
  })
}

/** The league's current link (owners and admins only); `code` is null until one is made. */
export function useLeagueInvite(slug: string, enabled: boolean) {
  return useQuery({
    queryKey: ['leagues', 'invite', slug],
    queryFn: () => getJson<Invite>(`/api/leagues/${encodeURIComponent(slug)}/invite`),
    enabled,
  })
}

/** Makes the league's link, or replaces it (`rotate`) so the old one stops working. */
export function useChangeInvite(slug: string, action: 'create' | 'rotate') {
  const queryClient = useQueryClient()
  const path = `/api/leagues/${encodeURIComponent(slug)}/invite${action === 'rotate' ? '/rotate' : ''}`
  return useMutation({
    mutationFn: () => send<Invite>('POST', path),
    onSuccess: (invite) => queryClient.setQueryData(['leagues', 'invite', slug], invite),
  })
}

export function useMembers(slug: string, enabled: boolean) {
  return useQuery({
    queryKey: ['leagues', 'members', slug],
    queryFn: () => getJson<Member[]>(`/api/leagues/${encodeURIComponent(slug)}/members`),
    enabled,
  })
}

/** Owner only: make a member an admin or a manager. */
export function useChangeRole(slug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: ({ userId, role }: { userId: string; role: 'ADMIN' | 'MANAGER' }) =>
      send('PUT', `/api/leagues/${encodeURIComponent(slug)}/members/${userId}/role`, { role }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['leagues'] }),
  })
}

/** Owner only: remove a member (refused while they play in the active season). */
export function useRemoveMember(slug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (userId: string) => send('DELETE', `/api/leagues/${encodeURIComponent(slug)}/members/${userId}`),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['leagues'] }),
  })
}

/** Sets the signed-in member's own club name in this league. */
export function useSetClubName(slug: string) {
  const queryClient = useQueryClient()
  return useMutation({
    mutationFn: (name: string) => send<{ name: string }>('PUT', `/api/leagues/${encodeURIComponent(slug)}/club`, { name }),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['leagues'] }),
  })
}
