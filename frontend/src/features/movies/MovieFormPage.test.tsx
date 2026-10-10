import { screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, it } from 'vitest'
import type { MovieInput } from '../../api/types'
import { detail, mockApi, on, referenceHandlers, renderRoutes } from '../../test/utils'
import { MovieFormPage } from './MovieFormPage'
import { validateMovie } from './movieFormModel'
import { EMPTY_MOVIE } from './movieFormModel'

const routes = [
  { path: '/movies/new', element: <MovieFormPage /> },
  { path: '/movies/:id/edit', element: <MovieFormPage /> },
  { path: '/movies/:id', element: <p>Detail page</p> },
]

describe('validateMovie', () => {
  it('requires only a title', () => {
    expect(validateMovie({ ...EMPTY_MOVIE, title: 'Ok' })).toEqual({})
    expect(validateMovie(EMPTY_MOVIE)).toHaveProperty('title')
  })

  it('checks ranges and the poster URL', () => {
    const errors = validateMovie({ ...EMPTY_MOVIE, title: 'X', releaseYear: 1700, runtimeMinutes: 0, posterUrl: 'ftp://x' })
    expect(Object.keys(errors).sort()).toEqual(['posterUrl', 'releaseYear', 'runtimeMinutes'])
  })
})

describe('Adding a movie', () => {
  it('shows field errors without calling the API', async () => {
    const requests = mockApi(referenceHandlers)
    renderRoutes(routes, '/movies/new')

    await userEvent.type(screen.getByLabelText(/Release year/), '1700')
    await userEvent.click(screen.getByRole('button', { name: 'Add to journal' }))

    expect(await screen.findByText('Title is required')).toBeInTheDocument()
    expect(screen.getByText('Enter a year between 1870 and 2100')).toBeInTheDocument()
    expect(requests.some((r) => r.method === 'POST')).toBe(false)
  })

  it('creates a movie without a rating or review, with a first viewing', async () => {
    const requests = mockApi([
      on('POST', '/api/v1/movies', () => ({ status: 201, body: detail({ id: 42, title: 'Ronth' }) })),
      ...referenceHandlers,
    ])
    const { router } = renderRoutes(routes, '/movies/new')

    await userEvent.type(screen.getByLabelText(/^Title/), 'Ronth')
    await userEvent.type(screen.getByLabelText(/Release year/), '2025')
    await userEvent.type(screen.getByLabelText(/^Genres/), 'Drama - Journey{Enter}')
    await userEvent.click(screen.getByLabelText('Log when and where I watched it'))
    await userEvent.type(screen.getByLabelText(/Platform or service/), 'Jio Hotstar')
    await userEvent.click(screen.getByRole('button', { name: 'Add to journal' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/movies/42'))
    const body = requests.find((r) => r.method === 'POST')?.body as { movie: MovieInput; initialViewing: Record<string, unknown> }
    expect(body.movie).toMatchObject({ title: 'Ronth', releaseYear: 2025, rating: null, review: null, genres: ['Drama - Journey'] })
    expect(body.initialViewing).toMatchObject({ platformName: 'Jio Hotstar' })
  })

  it('warns about a possible duplicate and blocks an exact one', async () => {
    mockApi([
      on('GET', '/api/v1/movies/duplicates', () => ({
        body: [{ id: 5, title: 'Drishyam', format: 'MOVIE', releaseYear: 2013, language: 'Malayalam', exactMatch: true }],
      })),
      ...referenceHandlers,
    ])
    renderRoutes(routes, '/movies/new')

    await userEvent.type(screen.getByLabelText(/^Title/), 'Drishyam')

    expect(await screen.findByText(/already in your journal/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Drishyam' })).toHaveAttribute('href', '/movies/5')
    expect(screen.getByRole('button', { name: 'Add to journal' })).toBeDisabled()
  })

  it('shows a server-side conflict message', async () => {
    mockApi([
      on('POST', '/api/v1/movies', () => ({ status: 409, body: { title: 'Conflict', detail: 'This movie is already in your journal.' } })),
      ...referenceHandlers,
    ])
    renderRoutes(routes, '/movies/new')

    await userEvent.type(screen.getByLabelText(/^Title/), 'Anything')
    await userEvent.click(screen.getByRole('button', { name: 'Add to journal' }))

    expect(await screen.findByText('This movie is already in your journal.')).toBeInTheDocument()
  })
})

describe('Editing a movie', () => {
  it('loads the movie, changes the rating and review, and saves with PUT', async () => {
    const requests = mockApi([
      on('GET', '/api/v1/movies/1', () => ({ body: detail() })),
      on('PUT', '/api/v1/movies/1', (r) => ({ body: detail({ ...(r.body as object) }) })),
      ...referenceHandlers,
    ])
    const { router } = renderRoutes(routes, '/movies/1/edit')

    const review = await screen.findByLabelText(/^Review/)
    expect(review).toHaveValue('Tense and smart.')
    await userEvent.click(screen.getByLabelText('Beyond the scale (∞)'))
    await userEvent.clear(review)
    await userEvent.type(review, 'Even better the second time.')
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))

    await waitFor(() => expect(router.state.location.pathname).toBe('/movies/1'))
    expect(requests.find((r) => r.method === 'PUT')?.body).toMatchObject({
      title: 'Midnight Signal',
      rating: null,
      ratingBeyondScale: true,
      review: 'Even better the second time.',
      genres: ['Thriller - Mystery'],
    })
  })
})
