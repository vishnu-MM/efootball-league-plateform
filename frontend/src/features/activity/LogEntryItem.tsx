import { Link } from 'react-router'
import { formatWhen, type LogEntry } from './useLog'

/** One log line: what happened, the reason if there was one, when, and a link to the match. */
export function LogEntryItem({ slug, entry }: { slug: string; entry: LogEntry }) {
  return (
    <li className="flex flex-col gap-1 rounded-inner bg-tile-soft px-4 py-3">
      <p className="text-sm">{entry.summary}</p>
      {entry.reason && <p className="text-xs">“{entry.reason}”</p>}
      <p className="flex flex-wrap gap-x-3 text-xs text-tile-muted">
        <time dateTime={entry.createdAt}>{formatWhen(entry.createdAt)}</time>
        {entry.fixtureId && (
          <Link to={`/leagues/${slug}/fixtures/${entry.fixtureId}`} className="underline underline-offset-2">
            View match
          </Link>
        )}
      </p>
    </li>
  )
}
