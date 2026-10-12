import { useRef, useState } from 'react'
import { postersApi } from '../../api/endpoints'
import { useInvalidatePosters } from '../../api/queries'
import type { PosterLookupResult, PosterStatus } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { Dialog } from '../../components/ui/Dialog'
import { plural } from '../../lib/format'

type Phase = 'idle' | 'running' | 'done' | 'stopped' | 'error'

interface PosterLookupDialogProps {
  open: boolean
  onClose: () => void
  status: PosterStatus
}

/**
 * Runs the lookup in small batches so progress is visible and Stop takes effect quickly.
 * Each batch is saved on the server as it goes, so stopping loses nothing.
 */
export function PosterLookupDialog({ open, onClose, status }: PosterLookupDialogProps) {
  const [phase, setPhase] = useState<Phase>('idle')
  const [retryNotFound, setRetryNotFound] = useState(false)
  const [results, setResults] = useState<PosterLookupResult[]>([])
  const [total, setTotal] = useState(0)
  const [error, setError] = useState<string>()
  const stopRequested = useRef(false)
  const invalidate = useInvalidatePosters()

  const toCheck = status.pending + (retryNotFound ? status.notFound : 0)
  const found = results.filter((r) => r.outcome === 'FOUND').length
  const missing = results.filter((r) => r.outcome !== 'FOUND')

  const run = async () => {
    stopRequested.current = false
    setPhase('running')
    setResults([])
    setError(undefined)
    setTotal(toCheck)
    let runStartedAt: string | undefined
    try {
      for (;;) {
        const batch = await postersApi.lookupBatch({ runStartedAt, retryNotFound, batchSize: 8 })
        runStartedAt = batch.runStartedAt
        setResults((all) => [...all, ...batch.results])
        if (batch.remaining === 0 || batch.results.length === 0) {
          setPhase('done')
          break
        }
        if (stopRequested.current) {
          setPhase('stopped')
          break
        }
      }
    } catch (e) {
      setError(e instanceof Error ? e.message : 'The lookup failed')
      setPhase('error')
    } finally {
      await invalidate()
    }
  }

  const close = () => {
    if (phase === 'running') return
    setPhase('idle')
    setResults([])
    onClose()
  }

  const progress = total > 0 ? Math.min(100, Math.round((results.length / total) * 100)) : 0

  return (
    <Dialog
      open={open}
      onClose={close}
      title="Find posters"
      description={`Looks up each title on ${status.source} and saves the image in your database.`}
      footer={
        phase === 'running' ? (
          <Button onClick={() => (stopRequested.current = true)}>Stop after this batch</Button>
        ) : phase === 'idle' ? (
          <>
            <Button variant="ghost" onClick={close}>
              Cancel
            </Button>
            <Button variant="primary" onClick={() => void run()} disabled={toCheck === 0}>
              Look up {plural(toCheck, 'title')}
            </Button>
          </>
        ) : (
          <Button variant="primary" onClick={close}>
            Done
          </Button>
        )
      }
    >
      {phase === 'idle' ? (
        <div className="space-y-4 text-sm text-ink-2">
          <ul className="space-y-1">
            <li>{plural(status.withFetchedPoster + status.withOwnPoster, 'title')} already have a poster.</li>
            <li>{plural(status.pending, 'title')} haven't been looked up yet.</li>
            {status.notFound > 0 && <li>{plural(status.notFound, 'title')} had no confident match last time.</li>}
          </ul>
          <p className="text-ink-3">
            Only the title, release year, format and language are sent. A poster is used only when the title, year
            and type all agree; otherwise the placeholder stays. Poster URLs you entered yourself are never replaced,
            and posters you removed aren't fetched again.
          </p>
          {status.notFound > 0 && (
            <label className="inline-flex items-center gap-2">
              <input
                type="checkbox"
                checked={retryNotFound}
                onChange={(e) => setRetryNotFound(e.target.checked)}
                className="accent-[var(--color-accent)]"
              />
              Also retry titles that weren't found before
            </label>
          )}
        </div>
      ) : (
        <div className="space-y-4 text-sm">
          <div>
            <div className="mb-1.5 flex justify-between text-xs text-ink-3">
              <span>
                {results.length} of {total} checked
              </span>
              <span className="num">{progress}%</span>
            </div>
            <div
              className="h-2 overflow-hidden rounded-full bg-surface-3"
              role="progressbar"
              aria-valuemin={0}
              aria-valuemax={total}
              aria-valuenow={results.length}
              aria-label="Poster lookup progress"
            >
              <div className="h-full rounded-full bg-accent transition-[width]" style={{ width: `${progress}%` }} />
            </div>
          </div>
          <p className="text-ink-2" aria-live="polite">
            {phase === 'running' && 'Looking up posters…'}
            {phase === 'done' && `Finished: ${plural(found, 'poster')} found.`}
            {phase === 'stopped' && `Stopped: ${plural(found, 'poster')} found so far. Run it again to continue.`}
            {phase === 'error' && <span className="text-danger">{error}</span>}
          </p>
          {missing.length > 0 && phase !== 'running' && (
            <details className="rounded-xl border border-line px-4 py-3">
              <summary className="cursor-pointer text-ink-3">
                {plural(missing.length, 'title')} without a confident match
              </summary>
              <ul className="mt-2 max-h-48 space-y-0.5 overflow-y-auto text-xs text-ink-2">
                {missing.map((r) => (
                  <li key={r.movieId}>
                    {r.title}
                    {r.outcome === 'FAILED' && <span className="text-danger"> · lookup failed, will retry next run</span>}
                  </li>
                ))}
              </ul>
              <p className="mt-2 text-xs text-ink-3">You can add a poster URL to any of these from its Edit page.</p>
            </details>
          )}
        </div>
      )}
    </Dialog>
  )
}
