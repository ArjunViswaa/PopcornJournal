import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import type { Dashboard } from '../../api/types'
import { mockApi, on, renderWithProviders } from '../../test/utils'
import { DashboardPage } from './DashboardPage'

function dashboard(overrides: Partial<Dashboard> = {}): Dashboard {
  return {
    today: '2026-07-15',
    totalMovies: 7,
    totalViewings: 8,
    datedViewings: 3,
    undatedViewings: 5,
    moviesThisYear: 2,
    viewingsThisYear: 3,
    moviesThisMonth: 1,
    viewingsThisMonth: 2,
    rating: { average: 8, ratedCount: 5, beyondScaleCount: 1, unratedCount: 1 },
    topRated: [{ id: 3, title: 'Paper Kites', releaseYear: 2019, language: 'Malayalam', rating: null, ratingBeyondScale: true, lastWatchedOn: null }],
    recentlyWatched: [
      { sessionId: 1, movieId: 1, title: 'Midnight Signal', watchedOn: '2026-07-10', method: 'STREAMING', platform: 'Netflix', venue: null, rating: 9, ratingBeyondScale: false },
    ],
    platforms: [{ label: 'Netflix', count: 2 }],
    languages: [{ label: 'English', count: 3 }],
    genreCategories: [],
    releaseDecades: [],
    ratingDistribution: [],
    timeline: [],
    ...overrides,
  }
}

describe('DashboardPage', () => {
  it('separates unique titles from viewing sessions and explains undated viewings', async () => {
    mockApi([on('GET', '/api/v1/stats/dashboard', () => ({ body: dashboard() }))])
    renderWithProviders(<DashboardPage />)

    expect(await screen.findByText('Unique titles watched')).toBeInTheDocument()
    expect(screen.getByText('7')).toBeInTheDocument()
    expect(screen.getByText(/8 viewing sessions · 3 with a recorded date/)).toBeInTheDocument()
    expect(screen.getByText(/5 viewings have no recorded date/)).toBeInTheDocument()
    expect(screen.getByText(/1 ∞ \(not averaged\)/)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: /Midnight Signal/ })).toHaveAttribute('href', '/movies/1')
  })

  it('shows empty-chart messages instead of zero bars when data is missing', async () => {
    mockApi([on('GET', '/api/v1/stats/dashboard', () => ({ body: dashboard() }))])
    renderWithProviders(<DashboardPage />)

    expect(await screen.findByText('No viewings have a date yet.')).toBeInTheDocument()
    expect(screen.getByText('No titles record a release year.')).toBeInTheDocument()
  })

  it('shows "—" for the average when nothing is rated, not 0', async () => {
    mockApi([
      on('GET', '/api/v1/stats/dashboard', () => ({
        body: dashboard({ rating: { average: null, ratedCount: 0, beyondScaleCount: 0, unratedCount: 7 } }),
      })),
    ])
    renderWithProviders(<DashboardPage />)

    expect(await screen.findByText('—')).toBeInTheDocument()
  })

  it('invites an import when the journal is empty', async () => {
    mockApi([on('GET', '/api/v1/stats/dashboard', () => ({ body: dashboard({ totalMovies: 0 }) }))])
    renderWithProviders(<DashboardPage />)

    expect(await screen.findByText('Nothing in the journal yet')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Import your spreadsheet' })).toBeInTheDocument()
  })
})
