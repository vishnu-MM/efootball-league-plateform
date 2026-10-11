import { useId } from 'react'
import { cn } from '../../lib/cn'
import { initials } from '../../lib/initials'

/** Kit colour pairs: [shirt, trim]. Each pair keeps the initials readable. */
const KITS: ReadonlyArray<readonly [string, string]> = [
  ['#1E7A4F', '#F6F7F4'],
  ['#C8263A', '#FFFFFF'],
  ['#1B3A8C', '#F2B705'],
  ['#5B2A86', '#F6F7F4'],
  ['#0F5257', '#F5C84C'],
  ['#172026', '#4CC38A'],
  ['#7A1F3D', '#F2B705'],
  ['#B4541A', '#FFFFFF'],
  ['#2D6CDF', '#FFFFFF'],
  ['#F2B705', '#172026'],
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

/** A generated club crest: shield, kit colours and pattern derived from the seed, with the league's initials. */
export function Crest({ seed, name, size = 40, className }: CrestProps) {
  const clipId = useId()
  const h = hash(seed || name)
  const [shirt, trim] = KITS[h % KITS.length]
  const pattern = PATTERNS[(h >>> 8) % PATTERNS.length]
  const letters = initials(name)

  return (
    <svg
      viewBox="0 0 40 46"
      width={size}
      height={(size * 46) / 40}
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
        <rect width="40" height="46" fill={shirt} />
        {pattern === 'hoop' && <rect y="29" width="40" height="6" fill={trim} opacity="0.9" />}
        {pattern === 'sash' && <path d="M-4 40 44 4v9L-4 49z" fill={trim} opacity="0.25" />}
        {pattern === 'halves' && <rect x="20" width="20" height="46" fill="#000" opacity="0.14" />}
      </g>
      <path d={SHIELD} fill="none" stroke={trim} strokeOpacity="0.55" strokeWidth="1.5" />
      <text
        x="20"
        y={pattern === 'hoop' ? 22.5 : 25.5}
        textAnchor="middle"
        dominantBaseline="middle"
        fill={trim}
        style={{ fontFamily: 'Archivo Variable, sans-serif', fontWeight: 800, fontStretch: '125%', fontSize: letters.length > 1 ? 13 : 16 }}
      >
        {letters}
      </text>
    </svg>
  )
}
