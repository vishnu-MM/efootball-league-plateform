import { TriangleAlert } from 'lucide-react'
import type { ReactNode } from 'react'
import { Bento } from '../layout/Page'
import { cn } from '../../lib/cn'
import { Button } from './Button'
import { IconTile, Tile } from './Tile'

export function Skeleton({ className }: { className?: string }) {
  return <div aria-hidden className={cn('animate-pulse rounded-pill bg-tile-soft', className)} />
}

/** Placeholder tiles shown while a list of leagues loads. */
export function ListSkeleton({ rows = 3, label }: { rows?: number; label: string }) {
  return (
    <Bento role="status" aria-label={label}>
      {Array.from({ length: rows }, (_, i) => (
        <Tile key={i} className="flex min-h-52 flex-col justify-between md:col-span-3 xl:col-span-4">
          <div className="flex items-start justify-between">
            <Skeleton className="h-14 w-12 rounded-tile" />
            <Skeleton className="size-11" />
          </div>
          <div className="flex flex-col gap-3">
            <Skeleton className="h-7 w-3/4" />
            <Skeleton className="h-5 w-1/4" />
          </div>
        </Tile>
      ))}
    </Bento>
  )
}

type EmptyStateProps = {
  title: string
  children?: ReactNode
  actions?: ReactNode
  icon?: ReactNode
  /** Render as a card nested inside a tile instead of as a tile of its own. */
  nested?: boolean
  className?: string
}

/*
 * Illustration slot: the spec calls for a retro rubber-hose illustration here.
 * Until artwork exists, the icon sits in a mustard icon tile in its place.
 */
export function EmptyState({ title, children, actions, icon, nested, className }: EmptyStateProps) {
  const content = (
    <>
      {icon && <IconTile variant="mustard">{icon}</IconTile>}
      <h3 className="type-title">{title}</h3>
      {children && <div className="max-w-[56ch] text-tile-muted">{children}</div>}
      {actions && <div className="mt-2 flex flex-wrap gap-3">{actions}</div>}
    </>
  )
  if (nested) {
    return <div className={cn('flex flex-col items-start gap-4 rounded-inner bg-tile-soft p-5', className)}>{content}</div>
  }
  return (
    <Tile className={cn('flex flex-col items-start gap-4 md:col-span-6 xl:col-span-7', className)}>{content}</Tile>
  )
}

/** Alert block: a coral icon tile and a short message. Reads on any surface. */
export function ErrorState({ message, onRetry }: { message: string; onRetry?: () => void }) {
  return (
    <div role="alert" className="tone-ink flex flex-wrap items-center gap-4 rounded-inner bg-surface-dark-2 p-3 pr-4 text-on-dark">
      <IconTile variant="coral" className="size-10">
        <TriangleAlert className="size-5" />
      </IconTile>
      <p className="min-w-0 flex-1">{message}</p>
      {onRetry && (
        <Button variant="secondary" size="sm" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  )
}
