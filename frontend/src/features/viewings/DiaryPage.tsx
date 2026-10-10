import { BookOpen } from 'lucide-react'
import { Link, useSearchParams } from 'react-router'
import { useDiary } from '../../api/queries'
import type { ViewingSession } from '../../api/types'
import { PageHeader } from '../../components/layout/AppShell'
import { Select } from '../../components/ui/Field'
import { Pagination } from '../../components/ui/Pagination'
import { RatingBadge } from '../../components/ui/Rating'
import { EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { formatDate, methodLabel } from '../../lib/format'

function monthHeading(iso: string | null): string {
  if (!iso) return 'Date not recorded'
  return new Date(`${iso}T00:00:00`).toLocaleDateString('en-GB', { month: 'long', year: 'numeric' })
}

/** Groups consecutive viewings under month headings; undated ones share one group. */
function groupByMonth(viewings: ViewingSession[]) {
  const groups: { heading: string; items: ViewingSession[] }[] = []
  for (const v of viewings) {
    const heading = monthHeading(v.watchedOn)
    const last = groups.at(-1)
    if (last && last.heading === heading) last.items.push(v)
    else groups.push({ heading, items: [v] })
  }
  return groups
}

export function DiaryPage() {
  const [params, setParams] = useSearchParams()
  const filter = params.get('filter') ?? ''
  const page = Number(params.get('page') ?? 0) || 0
  const year = /^\d{4}$/.test(filter) ? Number(filter) : undefined
  const undated = filter === 'undated'
  const { data, isPending, error, refetch, isFetching } = useDiary({
    page,
    ...(year !== undefined ? { year } : {}),
    ...(undated ? { undated: true } : {}),
  })
  const currentYear = new Date().getFullYear()

  return (
    <>
      <PageHeader
        eyebrow="Diary"
        title="Viewing history"
        description="Every time you watched something, newest first. Viewings without a recorded date are listed at the end."
        actions={
          <Select
            aria-label="Show"
            value={filter}
            onChange={(e) => setParams(e.target.value ? { filter: e.target.value } : {}, { replace: true })}
            className="w-48"
          >
            <option value="">All viewings</option>
            {Array.from({ length: 8 }, (_, i) => currentYear - i).map((y) => (
              <option key={y} value={y}>
                {y}
              </option>
            ))}
            <option value="undated">Date not recorded</option>
          </Select>
        }
      />

      {error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : isPending ? (
        <div className="space-y-2" aria-busy="true" aria-label="Loading diary">
          {Array.from({ length: 8 }, (_, i) => (
            <Skeleton key={i} className="h-14 w-full" />
          ))}
        </div>
      ) : data.content.length === 0 ? (
        <EmptyState icon={BookOpen} title="No viewings here">
          {year ? `Nothing logged with a date in ${year}.` : 'Log a viewing from any movie page.'}
        </EmptyState>
      ) : (
        <div className={isFetching ? 'opacity-60 transition-opacity' : ''}>
          {groupByMonth(data.content).map((group) => (
            <section key={group.heading} className="mb-8" aria-label={group.heading}>
              <h2 className="eyebrow mb-3">{group.heading}</h2>
              <ol className="card divide-y divide-line">
                {group.items.map((v) => (
                  <li key={v.id}>
                    <Link to={`/movies/${v.movieId}`} className="flex items-center gap-4 px-5 py-3 hover:bg-surface-2">
                      <span className="num w-24 shrink-0 text-xs text-ink-3">{v.watchedOn ? formatDate(v.watchedOn) : '—'}</span>
                      <span className="min-w-0 flex-1">
                        <span className="block truncate font-medium">{v.movieTitle}</span>
                        <span className="block truncate text-xs text-ink-3">
                          {[methodLabel(v.method), v.venue?.name, v.platform?.name].filter(Boolean).join(' · ')}
                        </span>
                      </span>
                      {(v.rating !== null || v.ratingBeyondScale) && (
                        <RatingBadge rating={v.rating} ratingBeyondScale={v.ratingBeyondScale} size="sm" />
                      )}
                    </Link>
                  </li>
                ))}
              </ol>
            </section>
          ))}
          <Pagination
            page={data.page}
            totalPages={data.totalPages}
            totalElements={data.totalElements}
            size={data.size}
            onPage={(p) => {
              const next = new URLSearchParams(params)
              if (p) next.set('page', String(p))
              else next.delete('page')
              setParams(next)
            }}
          />
        </div>
      )}
    </>
  )
}
