import { Tabs } from 'radix-ui'
import { useState } from 'react'
import { cn } from '../../lib/cn'
import { BOARDS, cardLine, type Board, type Stats, type StatRow } from './useStats'

/** Goals / Assists / MVP tabs, each a ranked list cut to `limit`. */
export function Leaderboards({ stats, limit, className }: { stats: Stats; limit: number; className?: string }) {
  const [board, setBoard] = useState<Board>('scorers')
  return (
    <Tabs.Root value={board} onValueChange={(v) => setBoard(v as Board)} className={cn('flex flex-col gap-4', className)}>
      <Tabs.List aria-label="Leaderboard" className="flex gap-2">
        {BOARDS.map((b) => (
          <Tabs.Trigger
            key={b.key}
            value={b.key}
            className="press h-9 rounded-pill bg-tile-soft px-4 text-xs data-[state=active]:bg-ink data-[state=active]:text-on-dark"
          >
            {b.label}
          </Tabs.Trigger>
        ))}
      </Tabs.List>
      {BOARDS.map((b) => (
        <Tabs.Content key={b.key} value={b.key}>
          <BoardList rows={stats[b.key].slice(0, limit)} unit={b.unit} />
        </Tabs.Content>
      ))}
    </Tabs.Root>
  )
}

function BoardList({ rows, unit }: { rows: StatRow[]; unit: [string, string] }) {
  if (rows.length === 0) {
    return <p className="rounded-inner bg-tile-soft p-5 text-tile-muted">Nobody yet. Final results with scorers fill this in.</p>
  }
  return (
    <ol className="flex flex-col gap-2">
      {rows.map((row, i) => (
        <li key={`${row.userId}:${row.name}:${i}`} className="flex items-center gap-3 rounded-inner bg-tile-soft px-4 py-3">
          <span className="w-6 shrink-0 text-right font-metric text-sm text-tile-muted tabular-nums">{row.rank}</span>
          <span className="flex min-w-0 flex-1 flex-col">
            <span className="truncate text-sm font-medium">{row.name}</span>
            <span className="truncate text-xs text-tile-muted">
              {row.teamName}
              {row.cardType && ` · ${cardLine(row)}`}
            </span>
          </span>
          <span className="shrink-0 font-metric text-xl font-semibold tabular-nums" aria-label={`${row.count} ${row.count === 1 ? unit[0] : unit[1]}`}>
            {row.count}
          </span>
        </li>
      ))}
    </ol>
  )
}
