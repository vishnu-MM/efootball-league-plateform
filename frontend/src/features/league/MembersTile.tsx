import { MoreHorizontal, ShieldCheck, UserMinus, UserRound, Users } from 'lucide-react'
import { DropdownMenu } from 'radix-ui'
import { useState, type ReactNode } from 'react'
import { Link } from 'react-router'
import { toast } from 'sonner'
import { Avatar } from '../../components/ui/Avatar'
import { ConfirmDialog } from '../../components/ui/ConfirmDialog'
import { RoleBadge } from '../../components/ui/RoleBadge'
import { ErrorState, Skeleton } from '../../components/ui/States'
import { IconTile, Tile } from '../../components/ui/Tile'
import { cn } from '../../lib/cn'
import { useMe } from '../auth/useAuth'
import { useChangeRole, useMembers, useRemoveMember, type Member } from './useInvites'

/** Who is in the league; shown to members only. The owner can change roles and remove people here. */
export function MembersTile({ slug, isOwner, className }: { slug: string; isOwner: boolean; className?: string }) {
  const { data: me } = useMe()
  const { data, isPending, isError, refetch } = useMembers(slug, true)
  const changeRole = useChangeRole(slug)
  const removeMember = useRemoveMember(slug)
  const [removing, setRemoving] = useState<Member | null>(null)

  function setRole(member: Member, role: 'ADMIN' | 'MANAGER') {
    changeRole.mutate(
      { userId: member.userId, role },
      {
        onSuccess: () => toast.success(`${member.displayName} is now ${role === 'ADMIN' ? 'an admin' : 'a manager'}`),
        onError: (error) => toast.error(error.message),
      },
    )
  }

  function remove(member: Member) {
    removeMember.mutate(member.userId, {
      onSuccess: () => toast.success(`${member.displayName} was removed`),
      onError: (error) => toast.error(error.message),
    })
  }

  return (
    <Tile as="section" tone="sage" aria-labelledby="members-heading" className={cn('flex flex-col gap-4', className)}>
      <div className="flex items-center gap-4">
        <IconTile>
          <Users className="size-6" />
        </IconTile>
        <h2 id="members-heading" className="type-title">
          Members
        </h2>
      </div>

      {isPending && (
        <div role="status" aria-label="Loading members" className="flex flex-col gap-3 py-2">
          <Skeleton className="h-10" />
          <Skeleton className="h-10" />
        </div>
      )}
      {isError && <ErrorState message="Members couldn't be loaded." onRetry={() => refetch()} />}
      {data && (
        <ul className="divide-y divide-tile-soft">
          {data.map((member) => {
            const isMe = member.userId === me?.id
            return (
              <li key={member.userId} className="flex items-center gap-3 py-3">
                <Avatar name={member.displayName} className={cn('size-10', !isMe && 'bg-tile-soft')} />
                <span className="flex min-w-0 flex-1 flex-col">
                  <Link to={`/leagues/${slug}/squads/${member.userId}`} className="truncate text-sm hover:underline">
                    {member.displayName}
                    {isMe && <span className="ml-2 text-xs text-tile-muted">(you)</span>}
                  </Link>
                  {member.clubName && <span className="truncate text-xs text-tile-muted">{member.clubName}</span>}
                </span>
                <RoleBadge role={member.role} />
                {isOwner && member.role !== 'OWNER' && (
                  <MemberMenu
                    member={member}
                    onRole={(role) => setRole(member, role)}
                    onRemove={() => setRemoving(member)}
                  />
                )}
              </li>
            )
          })}
        </ul>
      )}

      <ConfirmDialog
        open={removing !== null}
        onOpenChange={(open) => !open && setRemoving(null)}
        title={`Remove ${removing?.displayName ?? 'member'}?`}
        confirmLabel="Remove"
        onConfirm={() => removing && remove(removing)}
      >
        They lose access to the league. You can invite them again with the invite link.
      </ConfirmDialog>
    </Tile>
  )
}

function MemberMenu({ member, onRole, onRemove }: { member: Member; onRole: (role: 'ADMIN' | 'MANAGER') => void; onRemove: () => void }) {
  return (
    // Non-modal, so the confirm dialog it opens can take focus cleanly.
    <DropdownMenu.Root modal={false}>
      <DropdownMenu.Trigger
        aria-label={`Change ${member.displayName}`}
        className="press grid size-9 place-items-center rounded-full bg-tile-soft text-ink"
      >
        <MoreHorizontal className="size-5" />
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content
          align="end"
          sideOffset={8}
          className="z-50 flex min-w-56 animate-pop flex-col gap-1 rounded-inner bg-surface-dark-2 p-2 text-on-dark"
        >
          {member.role === 'MANAGER' ? (
            <Item icon={<ShieldCheck className="size-4" />} onSelect={() => onRole('ADMIN')}>
              Make admin
            </Item>
          ) : (
            <Item icon={<UserRound className="size-4" />} onSelect={() => onRole('MANAGER')}>
              Make manager
            </Item>
          )}
          <DropdownMenu.Separator className="mx-3 my-1 h-px bg-hairline-dark" />
          <Item icon={<UserMinus className="size-4" />} onSelect={onRemove} danger>
            Remove from league
          </Item>
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  )
}

function Item({ children, icon, onSelect, danger }: { children: string; icon: ReactNode; onSelect: () => void; danger?: boolean }) {
  return (
    <DropdownMenu.Item
      onSelect={onSelect}
      className={cn(
        'flex h-10 cursor-pointer items-center gap-2.5 rounded-pill px-3 text-[0.8125rem] outline-none data-[highlighted]:bg-hairline-dark',
        danger && 'text-coral',
      )}
    >
      {icon}
      {children}
    </DropdownMenu.Item>
  )
}
