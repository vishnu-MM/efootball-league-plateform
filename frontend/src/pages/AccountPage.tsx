import { LogOut } from 'lucide-react'
import { Avatar } from '../components/ui/Avatar'
import { Button } from '../components/ui/Button'
import { useSignOut } from '../features/auth/useSignOut'
import { useMe } from '../features/auth/useAuth'

export function AccountPage() {
  const { data: me } = useMe()
  const signOut = useSignOut()
  if (!me) return null

  return (
    <section className="flex max-w-xl flex-col gap-8">
      <h1 className="type-title">Account</h1>
      <div className="flex items-center gap-4 rounded-xl border border-line bg-surface p-5">
        <Avatar name={me.displayName} className="size-14 text-lg" />
        <div className="min-w-0">
          <p className="truncate text-lg font-semibold">{me.displayName}</p>
          <p className="truncate text-ink-muted">{me.email ?? 'No email on file'}</p>
        </div>
      </div>
      <div>
        <Button variant="danger" onClick={signOut.run} disabled={signOut.pending}>
          <LogOut className="size-[18px]" aria-hidden />
          {signOut.pending ? 'Signing out…' : 'Sign out'}
        </Button>
      </div>
    </section>
  )
}
