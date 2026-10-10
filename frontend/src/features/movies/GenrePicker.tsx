import { X } from 'lucide-react'
import { useId, useState, type KeyboardEvent } from 'react'
import { Input } from '../../components/ui/Field'

interface GenrePickerProps {
  value: string[]
  onChange: (genres: string[]) => void
  suggestions: string[]
  inputId: string
  describedBy?: string | undefined
}

/** Pick existing genres or type a new one; Enter or comma adds, Backspace removes the last. */
export function GenrePicker({ value, onChange, suggestions, inputId, describedBy }: GenrePickerProps) {
  const [draft, setDraft] = useState('')
  const listId = useId()

  const add = (raw: string) => {
    const name = raw.trim().replace(/\s+/g, ' ')
    if (!name) return
    const exists = value.some((g) => g.toLowerCase() === name.toLowerCase())
    const canonical = suggestions.find((s) => s.toLowerCase() === name.toLowerCase()) ?? name
    if (!exists) onChange([...value, canonical])
    setDraft('')
  }

  const onKeyDown = (event: KeyboardEvent<HTMLInputElement>) => {
    if (event.key === 'Enter' || event.key === ',') {
      event.preventDefault()
      add(draft)
    } else if (event.key === 'Backspace' && draft === '' && value.length > 0) {
      onChange(value.slice(0, -1))
    }
  }

  return (
    <div>
      {value.length > 0 && (
        <ul className="mb-2 flex flex-wrap gap-1.5" aria-label="Selected genres">
          {value.map((genre) => (
            <li key={genre} className="inline-flex items-center gap-1 rounded-full bg-surface-3 py-1 pr-1 pl-3 text-xs text-ink">
              {genre}
              <button
                type="button"
                onClick={() => onChange(value.filter((g) => g !== genre))}
                aria-label={`Remove ${genre}`}
                className="rounded-full p-0.5 text-ink-3 hover:bg-surface-2 hover:text-ink"
              >
                <X className="size-3" />
              </button>
            </li>
          ))}
        </ul>
      )}
      <Input
        id={inputId}
        list={listId}
        value={draft}
        placeholder="Type a genre and press Enter"
        onChange={(e) => {
          // choosing from the datalist fills the whole value in one go
          const next = e.target.value
          if (suggestions.includes(next)) add(next)
          else setDraft(next)
        }}
        onKeyDown={onKeyDown}
        onBlur={() => add(draft)}
        aria-describedby={describedBy}
        autoComplete="off"
      />
      <datalist id={listId}>
        {suggestions
          .filter((s) => !value.includes(s))
          .map((s) => (
            <option key={s} value={s} />
          ))}
      </datalist>
    </div>
  )
}
