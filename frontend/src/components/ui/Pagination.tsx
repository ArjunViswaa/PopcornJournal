import { ChevronLeft, ChevronRight } from 'lucide-react'
import { Button } from './Button'

interface PaginationProps {
  page: number
  totalPages: number
  totalElements: number
  size: number
  onPage: (page: number) => void
}

export function Pagination({ page, totalPages, totalElements, size, onPage }: PaginationProps) {
  if (totalPages <= 1) return null
  const from = page * size + 1
  const to = Math.min(totalElements, (page + 1) * size)
  return (
    <nav aria-label="Pagination" className="flex items-center justify-between gap-4 pt-2">
      <p className="num text-sm text-ink-3">
        {from}–{to} of {totalElements}
      </p>
      <div className="flex items-center gap-2">
        <Button size="sm" onClick={() => onPage(page - 1)} disabled={page === 0} icon={<ChevronLeft className="size-4" />}>
          Previous
        </Button>
        <span className="num text-sm text-ink-2" aria-current="page">
          {page + 1} / {totalPages}
        </span>
        <Button size="sm" onClick={() => onPage(page + 1)} disabled={page >= totalPages - 1}>
          Next
          <ChevronRight className="size-4" />
        </Button>
      </div>
    </nav>
  )
}
