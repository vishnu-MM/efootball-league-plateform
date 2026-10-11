import { WifiOff } from 'lucide-react'
import { useSyncExternalStore } from 'react'

function subscribe(callback: () => void) {
  window.addEventListener('online', callback)
  window.addEventListener('offline', callback)
  return () => {
    window.removeEventListener('online', callback)
    window.removeEventListener('offline', callback)
  }
}

export function OfflineBanner() {
  const online = useSyncExternalStore(subscribe, () => navigator.onLine)
  if (online) return null
  return (
    <div role="status" className="flex items-center gap-2 bg-yellow-card-soft px-4 py-2 text-sm font-medium">
      <WifiOff className="size-4" aria-hidden />
      You're offline. You can browse saved pages, but changes need a connection.
    </div>
  )
}
