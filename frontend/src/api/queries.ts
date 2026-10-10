import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { moviesApi, referenceApi, statsApi, viewingsApi, watchlistApi } from './endpoints'
import type { MovieInput, MovieQuery, ViewingInput, WatchlistInput } from './types'

export const keys = {
  movies: ['movies'] as const,
  movieList: (query: MovieQuery) => ['movies', 'list', query] as const,
  movie: (id: number) => ['movies', 'detail', id] as const,
  diary: (params: object) => ['viewings', 'diary', params] as const,
  stats: ['stats'] as const,
  dashboard: ['stats', 'dashboard'] as const,
  insights: ['stats', 'insights'] as const,
  genres: ['reference', 'genres'] as const,
  platforms: ['reference', 'platforms'] as const,
  venues: ['reference', 'venues'] as const,
  languages: ['reference', 'languages'] as const,
  watchlist: ['watchlist'] as const,
}

export function useMovieList(query: MovieQuery) {
  return useQuery({
    queryKey: keys.movieList(query),
    queryFn: () => moviesApi.search(query),
    placeholderData: keepPreviousData,
  })
}

export function useMovie(id: number) {
  return useQuery({ queryKey: keys.movie(id), queryFn: () => moviesApi.get(id), enabled: Number.isFinite(id) })
}

export const useDashboard = () => useQuery({ queryKey: keys.dashboard, queryFn: statsApi.dashboard })
export const useInsights = () => useQuery({ queryKey: keys.insights, queryFn: statsApi.insights })
export const useGenres = () => useQuery({ queryKey: keys.genres, queryFn: referenceApi.genres, staleTime: 60_000 })
export const usePlatforms = () =>
  useQuery({ queryKey: keys.platforms, queryFn: referenceApi.platforms, staleTime: 60_000 })
export const useVenues = () => useQuery({ queryKey: keys.venues, queryFn: referenceApi.venues, staleTime: 60_000 })
export const useLanguages = () =>
  useQuery({ queryKey: keys.languages, queryFn: referenceApi.languages, staleTime: 60_000 })
export const useWatchlist = () => useQuery({ queryKey: keys.watchlist, queryFn: watchlistApi.list })

export function useDiary(params: { year?: number; undated?: boolean; page: number }) {
  return useQuery({
    queryKey: keys.diary(params),
    queryFn: () => viewingsApi.diary({ ...params, size: 30 }),
    placeholderData: keepPreviousData,
  })
}

/** Anything that changes movies or viewings can move every list and statistic. */
function useInvalidateJournal() {
  const client = useQueryClient()
  return () =>
    Promise.all([
      client.invalidateQueries({ queryKey: keys.movies }),
      client.invalidateQueries({ queryKey: ['viewings'] }),
      client.invalidateQueries({ queryKey: keys.stats }),
      client.invalidateQueries({ queryKey: ['reference'] }),
    ])
}

export function useCreateMovie() {
  const invalidate = useInvalidateJournal()
  return useMutation({
    mutationFn: ({ movie, initialViewing }: { movie: MovieInput; initialViewing: ViewingInput | null }) =>
      moviesApi.create(movie, initialViewing),
    onSuccess: invalidate,
  })
}

export function useUpdateMovie(id: number) {
  const invalidate = useInvalidateJournal()
  return useMutation({ mutationFn: (movie: MovieInput) => moviesApi.update(id, movie), onSuccess: invalidate })
}

export function useToggleFavourite(id: number) {
  const invalidate = useInvalidateJournal()
  return useMutation({
    mutationFn: (favourite: boolean) => moviesApi.setFavourite(id, favourite),
    onSuccess: invalidate,
  })
}

export function useDeleteMovie() {
  const client = useQueryClient()
  const invalidate = useInvalidateJournal()
  return useMutation({
    mutationFn: (id: number) => moviesApi.remove(id),
    onSuccess: (_data, id) => {
      client.removeQueries({ queryKey: keys.movie(id) })
      return invalidate()
    },
  })
}

export function useSaveViewing(movieId: number) {
  const invalidate = useInvalidateJournal()
  return useMutation({
    mutationFn: ({ id, viewing }: { id: number | null; viewing: ViewingInput }) =>
      id === null ? viewingsApi.create(movieId, viewing) : viewingsApi.update(id, viewing),
    onSuccess: invalidate,
  })
}

export function useDeleteViewing() {
  const invalidate = useInvalidateJournal()
  return useMutation({ mutationFn: (id: number) => viewingsApi.remove(id), onSuccess: invalidate })
}

export function useSaveWatchlistItem() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: ({ id, item }: { id: number | null; item: WatchlistInput }) =>
      id === null ? watchlistApi.create(item) : watchlistApi.update(id, item),
    onSuccess: () => client.invalidateQueries({ queryKey: keys.watchlist }),
  })
}

export function useDeleteWatchlistItem() {
  const client = useQueryClient()
  return useMutation({
    mutationFn: (id: number) => watchlistApi.remove(id),
    onSuccess: () => client.invalidateQueries({ queryKey: keys.watchlist }),
  })
}
