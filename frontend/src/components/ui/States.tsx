import { AlertTriangle, type LucideIcon } from 'lucide-react'
import type { ReactNode } from 'react'
import { Button } from './Button'

export function Skeleton({ className = '' }: { className?: string }) {
  return <div aria-hidden className={`animate-pulse rounded-lg bg-surface-2 ${className}`} />
}

export function EmptyState({
  icon: Icon,
  title,
  children,
  action,
}: {
  icon: LucideIcon
  title: string
  children?: ReactNode
  action?: ReactNode
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-3 rounded-2xl border border-dashed border-line-strong px-6 py-14 text-center">
      <div className="flex size-12 items-center justify-center rounded-full bg-surface-2">
        <Icon className="size-5 text-ink-3" aria-hidden />
      </div>
      <h3 className="font-display text-lg font-semibold">{title}</h3>
      {children && <div className="max-w-md text-sm text-ink-3">{children}</div>}
      {action}
    </div>
  )
}

export function ErrorState({ error, onRetry }: { error: unknown; onRetry?: () => void }) {
  const message = error instanceof Error ? error.message : 'Something went wrong.'
  return (
    <div
      role="alert"
      className="flex flex-col items-center gap-3 rounded-2xl border border-danger/30 bg-danger-wash px-6 py-10 text-center"
    >
      <AlertTriangle className="size-6 text-danger" aria-hidden />
      <p className="font-medium text-ink">Couldn't load this</p>
      <p className="max-w-md text-sm text-ink-2">{message}</p>
      {onRetry && (
        <Button size="sm" onClick={onRetry}>
          Try again
        </Button>
      )}
    </div>
  )
}

export function Chip({ children, tone = 'neutral' }: { children: ReactNode; tone?: 'neutral' | 'accent' }) {
  const style = tone === 'accent' ? 'bg-accent-wash text-accent' : 'bg-surface-2 text-ink-2'
  return (
    <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium ${style}`}>
      {children}
    </span>
  )
}
