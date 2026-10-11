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
    <div role="status" className="mx-4 mt-3 flex items-center gap-2.5 rounded-pill bg-mustard px-4 py-2.5 text-xs text-ink md:mx-6 md:mt-0 md:mb-4">
      <WifiOff className="size-4" aria-hidden />
      You're offline. You can browse saved pages, but changes need a connection.
    </div>
  )
}
