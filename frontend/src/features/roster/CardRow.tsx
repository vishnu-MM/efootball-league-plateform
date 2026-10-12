import type { ReactNode } from 'react'
import { cn } from '../../lib/cn'
import { cardLine, type CardType, type Position } from './useRoster'

type CardRowProps = {
  card: { playerName: string; cardType: CardType; rating: number; position: Position }
  /** Status chip, buttons, etc. on the right. */
  children?: ReactNode
  className?: string
}

/** One card: rating block, player name, type and position. */
export function CardRow({ card, children, className }: CardRowProps) {
  return (
    <div className={cn('flex items-center gap-3', className)}>
      <span className="grid size-12 shrink-0 place-items-center rounded-tile bg-paper font-metric text-lg font-semibold text-ink tabular-nums">
        {card.rating}
      </span>
      <span className="flex min-w-0 flex-1 flex-col">
        <span className="truncate text-sm">{card.playerName}</span>
        <span className="truncate text-xs text-tile-muted">{cardLine(card)}</span>
      </span>
      {children}
    </div>
  )
}

const STATUS_CLASS = {
  PENDING: 'bg-mustard text-ink',
  APPROVED: 'bg-aqua text-ink',
  REJECTED: 'bg-coral text-ink',
} as const

const STATUS_LABEL = { PENDING: 'Pending', APPROVED: 'Approved', REJECTED: 'Rejected' } as const

export function StatusChip({ status }: { status: keyof typeof STATUS_CLASS }) {
  return (
    <span className={cn('inline-flex h-7 shrink-0 items-center rounded-pill px-3 text-xs', STATUS_CLASS[status])}>
      {STATUS_LABEL[status]}
    </span>
  )
}
