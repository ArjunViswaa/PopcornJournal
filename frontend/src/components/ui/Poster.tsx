import { useState } from 'react'
import type { MovieFormat } from '../../api/types'

/** A stable hue per title so a movie's placeholder looks the same everywhere. */
function hueFor(title: string): number {
  let hash = 0
  for (const char of title) hash = (hash * 31 + char.charCodeAt(0)) >>> 0
  return hash % 360
}

interface PosterProps {
  title: string
  year: number | null
  format: MovieFormat
  posterUrl: string | null
  className?: string
  size?: 'xs' | 'sm' | 'md' | 'lg'
  /** The placeholder's top-left "Feature"/"Series" label; hidden when something else sits there. */
  showFormatLabel?: boolean
}

/**
 * Shows the poster when a URL is stored; otherwise a typographic placeholder in the
 * style of a festival programme card. No external image service is involved.
 */
export function Poster({
  title,
  year,
  format,
  posterUrl,
  className = '',
  size = 'md',
  showFormatLabel = true,
}: PosterProps) {
  const [failed, setFailed] = useState(false)
  if (posterUrl && !failed) {
    return (
      <img
        src={posterUrl}
        alt={`Poster for ${title}`}
        loading="lazy"
        onError={() => setFailed(true)}
        className={`aspect-[2/3] w-full rounded-xl object-cover ${className}`}
      />
    )
  }

  const hue = hueFor(title)
  if (size === 'xs') {
    return (
      <div
        aria-hidden
        className={`flex aspect-[2/3] w-full items-center justify-center rounded-md font-display text-lg font-semibold ${className}`}
        style={{ backgroundColor: `oklch(0.27 0.045 ${hue})`, color: `oklch(0.9 0.04 ${hue})` }}
      >
        {title.trim().charAt(0).toUpperCase()}
      </div>
    )
  }
  const baseRem = { sm: 0.875, md: 1.125, lg: 1.875 }[size]
  // Shrink the title until its longest word fits the card (82% of the width after padding,
  // ~0.5em per character), so "Kaalamaadan" never splits mid-word on a narrow card.
  const longestWord = Math.max(4, ...title.split(/\s+/).map((word) => word.length))
  const titleFontSize = `min(${baseRem}rem, calc(82cqw / ${(longestWord * 0.5).toFixed(2)}))`
  return (
    <div
      aria-hidden
      className={`relative flex aspect-[2/3] w-full flex-col justify-between overflow-hidden rounded-xl p-[9%] ${className}`}
      style={{ backgroundColor: `oklch(0.27 0.045 ${hue})`, containerType: 'inline-size' }}
    >
      {/* film perforations down both edges */}
      <div className="absolute inset-y-0 left-[3%] flex w-[3%] flex-col justify-around opacity-30">
        {Array.from({ length: 9 }, (_, i) => (
          <span key={i} className="aspect-square w-full rounded-[1px] bg-black" />
        ))}
      </div>
      <div className="absolute inset-y-0 right-[3%] flex w-[3%] flex-col justify-around opacity-30">
        {Array.from({ length: 9 }, (_, i) => (
          <span key={i} className="aspect-square w-full rounded-[1px] bg-black" />
        ))}
      </div>
      <span
        // keeps its space so the title stays put when the label is hidden
        aria-hidden
        className={`eyebrow relative ${showFormatLabel ? '' : 'invisible'}`}
        style={{ color: `oklch(0.8 0.06 ${hue})` }}
      >
        {format === 'SERIES' ? 'Series' : 'Feature'}
      </span>
      <span
        className="relative line-clamp-4 font-display font-semibold leading-tight [overflow-wrap:anywhere]"
        style={{ color: `oklch(0.95 0.02 ${hue})`, fontSize: titleFontSize }}
      >
        {title}
      </span>
      <span className="num relative text-xs" style={{ color: `oklch(0.75 0.05 ${hue})` }}>
        {year ?? '—'}
      </span>
    </div>
  )
}
