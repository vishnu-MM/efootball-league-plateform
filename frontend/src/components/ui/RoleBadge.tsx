import type { Role } from '../../features/league/useLeagues'
import { cn } from '../../lib/cn'

const LABELS: Record<Role, string> = {
  OWNER: 'Owner',
  ADMIN: 'Admin',
  MANAGER: 'Manager',
}

/** A chip: tinted on dark tiles, the tile's own soft hue on bright ones. */
export function RoleBadge({ role, className }: { role: Role; className?: string }) {
  return (
    <span className={cn('inline-flex h-6 items-center rounded-pill bg-chip px-2.5 text-xs text-chip-fg', className)}>
      {LABELS[role]}
    </span>
  )
}
