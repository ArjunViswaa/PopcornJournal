import { createBrowserRouter, RouterProvider } from 'react-router'
import { AppShell } from './components/layout/AppShell'
import { DashboardPage } from './features/dashboard/DashboardPage'
import { LibraryPage } from './features/movies/LibraryPage'
import { MovieDetailPage } from './features/movies/MovieDetailPage'
import { MovieFormPage } from './features/movies/MovieFormPage'
import { DiaryPage } from './features/viewings/DiaryPage'
import { StatsPage } from './features/stats/StatsPage'
import { WatchlistPage } from './features/watchlist/WatchlistPage'
import { ImportPage } from './features/import/ImportPage'
import { NotFoundPage } from './features/NotFoundPage'

export const routes = [
  {
    element: <AppShell />,
    children: [
      { path: '/', element: <DashboardPage /> },
      { path: '/movies', element: <LibraryPage /> },
      { path: '/movies/new', element: <MovieFormPage /> },
      { path: '/movies/:id', element: <MovieDetailPage /> },
      { path: '/movies/:id/edit', element: <MovieFormPage /> },
      { path: '/diary', element: <DiaryPage /> },
      { path: '/stats', element: <StatsPage /> },
      { path: '/watchlist', element: <WatchlistPage /> },
      { path: '/import', element: <ImportPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]

const router = createBrowserRouter(routes)

export function App() {
  return <RouterProvider router={router} />
}
