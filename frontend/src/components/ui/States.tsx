import type { ReactNode } from 'react'
import { cn } from '../../lib/cn'
import { Button } from './Button'

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden className={cn('animate-pulse rounded-md bg-sunken', className)} />
}

/** Placeholder rows shown while a list loads. */
export function ListSkeleton({ rows = 3, label }: { rows?: number; label: string }) {
  return (
    <div role="status" aria-label={label} className="divide-y divide-line overflow-hidden rounded-xl border border-line bg-surface">
      {Array.from({ length: rows }, (_, i) => (
        <div key={i} className="flex items-center gap-3 p-4">
          <Skeleton className="h-11 w-10" />
          <div className="flex flex-1 flex-col gap-2">
            <Skeleton className="h-4 w-2/5" />
            <Skeleton className="h-3 w-1/5" />
          </div>
        </div>
      ))}
    </div>
  )
}

type EmptyStateProps = {
  title: string
  children?: ReactNode
  actions?: ReactNode
  icon?: ReactNode
}

export function EmptyState({ title, children, actions, icon }: EmptyStateProps) {
  return (
    <div className="flex flex-col items-start gap-3 rounded-xl border border-dashed border-line px-5 py-8">
      {icon && <div className="text-ink-muted">{icon}</div>}
      <h3 className="type-heading">{title}</h3>
      {children && <div className="max-w-prose text-ink-muted">{children}</div>}
      {actions && <div className="mt-1 flex flex-wrap gap-2">{actions}</div>}
    </div>
  )
}

export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="flex flex-wrap items-center justify-between gap-3 rounded-xl border-l-4 border-red-card bg-red-card-soft px-4 py-3">
      <p className="font-medium">{message}</p>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  )
}
