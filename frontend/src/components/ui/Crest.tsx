import { useId } from 'react'
import { cn } from '../../lib/cn'
import { initials } from '../../lib/initials'

/**
 * Kit pairs from the palette: [shirt, trim]. Shirts are always bright so a crest reads on
 * dark surfaces; the ink outline makes it read on bright ones.
 */
const KITS: ReadonlyArray<readonly [string, string]> = [
  ['var(--coral)', 'var(--ink)'],
  ['var(--violet)', 'var(--ink)'],
  ['var(--mustard)', 'var(--ink)'],
  ['var(--aqua)', 'var(--ink)'],
  ['var(--sage)', 'var(--ink)'],
  ['var(--paper)', 'var(--ink)'],
  ['var(--coral-soft)', 'var(--ink)'],
  ['var(--violet-soft)', 'var(--ink)'],
  ['var(--aqua-deep)', 'var(--ink)'],
  ['var(--sage-deep)', 'var(--ink)'],
]

type Pattern = 'plain' | 'hoop' | 'sash' | 'halves'
const PATTERNS: readonly Pattern[] = ['plain', 'hoop', 'sash', 'halves']

// Shield outline in a 40 x 46 box.
const SHIELD = 'M20 1.5 37.5 7v15.5c0 11-7.4 18.6-17.5 22-10.1-3.4-17.5-11-17.5-22V7z'

function hash(text: string) {
  let h = 2166136261
  for (let i = 0; i < text.length; i++) {
    h ^= text.charCodeAt(i)
    h = Math.imul(h, 16777619)
  }
  return h >>> 0
}

type CrestProps = {
  /** Stable seed so a league keeps its crest: use the slug once it exists. */
  seed: string
  name: string
  size?: number
  className?: string
}

/**
 * A generated club crest in the retro print style: flat palette fills inside a bold ink
 * outline, kit colours and pattern derived from the seed, with the league's initials.
 */
export function Crest({ seed, name, size = 40, className }: CrestProps) {
  const clipId = useId()
  const h = hash(seed || name)
  const [shirt, trim] = KITS[h % KITS.length]
  const pattern = PATTERNS[(h >>> 8) % PATTERNS.length]
  const letters = initials(name)

  return (
    <svg
      viewBox="-1 -1 42 48"
      width={size}
      height={(size * 48) / 42}
      role="img"
      aria-label={`${name} crest`}
      className={cn('shrink-0', className)}
    >
      <defs>
        <clipPath id={clipId}>
          <path d={SHIELD} />
        </clipPath>
      </defs>
      <g clipPath={`url(#${clipId})`}>
        <rect width="40" height="46" style={{ fill: shirt }} />
        {pattern === 'hoop' && <rect y="29" width="40" height="6" style={{ fill: trim }} />}
        {pattern === 'sash' && <path d="M-4 40 44 4v9L-4 49z" style={{ fill: trim }} opacity="0.3" />}
        {pattern === 'halves' && <rect x="20" width="20" height="46" style={{ fill: 'var(--ink)' }} opacity="0.16" />}
      </g>
      <path d={SHIELD} fill="none" style={{ stroke: 'var(--ink)' }} strokeWidth="2.25" strokeLinejoin="round" />
      <text
        x="20"
        y={pattern === 'hoop' ? 21 : 24.5}
        textAnchor="middle"
        dominantBaseline="middle"
        style={{
          fill: trim,
          fontFamily: 'var(--ff-display)',
          fontWeight: 600,
          fontSize: letters.length > 1 ? 16 : 19,
        }}
      >
        {letters}
      </text>
    </svg>
  )
}
