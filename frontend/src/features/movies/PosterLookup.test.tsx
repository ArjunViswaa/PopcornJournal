import { screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import { detail, mockApi, on, page, posterStatus, referenceHandlers, renderRoutes, summary } from '../../test/utils'
import { LibraryPage } from './LibraryPage'
import { MovieDetailPage } from './MovieDetailPage'

describe('Finding posters from the library', () => {
  it('is hidden while the feature is turned off', async () => {
    mockApi([on('GET', '/api/v1/movies', () => ({ body: page([summary()]) })), ...referenceHandlers])
    renderRoutes([{ path: '/movies', element: <LibraryPage /> }], '/movies')

    await screen.findByRole('link', { name: 'Midnight Signal (2024), watched 1 Jul 2026' })
    expect(screen.queryByRole('button', { name: 'Find posters' })).not.toBeInTheDocument()
  })

  it('runs batches until nothing remains and reports misses', async () => {
    let call = 0
    const requests = mockApi([
      on('GET', '/api/v1/posters/status', () => ({ body: posterStatus({ enabled: true, pending: 3 }) })),
      on('POST', '/api/v1/posters/lookup', () => {
        call += 1
        return call === 1
          ? { body: { runStartedAt: '2026-10-10T10:00:00Z', remaining: 1, results: [
              { movieId: 1, title: 'Ronth', outcome: 'FOUND', matchedTitle: 'Ronth' },
              { movieId: 2, title: 'Rare Film', outcome: 'NOT_FOUND', matchedTitle: null },
            ] } }
          : { body: { runStartedAt: '2026-10-10T10:00:00Z', remaining: 0, results: [
              { movieId: 3, title: 'Coolie', outcome: 'FOUND', matchedTitle: 'Coolie (2025 film)' },
            ] } }
      }),
      on('GET', '/api/v1/movies', () => ({ body: page([summary()]) })),
      ...referenceHandlers,
    ])
    renderRoutes([{ path: '/movies', element: <LibraryPage /> }], '/movies')

    await userEvent.click(await screen.findByRole('button', { name: 'Find posters' }))
    const dialog = await screen.findByRole('dialog', { name: 'Find posters' })
    await userEvent.click(within(dialog).getByRole('button', { name: 'Look up 3 titles' }))

    expect(await within(dialog).findByText('Finished: 2 posters found.')).toBeInTheDocument()
    expect(within(dialog).getByText('1 title without a confident match')).toBeInTheDocument()
    const lookups = requests.filter((r) => r.method === 'POST')
    expect(lookups).toHaveLength(2)
    // the second batch continues the same run
    expect(lookups[1]?.url.searchParams.get('runStartedAt')).toBe('2026-10-10T10:00:00Z')
  })
})

describe('Poster on the movie page', () => {
  const routes = [{ path: '/movies/:id', element: <MovieDetailPage /> }]

  it('credits a fetched poster and can remove it', async () => {
    const requests = mockApi([
      on('GET', '/api/v1/movies/1', () => ({
        body: detail({
          fetchedPoster: {
            url: '/api/v1/movies/1/poster?v=1',
            source: 'Wikipedia',
            sourcePageUrl: 'https://en.wikipedia.org/wiki/Midnight_Signal_(film)',
            matchedTitle: 'Midnight Signal (film)',
            fetchedAt: '2026-10-10T10:00:00Z',
          },
        }),
      })),
      on('DELETE', '/api/v1/movies/1/poster', () => ({ status: 204 })),
      ...referenceHandlers,
    ])
    renderRoutes(routes, '/movies/1')

    expect(await screen.findByRole('img', { name: 'Poster for Midnight Signal' })).toHaveAttribute(
      'src',
      '/api/v1/movies/1/poster?v=1',
    )
    expect(screen.getByRole('link', { name: /Wikipedia/ })).toHaveAttribute('rel', 'noopener noreferrer')
    await userEvent.click(screen.getByRole('button', { name: 'Wrong poster? Remove it' }))
    await waitFor(() => expect(requests.some((r) => r.method === 'DELETE')).toBe(true))
  })

  it('uploads a chosen image and rejects non-images before sending', async () => {
    const requests = mockApi([
      on('GET', '/api/v1/movies/1', () => ({ body: detail() })),
      on('POST', '/api/v1/movies/1/poster', () => ({ status: 204 })),
      ...referenceHandlers,
    ])
    renderRoutes(routes, '/movies/1')
    const input = await screen.findByLabelText('Poster image file')

    await userEvent.upload(input, new File(['hello'], 'notes.txt', { type: 'text/plain' }), { applyAccept: false })
    expect(await screen.findByText('Choose a JPEG, PNG, WebP or GIF image')).toBeInTheDocument()
    expect(requests.some((r) => r.method === 'POST')).toBe(false)

    await userEvent.upload(input, new File(['png-bytes'], 'rythmn.png', { type: 'image/png' }))
    expect(await screen.findByText('Poster uploaded')).toBeInTheDocument()
    expect(requests.some((r) => r.method === 'POST' && r.url.pathname === '/api/v1/movies/1/poster')).toBe(true)
  })

  it('labels an uploaded poster as yours', async () => {
    mockApi([
      on('GET', '/api/v1/movies/1', () => ({
        body: detail({
          fetchedPoster: { url: '/api/v1/movies/1/poster?v=2', source: 'Upload', sourcePageUrl: null, matchedTitle: 'mine.png', fetchedAt: '2026-10-10T10:00:00Z' },
        }),
      })),
      ...referenceHandlers,
    ])
    renderRoutes(routes, '/movies/1')

    expect(await screen.findByText('Poster uploaded by you')).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Replace image' })).toBeInTheDocument()
    expect(screen.getByRole('button', { name: 'Remove poster' })).toBeInTheDocument()
  })

  it('prefers the poster URL the user entered', async () => {
    mockApi([
      on('GET', '/api/v1/movies/1', () => ({
        body: detail({
          posterUrl: 'https://example.org/mine.jpg',
          fetchedPoster: { url: '/api/v1/movies/1/poster?v=1', source: 'Wikipedia', sourcePageUrl: null, matchedTitle: null, fetchedAt: '2026-10-10T10:00:00Z' },
        }),
      })),
      ...referenceHandlers,
    ])
    renderRoutes(routes, '/movies/1')

    expect(await screen.findByRole('img', { name: 'Poster for Midnight Signal' })).toHaveAttribute('src', 'https://example.org/mine.jpg')
    expect(screen.getByText('Poster from your own URL')).toBeInTheDocument()
  })
})
