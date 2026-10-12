import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { mockApi, on, page, referenceHandlers, renderWithProviders, summary } from '../../test/utils'
import { LibraryPage } from './LibraryPage'

describe('LibraryPage', () => {
  it('shows a skeleton, then the movies from the API', async () => {
    mockApi([
      on('GET', '/api/v1/movies', () => ({ body: page([summary(), summary({ id: 2, title: 'Harbour Lights', rating: null })]) })),
      ...referenceHandlers,
    ])
    renderWithProviders(<LibraryPage />, '/movies')

    expect(screen.getByLabelText('Loading movies')).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: 'Midnight Signal (2024), watched 1 Jul 2026' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Harbour Lights (2024), watched 1 Jul 2026' })).toBeInTheDocument()
    expect(screen.getByText('2 titles match')).toBeInTheDocument()
  })

  it('tags each poster with its latest watch date, and only when one is recorded', async () => {
    mockApi([
      on('GET', '/api/v1/movies', () => ({
        body: page([summary(), summary({ id: 2, title: 'Undated Film', lastWatchedOn: null })]),
      })),
      ...referenceHandlers,
    ])
    renderWithProviders(<LibraryPage />, '/movies')

    const dated = await screen.findByRole('link', { name: 'Midnight Signal (2024), watched 1 Jul 2026' })
    expect(within(dated).getByText('1 Jul 2026')).toBeInTheDocument()
    const undated = screen.getByRole('link', { name: 'Undated Film (2024)' })
    expect(within(undated).queryByText(/2026/)).not.toBeInTheDocument()
  })

  it('sends the search text and filters to the backend', async () => {
    const requests = mockApi([on('GET', '/api/v1/movies', () => ({ body: page([summary()]) })), ...referenceHandlers])
    renderWithProviders(<LibraryPage />, '/movies')
    await screen.findByRole('link', { name: 'Midnight Signal (2024), watched 1 Jul 2026' })

    await userEvent.type(screen.getByLabelText('Search by title'), 'signal')
    await waitFor(() =>
      expect(requests.some((r) => r.url.pathname === '/api/v1/movies' && r.url.searchParams.get('q') === 'signal')).toBe(true),
    )

    await userEvent.selectOptions(screen.getByLabelText('Minimum rating'), '8')
    await userEvent.selectOptions(screen.getByLabelText('Sort by'), 'HIGHEST_RATED')
    await waitFor(() => {
      const last = requests.filter((r) => r.url.pathname === '/api/v1/movies').at(-1)
      expect(last?.url.searchParams.get('minRating')).toBe('8')
      expect(last?.url.searchParams.get('sort')).toBe('HIGHEST_RATED')
      expect(last?.url.searchParams.get('q')).toBe('signal')
    })
  })

  it('defaults to most recently watched without putting it in the URL', async () => {
    const requests = mockApi([on('GET', '/api/v1/movies', () => ({ body: page([summary()]) })), ...referenceHandlers])
    const { router } = renderWithProviders(<LibraryPage />, '/movies')
    await screen.findByRole('link', { name: 'Midnight Signal (2024), watched 1 Jul 2026' })

    expect(screen.getByLabelText('Sort by')).toHaveValue('LATEST_WATCHED')
    expect(requests.find((r) => r.url.pathname === '/api/v1/movies')?.url.searchParams.has('sort')).toBe(false)

    await userEvent.selectOptions(screen.getByLabelText('Sort by'), 'TITLE')
    await waitFor(() => expect(router.state.location.search).toContain('sort=TITLE'))
    await userEvent.selectOptions(screen.getByLabelText('Sort by'), 'LATEST_WATCHED')
    await waitFor(() => expect(router.state.location.search).not.toContain('sort='))
  })

  it('switches personal lists through the tabs', async () => {
    const requests = mockApi([on('GET', '/api/v1/movies', () => ({ body: page([summary()]) })), ...referenceHandlers])
    renderWithProviders(<LibraryPage />, '/movies')
    await screen.findByRole('link', { name: 'Midnight Signal (2024), watched 1 Jul 2026' })

    await userEvent.click(screen.getByRole('tab', { name: 'Unrated' }))

    await waitFor(() => expect(requests.at(-1)?.url.searchParams.get('list')).toBe('UNRATED'))
    expect(screen.getByRole('tab', { name: 'Unrated' })).toHaveAttribute('aria-selected', 'true')
  })

  it('explains an empty filtered result', async () => {
    mockApi([on('GET', '/api/v1/movies', () => ({ body: page([]) })), ...referenceHandlers])
    renderWithProviders(<LibraryPage />, '/movies?q=nothing')

    expect(await screen.findByText('No titles match')).toBeInTheDocument()
  })

  it('shows the server error and lets you retry', async () => {
    mockApi([
      on('GET', '/api/v1/movies', () => ({ status: 500, body: { title: 'Unexpected error', detail: 'Database unavailable' } })),
      ...referenceHandlers,
    ])
    renderWithProviders(<LibraryPage />, '/movies')

    const alert = await screen.findByRole('alert')
    expect(within(alert).getByText('Database unavailable')).toBeInTheDocument()
    expect(within(alert).getByRole('button', { name: 'Try again' })).toBeInTheDocument()
  })

  it('switches between grid and list layouts', async () => {
    mockApi([on('GET', '/api/v1/movies', () => ({ body: page([summary({ platforms: ['Netflix'] })]) })), ...referenceHandlers])
    renderWithProviders(<LibraryPage />, '/movies')
    await screen.findByRole('link', { name: 'Midnight Signal (2024), watched 1 Jul 2026' })

    await userEvent.click(screen.getByRole('button', { name: 'List view' }))

    expect(screen.getByRole('button', { name: 'List view' })).toHaveAttribute('aria-pressed', 'true')
    expect(screen.getByText('Netflix', { selector: 'p' })).toBeInTheDocument()
  })
})
