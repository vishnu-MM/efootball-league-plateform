import { ChevronRight } from 'lucide-react'
import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { Crest } from '../../components/ui/Crest'

type LeagueListItem = { id: string; name: string; slug: string; aside?: ReactNode }

export function LeagueList({ leagues, label }: { leagues: LeagueListItem[]; label: string }) {
  return (
    <ul aria-label={label} className="divide-y divide-line overflow-hidden rounded-xl border border-line bg-surface">
      {leagues.map((league) => (
        <li key={league.id}>
          <Link
            to={`/leagues/${league.slug}`}
            className="flex items-center gap-3.5 px-4 py-3.5 hover:bg-sunken focus-visible:-outline-offset-2"
          >
            <Crest seed={league.slug} name={league.name} size={36} />
            <span className="min-w-0 flex-1 truncate font-semibold [font-stretch:105%]">{league.name}</span>
            {league.aside}
            <ChevronRight className="size-5 text-ink-muted" aria-hidden />
          </Link>
        </li>
      ))}
    </ul>
  )
}
