import type { MovieFormat, ViewingMethod } from '../api/types'

export const METHOD_LABELS: Record<ViewingMethod, string> = {
  CINEMA: 'Cinema',
  STREAMING: 'Streaming',
  TELEVISION: 'Television',
  PHYSICAL_MEDIA: 'Physical media',
  OTHER: 'Other',
}

export const FORMAT_LABELS: Record<MovieFormat, string> = { MOVIE: 'Movie', SERIES: 'Series' }

export function methodLabel(method: string | null | undefined): string {
  if (!method || method === 'NOT_RECORDED') return 'Not recorded'
  return METHOD_LABELS[method as ViewingMethod] ?? method
}

/** Dates are calendar days with no time zone, so parse them as local dates. */
function parseLocalDate(iso: string): Date {
  const [y, m, d] = iso.split('-').map(Number)
  return new Date(y ?? 1970, (m ?? 1) - 1, d ?? 1)
}

export function formatDate(iso: string | null | undefined, style: 'short' | 'long' = 'short'): string {
  if (!iso) return 'Date not recorded'
  return parseLocalDate(iso).toLocaleDateString('en-GB', {
    day: 'numeric',
    month: style === 'long' ? 'long' : 'short',
    year: 'numeric',
  })
}

export function formatMonth(period: string): string {
  return parseLocalDate(`${period}-01`).toLocaleDateString('en-GB', { month: 'short', year: '2-digit' })
}

export function ratingText(rating: number | null, beyondScale: boolean): string {
  if (beyondScale) return '∞'
  return rating === null ? 'Unrated' : `${rating}/10`
}

export function formatAverage(value: number | null): string {
  return value === null ? '—' : value.toFixed(1)
}

export function plural(count: number, one: string, many = `${one}s`): string {
  return `${count.toLocaleString('en-GB')} ${count === 1 ? one : many}`
}

export function todayIso(): string {
  const now = new Date()
  const pad = (n: number) => String(n).padStart(2, '0')
  return `${now.getFullYear()}-${pad(now.getMonth() + 1)}-${pad(now.getDate())}`
}

/** "Where": platform and/or venue, or the method when neither is known. */
export function whereWatched(platform: string | null, venue: string | null, method: string | null): string {
  const places = [venue, platform].filter(Boolean)
  return places.length ? places.join(' · ') : methodLabel(method)
}
