import { AlertTriangle } from 'lucide-react'
import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { moviesApi } from '../../api/endpoints'
import { useGenres, useLanguages } from '../../api/queries'
import type { DuplicateCandidate, MovieInput, ViewingInput } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { Field, Input, Select, Textarea } from '../../components/ui/Field'
import { RatingInput } from '../../components/ui/Rating'
import { useDebouncedValue } from '../../lib/hooks'
import { EMPTY_VIEWING, validateViewing, ViewingFields } from '../viewings/ViewingDialog'
import { GenrePicker } from './GenrePicker'
import { validateMovie } from './movieFormModel'
import { todayIso } from '../../lib/format'

interface MovieFormProps {
  initial: MovieInput
  movieId?: number
  submitLabel: string
  submitting: boolean
  serverErrors: Record<string, string>
  onSubmit: (movie: MovieInput, initialViewing: ViewingInput | null) => void
  onCancel: () => void
}

const blank = (value: string) => (value.trim() === '' ? null : value)
const toNumber = (value: string) => (value.trim() === '' ? null : Number(value))

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <section className="card p-5 sm:p-6">
      <h2 className="mb-5 font-display text-lg font-semibold">{title}</h2>
      {children}
    </section>
  )
}

export function MovieForm({ initial, movieId, submitLabel, submitting, serverErrors, onSubmit, onCancel }: MovieFormProps) {
  const [movie, setMovie] = useState<MovieInput>(initial)
  const [logViewing, setLogViewing] = useState(false)
  const [viewing, setViewing] = useState<ViewingInput>({ ...EMPTY_VIEWING, watchedOn: todayIso() })
  const [errors, setErrors] = useState<Record<string, string>>({})
  const [duplicates, setDuplicates] = useState<DuplicateCandidate[]>([])
  const genres = useGenres()
  const languages = useLanguages()
  const isNew = movieId === undefined

  useEffect(() => setErrors((current) => ({ ...current, ...serverErrors })), [serverErrors])

  const identity = useDebouncedValue(
    { title: movie.title.trim(), format: movie.format, releaseYear: movie.releaseYear, language: movie.language },
    400,
  )
  useEffect(() => {
    if (!identity.title) {
      setDuplicates([])
      return
    }
    let cancelled = false
    moviesApi
      .duplicates({ ...identity, ...(movieId !== undefined ? { excludeId: movieId } : {}) })
      .then((found) => !cancelled && setDuplicates(found))
      // the warning is a convenience; the server still rejects true duplicates on save
      .catch(() => !cancelled && setDuplicates([]))
    return () => {
      cancelled = true
    }
  }, [identity.title, identity.format, identity.releaseYear, identity.language, movieId])

  const set = <K extends keyof MovieInput>(key: K, value: MovieInput[K]) => {
    setMovie((m) => ({ ...m, [key]: value }))
    if (errors[key]) setErrors(({ [key]: _removed, ...rest }) => rest)
  }

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const found = { ...validateMovie(movie), ...(isNew && logViewing ? validateViewing(viewing) : {}) }
    setErrors(found)
    if (Object.keys(found).length > 0) {
      document.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus()
      return
    }
    onSubmit({ ...movie, title: movie.title.trim() }, isNew && logViewing ? viewing : null)
  }

  const exact = duplicates.filter((d) => d.exactMatch)
  const similar = duplicates.filter((d) => !d.exactMatch)

  return (
    <form onSubmit={submit} noValidate className="flex flex-col gap-6">
      <Section title="The film">
        <div className="grid gap-4 sm:grid-cols-6">
          <Field label="Title" error={errors.title} className="sm:col-span-4">
            {(p) => <Input value={movie.title} onChange={(e) => set('title', e.target.value)} autoFocus={isNew} required {...p} />}
          </Field>
          <Field label="Format" className="sm:col-span-2">
            {(p) => (
              <Select value={movie.format} onChange={(e) => set('format', e.target.value as MovieInput['format'])} {...p}>
                <option value="MOVIE">Movie</option>
                <option value="SERIES">Series</option>
              </Select>
            )}
          </Field>

          {(exact.length > 0 || similar.length > 0) && (
            <div
              role="status"
              className={`flex gap-3 rounded-xl border px-4 py-3 text-sm sm:col-span-6 ${
                exact.length ? 'border-danger/40 bg-danger-wash' : 'border-accent/30 bg-accent-wash'
              }`}
            >
              <AlertTriangle className={`mt-0.5 size-4 shrink-0 ${exact.length ? 'text-danger' : 'text-accent'}`} aria-hidden />
              <div>
                <p className="text-ink">
                  {exact.length
                    ? 'This title is already in your journal with the same year and language.'
                    : 'You have a title with the same name. Fine if it is a different film.'}
                </p>
                <ul className="mt-1 text-ink-2">
                  {[...exact, ...similar].map((d) => (
                    <li key={d.id}>
                      <Link to={`/movies/${d.id}`} className="underline hover:text-ink">
                        {d.title}
                      </Link>{' '}
                      <span className="text-ink-3">{[d.releaseYear, d.language].filter(Boolean).join(' · ')}</span>
                    </li>
                  ))}
                </ul>
              </div>
            </div>
          )}

          <Field label="Original title" optional className="sm:col-span-3">
            {(p) => <Input value={movie.originalTitle ?? ''} onChange={(e) => set('originalTitle', blank(e.target.value))} {...p} />}
          </Field>
          <Field label="Release year" optional error={errors.releaseYear} className="sm:col-span-1">
            {(p) => (
              <Input type="number" inputMode="numeric" value={movie.releaseYear ?? ''} onChange={(e) => set('releaseYear', toNumber(e.target.value))} {...p} />
            )}
          </Field>
          <Field label="Language" optional className="sm:col-span-2">
            {(p) => (
              <>
                <Input list="language-options" value={movie.language ?? ''} onChange={(e) => set('language', blank(e.target.value))} autoComplete="off" {...p} />
                <datalist id="language-options">
                  {languages.data?.map((l) => <option key={l} value={l} />)}
                </datalist>
              </>
            )}
          </Field>
          <Field label="Genres" optional error={errors.genres} className="sm:col-span-6">
            {(p) => (
              <GenrePicker
                inputId={p.id}
                describedBy={p['aria-describedby']}
                value={movie.genres}
                onChange={(g) => set('genres', g)}
                suggestions={genres.data?.map((g) => g.name) ?? []}
              />
            )}
          </Field>
          <Field label="Synopsis" optional className="sm:col-span-6">
            {(p) => <Textarea value={movie.synopsis ?? ''} onChange={(e) => set('synopsis', blank(e.target.value))} {...p} />}
          </Field>
        </div>
      </Section>

      <Section title="Your take">
        <div className="flex flex-col gap-5">
          <RatingInput
            legend="Your rating"
            rating={movie.rating}
            ratingBeyondScale={movie.ratingBeyondScale}
            onChange={(r) => setMovie((m) => ({ ...m, ...r }))}
          />
          <Field label="Review" optional hint="Leave it for later if you like">
            {(p) => <Textarea rows={5} value={movie.review ?? ''} onChange={(e) => set('review', blank(e.target.value))} {...p} />}
          </Field>
          <Field label="Private notes" optional>
            {(p) => <Textarea value={movie.privateNotes ?? ''} onChange={(e) => set('privateNotes', blank(e.target.value))} {...p} />}
          </Field>
          <label className="inline-flex cursor-pointer items-center gap-2.5 text-sm text-ink-2">
            <input
              type="checkbox"
              checked={movie.favourite}
              onChange={(e) => set('favourite', e.target.checked)}
              className="size-4 accent-[var(--color-accent)]"
            />
            Mark as a favourite
          </label>
        </div>
      </Section>

      <Section title="Credits & details">
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Director" optional>
            {(p) => <Input value={movie.director ?? ''} onChange={(e) => set('director', blank(e.target.value))} {...p} />}
          </Field>
          <Field label="Runtime (minutes)" optional error={errors.runtimeMinutes}>
            {(p) => (
              <Input type="number" inputMode="numeric" value={movie.runtimeMinutes ?? ''} onChange={(e) => set('runtimeMinutes', toNumber(e.target.value))} {...p} />
            )}
          </Field>
          <Field label="Cast" optional className="sm:col-span-2">
            {(p) => <Input value={movie.castMembers ?? ''} onChange={(e) => set('castMembers', blank(e.target.value))} {...p} />}
          </Field>
          <Field label="Poster URL" optional error={errors.posterUrl} hint="Overrides any poster found automatically">
            {(p) => <Input type="url" value={movie.posterUrl ?? ''} onChange={(e) => set('posterUrl', blank(e.target.value))} {...p} />}
          </Field>
          <Field label="Metadata source" optional hint="Where these details came from">
            {(p) => <Input value={movie.metadataSource ?? ''} onChange={(e) => set('metadataSource', blank(e.target.value))} {...p} />}
          </Field>
        </div>
      </Section>

      {isNew && (
        <Section title="First viewing">
          <label className="mb-4 inline-flex cursor-pointer items-center gap-2.5 text-sm text-ink-2">
            <input
              type="checkbox"
              checked={logViewing}
              onChange={(e) => setLogViewing(e.target.checked)}
              className="size-4 accent-[var(--color-accent)]"
            />
            Log when and where I watched it
          </label>
          {logViewing && <ViewingFields value={viewing} onChange={setViewing} errors={errors} />}
        </Section>
      )}

      <div className="sticky bottom-0 -mx-4 flex justify-end gap-2 border-t border-line bg-bg/95 px-4 py-4 backdrop-blur sm:mx-0 sm:rounded-2xl sm:border">
        <Button variant="ghost" onClick={onCancel}>
          Cancel
        </Button>
        <Button variant="primary" type="submit" loading={submitting} disabled={exact.length > 0}>
          {submitLabel}
        </Button>
      </div>
    </form>
  )
}
