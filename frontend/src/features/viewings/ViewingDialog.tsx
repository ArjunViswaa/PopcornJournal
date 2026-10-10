import { useEffect, useId, useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import { usePlatforms, useSaveViewing, useVenues } from '../../api/queries'
import type { ViewingInput, ViewingMethod, ViewingSession } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { Dialog } from '../../components/ui/Dialog'
import { Field, Input, Select, Textarea } from '../../components/ui/Field'
import { RatingInput } from '../../components/ui/Rating'
import { useToast } from '../../components/ui/Toast'
import { METHOD_LABELS, todayIso } from '../../lib/format'

export const EMPTY_VIEWING: ViewingInput = {
  watchedOn: null,
  watchedAtTime: null,
  method: null,
  platformName: null,
  venueName: null,
  location: null,
  rating: null,
  ratingBeyondScale: false,
  notes: null,
}

export function viewingToInput(v: ViewingSession): ViewingInput {
  return {
    watchedOn: v.watchedOn,
    watchedAtTime: v.watchedAtTime ? v.watchedAtTime.slice(0, 5) : null,
    method: v.method,
    platformName: v.platform?.name ?? null,
    venueName: v.venue?.name ?? null,
    location: v.location,
    rating: v.rating,
    ratingBeyondScale: v.ratingBeyondScale,
    notes: v.notes,
  }
}

/** Client-side checks mirror the API's, so most mistakes are caught before a round trip. */
export function validateViewing(v: ViewingInput): Record<string, string> {
  const errors: Record<string, string> = {}
  if (v.watchedAtTime && !v.watchedOn) errors.watchedAtTime = 'A time needs a date'
  if (v.watchedOn && v.watchedOn > todayIso()) errors.watchedOn = "Watch date can't be in the future"
  return errors
}

const blank = (value: string) => (value.trim() === '' ? null : value)

/** The fields of one viewing; reused by the add-movie form for the first viewing. */
export function ViewingFields({
  value,
  onChange,
  errors,
}: {
  value: ViewingInput
  onChange: (next: ViewingInput) => void
  errors: Record<string, string>
}) {
  const platforms = usePlatforms()
  const venues = useVenues()
  const platformListId = useId()
  const venueListId = useId()
  const set = <K extends keyof ViewingInput>(key: K, v: ViewingInput[K]) => onChange({ ...value, [key]: v })

  return (
    <div className="grid gap-4 sm:grid-cols-2">
      <Field label="Watched on" optional error={errors.watchedOn} hint="Leave empty if you don't remember">
        {(p) => (
          <Input type="date" max={todayIso()} value={value.watchedOn ?? ''} onChange={(e) => set('watchedOn', blank(e.target.value))} {...p} />
        )}
      </Field>
      <Field label="Time" optional error={errors.watchedAtTime}>
        {(p) => <Input type="time" value={value.watchedAtTime ?? ''} onChange={(e) => set('watchedAtTime', blank(e.target.value))} {...p} />}
      </Field>
      <Field label="How" optional hint="Cinema, streaming, TV…">
        {(p) => (
          <Select value={value.method ?? ''} onChange={(e) => set('method', (e.target.value || null) as ViewingMethod | null)} {...p}>
            <option value="">Not recorded</option>
            {Object.entries(METHOD_LABELS).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </Select>
        )}
      </Field>
      <Field label="Platform or service" optional hint="e.g. Netflix">
        {(p) => (
          <>
            <Input list={platformListId} value={value.platformName ?? ''} onChange={(e) => set('platformName', blank(e.target.value))} autoComplete="off" {...p} />
            <datalist id={platformListId}>
              {platforms.data?.map((pl) => <option key={pl.id} value={pl.name} />)}
            </datalist>
          </>
        )}
      </Field>
      <Field label="Theatre or venue" optional>
        {(p) => (
          <>
            <Input list={venueListId} value={value.venueName ?? ''} onChange={(e) => set('venueName', blank(e.target.value))} autoComplete="off" {...p} />
            <datalist id={venueListId}>
              {venues.data?.map((v) => <option key={v.id} value={v.name} />)}
            </datalist>
          </>
        )}
      </Field>
      <Field label="Location" optional hint="City or place">
        {(p) => <Input value={value.location ?? ''} onChange={(e) => set('location', blank(e.target.value))} {...p} />}
      </Field>
      <div className="sm:col-span-2">
        <RatingInput
          legend="Rating for this viewing (optional)"
          rating={value.rating}
          ratingBeyondScale={value.ratingBeyondScale}
          onChange={(r) => onChange({ ...value, ...r })}
        />
      </div>
      <Field label="Notes" optional className="sm:col-span-2">
        {(p) => <Textarea value={value.notes ?? ''} onChange={(e) => set('notes', blank(e.target.value))} {...p} />}
      </Field>
    </div>
  )
}

interface ViewingDialogProps {
  movieId: number
  movieTitle: string
  viewing: ViewingSession | null
  open: boolean
  onClose: () => void
}

export function ViewingDialog({ movieId, movieTitle, viewing, open, onClose }: ViewingDialogProps) {
  const [value, setValue] = useState<ViewingInput>(EMPTY_VIEWING)
  const [errors, setErrors] = useState<Record<string, string>>({})
  const save = useSaveViewing(movieId)
  const toast = useToast()

  useEffect(() => {
    if (open) {
      setValue(viewing ? viewingToInput(viewing) : { ...EMPTY_VIEWING, watchedOn: todayIso() })
      setErrors({})
    }
  }, [open, viewing])

  const submit = (event: FormEvent) => {
    event.preventDefault()
    const found = validateViewing(value)
    setErrors(found)
    if (Object.keys(found).length > 0) return
    save.mutate(
      { id: viewing?.id ?? null, viewing: value },
      {
        onSuccess: () => {
          toast.success(viewing ? 'Viewing updated' : 'Viewing added')
          onClose()
        },
        onError: (error) => {
          if (error instanceof ApiError && error.fieldErrors.length) {
            setErrors(Object.fromEntries(error.fieldErrors.map((f) => [f.field.replace(/^.*\./, ''), f.message])))
          }
          toast.error(error.message)
        },
      },
    )
  }

  const formId = useId()
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={viewing ? 'Edit viewing' : 'Log a viewing'}
      description={movieTitle}
      size="lg"
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" type="submit" form={formId} loading={save.isPending}>
            {viewing ? 'Save changes' : 'Add viewing'}
          </Button>
        </>
      }
    >
      <form id={formId} onSubmit={submit} noValidate>
        <ViewingFields value={value} onChange={setValue} errors={errors} />
      </form>
    </Dialog>
  )
}
