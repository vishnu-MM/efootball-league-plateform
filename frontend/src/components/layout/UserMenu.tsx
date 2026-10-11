import { ChevronsUpDown, LogOut, UserRound } from 'lucide-react'
import { DropdownMenu } from 'radix-ui'
import type { ReactNode } from 'react'
import { useMe } from '../../features/auth/useAuth'
import { useSignOut } from '../../features/auth/useSignOut'
import { Avatar } from '../ui/Avatar'

export function UserMenu() {
  const { data: me } = useMe()
  const signOut = useSignOut()
  if (!me) return null

  return (
    <DropdownMenu.Root>
      <DropdownMenu.Trigger className="flex w-full items-center gap-3 rounded-lg p-2 text-left hover:bg-sunken data-[state=open]:bg-sunken">
        <Avatar name={me.displayName} />
        <span className="min-w-0 flex-1">
          <span className="block truncate text-sm font-semibold">{me.displayName}</span>
          {me.email && <span className="block truncate text-xs text-ink-muted">{me.email}</span>}
        </span>
        <ChevronsUpDown className="size-4 text-ink-muted" aria-hidden />
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content
          side="top"
          align="start"
          sideOffset={6}
          className="z-50 min-w-[var(--radix-dropdown-menu-trigger-width)] rounded-xl border border-line bg-surface p-1 shadow-lg"
        >
          <MenuItem onSelect={signOut.navigateToAccount} icon={<UserRound className="size-4" />}>
            Account
          </MenuItem>
          <DropdownMenu.Separator className="my-1 h-px bg-line" />
          <MenuItem onSelect={signOut.run} icon={<LogOut className="size-4" />}>
            Sign out
          </MenuItem>
        </DropdownMenu.Content>
      </DropdownMenu.Portal>
    </DropdownMenu.Root>
  )
}

function MenuItem({ children, icon, onSelect }: { children: string; icon: ReactNode; onSelect: () => void }) {
  return (
    <DropdownMenu.Item
      onSelect={onSelect}
      className="flex h-10 cursor-pointer items-center gap-2.5 rounded-lg px-3 text-sm font-medium outline-none data-[highlighted]:bg-sunken"
    >
      {icon}
      {children}
    </DropdownMenu.Item>
  )
}
