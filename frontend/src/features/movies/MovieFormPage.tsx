import { useState } from 'react'
import { useNavigate, useParams } from 'react-router'
import { ApiError } from '../../api/client'
import { useCreateMovie, useMovie, useUpdateMovie } from '../../api/queries'
import type { MovieInput, ViewingInput } from '../../api/types'
import { PageHeader } from '../../components/layout/AppShell'
import { ErrorState, Skeleton } from '../../components/ui/States'
import { useToast } from '../../components/ui/Toast'
import { MovieForm } from './MovieForm'
import { EMPTY_MOVIE, fieldKey, movieToInput } from './movieFormModel'

function fieldErrorsOf(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError)) return {}
  return Object.fromEntries(error.fieldErrors.map((f) => [fieldKey(f.field), f.message]))
}

export function MovieFormPage() {
  const params = useParams()
  return params.id ? <EditMovie id={Number(params.id)} /> : <NewMovie />
}

function NewMovie() {
  const navigate = useNavigate()
  const toast = useToast()
  const create = useCreateMovie()
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({})

  const submit = (movie: MovieInput, initialViewing: ViewingInput | null) =>
    create.mutate(
      { movie, initialViewing },
      {
        onSuccess: (created) => {
          toast.success(`Added “${created.title}”`)
          void navigate(`/movies/${created.id}`)
        },
        onError: (error) => {
          setServerErrors(fieldErrorsOf(error))
          toast.error(error.message)
        },
      },
    )

  return (
    <>
      <PageHeader eyebrow="New entry" title="Add a movie" description="Only the title is required. Fill in the rest now or later." />
      <MovieForm
        initial={EMPTY_MOVIE}
        submitLabel="Add to journal"
        submitting={create.isPending}
        serverErrors={serverErrors}
        onSubmit={submit}
        onCancel={() => void navigate(-1)}
      />
    </>
  )
}

function EditMovie({ id }: { id: number }) {
  const navigate = useNavigate()
  const toast = useToast()
  const { data, isPending, error, refetch } = useMovie(id)
  const update = useUpdateMovie(id)
  const [serverErrors, setServerErrors] = useState<Record<string, string>>({})

  if (isPending) return <Skeleton className="h-96 w-full" />
  if (error) return <ErrorState error={error} onRetry={() => void refetch()} />

  const submit = (movie: MovieInput) =>
    update.mutate(movie, {
      onSuccess: () => {
        toast.success('Changes saved')
        void navigate(`/movies/${id}`)
      },
      onError: (e) => {
        setServerErrors(fieldErrorsOf(e))
        toast.error(e.message)
      },
    })

  return (
    <>
      <PageHeader eyebrow="Edit" title={data.title} />
      <MovieForm
        key={data.id}
        movieId={data.id}
        initial={movieToInput(data)}
        submitLabel="Save changes"
        submitting={update.isPending}
        serverErrors={serverErrors}
        onSubmit={submit}
        onCancel={() => void navigate(`/movies/${id}`)}
      />
    </>
  )
}
