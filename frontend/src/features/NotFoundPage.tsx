import { Film } from 'lucide-react'
import { ButtonLink } from '../components/ui/Button'
import { EmptyState } from '../components/ui/States'

export function NotFoundPage() {
  return (
    <EmptyState
      icon={Film}
      title="This reel is missing"
      action={<ButtonLink to="/">Back to the dashboard</ButtonLink>}
    >
      The page you asked for doesn't exist.
    </EmptyState>
  )
}
