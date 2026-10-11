import { Tooltip } from 'radix-ui'
import type { ReactElement } from 'react'

/** Dark mono tooltip that pops in. Needs a Tooltip.Provider above it (the app shell has one). */
export function Tip({ label, children, side = 'right' }: { label: string; children: ReactElement; side?: 'right' | 'top' }) {
  return (
    <Tooltip.Root>
      {/* A wrapper keeps Radix from merging its props into the child (which would flatten NavLink's className function). */}
      <Tooltip.Trigger asChild>
        <span className="inline-flex">{children}</span>
      </Tooltip.Trigger>
      <Tooltip.Portal>
        <Tooltip.Content
          side={side}
          sideOffset={10}
          className="z-50 animate-pop rounded-tile bg-surface-dark-2 px-3 py-1.5 font-mono text-xs text-on-dark"
        >
          {label}
        </Tooltip.Content>
      </Tooltip.Portal>
    </Tooltip.Root>
  )
}
