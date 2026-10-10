import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { ViewingSession } from '../../api/types'
import { detail, mockApi, on, referenceHandlers, renderRoutes } from '../../test/utils'
import { MovieDetailPage } from './MovieDetailPage'

const routes = [
  { path: '/movies/:id', element: <MovieDetailPage /> },
  { path: '/movies', element: <p>Library page</p> },
]

const viewing: ViewingSession = {
  id: 11,
  movieId: 1,
  movieTitle: 'Midnight Signal',
  watchedOn: '2026-07-01',
  watchedAtTime: null,
  method: 'CINEMA',
  platform: null,
  venue: { id: 3, name: 'Broadway' },
  location: null,
  rating: null,
  ratingBeyondScale: false,
  notes: 'Packed hall',
  imported: true,
  createdAt: '2026-07-01T10:00:00Z',
  updatedAt: '2026-07-01T10:00:00Z',
}

describe('MovieDetailPage', () => {
  it('shows the movie, its viewings and where it came from', async () => {
    mockApi([
      on('GET', '/api/v1/movies/1', () => ({
        body: detail({ viewings: [viewing], importedFrom: [{ sheet: 'Watch_Sheet', rowNumber: 7, originalValues: { Rating: 'Infinity' } }] }),
      })),
      ...referenceHandlers,
    ])
    renderRoutes(routes, '/movies/1')

    expect(await screen.findByRole('heading', { name: 'Midnight Signal', level: 1 })).toBeInTheDocument()
    expect(screen.getByText('Tense and smart.')).toBeInTheDocument()
    expect(screen.getByText('1 viewing')).toBeInTheDocument()
    expect(screen.getByText('Packed hall')).toBeInTheDocument()
    expect(screen.getByText('Watch_Sheet, row 7')).toBeInTheDocument()
  })

  it('logs a new viewing through the dialog', async () => {
    const requests = mockApi([
      on('GET', '/api/v1/movies/1', () => ({ body: detail() })),
      on('POST', '/api/v1/movies/1/viewings', () => ({ status: 201, body: viewing })),
      ...referenceHandlers,
    ])
    renderRoutes(routes, '/movies/1')
    await userEvent.click(await screen.findByRole('button', { name: 'Log a viewing' }))

    const dialog = await screen.findByRole('dialog', { name: 'Log a viewing' })
    await userEvent.clear(within(dialog).getByLabelText(/Watched on/))
    await userEvent.type(within(dialog).getByLabelText(/Watched on/), '2026-03-21')
    await userEvent.selectOptions(within(dialog).getByLabelText(/^How/), 'STREAMING')
    await userEvent.type(within(dialog).getByLabelText(/Platform or service/), 'Netflix')
    await userEvent.click(within(dialog).getByLabelText('9 out of 10'))
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add viewing' }))

    await waitFor(() => expect(requests.some((r) => r.method === 'POST')).toBe(true))
    expect(requests.find((r) => r.method === 'POST')?.body).toMatchObject({
      watchedOn: '2026-03-21',
      method: 'STREAMING',
      platformName: 'Netflix',
      rating: 9,
    })
    expect(await screen.findByText('Viewing added')).toBeInTheDocument()
  })

  it('rejects a time without a date before calling the API', async () => {
    const requests = mockApi([on('GET', '/api/v1/movies/1', () => ({ body: detail() })), ...referenceHandlers])
    renderRoutes(routes, '/movies/1')
    await userEvent.click(await screen.findByRole('button', { name: 'Log a viewing' }))

    const dialog = await screen.findByRole('dialog', { name: 'Log a viewing' })
    await userEvent.clear(within(dialog).getByLabelText(/Watched on/))
    await userEvent.type(within(dialog).getByLabelText(/^Time/), '21:30')
    await userEvent.click(within(dialog).getByRole('button', { name: 'Add viewing' }))

    expect(await within(dialog).findByText('A time needs a date')).toBeInTheDocument()
    expect(requests.some((r) => r.method === 'POST')).toBe(false)
  })

  it('deletes a single viewing after confirmation, keeping the movie', async () => {
    const requests = mockApi([
      on('GET', '/api/v1/movies/1', () => ({ body: detail({ viewings: [viewing] }) })),
      on('DELETE', '/api/v1/viewings/11', () => ({ status: 204 })),
      ...referenceHandlers,
    ])
    const { router } = renderRoutes(routes, '/movies/1')

    await userEvent.click(await screen.findByRole('button', { name: 'Delete viewing' }))
    const confirm = await screen.findByRole('dialog', { name: 'Delete this viewing?' })
    expect(within(confirm).getByText(/and its other viewings stay/)).toBeInTheDocument()
    await userEvent.click(within(confirm).getByRole('button', { name: 'Delete viewing' }))

    await waitFor(() => expect(requests.some((r) => r.method === 'DELETE')).toBe(true))
    expect(requests.some((r) => r.method === 'DELETE' && r.url.pathname === '/api/v1/movies/1')).toBe(false)
    expect(router.state.location.pathname).toBe('/movies/1')
  })

  it('deletes the movie only after confirming', async () => {
    const requests = mockApi([
      on('GET', '/api/v1/movies/1', () => ({ body: detail({ viewings: [viewing] }) })),
      on('DELETE', '/api/v1/movies/1', () => ({ status: 204 })),
      ...referenceHandlers,
    ])
    const { router } = renderRoutes(routes, '/movies/1')

    await userEvent.click(await screen.findByRole('button', { name: 'Delete' }))
    const confirm = await screen.findByRole('dialog', { name: /Delete “Midnight Signal”/ })
    await userEvent.click(within(confirm).getByRole('button', { name: 'Cancel' }))
    expect(requests.some((r) => r.method === 'DELETE')).toBe(false)

    await userEvent.click(screen.getByRole('button', { name: 'Delete' }))
    await userEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: 'Delete movie' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/movies'))
  })

  it('shows a not-found state for a missing movie', async () => {
    mockApi([on('GET', '/api/v1/movies/99', () => ({ status: 404, body: { title: 'Not found', detail: 'Movie 99 was not found' } }))])
    renderRoutes(routes, '/movies/99')

    expect(await screen.findByText('Movie not found')).toBeInTheDocument()
  })
})
