import { Globe, Lock } from 'lucide-react'
import { RadioGroup } from 'radix-ui'
import { useState, type FormEvent, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { Button } from '../components/ui/Button'
import { Crest } from '../components/ui/Crest'
import { TextField } from '../components/ui/Field'
import { ErrorState } from '../components/ui/States'
import { useCreateLeague, type Visibility } from '../features/league/useLeagues'
import { slugify } from '../lib/slug'

const NAME_MIN = 3
const NAME_MAX = 80

export function CreateLeaguePage() {
  const [name, setName] = useState('')
  const [visibility, setVisibility] = useState<Visibility>('PRIVATE')
  const [nameError, setNameError] = useState<string>()
  const createLeague = useCreateLeague()
  const navigate = useNavigate()
  const trimmed = name.trim()

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (trimmed.length < NAME_MIN) {
      setNameError(`Use at least ${NAME_MIN} characters.`)
      return
    }
    createLeague.mutate(
      { name: trimmed, visibility },
      {
        onSuccess: (league) => {
          toast.success(`${league.name} created`)
          navigate(`/leagues/${league.slug}`)
        },
      },
    )
  }

  return (
    <section className="flex max-w-xl flex-col gap-8">
      <div className="flex items-center gap-5">
        <Crest seed={slugify(trimmed)} name={trimmed || 'New league'} size={64} />
        <div className="flex min-w-0 flex-col gap-1">
          <h1 className="type-title">New league</h1>
          <p className="text-ink-muted">Your crest is made from the name. You'll be the owner.</p>
        </div>
      </div>

      <form className="flex flex-col gap-6" onSubmit={onSubmit} noValidate>
        <TextField
          label="League name"
          placeholder="e.g. Sunday Night Cup"
          value={name}
          maxLength={NAME_MAX}
          autoComplete="off"
          autoFocus
          error={nameError}
          hint={`${NAME_MIN}–${NAME_MAX} characters. You can't change it yet, so pick one you like.`}
          onChange={(e) => {
            setName(e.target.value)
            setNameError(undefined)
          }}
        />

        <fieldset className="flex flex-col gap-2">
          <legend className="mb-1.5 text-sm font-semibold">Who can see it</legend>
          <RadioGroup.Root
            value={visibility}
            onValueChange={(value) => setVisibility(value as Visibility)}
            className="grid gap-3 sm:grid-cols-2"
          >
            <VisibilityOption value="PRIVATE" icon={<Lock className="size-5" />} title="Private">
              Only members can see the league.
            </VisibilityOption>
            <VisibilityOption value="PUBLIC" icon={<Globe className="size-5" />} title="Public">
              Anyone can view it. Only members can play.
            </VisibilityOption>
          </RadioGroup.Root>
        </fieldset>

        {createLeague.isError && <ErrorState message={createLeague.error.message} />}

        <div className="flex flex-wrap gap-3">
          <Button type="submit" size="lg" disabled={createLeague.isPending}>
            {createLeague.isPending ? 'Creating league…' : 'Create league'}
          </Button>
          <Button size="lg" variant="ghost" onClick={() => navigate(-1)}>
            Cancel
          </Button>
        </div>
      </form>
    </section>
  )
}

function VisibilityOption({ value, icon, title, children }: { value: Visibility; icon: ReactNode; title: string; children: ReactNode }) {
  return (
    <RadioGroup.Item
      value={value}
      className="group flex flex-col items-start gap-2 rounded-xl border border-line bg-surface p-4 text-left hover:bg-sunken data-[state=checked]:border-turf data-[state=checked]:bg-turf-soft data-[state=checked]:ring-1 data-[state=checked]:ring-turf"
    >
      <span className="flex w-full items-center justify-between gap-2">
        <span className="flex items-center gap-2 font-semibold group-data-[state=checked]:text-turf-strong">
          {icon}
          {title}
        </span>
        <span className="flex size-5 items-center justify-center rounded-full border-2 border-line group-data-[state=checked]:border-turf">
          <RadioGroup.Indicator className="size-2.5 rounded-full bg-turf" />
        </span>
      </span>
      <span className="text-sm text-ink-muted">{children}</span>
    </RadioGroup.Item>
  )
}
