import { useMutation } from '@tanstack/react-query'
import { ImagePlus, Loader2, X } from 'lucide-react'
import { useEffect, useId, useRef, useState } from 'react'
import { postForm } from '../../api/client'
import { ErrorState } from '../../components/ui/States'
import { cn } from '../../lib/cn'

export type UploadPurpose = 'CARD_PROOF' | 'DISPUTE_EVIDENCE'

const MAX_BYTES = 5 * 1024 * 1024
const ACCEPT = 'image/png,image/jpeg,image/webp'

type Picked = { id: string; preview: string; name: string }

type ScreenshotPickerProps = {
  slug: string
  purpose: UploadPurpose
  label: string
  hint?: string
  max: number
  /** The upload ids, in order, whenever they change. */
  onChange: (ids: string[]) => void
  required?: boolean
}

/**
 * Picks screenshots and uploads each one straight away, showing thumbnails. The form that owns it sends the
 * resulting upload ids with its request; the server attaches them then.
 */
export function ScreenshotPicker({ slug, purpose, label, hint, max, onChange, required }: ScreenshotPickerProps) {
  const inputId = useId()
  const input = useRef<HTMLInputElement>(null)
  const [picked, setPicked] = useState<Picked[]>([])
  const [tooBig, setTooBig] = useState(false)
  const upload = useMutation({
    mutationFn: (file: File) => {
      const form = new FormData()
      form.append('file', file)
      form.append('purpose', purpose)
      return postForm<{ id: string }>(`/api/leagues/${encodeURIComponent(slug)}/uploads`, form)
    },
  })

  // Free the preview URLs when the picker goes away.
  const previews = useRef<string[]>([])
  useEffect(() => () => previews.current.forEach((url) => URL.revokeObjectURL(url)), [])

  function update(next: Picked[]) {
    setPicked(next)
    onChange(next.map((p) => p.id))
  }

  function choose(file: File | undefined) {
    if (input.current) input.current.value = ''
    if (!file) return
    setTooBig(file.size > MAX_BYTES)
    if (file.size > MAX_BYTES) return
    upload.mutate(file, {
      onSuccess: ({ id }) => {
        const preview = URL.createObjectURL(file)
        previews.current.push(preview)
        update([...picked, { id, preview, name: file.name }])
      },
    })
  }

  return (
    <div className="flex flex-col gap-2">
      <span className="type-label px-1 text-tile-muted" id={`${inputId}-label`}>
        {label}
        {required && <span className="sr-only"> (required)</span>}
      </span>
      <div className="flex flex-wrap gap-3" aria-labelledby={`${inputId}-label`} role="group">
        {picked.map((p) => (
          <div key={p.id} className="relative size-24 overflow-hidden rounded-inner bg-tile-soft">
            <img src={p.preview} alt={p.name} className="size-full object-cover" />
            <button
              type="button"
              aria-label={`Remove ${p.name}`}
              onClick={() => update(picked.filter((x) => x.id !== p.id))}
              className="press absolute top-1 right-1 grid size-7 place-items-center rounded-full bg-paper text-ink"
            >
              <X className="size-4" />
            </button>
          </div>
        ))}
        {picked.length < max && (
          <label
            htmlFor={inputId}
            className={cn(
              'press grid size-24 cursor-pointer place-items-center rounded-inner border-2 border-dashed border-tile-soft text-tile-muted hover:bg-tile-soft',
              upload.isPending && 'pointer-events-none opacity-60',
            )}
          >
            {upload.isPending ? <Loader2 className="size-6 animate-spin" aria-label="Uploading" /> : <ImagePlus className="size-6" aria-hidden />}
            <span className="sr-only">Add a screenshot</span>
            <input
              ref={input}
              id={inputId}
              type="file"
              accept={ACCEPT}
              className="sr-only"
              disabled={upload.isPending}
              onChange={(e) => choose(e.target.files?.[0])}
            />
          </label>
        )}
      </div>
      {hint && <p className="px-1 text-xs text-tile-muted">{hint}</p>}
      {tooBig && <ErrorState message="That file is over 5 MB. Crop it or save it as JPEG." />}
      {upload.isError && <ErrorState message={upload.error.message} />}
    </div>
  )
}
