import type { MovieDetail, MovieInput } from '../../api/types'

export const EMPTY_MOVIE: MovieInput = {
  title: '',
  originalTitle: null,
  format: 'MOVIE',
  releaseYear: null,
  language: null,
  synopsis: null,
  rating: null,
  ratingBeyondScale: false,
  review: null,
  privateNotes: null,
  favourite: false,
  director: null,
  castMembers: null,
  runtimeMinutes: null,
  posterUrl: null,
  metadataSource: null,
  genres: [],
}

export function movieToInput(movie: MovieDetail): MovieInput {
  return {
    title: movie.title,
    originalTitle: movie.originalTitle,
    format: movie.format,
    releaseYear: movie.releaseYear,
    language: movie.language,
    synopsis: movie.synopsis,
    rating: movie.rating,
    ratingBeyondScale: movie.ratingBeyondScale,
    review: movie.review,
    privateNotes: movie.privateNotes,
    favourite: movie.favourite,
    director: movie.director,
    castMembers: movie.castMembers,
    runtimeMinutes: movie.runtimeMinutes,
    posterUrl: movie.posterUrl,
    metadataSource: movie.metadataSource,
    genres: movie.genres.map((g) => g.name),
  }
}

/** Same rules as the API's bean validation, so errors show before submitting. */
export function validateMovie(movie: MovieInput): Record<string, string> {
  const errors: Record<string, string> = {}
  if (!movie.title.trim()) errors.title = 'Title is required'
  else if (movie.title.length > 250) errors.title = 'Title is too long'
  if (movie.releaseYear !== null && (movie.releaseYear < 1870 || movie.releaseYear > 2100)) {
    errors.releaseYear = 'Enter a year between 1870 and 2100'
  }
  if (movie.runtimeMinutes !== null && (movie.runtimeMinutes < 1 || movie.runtimeMinutes > 2000)) {
    errors.runtimeMinutes = 'Runtime must be between 1 and 2000 minutes'
  }
  if (movie.posterUrl && !/^https?:\/\/\S+$/.test(movie.posterUrl)) {
    errors.posterUrl = 'Poster must be an http(s) URL'
  }
  if (movie.genres.length > 10) errors.genres = 'At most 10 genres'
  return errors
}

/** "movie.releaseYear" from the API maps to the "releaseYear" field. */
export function fieldKey(apiField: string): string {
  return apiField.replace(/^(movie|initialViewing)\./, '')
}
