import { Clapperboard, Popcorn } from 'lucide-react'
import { Link } from 'react-router'
import { useDashboard } from '../../api/queries'
import type { Dashboard } from '../../api/types'
import { BarChartCard } from '../../components/charts/BarChartCard'
import { StatTile } from '../../components/charts/StatTile'
import { PageHeader } from '../../components/layout/AppShell'
import { ButtonLink } from '../../components/ui/Button'
import { RatingBadge } from '../../components/ui/Rating'
import { EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { formatAverage, formatDate, formatMonth, plural, whereWatched } from '../../lib/format'

export function DashboardPage() {
  const { data, isPending, error, refetch } = useDashboard()

  if (isPending) return <DashboardSkeleton />
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />
  if (data.totalMovies === 0) {
    return (
      <>
        <PageHeader eyebrow="Dashboard" title="Your film journal" />
        <EmptyState
          icon={Popcorn}
          title="Nothing in the journal yet"
          action={
            <div className="flex gap-2">
              <ButtonLink to="/import" variant="primary">
                Import your spreadsheet
              </ButtonLink>
              <ButtonLink to="/movies/new">Add a movie</ButtonLink>
            </div>
          }
        >
          Import your historical spreadsheet or add the last thing you watched.
        </EmptyState>
      </>
    )
  }
  return <DashboardContent data={data} />
}

function DashboardContent({ data }: { data: Dashboard }) {
  const year = data.today.slice(0, 4)
  const month = new Date(`${data.today}T00:00:00`).toLocaleDateString('en-GB', { month: 'long' })
  const { rating } = data

  return (
    <>
      <PageHeader eyebrow={formatDate(data.today, 'long')} title="Your film journal" />

      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <StatTile
          hero
          label="Unique titles watched"
          value={data.totalMovies.toLocaleString('en-GB')}
          note={`${plural(data.totalViewings, 'viewing session')} · ${data.datedViewings} with a recorded date`}
        />
        <StatTile
          label={`Watched in ${year}`}
          value={data.moviesThisYear}
          note={plural(data.viewingsThisYear, 'dated viewing')}
        />
        <StatTile label={`Watched in ${month}`} value={data.moviesThisMonth} note={plural(data.viewingsThisMonth, 'dated viewing')} />
        <StatTile
          label="Average rating"
          value={formatAverage(rating.average)}
          note={`${rating.ratedCount} rated · ${rating.beyondScaleCount} ∞ (not averaged) · ${rating.unratedCount} unrated`}
        />
      </div>

      {data.undatedViewings > 0 && (
        <p className="mt-3 text-xs text-ink-3">
          {plural(data.undatedViewings, 'viewing')} {data.undatedViewings === 1 ? 'has' : 'have'} no recorded date, so
          they count towards totals but not towards yearly, monthly or timeline figures.
        </p>
      )}

      <div className="mt-8 grid gap-4 lg:grid-cols-3">
        <section aria-labelledby="recent-heading" className="card p-5 lg:col-span-2">
          <div className="mb-4 flex items-center justify-between">
            <h2 id="recent-heading" className="text-sm font-semibold">
              Recently watched
            </h2>
            <Link to="/diary" className="text-xs text-ink-3 hover:text-ink">
              Open diary →
            </Link>
          </div>
          {data.recentlyWatched.length === 0 ? (
            <p className="text-sm text-ink-3">No dated viewings yet.</p>
          ) : (
            <ul className="divide-y divide-line">
              {data.recentlyWatched.map((v) => (
                <li key={v.sessionId}>
                  <Link
                    to={`/movies/${v.movieId}`}
                    className="-mx-2 flex items-center gap-4 rounded-lg px-2 py-2.5 hover:bg-surface-2"
                  >
                    <span className="num w-24 shrink-0 text-xs text-ink-3">{formatDate(v.watchedOn)}</span>
                    <span className="min-w-0 flex-1">
                      <span className="block truncate font-medium">{v.title}</span>
                      <span className="block truncate text-xs text-ink-3">
                        {whereWatched(v.platform, v.venue, v.method)}
                      </span>
                    </span>
                    <RatingBadge rating={v.rating} ratingBeyondScale={v.ratingBeyondScale} size="sm" />
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section aria-labelledby="top-heading" className="card p-5">
          <div className="mb-4 flex items-center justify-between">
            <h2 id="top-heading" className="text-sm font-semibold">
              Highest rated
            </h2>
            <Link to="/movies?list=TOP_RATED" className="text-xs text-ink-3 hover:text-ink">
              All →
            </Link>
          </div>
          <ol className="space-y-1">
            {data.topRated.map((m, index) => (
              <li key={m.id}>
                <Link to={`/movies/${m.id}`} className="-mx-2 flex items-center gap-3 rounded-lg px-2 py-2 hover:bg-surface-2">
                  <span className="num w-4 text-xs text-ink-3">{index + 1}</span>
                  <span className="min-w-0 flex-1">
                    <span className="block truncate font-medium">{m.title}</span>
                    <span className="block text-xs text-ink-3">
                      {[m.releaseYear, m.language].filter(Boolean).join(' · ') || 'No details'}
                    </span>
                  </span>
                  <RatingBadge rating={m.rating} ratingBeyondScale={m.ratingBeyondScale} size="sm" />
                </Link>
              </li>
            ))}
          </ol>
        </section>
      </div>

      <div className="mt-4 grid gap-4 lg:grid-cols-2">
        <BarChartCard
          title="Viewing timeline"
          description="Dated viewings per month"
          data={data.timeline.map((p) => ({ label: p.period, value: p.viewings, detail: plural(p.movies, 'title') }))}
          formatLabel={formatMonth}
          valueName="viewings"
          emptyMessage="No viewings have a date yet."
        />
        <BarChartCard
          title="Rating distribution"
          description={`Movie ratings · ${rating.unratedCount} unrated not shown`}
          data={data.ratingDistribution.map((b) => ({ label: b.label, value: b.count }))}
          valueName="titles"
          emptyMessage="No ratings yet."
        />
        <BarChartCard
          title="Favourite platforms"
          description="Viewings per streaming service or source"
          orientation="bar"
          data={data.platforms.slice(0, 8).map((p) => ({ label: p.label, value: p.count }))}
          valueName="viewings"
          emptyMessage="No viewings record a platform."
        />
        <BarChartCard
          title="Languages"
          description="Titles per language"
          orientation="bar"
          data={data.languages.map((p) => ({ label: p.label, value: p.count }))}
          valueName="titles"
          emptyMessage="No titles record a language."
        />
        <BarChartCard
          title="Genre families"
          description="Titles per genre category"
          orientation="bar"
          data={data.genreCategories.map((p) => ({ label: p.label, value: p.count }))}
          valueName="titles"
          emptyMessage="No titles have a genre."
        />
        <BarChartCard
          title="By release decade"
          description="When the titles you watched came out"
          data={data.releaseDecades.map((p) => ({ label: p.label, value: p.count }))}
          valueName="titles"
          emptyMessage="No titles record a release year."
        />
      </div>

      <div className="mt-8 flex justify-center">
        <ButtonLink to="/movies" icon={<Clapperboard className="size-4" />}>
          Browse the library
        </ButtonLink>
      </div>
    </>
  )
}

function DashboardSkeleton() {
  return (
    <div aria-busy="true" aria-label="Loading dashboard">
      <Skeleton className="mb-8 h-12 w-72" />
      <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-5">
        <Skeleton className="h-32 sm:col-span-2" />
        <Skeleton className="h-32" />
        <Skeleton className="h-32" />
        <Skeleton className="h-32" />
      </div>
      <div className="mt-8 grid gap-4 lg:grid-cols-3">
        <Skeleton className="h-80 lg:col-span-2" />
        <Skeleton className="h-80" />
      </div>
    </div>
  )
}
