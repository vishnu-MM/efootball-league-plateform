import { LogOut, UserRound } from 'lucide-react'
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
      <DropdownMenu.Trigger aria-label={`${me.displayName}: account menu`} className="press rounded-full">
        <Avatar name={me.displayName} className="size-11" />
      </DropdownMenu.Trigger>
      <DropdownMenu.Portal>
        <DropdownMenu.Content
          side="right"
          align="end"
          sideOffset={14}
          className="z-50 flex min-w-64 animate-pop flex-col gap-1 rounded-inner bg-surface-dark-2 p-2 text-on-dark"
        >
          <div className="flex flex-col gap-0.5 px-3 pt-2 pb-3">
            <span className="truncate font-display text-xl font-semibold uppercase">{me.displayName}</span>
            {me.email && <span className="truncate text-xs text-on-dark/75">{me.email}</span>}
          </div>
          <MenuItem onSelect={signOut.navigateToAccount} icon={<UserRound className="size-4" />}>
            Account
          </MenuItem>
          <DropdownMenu.Separator className="mx-3 my-1 h-px bg-hairline-dark" />
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
      className="flex h-10 cursor-pointer items-center gap-2.5 rounded-pill px-3 text-[0.8125rem] outline-none data-[highlighted]:bg-hairline-dark"
    >
      {icon}
      {children}
    </DropdownMenu.Item>
  )
}
