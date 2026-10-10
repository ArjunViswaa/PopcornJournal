// Thin fetch wrapper. Every non-2xx response becomes an ApiError carrying the
// backend's problem document, so screens can show its message and field errors.

export interface FieldProblem {
  field: string
  message: string
}

export class ApiError extends Error {
  readonly status: number
  readonly title: string
  readonly fieldErrors: FieldProblem[]
  readonly conflictsWith: unknown[]

  constructor(status: number, title: string, detail: string, fieldErrors: FieldProblem[], conflictsWith: unknown[]) {
    super(detail)
    this.name = 'ApiError'
    this.status = status
    this.title = title
    this.fieldErrors = fieldErrors
    this.conflictsWith = conflictsWith
  }
}

const BASE = (import.meta.env.VITE_API_BASE_URL as string | undefined) ?? ''

type QueryValue = string | number | boolean | null | undefined

export function buildQuery(params: Record<string, QueryValue>): string {
  const search = new URLSearchParams()
  for (const [key, value] of Object.entries(params)) {
    if (value !== undefined && value !== null && value !== '') {
      search.set(key, String(value))
    }
  }
  const text = search.toString()
  return text ? `?${text}` : ''
}

async function toError(response: Response): Promise<ApiError> {
  let body: Record<string, unknown> = {}
  try {
    body = (await response.json()) as Record<string, unknown>
  } catch {
    // non-JSON error (e.g. proxy failure while the backend is down)
  }
  const detail =
    typeof body.detail === 'string'
      ? body.detail
      : response.status === 502 || response.status === 504
        ? 'The server is not reachable. Is the backend running?'
        : `Request failed (${response.status})`
  return new ApiError(
    response.status,
    typeof body.title === 'string' ? body.title : 'Request failed',
    detail,
    Array.isArray(body.errors) ? (body.errors as FieldProblem[]) : [],
    Array.isArray(body.conflictsWith) ? body.conflictsWith : [],
  )
}

export async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let response: Response
  try {
    response = await fetch(BASE + path, {
      ...init,
      headers: {
        Accept: 'application/json',
        ...(init.body && !(init.body instanceof FormData) ? { 'Content-Type': 'application/json' } : {}),
        ...init.headers,
      },
    })
  } catch {
    throw new ApiError(0, 'Network error', 'Could not reach the server. Is the backend running?', [], [])
  }
  if (!response.ok) {
    throw await toError(response)
  }
  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

export const api = {
  get: <T>(path: string) => request<T>(path),
  post: <T>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body instanceof FormData ? body : JSON.stringify(body ?? {}) }),
  put: <T>(path: string, body: unknown) => request<T>(path, { method: 'PUT', body: JSON.stringify(body) }),
  patch: <T>(path: string, body: unknown) => request<T>(path, { method: 'PATCH', body: JSON.stringify(body) }),
  delete: (path: string) => request<void>(path, { method: 'DELETE' }),
}
