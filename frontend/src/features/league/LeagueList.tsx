import type { ReactNode } from 'react'
import { Link } from 'react-router'
import { Crest } from '../../components/ui/Crest'
import { OpenArrow } from '../../components/ui/Tile'
import { BENTO_GRID } from '../../lib/bento'
import { cn } from '../../lib/cn'
import { TONE_CLASS, toneAt } from '../../lib/tones'

type LeagueListItem = { id: string; name: string; slug: string; aside?: ReactNode }

/** Leagues as a bento of colour tiles; the colour rotation keeps neighbours distinct. */
export function LeagueList({ leagues, label }: { leagues: LeagueListItem[]; label: string }) {
  return (
    <ul aria-label={label} className={BENTO_GRID}>
      {leagues.map((league, index) => (
        <li key={league.id} className="md:col-span-3 xl:col-span-4">
          <Link
            to={`/leagues/${league.slug}`}
            className={cn(
              TONE_CLASS[toneAt(index)],
              'group flex h-full min-h-52 flex-col justify-between gap-6 rounded-card bg-tile p-5 text-tile-fg md:p-6',
            )}
          >
            <span className="flex items-start justify-between gap-4">
              <span className="grid size-16 place-items-center rounded-inner bg-tile-soft">
                <Crest seed={league.slug} name={league.name} size={40} />
              </span>
              <OpenArrow />
            </span>
            <span className="flex flex-col items-start gap-3">
              <span className="type-title line-clamp-2 break-words">{league.name}</span>
              {league.aside}
            </span>
          </Link>
        </li>
      ))}
    </ul>
  )
}
