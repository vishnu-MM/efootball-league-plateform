import { Slot } from 'radix-ui'
import type { ButtonHTMLAttributes } from 'react'
import { cn } from '../../lib/cn'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'
type Size = 'md' | 'lg' | 'sm'

// Colours come from the surrounding tile's tone, so a button always suits its surface.
const VARIANTS: Record<Variant, string> = {
  primary: 'bg-btn-primary text-btn-primary-fg',
  secondary: 'bg-btn-secondary text-btn-secondary-fg',
  ghost: 'text-tile-fg hover:bg-tile-soft',
  danger: 'bg-ink text-coral',
}

const SIZES: Record<Size, string> = {
  sm: 'h-9 px-4 text-xs',
  md: 'h-11 px-5 text-[0.8125rem]',
  lg: 'h-12 px-6 text-sm',
}

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant
  size?: Size
  /** Render the child element (e.g. a router Link) with button styles. */
  asChild?: boolean
}

export function Button({ variant = 'primary', size = 'md', asChild, className, type, ...props }: ButtonProps) {
  const Component = asChild ? Slot.Root : 'button'
  return (
    <Component
      type={asChild ? undefined : (type ?? 'button')}
      className={cn(
        'press inline-flex items-center justify-center gap-2 rounded-pill font-mono font-medium whitespace-nowrap',
        'disabled:pointer-events-none disabled:opacity-50',
        VARIANTS[variant],
        SIZES[size],
        className,
      )}
      {...props}
    />
  )
}
