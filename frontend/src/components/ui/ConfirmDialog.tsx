import { AlertDialog } from 'radix-ui'
import type { ReactElement, ReactNode } from 'react'
import { Button } from './Button'

type ConfirmDialogProps = {
  /** The element that opens the dialog, e.g. a Button. Leave out when controlling it with `open`. */
  trigger?: ReactElement
  open?: boolean
  onOpenChange?: (open: boolean) => void
  title: string
  children: ReactNode
  confirmLabel: string
  onConfirm: () => void
}

/** A dark modal tile over an ink scrim, asking before an action that can't be undone. */
export function ConfirmDialog({ trigger, open, onOpenChange, title, children, confirmLabel, onConfirm }: ConfirmDialogProps) {
  return (
    <AlertDialog.Root open={open} onOpenChange={onOpenChange}>
      {trigger && <AlertDialog.Trigger asChild>{trigger}</AlertDialog.Trigger>}
      <AlertDialog.Portal>
        <AlertDialog.Overlay className="fixed inset-0 z-40 bg-ink/60" />
        <AlertDialog.Content className="tone-dark fixed top-1/2 left-1/2 z-50 flex w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 animate-pop flex-col gap-4 rounded-card bg-tile p-6 text-tile-fg">
          <AlertDialog.Title className="type-title">{title}</AlertDialog.Title>
          <AlertDialog.Description className="text-tile-muted">{children}</AlertDialog.Description>
          <div className="mt-2 flex flex-wrap gap-3">
            <AlertDialog.Action asChild>
              <Button onClick={onConfirm}>{confirmLabel}</Button>
            </AlertDialog.Action>
            <AlertDialog.Cancel asChild>
              <Button variant="secondary">Cancel</Button>
            </AlertDialog.Cancel>
          </div>
        </AlertDialog.Content>
      </AlertDialog.Portal>
    </AlertDialog.Root>
  )
}
