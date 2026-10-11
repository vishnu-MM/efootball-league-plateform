import { useState, type FormEvent, type ReactNode } from 'react'
import { Navigate, useSearchParams } from 'react-router'
import { Button } from '../components/ui/Button'
import { TextField } from '../components/ui/Field'
import { ErrorState, Skeleton } from '../components/ui/States'
import { useDevLogin, useMe, useProviders } from '../features/auth/useAuth'

const PROVIDERS: Record<string, { label: string; icon: ReactNode }> = {
  google: { label: 'Continue with Google', icon: <GoogleMark /> },
  discord: { label: 'Continue with Discord', icon: <DiscordMark /> },
}

/** Only allow returning to paths inside this app. */
function safeNext(next: string | null) {
  return next && next.startsWith('/') && !next.startsWith('//') ? next : '/'
}

export function SignInPage() {
  const [params] = useSearchParams()
  const next = safeNext(params.get('next'))
  const { data: me } = useMe()
  const { data: providers, isPending } = useProviders()

  if (me) return <Navigate to={next} replace />

  const oauth = (providers ?? []).filter((p) => p !== 'dev')

  return (
    <section className="mx-auto flex w-full max-w-sm flex-col gap-8 pt-4 md:pt-16">
      <div className="flex flex-col gap-2">
        <h1 className="type-title">Sign in</h1>
        <p className="text-ink-muted">Use the account you already have. We'll create your profile the first time.</p>
      </div>

      {params.has('loginError') && <ErrorState message="Sign-in didn't complete. Try again, or use another method." />}

      {isPending && (
        <div className="flex flex-col gap-3">
          <Skeleton className="h-12" />
          <Skeleton className="h-12" />
        </div>
      )}
      {oauth.length > 0 && (
        <div className="flex flex-col gap-3">
          {oauth.map((provider) => (
            <Button key={provider} asChild variant="secondary" size="lg" className="w-full">
              {/* Full-page navigation: the server redirects to the provider and back. */}
              <a href={`/oauth2/authorization/${provider}`}>
                {PROVIDERS[provider]?.icon}
                {PROVIDERS[provider]?.label ?? `Continue with ${provider}`}
              </a>
            </Button>
          ))}
        </div>
      )}
      {providers && providers.length === 0 && (
        <ErrorState message="Sign-in isn't available right now. Try again later." />
      )}

      {providers?.includes('dev') && <DevLogin hasOAuth={oauth.length > 0} />}
    </section>
  )
}

function DevLogin({ hasOAuth }: { hasOAuth: boolean }) {
  const [name, setName] = useState('')
  const devLogin = useDevLogin()

  function onSubmit(event: FormEvent) {
    event.preventDefault()
    if (!name.trim()) return
    // On success the signed-in user reloads and SignInPage redirects to `next`.
    devLogin.mutate(name.trim())
  }

  return (
    <form onSubmit={onSubmit} className="flex flex-col gap-4 rounded-xl border border-dashed border-yellow-card bg-yellow-card-soft/50 p-4">
      <div className="flex flex-col gap-1">
        <h2 className="type-heading">Developer sign-in</h2>
        <p className="text-sm text-ink-muted">
          Local testing only{hasOAuth ? '' : ': no Google or Discord sign-in is configured'}. Enter any name to sign in as that person.
        </p>
      </div>
      <TextField label="Display name" value={name} maxLength={50} autoComplete="off" onChange={(e) => setName(e.target.value)} />
      {devLogin.isError && <ErrorState message={devLogin.error.message} />}
      <Button type="submit" disabled={devLogin.isPending || !name.trim()}>
        {devLogin.isPending ? 'Signing in…' : 'Sign in'}
      </Button>
    </form>
  )
}

function GoogleMark() {
  return (
    <svg viewBox="0 0 24 24" className="size-5" aria-hidden>
      <path fill="#4285F4" d="M22.6 12.2c0-.7-.1-1.4-.2-2.1H12v4h5.9a5 5 0 0 1-2.2 3.3v2.7h3.6c2.1-1.9 3.3-4.7 3.3-7.9z" />
      <path fill="#34A853" d="M12 23c3 0 5.5-1 7.3-2.7l-3.6-2.7c-1 .7-2.2 1.1-3.7 1.1-2.9 0-5.3-1.9-6.2-4.5H2.1v2.8A11 11 0 0 0 12 23z" />
      <path fill="#FBBC05" d="M5.8 14.2a6.6 6.6 0 0 1 0-4.3V7.1H2.1a11 11 0 0 0 0 9.9z" />
      <path fill="#EA4335" d="M12 5.4c1.6 0 3.1.6 4.2 1.7l3.2-3.2A11 11 0 0 0 2.1 7.1l3.7 2.8C6.7 7.3 9.1 5.4 12 5.4z" />
    </svg>
  )
}

function DiscordMark() {
  return (
    <svg viewBox="0 0 24 24" className="size-5" aria-hidden>
      <path
        fill="#5865F2"
        d="M20.3 4.4A19.8 19.8 0 0 0 15.4 3l-.6 1.3a18.4 18.4 0 0 0-5.6 0L8.6 3a19.7 19.7 0 0 0-4.9 1.5C.6 9.1-.3 13.6.1 18.1a19.9 19.9 0 0 0 6 3l1.3-2a13 13 0 0 1-2-1l.5-.4a14.2 14.2 0 0 0 12.2 0l.5.4c-.6.4-1.3.7-2 1l1.3 2a19.8 19.8 0 0 0 6-3c.5-5.2-.9-9.7-3.6-13.7zM8 15.3c-1.2 0-2.2-1.1-2.2-2.4S6.8 10.5 8 10.5s2.2 1.1 2.2 2.4-1 2.4-2.2 2.4zm8 0c-1.2 0-2.2-1.1-2.2-2.4s1-2.4 2.2-2.4 2.2 1.1 2.2 2.4-1 2.4-2.2 2.4z"
      />
    </svg>
  )
}
