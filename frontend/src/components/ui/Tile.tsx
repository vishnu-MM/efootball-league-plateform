import { ArrowUpRight } from 'lucide-react'
import type { HTMLAttributes, ReactNode } from 'react'
import { cn } from '../../lib/cn'
import { TONE_CLASS, type Tone } from '../../lib/tones'

type TileProps = HTMLAttributes<HTMLElement> & {
  tone?: Tone
  as?: 'div' | 'section' | 'article' | 'header'
}

/** A bento tile: one solid colour, large radius, no border or shadow. */
export function Tile({ tone = 'dark', as: Component = 'div', className, ...props }: TileProps) {
  return <Component className={cn(TONE_CLASS[tone], 'rounded-card bg-tile p-5 text-tile-fg md:p-6', className)} {...props} />
}

/** The ↗ circle on every navigable tile. Turns when its tile (a `group`) is hovered. */
export function OpenArrow({ className }: { className?: string }) {
  return (
    <span
      aria-hidden
      className={cn(
        'grid size-11 shrink-0 place-items-center rounded-full bg-tile-arrow text-ink transition-transform duration-200 group-hover:rotate-45',
        className,
      )}
    >
      <ArrowUpRight className="size-5" />
    </span>
  )
}

type IconTileProps = { children: ReactNode; variant?: 'soft' | 'mustard' | 'coral'; className?: string }

/** Rounded-square icon holder. Mustard draws attention, coral flags a problem, soft blends into the tile's hue. */
export function IconTile({ children, variant = 'soft', className }: IconTileProps) {
  return (
    <span
      aria-hidden
      className={cn(
        'grid size-12 shrink-0 place-items-center rounded-tile',
        variant === 'soft' && 'bg-tile-soft text-tile-fg',
        variant === 'mustard' && 'bg-mustard text-ink',
        variant === 'coral' && 'bg-coral text-ink',
        className,
      )}
    >
      {children}
    </span>
  )
}
