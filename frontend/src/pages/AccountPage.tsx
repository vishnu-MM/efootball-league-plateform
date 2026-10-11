import { LogOut } from 'lucide-react'
import { Bento, PageHeader } from '../components/layout/Page'
import { Avatar } from '../components/ui/Avatar'
import { Button } from '../components/ui/Button'
import { Tile } from '../components/ui/Tile'
import { useSignOut } from '../features/auth/useSignOut'
import { useMe } from '../features/auth/useAuth'

export function AccountPage() {
  const { data: me } = useMe()
  const signOut = useSignOut()
  if (!me) return null

  return (
    <section className="flex flex-col gap-6">
      <PageHeader title="Account" />
      <Bento>
        <Tile tone="violet" className="flex min-h-64 flex-col justify-between gap-8 md:col-span-6 xl:col-span-7">
          <div className="flex items-center gap-4 rounded-inner bg-tile-soft p-4">
            <Avatar name={me.displayName} className="size-14 text-2xl" />
            <div className="min-w-0">
              <p className="type-title truncate">{me.displayName}</p>
              <p className="truncate text-xs">{me.email ?? 'No email on file'}</p>
            </div>
          </div>
          <Button variant="danger" className="self-start" onClick={signOut.run} disabled={signOut.pending}>
            <LogOut className="size-[18px]" aria-hidden />
            {signOut.pending ? 'Signing out…' : 'Sign out'}
          </Button>
        </Tile>
      </Bento>
    </section>
  )
}
