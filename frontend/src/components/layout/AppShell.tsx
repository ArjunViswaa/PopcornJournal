import { BarChart3, BookOpen, Clapperboard, FileSpreadsheet, Home, ListPlus, Plus } from 'lucide-react'
import { NavLink, Outlet, useLocation } from 'react-router'
import { useEffect, useRef } from 'react'
import { ButtonLink } from '../ui/Button'

const NAV = [
  { to: '/', label: 'Dashboard', icon: Home, end: true },
  { to: '/movies', label: 'Library', icon: Clapperboard, end: false },
  { to: '/diary', label: 'Diary', icon: BookOpen, end: false },
  { to: '/stats', label: 'Statistics', icon: BarChart3, end: false },
  { to: '/watchlist', label: 'Watchlist', icon: ListPlus, end: false },
  { to: '/import', label: 'Import', icon: FileSpreadsheet, end: false },
]

function navClass({ isActive }: { isActive: boolean }) {
  return `flex items-center gap-3 rounded-xl px-3 py-2 text-sm font-medium transition-colors ${
    isActive ? 'bg-surface-2 text-ink' : 'text-ink-3 hover:bg-surface hover:text-ink'
  }`
}

function Brand() {
  return (
    <NavLink to="/" className="flex items-center gap-2.5 rounded-lg">
      <img src="/favicon.svg" alt="" className="size-8" />
      <span className="font-display text-lg font-semibold tracking-tight">
        Popcorn <span className="text-accent">Journal</span>
      </span>
    </NavLink>
  )
}

export function AppShell() {
  const location = useLocation()
  const main = useRef<HTMLElement>(null)

  // move focus to the page on navigation so keyboard and screen-reader users land in the content
  useEffect(() => {
    main.current?.focus({ preventScroll: true })
    window.scrollTo({ top: 0 })
  }, [location.pathname])

  return (
    <div className="min-h-dvh lg:grid lg:grid-cols-[15rem_1fr]">
      <a
        href="#main"
        className="sr-only z-50 rounded-lg bg-accent px-3 py-2 text-accent-ink focus:not-sr-only focus:fixed focus:top-3 focus:left-3"
      >
        Skip to content
      </a>

      <aside className="sticky top-0 hidden h-dvh flex-col gap-6 border-r border-line px-4 py-6 lg:flex">
        <Brand />
        <ButtonLink to="/movies/new" variant="primary" icon={<Plus className="size-4" />} className="w-full">
          Add movie
        </ButtonLink>
        <nav aria-label="Main" className="flex flex-col gap-1">
          {NAV.map(({ to, label, icon: Icon, end }) => (
            <NavLink key={to} to={to} end={end} className={navClass}>
              <Icon className="size-4" aria-hidden />
              {label}
            </NavLink>
          ))}
        </nav>
        <p className="mt-auto text-xs leading-relaxed text-ink-3">
          Stored locally in PostgreSQL. Nothing leaves this machine.
        </p>
      </aside>

      <div className="sticky top-0 z-30 border-b border-line bg-bg/90 backdrop-blur lg:hidden">
        <div className="flex items-center justify-between px-4 py-3">
          <Brand />
          <ButtonLink to="/movies/new" variant="primary" size="sm" icon={<Plus className="size-4" />}>
            Add
          </ButtonLink>
        </div>
        <nav aria-label="Main" className="flex gap-1 overflow-x-auto px-3 pb-2">
          {NAV.map(({ to, label, end }) => (
            <NavLink key={to} to={to} end={end} className={(s) => `${navClass(s)} shrink-0 py-1.5`}>
              {label}
            </NavLink>
          ))}
        </nav>
      </div>

      <main id="main" ref={main} tabIndex={-1} className="min-w-0 px-4 py-6 outline-none sm:px-6 lg:px-10 lg:py-10">
        <div className="mx-auto max-w-6xl">
          <Outlet />
        </div>
      </main>
    </div>
  )
}

export function PageHeader({
  eyebrow,
  title,
  description,
  actions,
}: {
  eyebrow?: string
  title: string
  description?: string
  actions?: React.ReactNode
}) {
  return (
    <header className="mb-8 flex flex-col gap-4 sm:flex-row sm:items-end sm:justify-between">
      <div>
        {eyebrow && <p className="eyebrow mb-2">{eyebrow}</p>}
        <h1 className="font-display text-3xl font-semibold tracking-tight sm:text-4xl">{title}</h1>
        {description && <p className="mt-2 max-w-2xl text-sm text-ink-3">{description}</p>}
      </div>
      {actions && <div className="flex flex-wrap gap-2">{actions}</div>}
    </header>
  )
}
