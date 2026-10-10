import { ListPlus, Pencil, Plus, Trash2 } from 'lucide-react'
import { useEffect, useId, useState, type FormEvent } from 'react'
import { useDeleteWatchlistItem, useGenres, useSaveWatchlistItem, useWatchlist } from '../../api/queries'
import type { WatchlistInput, WatchlistItem } from '../../api/types'
import { PageHeader } from '../../components/layout/AppShell'
import { Button, IconButton } from '../../components/ui/Button'
import { ConfirmDialog, Dialog } from '../../components/ui/Dialog'
import { Field, Input, Textarea } from '../../components/ui/Field'
import { Chip, EmptyState, ErrorState, Skeleton } from '../../components/ui/States'
import { useToast } from '../../components/ui/Toast'

const EMPTY: WatchlistInput = { title: '', recommendedBy: null, genre: null, notes: null }
const blank = (v: string) => (v.trim() === '' ? null : v)

export function WatchlistPage() {
  const { data, isPending, error, refetch } = useWatchlist()
  const remove = useDeleteWatchlistItem()
  const toast = useToast()
  const [editing, setEditing] = useState<{ open: boolean; item: WatchlistItem | null }>({ open: false, item: null })
  const [toDelete, setToDelete] = useState<WatchlistItem | null>(null)

  return (
    <>
      <PageHeader
        eyebrow="Watchlist"
        title="Recommended to watch"
        description="Kept separate from your journal: these don't count as watched."
        actions={
          <Button variant="primary" icon={<Plus className="size-4" />} onClick={() => setEditing({ open: true, item: null })}>
            Add recommendation
          </Button>
        }
      />

      {error ? (
        <ErrorState error={error} onRetry={() => void refetch()} />
      ) : isPending ? (
        <Skeleton className="h-40 w-full" />
      ) : data.length === 0 ? (
        <EmptyState icon={ListPlus} title="Nothing on the watchlist">
          Add something a friend recommended.
        </EmptyState>
      ) : (
        <ul className="card divide-y divide-line">
          {data.map((item) => (
            <li key={item.id} className="flex items-start gap-4 px-5 py-4">
              <div className="min-w-0 flex-1">
                <p className="font-medium">{item.title}</p>
                <p className="text-xs text-ink-3">
                  {item.recommendedBy ? `Recommended by ${item.recommendedBy}` : 'No recommender noted'}
                </p>
                {item.notes && <p className="mt-1 text-sm text-ink-2">{item.notes}</p>}
              </div>
              {item.genre && <Chip>{item.genre}</Chip>}
              <IconButton label={`Edit ${item.title}`} onClick={() => setEditing({ open: true, item })}>
                <Pencil className="size-4" />
              </IconButton>
              <IconButton label={`Delete ${item.title}`} onClick={() => setToDelete(item)}>
                <Trash2 className="size-4" />
              </IconButton>
            </li>
          ))}
        </ul>
      )}

      <WatchlistDialog open={editing.open} item={editing.item} onClose={() => setEditing({ open: false, item: null })} />
      <ConfirmDialog
        open={toDelete !== null}
        title="Remove from watchlist?"
        confirmLabel="Remove"
        busy={remove.isPending}
        message={<>“{toDelete?.title}” will be removed from the watchlist.</>}
        onCancel={() => setToDelete(null)}
        onConfirm={() =>
          toDelete &&
          remove.mutate(toDelete.id, {
            onSuccess: () => {
              toast.success('Removed from watchlist')
              setToDelete(null)
            },
            onError: (e) => toast.error(e.message),
          })
        }
      />
    </>
  )
}

function WatchlistDialog({ open, item, onClose }: { open: boolean; item: WatchlistItem | null; onClose: () => void }) {
  const [value, setValue] = useState<WatchlistInput>(EMPTY)
  const [error, setError] = useState<string>()
  const save = useSaveWatchlistItem()
  const genres = useGenres()
  const toast = useToast()
  const formId = useId()

  useEffect(() => {
    if (open) {
      setValue(item ? { title: item.title, recommendedBy: item.recommendedBy, genre: item.genre, notes: item.notes } : EMPTY)
      setError(undefined)
    }
  }, [open, item])

  const submit = (event: FormEvent) => {
    event.preventDefault()
    if (!value.title.trim()) {
      setError('Title is required')
      return
    }
    save.mutate(
      { id: item?.id ?? null, item: value },
      {
        onSuccess: () => {
          toast.success(item ? 'Recommendation updated' : 'Added to watchlist')
          onClose()
        },
        onError: (e) => toast.error(e.message),
      },
    )
  }

  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={item ? 'Edit recommendation' : 'Add recommendation'}
      footer={
        <>
          <Button variant="ghost" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="primary" type="submit" form={formId} loading={save.isPending}>
            Save
          </Button>
        </>
      }
    >
      <form id={formId} onSubmit={submit} noValidate className="grid gap-4">
        <Field label="Title" error={error}>
          {(p) => <Input value={value.title} onChange={(e) => setValue({ ...value, title: e.target.value })} {...p} />}
        </Field>
        <Field label="Recommended by" optional>
          {(p) => <Input value={value.recommendedBy ?? ''} onChange={(e) => setValue({ ...value, recommendedBy: blank(e.target.value) })} {...p} />}
        </Field>
        <Field label="Genre" optional>
          {(p) => (
            <>
              <Input list="watchlist-genres" value={value.genre ?? ''} onChange={(e) => setValue({ ...value, genre: blank(e.target.value) })} autoComplete="off" {...p} />
              <datalist id="watchlist-genres">
                {genres.data?.map((g) => <option key={g.id} value={g.name} />)}
              </datalist>
            </>
          )}
        </Field>
        <Field label="Notes" optional>
          {(p) => <Textarea value={value.notes ?? ''} onChange={(e) => setValue({ ...value, notes: blank(e.target.value) })} {...p} />}
        </Field>
      </form>
    </Dialog>
  )
}
