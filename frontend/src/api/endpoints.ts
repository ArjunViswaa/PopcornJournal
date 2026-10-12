import { api, buildQuery } from './client'
import type {
  Dashboard,
  DuplicateCandidate,
  GenreSummary,
  ImportReport,
  ImportRun,
  PosterBatch,
  PosterLookupResult,
  PosterStatus,
  Insights,
  MovieDetail,
  MovieFormat,
  MovieInput,
  MovieQuery,
  MovieSummary,
  Page,
  PlatformSummary,
  VenueSummary,
  ViewingInput,
  ViewingSession,
  WatchlistInput,
  WatchlistItem,
} from './types'

export const moviesApi = {
  search: (query: MovieQuery) => api.get<Page<MovieSummary>>(`/api/v1/movies${buildQuery({ ...query })}`),
  get: (id: number) => api.get<MovieDetail>(`/api/v1/movies/${id}`),
  create: (movie: MovieInput, initialViewing: ViewingInput | null) =>
    api.post<MovieDetail>('/api/v1/movies', { movie, initialViewing }),
  update: (id: number, movie: MovieInput) => api.put<MovieDetail>(`/api/v1/movies/${id}`, movie),
  setFavourite: (id: number, favourite: boolean) =>
    api.patch<MovieDetail>(`/api/v1/movies/${id}/favourite`, { favourite }),
  remove: (id: number) => api.delete(`/api/v1/movies/${id}`),
  duplicates: (params: { title: string; format?: MovieFormat; releaseYear?: number | null; language?: string | null; excludeId?: number }) =>
    api.get<DuplicateCandidate[]>(`/api/v1/movies/duplicates${buildQuery(params)}`),
}

export const viewingsApi = {
  forMovie: (movieId: number) => api.get<ViewingSession[]>(`/api/v1/movies/${movieId}/viewings`),
  diary: (params: { year?: number; undated?: boolean; page?: number; size?: number }) =>
    api.get<Page<ViewingSession>>(`/api/v1/viewings${buildQuery(params)}`),
  create: (movieId: number, viewing: ViewingInput) =>
    api.post<ViewingSession>(`/api/v1/movies/${movieId}/viewings`, viewing),
  update: (id: number, viewing: ViewingInput) => api.put<ViewingSession>(`/api/v1/viewings/${id}`, viewing),
  remove: (id: number) => api.delete(`/api/v1/viewings/${id}`),
}

export const postersApi = {
  status: () => api.get<PosterStatus>('/api/v1/posters/status'),
  lookupBatch: (params: { runStartedAt?: string; retryNotFound?: boolean; batchSize?: number }) =>
    api.post<PosterBatch>(`/api/v1/posters/lookup${buildQuery(params)}`),
  lookupOne: (movieId: number) => api.post<PosterLookupResult>(`/api/v1/movies/${movieId}/poster/lookup`),
  remove: (movieId: number) => api.delete(`/api/v1/movies/${movieId}/poster`),
  upload: (movieId: number, file: File) => {
    const form = new FormData()
    form.append('file', file)
    return api.post<void>(`/api/v1/movies/${movieId}/poster`, form)
  },
}

export const statsApi = {
  dashboard: () => api.get<Dashboard>('/api/v1/stats/dashboard'),
  insights: () => api.get<Insights>('/api/v1/stats/insights'),
}

export const referenceApi = {
  genres: () => api.get<GenreSummary[]>('/api/v1/genres'),
  platforms: () => api.get<PlatformSummary[]>('/api/v1/platforms'),
  venues: () => api.get<VenueSummary[]>('/api/v1/venues'),
  languages: () => api.get<string[]>('/api/v1/languages'),
}

export const watchlistApi = {
  list: () => api.get<WatchlistItem[]>('/api/v1/watchlist'),
  create: (item: WatchlistInput) => api.post<WatchlistItem>('/api/v1/watchlist', item),
  update: (id: number, item: WatchlistInput) => api.put<WatchlistItem>(`/api/v1/watchlist/${id}`, item),
  remove: (id: number) => api.delete(`/api/v1/watchlist/${id}`),
}

function fileForm(file: File): FormData {
  const form = new FormData()
  form.append('file', file)
  return form
}

export const importsApi = {
  preview: (file: File) => api.post<ImportReport>('/api/v1/imports/preview', fileForm(file)),
  commit: (file: File) => api.post<ImportReport>('/api/v1/imports', fileForm(file)),
  history: () => api.get<ImportRun[]>('/api/v1/imports'),
}
