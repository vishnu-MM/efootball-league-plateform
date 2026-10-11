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
    <div className="flex flex-col gap-2">
      <label htmlFor={inputId} className="type-label px-1 text-tile-muted">
        {label}
      </label>
      <input
        id={inputId}
        aria-invalid={error ? true : undefined}
        aria-describedby={[hint && hintId, error && errorId].filter(Boolean).join(' ') || undefined}
        className={cn(
          'h-13 rounded-pill bg-field px-5 font-mono text-sm text-field-fg placeholder:text-field-placeholder',
          // The error ring yields to the aqua focus ring while the field is focused.
          error && 'not-focus-visible:outline-2 not-focus-visible:outline-field-error',
          className,
        )}
        {...props}
      />
      {hint && !error && (
        <p id={hintId} className="px-1 text-xs text-tile-muted">
          {hint}
        </p>
      )}
      {error && (
        <p id={errorId} className="px-1 text-xs font-medium text-field-error">
          {error}
        </p>
      )}
    </div>
  )
}
