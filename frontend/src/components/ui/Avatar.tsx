import { cn } from '../../lib/cn'
import { initials } from '../../lib/initials'

/** "You" is aqua throughout the app. */
export function Avatar({ name, className }: { name: string; className?: string }) {
  return (
    <span
      aria-hidden
      className={cn(
        'inline-flex size-10 shrink-0 items-center justify-center rounded-full bg-aqua font-display text-base font-semibold text-ink',
        className,
      )}
    >
      {initials(name)}
    </span>
  )
}
