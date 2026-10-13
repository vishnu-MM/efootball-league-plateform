import { cn } from '../../lib/cn'

/** A stored screenshot as a small thumbnail that opens full size in a new tab. */
export function Thumbnail({ src, alt, className }: { src: string; alt: string; className?: string }) {
  return (
    <a href={src} target="_blank" rel="noreferrer" className={cn('press block size-20 shrink-0 overflow-hidden rounded-inner bg-tile-soft', className)}>
      <img src={src} alt={alt} loading="lazy" className="size-full object-cover" />
    </a>
  )
}
