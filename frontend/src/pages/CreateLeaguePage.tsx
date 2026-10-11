import { Globe, Lock } from 'lucide-react'
import { RadioGroup } from 'radix-ui'
import { useState, type FormEvent, type ReactNode } from 'react'
import { useNavigate } from 'react-router'
import { toast } from 'sonner'
import { Bento, PageHeader } from '../components/layout/Page'
import { Button } from '../components/ui/Button'
import { Crest } from '../components/ui/Crest'
import { TextField } from '../components/ui/Field'
import { ErrorState } from '../components/ui/States'
import { Tile } from '../components/ui/Tile'
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
    <section className="flex flex-col gap-6">
      <PageHeader title="New league">Your crest is made from the name. You'll be the owner.</PageHeader>

      <Bento>
        <Tile className="md:col-span-6 xl:col-span-7">
          <form className="flex flex-col gap-7" onSubmit={onSubmit} noValidate>
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
              <legend className="type-label mb-2 px-1 text-tile-muted">Who can see it</legend>
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
              <Button size="lg" variant="secondary" onClick={() => navigate(-1)}>
                Cancel
              </Button>
            </div>
          </form>
        </Tile>

        {/* Live preview. Shown first on phones and tablets so the crest is visible while typing. */}
        <Tile tone="coral" aria-hidden className="order-first flex flex-col justify-between gap-6 md:col-span-6 xl:order-none xl:col-span-5">
          <span className="grid size-28 place-items-center rounded-inner bg-tile-soft md:size-36">
            <Crest seed={slugify(trimmed)} name={trimmed || 'New league'} size={88} />
          </span>
          <span className="type-display line-clamp-3 break-words">{trimmed || 'New league'}</span>
        </Tile>
      </Bento>
    </section>
  )
}

function VisibilityOption({ value, icon, title, children }: { value: Visibility; icon: ReactNode; title: string; children: ReactNode }) {
  return (
    <RadioGroup.Item
      value={value}
      className="press group flex flex-col items-start gap-2 rounded-inner bg-tile-soft p-4 text-left text-tile-fg data-[state=checked]:bg-aqua data-[state=checked]:text-ink"
    >
      <span className="flex w-full items-center justify-between gap-2">
        <span className="type-heading flex items-center gap-2">
          {icon}
          {title}
        </span>
        <span className="grid size-5 place-items-center rounded-full border-2 border-current">
          <RadioGroup.Indicator className="size-2.5 rounded-full bg-ink" />
        </span>
      </span>
      <span className="text-xs opacity-80 group-data-[state=checked]:opacity-100">{children}</span>
    </RadioGroup.Item>
  )
}
