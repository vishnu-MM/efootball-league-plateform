import { Link } from 'react-router'
import { Bento } from '../components/layout/Page'
import { Button } from '../components/ui/Button'
import { Tile } from '../components/ui/Tile'

export function NotFoundPage() {
  return (
    <Bento>
      <Tile as="section" tone="coral" className="flex min-h-[24rem] flex-col justify-between gap-8 md:col-span-6 xl:col-span-7">
        <p className="type-poster type-tabular">404</p>
        <div className="flex flex-col items-start gap-4">
          <h1 className="type-display">This page is out of play</h1>
          <p className="max-w-[48ch]">The link may be broken or the page has moved.</p>
          <Button asChild size="lg" className="mt-2">
            <Link to="/">Go to home</Link>
          </Button>
        </div>
      </Tile>
    </Bento>
  )
}
