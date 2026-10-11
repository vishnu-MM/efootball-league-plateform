import { Shield } from 'lucide-react'
import { useState, type FormEvent } from 'react'
import { toast } from 'sonner'
import { Button } from '../../components/ui/Button'
import { TextField } from '../../components/ui/Field'
import { IconTile, Tile } from '../../components/ui/Tile'
import { cn } from '../../lib/cn'
import { useSetClubName } from './useInvites'

/**
 * The signed-in member names their club; fixtures and the table show it instead of their own name.
 * Give it `key={currentName}` so the field resets when the saved name changes.
 */
export function ClubTile({ slug, currentName, className }: { slug: string; currentName: string | null; className?: string }) {
  const [name, setName] = useState(currentName ?? '')
  const setClub = useSetClubName(slug)
  const trimmed = name.trim()

  function submit(event: FormEvent) {
    event.preventDefault()
    setClub.mutate(trimmed, { onSuccess: (club) => toast.success(`Club name saved: ${club.name}`) })
  }

  return (
    <Tile as="section" aria-labelledby="club-heading" className={cn('flex flex-col gap-4', className)}>
      <div className="flex items-center gap-4">
        <IconTile>
          <Shield className="size-6" />
        </IconTile>
        <h2 id="club-heading" className="type-title">
          Your club
        </h2>
      </div>
      <form onSubmit={submit} className="flex flex-col gap-4">
        <TextField
          label="Club name"
          placeholder="e.g. Kochi Blasters"
          maxLength={40}
          value={name}
          autoComplete="off"
          error={setClub.isError ? setClub.error.message : undefined}
          hint="Shown in fixtures and the table instead of your name."
          onChange={(e) => {
            setName(e.target.value)
            setClub.reset()
          }}
        />
        <Button
          type="submit"
          variant="secondary"
          className="self-start"
          disabled={setClub.isPending || trimmed.length < 2 || trimmed === (currentName ?? '')}
        >
          {setClub.isPending ? 'Saving…' : 'Save club name'}
        </Button>
      </form>
    </Tile>
  )
}
