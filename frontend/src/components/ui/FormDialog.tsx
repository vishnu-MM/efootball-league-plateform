import { Dialog } from 'radix-ui'
import type { FormEvent, ReactElement, ReactNode, TextareaHTMLAttributes } from 'react'
import { Button } from './Button'

type FormDialogProps = {
  /** The element that opens the dialog, e.g. a Button. Leave out when controlling it with `open`. */
  trigger?: ReactElement
  open?: boolean
  onOpenChange?: (open: boolean) => void
  title: string
  description?: ReactNode
  children: ReactNode
  submitLabel: string
  pending?: boolean
  onSubmit: () => void
}

/** A dark modal tile with a small form, like ConfirmDialog but for actions that need input (e.g. a reason). */
export function FormDialog({ trigger, open, onOpenChange, title, description, children, submitLabel, pending, onSubmit }: FormDialogProps) {
  function submit(event: FormEvent) {
    event.preventDefault()
    onSubmit()
  }

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      {trigger && <Dialog.Trigger asChild>{trigger}</Dialog.Trigger>}
      <Dialog.Portal>
        <Dialog.Overlay className="fixed inset-0 z-40 bg-ink/60" />
        <Dialog.Content className="tone-dark fixed top-1/2 left-1/2 z-50 flex max-h-[calc(100dvh-2rem)] w-[calc(100%-2rem)] max-w-md -translate-x-1/2 -translate-y-1/2 animate-pop flex-col gap-4 overflow-y-auto rounded-card bg-tile p-6 text-tile-fg">
          <Dialog.Title className="type-title">{title}</Dialog.Title>
          {description ? (
            <Dialog.Description className="text-sm text-tile-muted">{description}</Dialog.Description>
          ) : (
            <Dialog.Description className="sr-only">{title}</Dialog.Description>
          )}
          <form onSubmit={submit} className="flex flex-col gap-4">
            {children}
            <div className="mt-2 flex flex-wrap gap-3">
              <Button type="submit" disabled={pending}>
                {submitLabel}
              </Button>
              <Dialog.Close asChild>
                <Button variant="secondary">Cancel</Button>
              </Dialog.Close>
            </div>
          </form>
        </Dialog.Content>
      </Dialog.Portal>
    </Dialog.Root>
  )
}

/** A multi-line text input styled like TextField. */
export function TextArea({ label, id, ...props }: TextareaHTMLAttributes<HTMLTextAreaElement> & { label: string; id: string }) {
  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={id} className="type-label px-1 text-tile-muted">
        {label}
      </label>
      <textarea id={id} rows={3} className="rounded-inner bg-field px-5 py-3 text-sm text-field-fg" {...props} />
    </div>
  )
}
