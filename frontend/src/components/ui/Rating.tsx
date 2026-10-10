import { Infinity as InfinityIcon, Star } from 'lucide-react'
import { useId } from 'react'

interface RatingValue {
  rating: number | null
  ratingBeyondScale: boolean
}

export function RatingBadge({ rating, ratingBeyondScale, size = 'md' }: RatingValue & { size?: 'sm' | 'md' | 'lg' }) {
  const text = { sm: 'text-xs', md: 'text-sm', lg: 'text-2xl' }[size]
  const pad = { sm: 'px-2 py-0.5', md: 'px-2.5 py-1', lg: 'px-4 py-2' }[size]
  if (ratingBeyondScale) {
    return (
      <span
        className={`inline-flex items-center gap-1 rounded-lg bg-accent font-semibold text-accent-ink ${text} ${pad}`}
        title="Beyond the scale"
        aria-label="Rated beyond the 1 to 10 scale"
      >
        <InfinityIcon className={size === 'lg' ? 'size-6' : 'size-3.5'} aria-hidden strokeWidth={2.5} />
      </span>
    )
  }
  if (rating === null) {
    return (
      <span className={`inline-flex items-center rounded-lg bg-surface-2 text-ink-3 ${text} ${pad}`}>Unrated</span>
    )
  }
  return (
    <span
      className={`num inline-flex items-baseline gap-0.5 rounded-lg bg-accent-wash font-semibold text-accent ${text} ${pad}`}
      aria-label={`Rated ${rating} out of 10`}
    >
      <Star className={`${size === 'lg' ? 'size-5' : 'size-3'} self-center fill-current`} aria-hidden />
      {rating}
      <span className="text-[0.75em] font-normal text-ink-3">/10</span>
    </span>
  )
}

interface RatingInputProps extends RatingValue {
  onChange: (value: RatingValue) => void
  legend?: string
}

/**
 * Radio group of 1-10 plus ∞ and "no rating". Built on native radios, so arrow keys
 * and screen-reader announcements work without extra code.
 */
export function RatingInput({ rating, ratingBeyondScale, onChange, legend = 'Rating' }: RatingInputProps) {
  const name = useId()
  const current = ratingBeyondScale ? 'inf' : rating === null ? 'none' : String(rating)
  const options = ['none', ...Array.from({ length: 10 }, (_, i) => String(i + 1)), 'inf']

  return (
    <fieldset>
      <legend className="mb-1.5 text-sm font-medium text-ink-2">{legend}</legend>
      <div className="flex flex-wrap gap-1.5">
        {options.map((option) => {
          const checked = current === option
          const label = option === 'none' ? 'None' : option === 'inf' ? '∞' : option
          return (
            <label
              key={option}
              className={`num relative flex h-9 min-w-9 cursor-pointer items-center justify-center rounded-lg border px-2 text-sm font-medium transition-colors has-[:focus-visible]:outline-2 has-[:focus-visible]:outline-accent ${
                checked
                  ? 'border-accent bg-accent text-accent-ink'
                  : 'border-line-strong bg-surface-2 text-ink-2 hover:border-ink-3 hover:text-ink'
              }`}
            >
              <input
                type="radio"
                name={name}
                value={option}
                checked={checked}
                className="sr-only"
                aria-label={option === 'none' ? 'No rating' : option === 'inf' ? 'Beyond the scale (∞)' : `${option} out of 10`}
                onChange={() =>
                  onChange(
                    option === 'inf'
                      ? { rating: null, ratingBeyondScale: true }
                      : { rating: option === 'none' ? null : Number(option), ratingBeyondScale: false },
                  )
                }
              />
              {label}
            </label>
          )
        })}
      </div>
    </fieldset>
  )
}
