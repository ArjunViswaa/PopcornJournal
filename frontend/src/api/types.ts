// Mirrors the backend DTOs in com.popcornjournal.dto. Dates are ISO strings.

export type MovieFormat = 'MOVIE' | 'SERIES'
export type ViewingMethod = 'CINEMA' | 'STREAMING' | 'TELEVISION' | 'PHYSICAL_MEDIA' | 'OTHER'

export interface NamedRef {
  id: number
  name: string
}

export interface Genre {
  id: number
  name: string
  category: string
}

export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  first: boolean
  last: boolean
}

export interface MovieSummary {
  id: number
  title: string
  format: MovieFormat
  releaseYear: number | null
  language: string | null
  genres: string[]
  rating: number | null
  ratingBeyondScale: boolean
  favourite: boolean
  hasReview: boolean
  posterUrl: string | null
  viewingCount: number
  lastWatchedOn: string | null
  platforms: string[]
  venues: string[]
}

export interface ViewingSession {
  id: number
  movieId: number
  movieTitle: string
  watchedOn: string | null
  watchedAtTime: string | null
  method: ViewingMethod | null
  platform: NamedRef | null
  venue: NamedRef | null
  location: string | null
  rating: number | null
  ratingBeyondScale: boolean
  notes: string | null
  imported: boolean
  createdAt: string
  updatedAt: string
}

export interface SourceRow {
  sheet: string
  rowNumber: number
  originalValues: Record<string, string | number | boolean | null>
}

export interface MovieDetail {
  id: number
  title: string
  originalTitle: string | null
  format: MovieFormat
  releaseYear: number | null
  language: string | null
  synopsis: string | null
  rating: number | null
  ratingBeyondScale: boolean
  review: string | null
  privateNotes: string | null
  favourite: boolean
  director: string | null
  castMembers: string | null
  runtimeMinutes: number | null
  /** The poster URL the user entered; it wins over fetchedPoster. */
  posterUrl: string | null
  fetchedPoster: FetchedPoster | null
  metadataSource: string | null
  genres: Genre[]
  viewings: ViewingSession[]
  importedFrom: SourceRow[]
  createdAt: string
  updatedAt: string
}

export interface FetchedPoster {
  url: string
  source: string
  sourcePageUrl: string | null
  matchedTitle: string | null
  fetchedAt: string
}

export interface PosterStatus {
  enabled: boolean
  source: string
  totalMovies: number
  withOwnPoster: number
  withFetchedPoster: number
  pending: number
  notFound: number
  rejected: number
}

export interface PosterLookupResult {
  movieId: number
  title: string
  outcome: 'FOUND' | 'NOT_FOUND' | 'FAILED'
  matchedTitle: string | null
}

export interface PosterBatch {
  runStartedAt: string
  results: PosterLookupResult[]
  remaining: number
}

export interface MovieInput {
  title: string
  originalTitle: string | null
  format: MovieFormat
  releaseYear: number | null
  language: string | null
  synopsis: string | null
  rating: number | null
  ratingBeyondScale: boolean
  review: string | null
  privateNotes: string | null
  favourite: boolean
  director: string | null
  castMembers: string | null
  runtimeMinutes: number | null
  posterUrl: string | null
  metadataSource: string | null
  genres: string[]
}

export interface ViewingInput {
  watchedOn: string | null
  watchedAtTime: string | null
  method: ViewingMethod | null
  platformName: string | null
  venueName: string | null
  location: string | null
  rating: number | null
  ratingBeyondScale: boolean
  notes: string | null
}

export interface DuplicateCandidate {
  id: number
  title: string
  format: MovieFormat
  releaseYear: number | null
  language: string | null
  exactMatch: boolean
}

export type RatingTier = 'RATED' | 'UNRATED' | 'BEYOND_SCALE'
export type PersonalList =
  | 'FAVOURITES'
  | 'TOP_RATED'
  | 'RECENTLY_WATCHED'
  | 'UNRATED'
  | 'NO_REVIEW'
  | 'MISSING_METADATA'
export type MovieSort =
  | 'TITLE'
  | 'LATEST_WATCHED'
  | 'EARLIEST_WATCHED'
  | 'HIGHEST_RATED'
  | 'LOWEST_RATED'
  | 'RECENTLY_ADDED'
  | 'RELEASE_YEAR'

export interface MovieQuery {
  q?: string
  language?: string
  genreId?: number
  category?: string
  platformId?: number
  venueId?: number
  method?: ViewingMethod
  format?: MovieFormat
  watchedYear?: number
  minRating?: number
  maxRating?: number
  ratingTier?: RatingTier
  list?: PersonalList
  sort?: MovieSort
  page?: number
  size?: number
}

export interface GenreSummary extends Genre {
  movieCount: number
}

export interface PlatformSummary {
  id: number
  name: string
  defaultMethod: ViewingMethod
  viewingCount: number
}

export interface VenueSummary {
  id: number
  name: string
  city: string | null
  viewingCount: number
}

export interface WatchlistItem {
  id: number
  title: string
  recommendedBy: string | null
  genre: string | null
  notes: string | null
  imported: boolean
  createdAt: string
}

export interface WatchlistInput {
  title: string
  recommendedBy: string | null
  genre: string | null
  notes: string | null
}

// ---- statistics

export interface LabelCount {
  label: string
  count: number
}

export interface LabelAverage {
  label: string
  averageRating: number | null
  ratedCount: number
  beyondScaleCount: number
  movieCount: number
}

export interface RatingBucket {
  label: string
  value: number | null
  beyondScale: boolean
  count: number
}

export interface PeriodCount {
  period: string
  viewings: number
  movies: number
}

export interface RatingSummary {
  average: number | null
  ratedCount: number
  beyondScaleCount: number
  unratedCount: number
}

export interface TopMovie {
  id: number
  title: string
  releaseYear: number | null
  language: string | null
  rating: number | null
  ratingBeyondScale: boolean
  lastWatchedOn: string | null
}

export interface RecentViewing {
  sessionId: number
  movieId: number
  title: string
  watchedOn: string
  method: ViewingMethod | null
  platform: string | null
  venue: string | null
  rating: number | null
  ratingBeyondScale: boolean
}

export interface Dashboard {
  today: string
  totalMovies: number
  totalViewings: number
  datedViewings: number
  undatedViewings: number
  moviesThisYear: number
  viewingsThisYear: number
  moviesThisMonth: number
  viewingsThisMonth: number
  rating: RatingSummary
  topRated: TopMovie[]
  recentlyWatched: RecentViewing[]
  platforms: LabelCount[]
  languages: LabelCount[]
  genreCategories: LabelCount[]
  releaseDecades: LabelCount[]
  ratingDistribution: RatingBucket[]
  timeline: PeriodCount[]
}

export interface Insights {
  viewingsPerMonth: PeriodCount[]
  viewingsPerYear: PeriodCount[]
  undatedViewings: number
  averageByGenre: LabelAverage[]
  averageByCategory: LabelAverage[]
  averageByLanguage: LabelAverage[]
  averageByFormat: LabelAverage[]
  platforms: LabelCount[]
  venues: LabelCount[]
  methods: LabelCount[]
  genresByMovies: LabelCount[]
  releaseYears: LabelCount[]
  rating: RatingSummary
  ratingDistribution: RatingBucket[]
}

// ---- import

export type ImportRowStatus =
  | 'NEW'
  | 'NEW_VIEWING'
  | 'ALREADY_IMPORTED'
  | 'DELETED_IN_APP'
  | 'CONFLICTS_WITH_EXISTING'
  | 'ERROR'

export interface ImportMessage {
  level: 'INFO' | 'WARNING' | 'ERROR'
  field: string | null
  text: string
}

export interface ImportReport {
  dryRun: boolean
  fileName: string
  sha256: string
  generatedAt: string
  importRunId: number | null
  sheets: {
    name: string
    role: 'WATCHED' | 'WATCHLIST' | 'IGNORED'
    dataRows: number
    blankRowsSkipped: number
    mappedColumns: string[]
    unmappedColumns: string[]
  }[]
  totals: {
    rowsRead: number
    newMovies: number
    newViewings: number
    newWatchlistItems: number
    mergedDuplicateRows: number
    alreadyImported: number
    deletedInApp: number
    conflicts: number
    errors: number
    warnings: number
  }
  rows: {
    sheet: string
    row: number
    title: string | null
    status: ImportRowStatus
    viewings: number
    messages: ImportMessage[]
  }[]
  newGenres: string[]
  newPlatforms: string[]
  newVenues: string[]
  mergedSpellings: { kind: string; storedAs: string; spellings: string[] }[]
  fileMessages: ImportMessage[]
}

export interface ImportRun {
  id: number
  fileName: string
  triggeredBy: 'CLI' | 'WEB'
  startedAt: string
  finishedAt: string
  summary: Record<string, unknown>
}
