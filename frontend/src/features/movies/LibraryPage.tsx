import { Clapperboard, ImageDown, LayoutGrid, List, Plus, Search, SearchX, X } from 'lucide-react'
import { useEffect, useMemo, useState } from 'react'
import { useSearchParams } from 'react-router'
import { useGenres, useLanguages, useMovieList, usePlatforms, usePosterStatus } from '../../api/queries'
import type { MovieFormat, MovieQuery, MovieSort, PersonalList, RatingTier } from '../../api/types'
import { PageHeader } from '../../components/layout/AppShell'
import { Button, ButtonLink } from '../../components/ui/Button'
import { Input, Select } from '../../components/ui/Field'
import { Pagination } from '../../components/ui/Pagination'
import { EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { useDebouncedValue, useStoredPreference } from '../../lib/hooks'
import { MovieCard, MovieRow } from './MovieCard'
import { PosterLookupDialog } from './PosterLookupDialog'

const PAGE_SIZE = 24

export const LISTS: { value: PersonalList | ''; label: string }[] = [
  { value: '', label: 'All' },
  { value: 'FAVOURITES', label: 'Favourites' },
  { value: 'TOP_RATED', label: 'Top rated' },
  { value: 'RECENTLY_WATCHED', label: 'Recently watched' },
  { value: 'UNRATED', label: 'Unrated' },
  { value: 'NO_REVIEW', label: 'No review' },
  { value: 'MISSING_METADATA', label: 'Missing details' },
]

const SORTS: { value: MovieSort; label: string }[] = [
  { value: 'TITLE', label: 'Title A–Z' },
  { value: 'LATEST_WATCHED', label: 'Latest watched' },
  { value: 'EARLIEST_WATCHED', label: 'Earliest watched' },
  { value: 'HIGHEST_RATED', label: 'Highest rated' },
  { value: 'LOWEST_RATED', label: 'Lowest rated' },
  { value: 'RELEASE_YEAR', label: 'Newest release' },
  { value: 'RECENTLY_ADDED', label: 'Recently added' },
]

const FILTER_KEYS = ['language', 'genreId', 'platformId', 'format', 'watchedYear', 'minRating', 'ratingTier'] as const

function numberParam(value: string | null): number | undefined {
  if (value === null || value === '') return undefined
  const n = Number(value)
  return Number.isFinite(n) ? n : undefined
}

/** The URL is the source of truth for the query, so filtered views can be bookmarked. */
function queryFromParams(params: URLSearchParams): MovieQuery {
  return {
    q: params.get('q') ?? undefined,
    list: (params.get('list') as PersonalList | null) ?? undefined,
    language: params.get('language') ?? undefined,
    genreId: numberParam(params.get('genreId')),
    platformId: numberParam(params.get('platformId')),
    format: (params.get('format') as MovieFormat | null) ?? undefined,
    watchedYear: numberParam(params.get('watchedYear')),
    minRating: numberParam(params.get('minRating')),
    ratingTier: (params.get('ratingTier') as RatingTier | null) ?? undefined,
    sort: (params.get('sort') as MovieSort | null) ?? undefined,
    page: numberParam(params.get('page')) ?? 0,
    size: PAGE_SIZE,
  }
}

export function LibraryPage() {
  const [params, setParams] = useSearchParams()
  const query = useMemo(() => queryFromParams(params), [params])
  const [view, setView] = useStoredPreference('library-view', 'grid', ['grid', 'list'] as const)
  const [searchText, setSearchText] = useState(query.q ?? '')
  const debouncedSearch = useDebouncedValue(searchText)

  const { data, isPending, isFetching, error, refetch } = useMovieList(query)
  const genres = useGenres()
  const platforms = usePlatforms()
  const languages = useLanguages()
  const posterStatus = usePosterStatus()
  const [posterDialog, setPosterDialog] = useState(false)
  const posters = posterStatus.data
  const canFindPosters = posters?.enabled === true && posters.pending + posters.notFound > 0

  const update = (changes: Record<string, string | number | undefined>) => {
    setParams(
      (current) => {
        const next = new URLSearchParams(current)
        for (const [key, value] of Object.entries(changes)) {
          if (value === undefined || value === '') next.delete(key)
          else next.set(key, String(value))
        }
        // any change other than paging starts again from the first page
        if (!('page' in changes)) next.delete('page')
        return next
      },
      { replace: true },
    )
  }

  useEffect(() => {
    // only the debounced text should trigger this, not every URL change
    if ((query.q ?? '') !== debouncedSearch.trim()) update({ q: debouncedSearch.trim() || undefined })
  }, [debouncedSearch])

  const activeFilters = FILTER_KEYS.filter((key) => params.get(key))
  const currentYear = new Date().getFullYear()
  const years = Array.from({ length: 12 }, (_, i) => currentYear - i)

  return (
    <>
      <PageHeader
        eyebrow="Library"
        title="Everything you've watched"
        description={data ? `${data.totalElements.toLocaleString('en-GB')} titles match` : undefined}
        actions={
          <>
            {canFindPosters && (
              <Button icon={<ImageDown className="size-4" />} onClick={() => setPosterDialog(true)}>
                Find posters
              </Button>
            )}
            <ButtonLink to="/movies/new" variant="primary" icon={<Plus className="size-4" />}>
              Add movie
            </ButtonLink>
          </>
        }
      />
      {posters && <PosterLookupDialog open={posterDialog} onClose={() => setPosterDialog(false)} status={posters} />}

      <div className="mb-4 flex flex-wrap items-center gap-1" role="tablist" aria-label="Personal lists">
        {LISTS.map((list) => {
          const selected = (query.list ?? '') === list.value
          return (
            <button
              key={list.value || 'all'}
              type="button"
              role="tab"
              aria-selected={selected}
              onClick={() => update({ list: list.value || undefined })}
              className={`rounded-full px-3.5 py-1.5 text-sm transition-colors ${
                selected ? 'bg-ink text-bg' : 'text-ink-3 hover:bg-surface-2 hover:text-ink'
              }`}
            >
              {list.label}
            </button>
          )
        })}
      </div>

      <div className="card mb-6 flex flex-col gap-3 p-3">
        <div className="relative">
          <Search className="pointer-events-none absolute top-1/2 left-3 size-4 -translate-y-1/2 text-ink-3" aria-hidden />
          <Input
            type="search"
            aria-label="Search by title"
            placeholder="Search titles…"
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            className="pl-9"
          />
        </div>
        <div className="grid grid-cols-2 gap-2 sm:grid-cols-4 lg:grid-cols-8">
          <Select aria-label="Language" value={query.language ?? ''} onChange={(e) => update({ language: e.target.value })}>
            <option value="">All languages</option>
            {languages.data?.map((l) => (
              <option key={l} value={l}>
                {l}
              </option>
            ))}
          </Select>
          <Select aria-label="Genre" value={query.genreId ?? ''} onChange={(e) => update({ genreId: e.target.value })}>
            <option value="">All genres</option>
            {genres.data?.filter((g) => g.movieCount > 0).map((g) => (
              <option key={g.id} value={g.id}>
                {g.name}
              </option>
            ))}
          </Select>
          <Select aria-label="Platform" value={query.platformId ?? ''} onChange={(e) => update({ platformId: e.target.value })}>
            <option value="">All platforms</option>
            {platforms.data?.map((p) => (
              <option key={p.id} value={p.id}>
                {p.name}
              </option>
            ))}
          </Select>
          <Select aria-label="Format" value={query.format ?? ''} onChange={(e) => update({ format: e.target.value })}>
            <option value="">Movies & series</option>
            <option value="MOVIE">Movies</option>
            <option value="SERIES">Series</option>
          </Select>
          <Select aria-label="Watched in year" value={query.watchedYear ?? ''} onChange={(e) => update({ watchedYear: e.target.value })}>
            <option value="">Any watch year</option>
            {years.map((y) => (
              <option key={y} value={y}>
                Watched in {y}
              </option>
            ))}
          </Select>
          <Select aria-label="Minimum rating" value={query.minRating ?? ''} onChange={(e) => update({ minRating: e.target.value })}>
            <option value="">Any rating</option>
            {[9, 8, 7, 6, 5].map((r) => (
              <option key={r} value={r}>
                {r}+ (incl. ∞)
              </option>
            ))}
          </Select>
          <Select aria-label="Rating tier" value={query.ratingTier ?? ''} onChange={(e) => update({ ratingTier: e.target.value })}>
            <option value="">Rated or not</option>
            <option value="RATED">Rated 1–10</option>
            <option value="BEYOND_SCALE">∞ only</option>
            <option value="UNRATED">Unrated</option>
          </Select>
          <Select aria-label="Sort by" value={query.sort ?? ''} onChange={(e) => update({ sort: e.target.value })}>
            <option value="">Default order</option>
            {SORTS.map((s) => (
              <option key={s.value} value={s.value}>
                {s.label}
              </option>
            ))}
          </Select>
        </div>
        <div className="flex items-center justify-between gap-2">
          {activeFilters.length > 0 || query.q ? (
            <button
              type="button"
              onClick={() => {
                setSearchText('')
                setParams(query.list ? { list: query.list } : {}, { replace: true })
              }}
              className="inline-flex items-center gap-1 rounded-lg px-2 py-1 text-xs text-ink-3 hover:bg-surface-2 hover:text-ink"
            >
              <X className="size-3.5" /> Clear filters
            </button>
          ) : (
            <span />
          )}
          <div className="flex rounded-lg border border-line p-0.5" role="group" aria-label="Layout">
            <button
              type="button"
              aria-pressed={view === 'grid'}
              aria-label="Grid view"
              onClick={() => setView('grid')}
              className={`rounded-md p-1.5 ${view === 'grid' ? 'bg-surface-3 text-ink' : 'text-ink-3 hover:text-ink'}`}
            >
              <LayoutGrid className="size-4" />
            </button>
            <button
              type="button"
              aria-pressed={view === 'list'}
              aria-label="List view"
              onClick={() => setView('list')}
              className={`rounded-md p-1.5 ${view === 'list' ? 'bg-surface-3 text-ink' : 'text-ink-3 hover:text-ink'}`}
            >
              <List className="size-4" />
            </button>
          </div>
        </div>
      </div>

      {error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : isPending ? (
        <LibrarySkeleton />
      ) : data.content.length === 0 ? (
        query.q || activeFilters.length || query.list ? (
          <EmptyState icon={SearchX} title="No titles match">
            Try a different search or clear some filters.
          </EmptyState>
        ) : (
          <EmptyState
            icon={Clapperboard}
            title="Your library is empty"
            action={<ButtonLink to="/import" variant="primary">Import your spreadsheet</ButtonLink>}
          />
        )
      ) : (
        <div className={`transition-opacity ${isFetching ? 'opacity-60' : ''}`} aria-busy={isFetching}>
          {view === 'grid' ? (
            <ul className="grid grid-cols-2 gap-2 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-6">
              {data.content.map((movie) => (
                <li key={movie.id}>
                  <MovieCard movie={movie} />
                </li>
              ))}
            </ul>
          ) : (
            <ul className="card divide-y divide-line p-1.5">
              {data.content.map((movie) => (
                <li key={movie.id}>
                  <MovieRow movie={movie} />
                </li>
              ))}
            </ul>
          )}
          <div className="mt-6">
            <Pagination
              page={data.page}
              totalPages={data.totalPages}
              totalElements={data.totalElements}
              size={data.size}
              onPage={(page) => update({ page: page || undefined })}
            />
          </div>
        </div>
      )}
    </>
  )
}

function LibrarySkeleton() {
  return (
    <div className="grid grid-cols-2 gap-4 sm:grid-cols-3 md:grid-cols-4 xl:grid-cols-6" aria-busy="true" aria-label="Loading movies">
      {Array.from({ length: 12 }, (_, i) => (
        <div key={i} className="flex flex-col gap-2 p-2">
          <Skeleton className="aspect-[2/3] w-full rounded-xl" />
          <Skeleton className="h-4 w-3/4" />
          <Skeleton className="h-3 w-1/2" />
        </div>
      ))}
    </div>
  )
}
