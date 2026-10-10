import { Heart, MessageSquareText } from 'lucide-react'
import { Link } from 'react-router'
import type { MovieSummary } from '../../api/types'
import { Poster } from '../../components/ui/Poster'
import { RatingBadge } from '../../components/ui/Rating'
import { formatDate } from '../../lib/format'

function whereText(movie: MovieSummary): string | null {
  const places = [...movie.venues, ...movie.platforms]
  if (places.length === 0) return null
  return places.length > 2 ? `${places.slice(0, 2).join(' · ')} +${places.length - 2}` : places.join(' · ')
}

function details(movie: MovieSummary): string {
  return [movie.releaseYear, movie.language, movie.format === 'SERIES' ? 'Series' : null].filter(Boolean).join(' · ')
}

export function MovieCard({ movie }: { movie: MovieSummary }) {
  return (
    <Link
      to={`/movies/${movie.id}`}
      className="group flex flex-col gap-3 rounded-2xl p-2 transition-colors hover:bg-surface"
      aria-label={`${movie.title}${movie.releaseYear ? ` (${movie.releaseYear})` : ''}`}
    >
      {/* the lift sits on the wrapper so the overlays move with the poster */}
      <div className="relative transition-transform duration-200 group-hover:-translate-y-0.5">
        <Poster title={movie.title} year={movie.releaseYear} format={movie.format} posterUrl={movie.posterUrl} />
        {movie.favourite && (
          <span className="absolute top-2 right-2 rounded-full bg-black/60 p-1.5" title="Favourite">
            <Heart className="size-3.5 fill-accent text-accent" aria-label="Favourite" />
          </span>
        )}
        {/* bottom-right: the placeholder prints the release year bottom-left */}
        <div className="absolute right-2 bottom-2 rounded-lg bg-black/70 backdrop-blur-sm">
          <RatingBadge rating={movie.rating} ratingBeyondScale={movie.ratingBeyondScale} size="sm" />
        </div>
      </div>
      <div className="min-w-0 px-1">
        <p className="truncate font-medium text-ink">{movie.title}</p>
        <p className="truncate text-xs text-ink-3">{details(movie) || 'No details recorded'}</p>
        {movie.genres.length > 0 && <p className="mt-1 truncate text-xs text-ink-2">{movie.genres.join(', ')}</p>}
      </div>
    </Link>
  )
}

export function MovieRow({ movie }: { movie: MovieSummary }) {
  const where = whereText(movie)
  return (
    <Link
      to={`/movies/${movie.id}`}
      className="grid grid-cols-[3rem_1fr_auto] items-center gap-4 rounded-xl px-3 py-2.5 transition-colors hover:bg-surface sm:grid-cols-[3rem_1fr_10rem_9rem_auto]"
    >
      <Poster title={movie.title} year={movie.releaseYear} format={movie.format} posterUrl={movie.posterUrl} size="xs" />
      <div className="min-w-0">
        <p className="flex items-center gap-2 truncate font-medium">
          {movie.title}
          {movie.favourite && <Heart className="size-3.5 shrink-0 fill-accent text-accent" aria-label="Favourite" />}
          {movie.hasReview && <MessageSquareText className="size-3.5 shrink-0 text-ink-3" aria-label="Has a review" />}
        </p>
        <p className="truncate text-xs text-ink-3">
          {[details(movie), movie.genres.join(', ')].filter(Boolean).join(' · ') || 'No details recorded'}
        </p>
      </div>
      <p className="hidden truncate text-xs text-ink-2 sm:block">{where ?? <span className="text-ink-3">—</span>}</p>
      <p className="num hidden text-xs text-ink-3 sm:block">
        {movie.lastWatchedOn ? formatDate(movie.lastWatchedOn) : movie.viewingCount > 0 ? 'Date not recorded' : 'No viewings'}
      </p>
      <RatingBadge rating={movie.rating} ratingBeyondScale={movie.ratingBeyondScale} size="sm" />
    </Link>
  )
}
