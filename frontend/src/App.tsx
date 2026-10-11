import { usePublicLeagues } from './features/league/usePublicLeagues'
import { useStatus } from './features/status/useStatus'

function StatusBadge() {
  const { data, isPending, isError } = useStatus()
  if (isPending) return <span className="badge">Connecting…</span>
  if (isError) return <span className="badge badge-error">API offline</span>
  return <span className="badge badge-ok">API v{data.version} · database connected</span>
}

function PublicLeagues() {
  const { data, isPending, isError } = usePublicLeagues()
  if (isPending) return <p className="muted">Loading leagues…</p>
  if (isError) return <p className="muted">Couldn't load leagues.</p>
  if (data.length === 0) return <p className="muted">No public leagues yet.</p>
  return (
    <ul className="list">
      {data.map((league) => (
        <li key={league.id}>{league.name}</li>
      ))}
    </ul>
  )
}

export default function App() {
  return (
    <main className="page">
      <header className="header">
        <h1>League Platform</h1>
        <StatusBadge />
      </header>
      <section>
        <h2>Public leagues</h2>
        <PublicLeagues />
      </section>
    </main>
  )
}
