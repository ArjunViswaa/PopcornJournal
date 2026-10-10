import { ArrowLeft, CalendarPlus, FileSpreadsheet, Film, Heart, Pencil, Trash2 } from 'lucide-react'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../api/client'
import { useDeleteMovie, useDeleteViewing, useMovie, useToggleFavourite } from '../../api/queries'
import type { MovieDetail, ViewingSession } from '../../api/types'
import { Button, ButtonLink, IconButton } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/Dialog'
import { Poster } from '../../components/ui/Poster'
import { RatingBadge } from '../../components/ui/Rating'
import { Chip, EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { useToast } from '../../components/ui/Toast'
import { FORMAT_LABELS, formatDate, methodLabel, plural } from '../../lib/format'
import { ViewingDialog } from '../viewings/ViewingDialog'

export function MovieDetailPage() {
  const id = Number(useParams().id)
  const { data: movie, isPending, error, refetch } = useMovie(id)

  if (isPending) return <DetailSkeleton />
  if (error) {
    if (error instanceof ApiError && error.status === 404) {
      return (
        <EmptyState icon={Film} title="Movie not found" action={<ButtonLink to="/movies">Back to the library</ButtonLink>}>
          It may have been deleted.
        </EmptyState>
      )
    }
    return <ErrorState error={error} onRetry={() => void refetch()} />
  }
  return <MovieDetailView movie={movie} />
}

function MovieDetailView({ movie }: { movie: MovieDetail }) {
  const navigate = useNavigate()
  const toast = useToast()
  const favourite = useToggleFavourite(movie.id)
  const deleteMovie = useDeleteMovie()
  const deleteViewing = useDeleteViewing()
  const [confirmDelete, setConfirmDelete] = useState(false)
  const [viewingToDelete, setViewingToDelete] = useState<ViewingSession | null>(null)
  const [editing, setEditing] = useState<{ open: boolean; viewing: ViewingSession | null }>({ open: false, viewing: null })

  const facts = [FORMAT_LABELS[movie.format], movie.releaseYear, movie.language, movie.runtimeMinutes ? `${movie.runtimeMinutes} min` : null].filter(Boolean)

  return (
    <article>
      <Link to="/movies" className="mb-6 inline-flex items-center gap-1.5 text-sm text-ink-3 hover:text-ink">
        <ArrowLeft className="size-4" /> Library
      </Link>

      <div className="grid gap-8 md:grid-cols-[minmax(0,15rem)_1fr]">
        <div className="mx-auto w-48 md:w-full">
          <Poster title={movie.title} year={movie.releaseYear} format={movie.format} posterUrl={movie.posterUrl} size="lg" />
        </div>

        <div className="min-w-0">
          <p className="eyebrow">{facts.join(' · ')}</p>
          <h1 className="mt-2 font-display text-4xl font-semibold tracking-tight text-balance sm:text-5xl">{movie.title}</h1>
          {movie.originalTitle && <p className="mt-1 text-ink-3">{movie.originalTitle}</p>}

          <div className="mt-4 flex flex-wrap items-center gap-2">
            <RatingBadge rating={movie.rating} ratingBeyondScale={movie.ratingBeyondScale} size="lg" />
            {movie.genres.map((g) => (
              <Chip key={g.id}>{g.name}</Chip>
            ))}
          </div>

          <div className="mt-6 flex flex-wrap gap-2">
            <Button
              variant={movie.favourite ? 'primary' : 'secondary'}
              aria-pressed={movie.favourite}
              loading={favourite.isPending}
              icon={<Heart className={`size-4 ${movie.favourite ? 'fill-current' : ''}`} />}
              onClick={() =>
                favourite.mutate(!movie.favourite, {
                  onSuccess: (m) => toast.success(m.favourite ? 'Added to favourites' : 'Removed from favourites'),
                  onError: (e) => toast.error(e.message),
                })
              }
            >
              {movie.favourite ? 'Favourite' : 'Add to favourites'}
            </Button>
            <ButtonLink to={`/movies/${movie.id}/edit`} icon={<Pencil className="size-4" />}>
              Edit
            </ButtonLink>
            <Button variant="ghost" icon={<Trash2 className="size-4" />} onClick={() => setConfirmDelete(true)}>
              Delete
            </Button>
          </div>

          <section className="mt-8" aria-labelledby="review-heading">
            <h2 id="review-heading" className="eyebrow mb-2">
              Review
            </h2>
            {movie.review ? (
              <p className="max-w-prose font-display text-lg leading-relaxed whitespace-pre-line text-ink">{movie.review}</p>
            ) : (
              <p className="text-sm text-ink-3">
                No review yet. <Link to={`/movies/${movie.id}/edit`} className="text-accent hover:underline">Write one</Link>
              </p>
            )}
          </section>

          {movie.synopsis && (
            <section className="mt-6" aria-labelledby="synopsis-heading">
              <h2 id="synopsis-heading" className="eyebrow mb-2">
                Synopsis
              </h2>
              <p className="max-w-prose text-sm leading-relaxed whitespace-pre-line text-ink-2">{movie.synopsis}</p>
            </section>
          )}

          {movie.privateNotes && (
            <section className="mt-6 rounded-xl border border-line bg-surface p-4" aria-labelledby="notes-heading">
              <h2 id="notes-heading" className="eyebrow mb-2">
                Private notes
              </h2>
              <p className="text-sm whitespace-pre-line text-ink-2">{movie.privateNotes}</p>
            </section>
          )}

          <dl className="mt-6 grid grid-cols-2 gap-x-6 gap-y-3 text-sm sm:grid-cols-3">
            {[
              ['Director', movie.director],
              ['Cast', movie.castMembers],
              ['Metadata source', movie.metadataSource],
              ['Added', formatDate(movie.createdAt.slice(0, 10))],
              ['Last updated', formatDate(movie.updatedAt.slice(0, 10))],
            ].map(([label, value]) => (
              <div key={label}>
                <dt className="text-xs text-ink-3">{label}</dt>
                <dd className="mt-0.5 text-ink-2">{value ?? <span className="text-ink-3">Not recorded</span>}</dd>
              </div>
            ))}
          </dl>
        </div>
      </div>

      <section className="mt-12" aria-labelledby="history-heading">
        <div className="mb-4 flex items-center justify-between gap-4">
          <div>
            <h2 id="history-heading" className="font-display text-2xl font-semibold">
              Viewing history
            </h2>
            <p className="text-sm text-ink-3">{plural(movie.viewings.length, 'viewing')}</p>
          </div>
          <Button variant="primary" icon={<CalendarPlus className="size-4" />} onClick={() => setEditing({ open: true, viewing: null })}>
            Log a viewing
          </Button>
        </div>

        {movie.viewings.length === 0 ? (
          <p className="rounded-xl border border-dashed border-line-strong px-4 py-8 text-center text-sm text-ink-3">
            No viewings recorded for this title.
          </p>
        ) : (
          <ol className="card divide-y divide-line">
            {movie.viewings.map((v) => (
              <li key={v.id} className="flex flex-col gap-2 px-5 py-4 sm:flex-row sm:items-start sm:gap-6">
                <div className="num w-36 shrink-0 text-sm">
                  <p className={v.watchedOn ? 'text-ink' : 'text-ink-3'}>{formatDate(v.watchedOn)}</p>
                  {v.watchedAtTime && <p className="text-xs text-ink-3">{v.watchedAtTime.slice(0, 5)}</p>}
                </div>
                <div className="min-w-0 flex-1">
                  <p className="text-sm font-medium">
                    {methodLabel(v.method)}
                    {(v.platform || v.venue) && (
                      <span className="font-normal text-ink-2"> · {[v.venue?.name, v.platform?.name].filter(Boolean).join(' · ')}</span>
                    )}
                  </p>
                  {v.location && <p className="text-xs text-ink-3">{v.location}</p>}
                  {v.notes && <p className="mt-1.5 text-sm whitespace-pre-line text-ink-2">{v.notes}</p>}
                  {v.imported && <p className="mt-1.5 text-xs text-ink-3">Imported from the spreadsheet</p>}
                </div>
                <div className="flex items-center gap-1">
                  {(v.rating !== null || v.ratingBeyondScale) && (
                    <RatingBadge rating={v.rating} ratingBeyondScale={v.ratingBeyondScale} size="sm" />
                  )}
                  <IconButton label="Edit viewing" onClick={() => setEditing({ open: true, viewing: v })}>
                    <Pencil className="size-4" />
                  </IconButton>
                  <IconButton label="Delete viewing" onClick={() => setViewingToDelete(v)}>
                    <Trash2 className="size-4" />
                  </IconButton>
                </div>
              </li>
            ))}
          </ol>
        )}
      </section>

      {movie.importedFrom.length > 0 && (
        <details className="group mt-8 rounded-xl border border-line px-5 py-4">
          <summary className="flex cursor-pointer items-center gap-2 text-sm text-ink-3 marker:content-none hover:text-ink">
            <FileSpreadsheet className="size-4" />
            Original spreadsheet {movie.importedFrom.length === 1 ? 'row' : 'rows'}
          </summary>
          {movie.importedFrom.map((row) => (
            <div key={`${row.sheet}-${row.rowNumber}`} className="mt-4">
              <p className="mb-2 text-xs text-ink-3">
                {row.sheet}, row {row.rowNumber}
              </p>
              <dl className="grid gap-x-6 gap-y-2 text-sm sm:grid-cols-[10rem_1fr]">
                {Object.entries(row.originalValues).map(([key, value]) => (
                  <div key={key} className="contents">
                    <dt className="text-ink-3">{key}</dt>
                    <dd className="break-words text-ink-2">{String(value)}</dd>
                  </div>
                ))}
              </dl>
            </div>
          ))}
        </details>
      )}

      <ViewingDialog
        movieId={movie.id}
        movieTitle={movie.title}
        open={editing.open}
        viewing={editing.viewing}
        onClose={() => setEditing({ open: false, viewing: null })}
      />

      <ConfirmDialog
        open={confirmDelete}
        title={`Delete “${movie.title}”?`}
        confirmLabel="Delete movie"
        busy={deleteMovie.isPending}
        onCancel={() => setConfirmDelete(false)}
        onConfirm={() =>
          deleteMovie.mutate(movie.id, {
            onSuccess: () => {
              toast.success(`Deleted “${movie.title}”`)
              void navigate('/movies')
            },
            onError: (e) => toast.error(e.message),
          })
        }
        message={
          <>
            This permanently removes the movie, its review and notes, and{' '}
            <strong className="text-ink">{plural(movie.viewings.length, 'viewing')}</strong>. A later spreadsheet import will
            not bring it back.
          </>
        }
      />

      <ConfirmDialog
        open={viewingToDelete !== null}
        title="Delete this viewing?"
        confirmLabel="Delete viewing"
        busy={deleteViewing.isPending}
        onCancel={() => setViewingToDelete(null)}
        onConfirm={() =>
          viewingToDelete &&
          deleteViewing.mutate(viewingToDelete.id, {
            onSuccess: () => {
              toast.success('Viewing deleted')
              setViewingToDelete(null)
            },
            onError: (e) => toast.error(e.message),
          })
        }
        message={
          <>
            The viewing on <strong className="text-ink">{formatDate(viewingToDelete?.watchedOn)}</strong> will be removed.{' '}
            {movie.title} and its other viewings stay.
          </>
        }
      />
    </article>
  )
}

function DetailSkeleton() {
  return (
    <div className="grid gap-8 md:grid-cols-[15rem_1fr]" aria-busy="true" aria-label="Loading movie">
      <Skeleton className="aspect-[2/3] w-full rounded-xl" />
      <div className="space-y-4">
        <Skeleton className="h-4 w-40" />
        <Skeleton className="h-12 w-3/4" />
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-28 w-full" />
      </div>
    </div>
  )
}
