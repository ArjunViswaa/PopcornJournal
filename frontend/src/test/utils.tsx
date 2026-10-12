import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import type { ReactNode } from 'react'
import { createMemoryRouter, RouterProvider, type RouteObject } from 'react-router'
import { vi } from 'vitest'
import { ToastProvider } from '../components/ui/Toast'
import type { MovieDetail, MovieSummary, Page, PosterStatus } from '../api/types'

export interface RecordedRequest {
  method: string
  url: URL
  body: unknown
}

type Handler = (request: RecordedRequest) => { status?: number; body?: unknown } | undefined

/**
 * Replaces fetch with a tiny router: the first handler that returns a response wins.
 * Unhandled requests fail loudly so a test can't silently pass on a missing mock.
 */
export function mockApi(handlers: Handler[]) {
  const requests: RecordedRequest[] = []
  vi.spyOn(globalThis, 'fetch').mockImplementation(async (input, init) => {
    const url = new URL(String(input), 'http://localhost')
    const method = init?.method ?? 'GET'
    let body: unknown = undefined
    if (typeof init?.body === 'string') body = JSON.parse(init.body)
    const request = { method, url, body }
    requests.push(request)
    for (const handler of handlers) {
      const result = handler(request)
      if (result) {
        const status = result.status ?? 200
        return new Response(status === 204 ? null : JSON.stringify(result.body ?? {}), {
          status,
          headers: { 'Content-Type': 'application/json' },
        })
      }
    }
    throw new Error(`Unmocked request: ${method} ${url.pathname}${url.search}`)
  })
  return requests
}

export const on =
  (method: string, path: string | RegExp, respond: (r: RecordedRequest) => { status?: number; body?: unknown }): Handler =>
  (request) => {
    const matches = typeof path === 'string' ? request.url.pathname === path : path.test(request.url.pathname)
    return request.method === method && matches ? respond(request) : undefined
  }

export function renderRoutes(routes: RouteObject[], initialPath: string) {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false }, mutations: { retry: false } } })
  const router = createMemoryRouter(routes, { initialEntries: [initialPath] })
  const utils = render(
    <QueryClientProvider client={client}>
      <ToastProvider>
        <RouterProvider router={router} />
      </ToastProvider>
    </QueryClientProvider>,
  )
  return { ...utils, router, client }
}

export function renderWithProviders(ui: ReactNode, path = '/') {
  return renderRoutes([{ path: '*', element: ui }], path)
}

export function page<T>(content: T[], overrides: Partial<Page<T>> = {}): Page<T> {
  return {
    content,
    page: 0,
    size: 24,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    first: true,
    last: true,
    ...overrides,
  }
}

export function summary(overrides: Partial<MovieSummary> = {}): MovieSummary {
  return {
    id: 1,
    title: 'Midnight Signal',
    format: 'MOVIE',
    releaseYear: 2024,
    language: 'English',
    genres: ['Thriller - Mystery'],
    rating: 8,
    ratingBeyondScale: false,
    favourite: false,
    hasReview: true,
    posterUrl: null,
    viewingCount: 1,
    lastWatchedOn: '2026-07-01',
    platforms: ['Netflix'],
    venues: [],
    ...overrides,
  }
}

export function detail(overrides: Partial<MovieDetail> = {}): MovieDetail {
  return {
    id: 1,
    title: 'Midnight Signal',
    originalTitle: null,
    format: 'MOVIE',
    releaseYear: 2024,
    language: 'English',
    synopsis: 'A radio host hears a voice.',
    rating: 8,
    ratingBeyondScale: false,
    review: 'Tense and smart.',
    privateNotes: null,
    favourite: false,
    director: null,
    castMembers: null,
    runtimeMinutes: null,
    posterUrl: null,
    fetchedPoster: null,
    metadataSource: null,
    genres: [{ id: 1, name: 'Thriller - Mystery', category: 'Thriller' }],
    viewings: [],
    importedFrom: [],
    createdAt: '2026-07-01T10:00:00Z',
    updatedAt: '2026-07-01T10:00:00Z',
    ...overrides,
  }
}

/** Reference-data endpoints most screens touch; tests can put specific handlers first. */
export const referenceHandlers: Handler[] = [
  on('GET', '/api/v1/genres', () => ({ body: [{ id: 1, name: 'Thriller - Mystery', category: 'Thriller', movieCount: 3 }] })),
  on('GET', '/api/v1/platforms', () => ({ body: [{ id: 7, name: 'Netflix', defaultMethod: 'STREAMING', viewingCount: 4 }] })),
  on('GET', '/api/v1/venues', () => ({ body: [] })),
  on('GET', '/api/v1/languages', () => ({ body: ['English', 'Tamil'] })),
  on('GET', '/api/v1/movies/duplicates', () => ({ body: [] })),
  on('GET', '/api/v1/posters/status', () => ({ body: posterStatus() })),
]

export function posterStatus(overrides: Partial<PosterStatus> = {}): PosterStatus {
  return {
    enabled: false,
    source: 'Wikipedia',
    totalMovies: 3,
    withOwnPoster: 0,
    withFetchedPoster: 0,
    pending: 3,
    notFound: 0,
    rejected: 0,
    ...overrides,
  }
}
