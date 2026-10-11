import { useId, type InputHTMLAttributes, type ReactNode } from 'react'
import { cn } from '../../lib/cn'

type TextFieldProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string
  hint?: ReactNode
  error?: string
}

export function TextField({ label, hint, error, className, id, ...props }: TextFieldProps) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const hintId = `${inputId}-hint`
  const errorId = `${inputId}-error`

  return (
    <div className="flex flex-col gap-1.5">
      <label htmlFor={inputId} className="text-sm font-semibold">
        {label}
      </label>
      <input
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={[hint && hintId, error && errorId].filter(Boolean).join(' ') || undefined}
        className={cn(
          'h-12 rounded-lg border border-line bg-surface px-3.5 text-base text-ink placeholder:text-ink-muted',
          'focus-visible:border-turf focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-turf/30',
          error && 'border-red-card',
          className,
        )}
        {...props}
      />
      {hint && !error && (
        <p id={hintId} className="text-sm text-ink-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="text-sm font-medium text-red-card">
          {error}
        </p>
      )}
    </div>
  )
}
