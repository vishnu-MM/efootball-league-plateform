import type { HTMLAttributes, ReactNode } from 'react'
import { BENTO_GRID } from '../../lib/bento'
import { cn } from '../../lib/cn'

export function Bento({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div className={cn(BENTO_GRID, className)} {...props} />
}

/** Page title (left-aligned, condensed caps) with an optional line below and actions on the right. */
export function PageHeader({ title, children, actions }: { title: ReactNode; children?: ReactNode; actions?: ReactNode }) {
  return (
    <header className="flex flex-wrap items-end justify-between gap-4">
      <div className="flex min-w-0 flex-col gap-2">
        <h1 className="type-display break-words">{title}</h1>
        {children && <p className="max-w-[60ch] text-tile-muted">{children}</p>}
      </div>
      {actions}
    </header>
  )
}
