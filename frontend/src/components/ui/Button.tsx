import { Slot } from 'radix-ui'
import type { ButtonHTMLAttributes } from 'react'
import { cn } from '../../lib/cn'

type Variant = 'primary' | 'secondary' | 'ghost' | 'danger'
type Size = 'md' | 'lg' | 'sm'

const VARIANTS: Record<Variant, string> = {
  primary: 'bg-turf text-on-turf hover:bg-turf-strong',
  secondary: 'border border-line bg-surface text-ink hover:bg-sunken',
  ghost: 'text-ink hover:bg-sunken',
  danger: 'border border-line bg-surface text-red-card hover:bg-red-card-soft',
}

const SIZES: Record<Size, string> = {
  sm: 'h-9 px-3 text-sm',
  md: 'h-11 px-4',
  lg: 'h-12 px-5 text-[1.0625rem]',
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
        'inline-flex items-center justify-center gap-2 rounded-lg font-semibold whitespace-nowrap transition-colors',
        'disabled:pointer-events-none disabled:opacity-50',
        VARIANTS[variant],
        SIZES[size],
        className,
      )}
      {...props}
    />
  )
}
