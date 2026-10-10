import { useInsights } from '../../api/queries'
import type { Insights, LabelAverage } from '../../api/types'
import { BarChartCard, type ChartDatum } from '../../components/charts/BarChartCard'
import { StatTile } from '../../components/charts/StatTile'
import { PageHeader } from '../../components/layout/AppShell'
import { ErrorState, Skeleton } from '../../components/ui/States'
import { formatAverage, formatMonth, methodLabel, plural } from '../../lib/format'

/** Groups with no 1-10 rating have no average; they're left out of the chart, not drawn as 0. */
function averages(rows: LabelAverage[], minRated = 1): ChartDatum[] {
  return rows
    .filter((r) => r.averageRating !== null && r.ratedCount >= minRated)
    .sort((a, b) => (b.averageRating ?? 0) - (a.averageRating ?? 0))
    .map((r) => ({
      label: r.label,
      value: Math.round((r.averageRating ?? 0) * 10) / 10,
      detail: `${r.ratedCount} rated${r.beyondScaleCount ? ` · ${r.beyondScaleCount} ∞` : ''} of ${r.movieCount}`,
    }))
}

export function StatsPage() {
  const { data, isPending, error, refetch } = useInsights()
  if (isPending) {
    return (
      <div className="grid gap-4 lg:grid-cols-2" aria-busy="true" aria-label="Loading statistics">
        {Array.from({ length: 6 }, (_, i) => (
          <Skeleton key={i} className="h-72" />
        ))}
      </div>
    )
  }
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />
  return <StatsContent data={data} />
}

function StatsContent({ data }: { data: Insights }) {
  const ratingNote = (
    <>
      ∞ ratings are shown on their own and never folded into averages. Groups without a 1–10 rating are omitted.
    </>
  )
  const datedTotal = data.viewingsPerYear.reduce((sum, y) => sum + y.viewings, 0)

  return (
    <>
      <PageHeader
        eyebrow="Statistics"
        title="Patterns in your watching"
        description="Calculated from the journal database. Missing values are counted separately, never as zero."
      />

      <div className="mb-6 grid gap-4 sm:grid-cols-2 lg:grid-cols-4">
        <StatTile label="Average rating" value={formatAverage(data.rating.average)} note={`across ${plural(data.rating.ratedCount, 'rated title')}`} />
        <StatTile label="Beyond the scale" value={`${data.rating.beyondScaleCount} ∞`} note="kept out of averages" />
        <StatTile label="Unrated titles" value={data.rating.unratedCount} />
        <StatTile label="Viewings with a date" value={datedTotal} note={`${data.undatedViewings} without one`} />
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        <BarChartCard
          title="Viewings per month"
          description="Only viewings with a recorded date"
          data={data.viewingsPerMonth.map((p) => ({ label: p.period, value: p.viewings, detail: plural(p.movies, 'title') }))}
          formatLabel={formatMonth}
          valueName="viewings"
          emptyMessage="No viewings have a date yet."
        />
        <BarChartCard
          title="Viewings per year"
          data={data.viewingsPerYear.map((p) => ({ label: p.period, value: p.viewings, detail: plural(p.movies, 'title') }))}
          valueName="viewings"
          emptyMessage="No viewings have a date yet."
        />
        <BarChartCard
          title="Rating distribution"
          description={`${data.rating.unratedCount} unrated titles not shown`}
          data={data.ratingDistribution.map((b) => ({ label: b.label, value: b.count }))}
          valueName="titles"
          emptyMessage="No ratings yet."
        />
        <BarChartCard
          title="How you watch"
          description="Viewings by method"
          orientation="bar"
          data={data.methods.map((m) => ({ label: m.label, value: m.count }))}
          formatLabel={methodLabel}
          valueName="viewings"
          emptyMessage="No viewings yet."
        />
        <BarChartCard
          title="Average rating by genre family"
          orientation="bar"
          data={averages(data.averageByCategory)}
          domain={[0, 10]}
          formatValue={(v) => v.toFixed(1)}
          valueName="average"
          emptyMessage="No rated titles have a genre."
          footnote={ratingNote}
        />
        <BarChartCard
          title="Average rating by language"
          orientation="bar"
          data={averages(data.averageByLanguage)}
          domain={[0, 10]}
          formatValue={(v) => v.toFixed(1)}
          valueName="average"
          emptyMessage="No rated titles have a language."
          footnote={ratingNote}
        />
        <BarChartCard
          title="Most-watched genres"
          description="Titles per genre"
          orientation="bar"
          data={data.genresByMovies.slice(0, 12).map((g) => ({ label: g.label, value: g.count }))}
          valueName="titles"
          emptyMessage="No titles have a genre."
        />
        <BarChartCard
          title="Average rating by genre"
          description="Genres with at least 3 rated titles"
          orientation="bar"
          data={averages(data.averageByGenre, 3).slice(0, 12)}
          domain={[0, 10]}
          formatValue={(v) => v.toFixed(1)}
          valueName="average"
          emptyMessage="No genre has 3 rated titles yet."
        />
        <BarChartCard
          title="Platforms"
          description="Viewings per streaming service or source"
          orientation="bar"
          data={data.platforms.map((p) => ({ label: p.label, value: p.count }))}
          valueName="viewings"
          emptyMessage="No viewings record a platform."
        />
        <BarChartCard
          title="Theatres"
          description="Cinema viewings per venue"
          orientation="bar"
          data={data.venues.map((v) => ({ label: v.label, value: v.count }))}
          valueName="viewings"
          emptyMessage="No viewings record a theatre."
        />
        <BarChartCard
          title="Movies vs series"
          orientation="bar"
          data={averages(data.averageByFormat)}
          formatLabel={(l) => (l === 'SERIES' ? 'Series' : 'Movies')}
          domain={[0, 10]}
          formatValue={(v) => v.toFixed(1)}
          valueName="average"
          emptyMessage="No rated titles yet."
        />
        <BarChartCard
          title="Release years"
          description="When the titles you watched came out"
          data={data.releaseYears.map((y) => ({ label: y.label, value: y.count }))}
          valueName="titles"
          emptyMessage="No titles record a release year."
        />
      </div>
    </>
  )
}
