import { useEffect, useState } from 'react'

export function useDebouncedValue<T>(value: T, delayMs = 300): T {
  const [debounced, setDebounced] = useState(value)
  useEffect(() => {
    const timer = window.setTimeout(() => setDebounced(value), delayMs)
    return () => window.clearTimeout(timer)
  }, [value, delayMs])
  return debounced
}

/** Reads a preference from localStorage; storage can be unavailable, so failures fall back. */
export function useStoredPreference<T extends string>(key: string, fallback: T, allowed: readonly T[]) {
  const [value, setValue] = useState<T>(() => {
    try {
      const stored = window.localStorage.getItem(key)
      return stored && (allowed as readonly string[]).includes(stored) ? (stored as T) : fallback
    } catch {
      return fallback
    }
  })
  const update = (next: T) => {
    setValue(next)
    try {
      window.localStorage.setItem(key, next)
    } catch {
      // preference simply won't persist
    }
  }
  return [value, update] as const
}
