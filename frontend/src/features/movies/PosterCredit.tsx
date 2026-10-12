import { ExternalLink, ImageDown, ImageUp } from 'lucide-react'
import { useRef } from 'react'
import { usePosterLookupOne, usePosterStatus, useRemovePoster, useUploadPoster } from '../../api/queries'
import type { MovieDetail } from '../../api/types'
import { Button } from '../../components/ui/Button'
import { useToast } from '../../components/ui/Toast'

const UPLOAD_SOURCE = 'Upload'
const MAX_BYTES = 5 * 1024 * 1024
const ACCEPTED = ['image/jpeg', 'image/png', 'image/webp', 'image/gif']

/**
 * Under the poster: where it came from, plus upload / remove / find actions.
 * A poster URL typed on the Edit page wins over everything, so it only gets a label.
 */
export function PosterCredit({ movie }: { movie: MovieDetail }) {
  const toast = useToast()
  const status = usePosterStatus()
  const lookup = usePosterLookupOne(movie.id)
  const remove = useRemovePoster(movie.id)
  const upload = useUploadPoster(movie.id)
  const fileInput = useRef<HTMLInputElement>(null)

  if (movie.posterUrl) {
    return <p className="mt-2 text-center text-xs text-ink-3">Poster from your own URL</p>
  }

  const fetched = movie.fetchedPoster
  const uploaded = fetched?.source === UPLOAD_SOURCE

  const chooseFile = (file: File | undefined) => {
    if (fileInput.current) fileInput.current.value = ''
    if (!file) return
    // the server checks the bytes too; this just gives a quicker message
    if (!ACCEPTED.includes(file.type)) return toast.error('Choose a JPEG, PNG, WebP or GIF image')
    if (file.size > MAX_BYTES) return toast.error('Posters can be up to 5 MB')
    upload.mutate(file, {
      onSuccess: () => toast.success('Poster uploaded'),
      onError: (e) => toast.error(e.message),
    })
  }

  const removePoster = () =>
    remove.mutate(undefined, {
      onSuccess: () =>
        toast.success(uploaded ? 'Poster removed' : 'Poster removed. It won’t be fetched again automatically.'),
      onError: (e) => toast.error(e.message),
    })

  return (
    <div className="mt-2 flex flex-col items-center gap-1.5 text-center text-xs text-ink-3">
      {fetched && (
        <p>
          {uploaded ? (
            'Poster uploaded by you'
          ) : (
            <>
              Poster via{' '}
              {fetched.sourcePageUrl ? (
                <a
                  href={fetched.sourcePageUrl}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center gap-0.5 underline hover:text-ink"
                >
                  {fetched.source}
                  <ExternalLink className="size-3" aria-hidden />
                </a>
              ) : (
                fetched.source
              )}
              {fetched.matchedTitle && fetched.matchedTitle !== movie.title && <> · “{fetched.matchedTitle}”</>}
            </>
          )}
        </p>
      )}

      <div className="flex flex-wrap justify-center gap-x-3 gap-y-1">
        <input
          ref={fileInput}
          type="file"
          accept={ACCEPTED.join(',')}
          className="sr-only"
          tabIndex={-1}
          aria-label="Poster image file"
          onChange={(e) => chooseFile(e.target.files?.[0])}
        />
        <Button
          size="sm"
          variant="ghost"
          icon={<ImageUp className="size-4" />}
          loading={upload.isPending}
          onClick={() => fileInput.current?.click()}
        >
          {fetched ? 'Replace image' : 'Upload poster'}
        </Button>
        {!fetched && status.data?.enabled && (
          <Button
            size="sm"
            variant="ghost"
            icon={<ImageDown className="size-4" />}
            loading={lookup.isPending}
            onClick={() =>
              lookup.mutate(undefined, {
                onSuccess: (r) =>
                  r.outcome === 'FOUND'
                    ? toast.success('Poster found')
                    : r.outcome === 'FAILED'
                      ? toast.error('The lookup failed. Check your connection and try again.')
                      : toast.error('No confident match found. You can upload a poster instead.'),
                onError: (e) => toast.error(e.message),
              })
            }
          >
            Find poster
          </Button>
        )}
      </div>

      {fetched && (
        <button
          type="button"
          className="text-ink-3 underline hover:text-ink disabled:opacity-50"
          disabled={remove.isPending}
          onClick={removePoster}
        >
          {uploaded ? 'Remove poster' : 'Wrong poster? Remove it'}
        </button>
      )}
    </div>
  )
}
