import type { Role } from '../../features/league/useLeagues'
import { cn } from '../../lib/cn'

const LABELS: Record<Role, string> = {
  OWNER: 'Owner',
  ADMIN: 'Admin',
  MANAGER: 'Manager',
}

export function RoleBadge({ role, className }: { role: Role; className?: string }) {
  return (
    <span
      className={cn(
        'inline-flex h-6 items-center rounded-full px-2.5 text-xs font-semibold',
        role === 'MANAGER' ? 'bg-sunken text-ink-muted' : 'bg-turf-soft text-turf-strong',
        className,
      )}
    >
      {LABELS[role]}
    </span>
  )
}
