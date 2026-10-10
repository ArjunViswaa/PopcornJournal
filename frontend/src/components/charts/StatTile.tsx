import type { ReactNode } from 'react'

export function StatTile({
  label,
  value,
  note,
  hero = false,
}: {
  label: string
  value: ReactNode
  note?: ReactNode
  hero?: boolean
}) {
  return (
    <div className={`card flex flex-col gap-1 p-5 ${hero ? 'sm:col-span-2' : ''}`}>
      <p className="text-sm text-ink-3">{label}</p>
      <p className={`num font-semibold tracking-tight text-ink ${hero ? 'text-5xl' : 'text-3xl'}`}>{value}</p>
      {note && <p className="text-xs text-ink-3">{note}</p>}
    </div>
  )
}
