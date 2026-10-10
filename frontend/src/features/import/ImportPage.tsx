import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { CheckCircle2, FileSpreadsheet, Upload } from 'lucide-react'
import { useId, useState } from 'react'
import { importsApi } from '../../api/endpoints'
import type { ImportReport } from '../../api/types'
import { PageHeader } from '../../components/layout/AppShell'
import { Button } from '../../components/ui/Button'
import { ConfirmDialog } from '../../components/ui/Dialog'
import { useToast } from '../../components/ui/Toast'
import { plural } from '../../lib/format'

const MAX_BYTES = 5 * 1024 * 1024

const STATUS_LABELS: Record<ImportReport['rows'][number]['status'], string> = {
  NEW: 'New',
  NEW_VIEWING: 'Extra viewing',
  ALREADY_IMPORTED: 'Already imported',
  DELETED_IN_APP: 'Deleted in app',
  CONFLICTS_WITH_EXISTING: 'Conflict',
  ERROR: 'Error',
}

export function ImportPage() {
  const toast = useToast()
  const client = useQueryClient()
  const inputId = useId()
  const [file, setFile] = useState<File | null>(null)
  const [fileError, setFileError] = useState<string>()
  const [report, setReport] = useState<ImportReport | null>(null)
  const [confirm, setConfirm] = useState(false)
  const history = useQuery({ queryKey: ['imports'], queryFn: importsApi.history })

  const preview = useMutation({ mutationFn: importsApi.preview, onSuccess: setReport })
  const commit = useMutation({
    mutationFn: importsApi.commit,
    onSuccess: async (result) => {
      setReport(result)
      setConfirm(false)
      toast.success(`Imported ${plural(result.totals.newMovies, 'movie')} and ${plural(result.totals.newViewings, 'viewing')}`)
      await client.invalidateQueries()
    },
    onError: (e) => {
      setConfirm(false)
      toast.error(`Nothing was imported: ${e.message}`)
    },
  })

  const choose = (chosen: File | undefined) => {
    setReport(null)
    setFileError(undefined)
    if (!chosen) return setFile(null)
    if (!chosen.name.toLowerCase().endsWith('.xlsx')) {
      setFile(null)
      return setFileError('Choose an .xlsx spreadsheet')
    }
    if (chosen.size > MAX_BYTES) {
      setFile(null)
      return setFileError('The file is larger than 5 MB')
    }
    setFile(chosen)
  }

  const t = report?.totals
  const changes = t ? t.newMovies + t.newViewings + t.newWatchlistItems : 0

  return (
    <>
      <PageHeader
        eyebrow="Import"
        title="Import from Excel"
        description="Preview first: a dry run validates every row and shows exactly what would change. Rows imported before are never modified, so re-importing the same file is safe."
      />

      <section className="card flex flex-col gap-4 p-6 sm:flex-row sm:items-center">
        <div className="flex size-12 shrink-0 items-center justify-center rounded-xl bg-surface-2">
          <FileSpreadsheet className="size-5 text-accent" aria-hidden />
        </div>
        <div className="min-w-0 flex-1">
          <label htmlFor={inputId} className="text-sm font-medium">
            Spreadsheet (.xlsx, up to 5 MB)
          </label>
          <input
            id={inputId}
            type="file"
            accept=".xlsx,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
            onChange={(e) => choose(e.target.files?.[0])}
            aria-invalid={fileError ? true : undefined}
            className="mt-1 block w-full text-sm text-ink-2 file:mr-3 file:rounded-lg file:border-0 file:bg-surface-3 file:px-3 file:py-1.5 file:text-ink hover:file:bg-line-strong"
          />
          {fileError && (
            <p role="alert" className="mt-1 text-xs text-danger">
              {fileError}
            </p>
          )}
        </div>
        <Button
          variant="primary"
          icon={<Upload className="size-4" />}
          disabled={!file}
          loading={preview.isPending}
          onClick={() => file && preview.mutate(file, { onError: (e) => toast.error(e.message) })}
        >
          Preview import
        </Button>
      </section>

      {report && t && (
        <section className="mt-6" aria-label="Import report">
          <div className={`mb-4 rounded-xl border px-4 py-3 text-sm ${report.dryRun ? 'border-accent/30 bg-accent-wash' : 'border-good/30 bg-good/10'}`}>
            {report.dryRun ? (
              <>Dry run of <strong>{report.fileName}</strong>: nothing has been written yet.</>
            ) : (
              <span className="inline-flex items-center gap-2">
                <CheckCircle2 className="size-4 text-good" /> Import run #{report.importRunId} committed.
              </span>
            )}
          </div>

          <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4 lg:grid-cols-6">
            {[
              ['Rows read', t.rowsRead],
              ['New movies', t.newMovies],
              ['New viewings', t.newViewings],
              ['Watchlist', t.newWatchlistItems],
              ['Already imported', t.alreadyImported],
              ['Repeat rows merged', t.mergedDuplicateRows],
              ['Deleted in app', t.deletedInApp],
              ['Conflicts', t.conflicts],
              ['Errors', t.errors],
              ['Warnings', t.warnings],
            ].map(([label, value]) => (
              <div key={label} className="card p-4">
                <dt className="text-xs text-ink-3">{label}</dt>
                <dd className="num mt-1 text-2xl font-semibold">{value}</dd>
              </div>
            ))}
          </dl>

          <div className="mt-4 grid gap-4 md:grid-cols-2">
            <div className="card p-5 text-sm">
              <h2 className="mb-3 font-semibold">Sheets</h2>
              <ul className="space-y-1 text-ink-2">
                {report.sheets.map((s) => (
                  <li key={s.name}>
                    <strong className="text-ink">{s.name}</strong> · {s.role.toLowerCase()} · {plural(s.dataRows, 'row')}
                    {s.unmappedColumns.length > 0 && <span className="text-ink-3"> · unmapped: {s.unmappedColumns.join(', ')}</span>}
                  </li>
                ))}
              </ul>
              {report.fileMessages.map((m, i) => (
                <p key={i} className={`mt-2 text-xs ${m.level === 'ERROR' ? 'text-danger' : 'text-ink-3'}`}>
                  {m.level}: {m.text}
                </p>
              ))}
            </div>
            <div className="card space-y-2 p-5 text-sm text-ink-2">
              <h2 className="mb-1 font-semibold text-ink">New reference data</h2>
              <p><span className="text-ink-3">Platforms:</span> {report.newPlatforms.join(', ') || 'none'}</p>
              <p><span className="text-ink-3">Theatres:</span> {report.newVenues.join(', ') || 'none'}</p>
              <p><span className="text-ink-3">Genres:</span> {report.newGenres.join(', ') || 'none'}</p>
              {report.mergedSpellings.map((m) => (
                <p key={m.kind + m.storedAs} className="text-xs text-ink-3">
                  Merged {m.kind} spellings {m.spellings.map((s) => `“${s}”`).join(', ')} → “{m.storedAs}”
                </p>
              ))}
            </div>
          </div>

          <ReportRows report={report} />

          {report.dryRun && (
            <div className="sticky bottom-0 mt-6 flex items-center justify-between gap-4 rounded-2xl border border-line bg-bg/95 px-5 py-4 backdrop-blur">
              <p className="text-sm text-ink-2">
                {changes === 0 ? 'Nothing new to import.' : `${plural(t.newMovies, 'movie')}, ${plural(t.newViewings, 'viewing')} and ${plural(t.newWatchlistItems, 'watchlist item')} will be added.`}
              </p>
              <Button variant="primary" disabled={changes === 0 || !file} onClick={() => setConfirm(true)}>
                Import
              </Button>
            </div>
          )}
        </section>
      )}

      {history.data && history.data.length > 0 && (
        <section className="mt-10" aria-labelledby="history-heading">
          <h2 id="history-heading" className="eyebrow mb-3">
            Previous imports
          </h2>
          <ul className="card divide-y divide-line text-sm">
            {history.data.map((run) => (
              <li key={run.id} className="flex flex-wrap items-center justify-between gap-2 px-5 py-3">
                <span>
                  #{run.id} · {run.fileName} <span className="text-ink-3">via {run.triggeredBy}</span>
                </span>
                <span className="num text-ink-3">
                  {new Date(run.startedAt).toLocaleString('en-GB')} · {String(run.summary.newMovies ?? 0)} movies,{' '}
                  {String(run.summary.newViewings ?? 0)} viewings
                </span>
              </li>
            ))}
          </ul>
        </section>
      )}

      <ConfirmDialog
        open={confirm}
        title="Import into the journal?"
        confirmLabel="Import now"
        busy={commit.isPending}
        onCancel={() => setConfirm(false)}
        onConfirm={() => file && commit.mutate(file)}
        message="The file is validated again and imported in a single transaction. If anything fails, nothing is written."
      />
    </>
  )
}

function ReportRows({ report }: { report: ImportReport }) {
  const [onlyNotable, setOnlyNotable] = useState(true)
  const rows = report.rows.filter(
    (r) => !onlyNotable || r.status !== 'NEW' || r.messages.some((m) => m.level !== 'INFO'),
  )
  return (
    <div className="card mt-4 p-5">
      <div className="mb-3 flex items-center justify-between">
        <h2 className="text-sm font-semibold">Rows</h2>
        <label className="inline-flex items-center gap-2 text-xs text-ink-3">
          <input type="checkbox" checked={onlyNotable} onChange={(e) => setOnlyNotable(e.target.checked)} className="accent-[var(--color-accent)]" />
          Only rows needing attention
        </label>
      </div>
      {rows.length === 0 ? (
        <p className="text-sm text-ink-3">No rows need attention.</p>
      ) : (
        <div className="max-h-[28rem] overflow-auto">
          <table className="w-full text-sm">
            <thead className="sticky top-0 bg-surface text-left text-xs text-ink-3">
              <tr>
                <th scope="col" className="py-2 pr-3 font-medium">Row</th>
                <th scope="col" className="py-2 pr-3 font-medium">Title</th>
                <th scope="col" className="py-2 pr-3 font-medium">Status</th>
                <th scope="col" className="py-2 font-medium">Notes</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={`${r.sheet}-${r.row}`} className="border-t border-line/60 align-top">
                  <td className="num py-2 pr-3 text-ink-3">{r.sheet === report.sheets[0]?.name ? r.row : `${r.sheet} ${r.row}`}</td>
                  <td className="py-2 pr-3">{r.title ?? <span className="text-ink-3">—</span>}</td>
                  <td className={`py-2 pr-3 whitespace-nowrap ${r.status === 'ERROR' ? 'text-danger' : 'text-ink-2'}`}>{STATUS_LABELS[r.status]}</td>
                  <td className="py-2 text-xs text-ink-3">
                    {r.messages.map((m, i) => (
                      <p key={i} className={m.level === 'ERROR' ? 'text-danger' : m.level === 'WARNING' ? 'text-accent' : ''}>
                        {m.field ? `${m.field}: ` : ''}
                        {m.text}
                      </p>
                    ))}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
