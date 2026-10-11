import { Link } from 'react-router'
import { Button } from '../components/ui/Button'

export function NotFoundPage() {
  return (
    <section className="flex flex-col items-start gap-5 pt-4 md:pt-12">
      <p className="type-display type-tabular text-ink-muted">404</p>
      <h1 className="type-title">This page is out of play</h1>
      <p className="max-w-[48ch] text-ink-muted">The link may be broken or the page has moved.</p>
      <Button asChild>
        <Link to="/">Go to home</Link>
      </Button>
    </section>
  )
}
