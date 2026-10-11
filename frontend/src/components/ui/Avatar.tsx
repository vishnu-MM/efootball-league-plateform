import { cn } from '../../lib/cn'
import { initials } from '../../lib/initials'

export function Avatar({ name, className }: { name: string; className?: string }) {
  return (
    <span
      aria-hidden
      className={cn(
        'inline-flex size-9 shrink-0 items-center justify-center rounded-full bg-turf-soft text-sm font-bold text-turf-strong [font-stretch:115%]',
        className,
      )}
    >
      {initials(name)}
    </span>
  )
}
