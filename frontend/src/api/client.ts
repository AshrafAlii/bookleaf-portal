import type { ApiErrorBody } from './types'

/**
 * A thin API client with two jobs:
 *
 * 1. Attach the bearer token to every request.
 * 2. Recover from an expired access token exactly once, transparently, by
 *    redeeming the httpOnly refresh cookie and retrying the original call.
 *
 * The refresh cookie is never read by JavaScript — that is the point of keeping
 * it httpOnly.
 */

const API_BASE = '/api/v1'

/**
 * Where the tab's own refresh token lives.
 *
 * `sessionStorage` is scoped to a single browser tab, so two tabs can hold two
 * different accounts at the same time — an author session in one window and an
 * admin session in another. `localStorage` is deliberately not used: it is shared
 * by every tab and persists across restarts.
 */
const TAB_REFRESH_TOKEN_KEY = 'bookleaf_refresh_token'

let accessToken: string | null = null
let refreshPromise: Promise<boolean> | null = null

export function setAccessToken(token: string | null): void {
  accessToken = token
}

export function getAccessToken(): string | null {
  return accessToken
}

export function setTabRefreshToken(token: string | null): void {
  try {
    if (token) {
      window.sessionStorage.setItem(TAB_REFRESH_TOKEN_KEY, token)
    } else {
      window.sessionStorage.removeItem(TAB_REFRESH_TOKEN_KEY)
    }
  } catch {
    // Private browsing or a blocked storage partition — the cookie still works.
  }
}

export function getTabRefreshToken(): string | null {
  try {
    return window.sessionStorage.getItem(TAB_REFRESH_TOKEN_KEY)
  } catch {
    return null
  }
}

/** Drops this tab's session without touching other tabs. */
export function clearTabRefreshToken(): void {
  setTabRefreshToken(null)
}

export class ApiError extends Error {
  status: number
  fieldErrors: Record<string, string>
  correlationId?: string

  constructor(body: ApiErrorBody) {
    super(body.message || 'Request failed')
    this.name = 'ApiError'
    this.status = body.status
    this.fieldErrors = body.fieldErrors ?? {}
    this.correlationId = body.correlationId
  }
}

async function parseError(response: Response): Promise<ApiError> {
  let body: ApiErrorBody = {
    status: response.status,
    error: response.statusText || 'Error',
    message: `Request failed with status ${response.status}`,
  }
  try {
    const parsed = await response.json()
    body = { ...body, ...parsed }
  } catch {
    // Non-JSON error body (e.g. from a proxy) — keep the default message.
  }
  return new ApiError(body)
}

async function request<T>(path: string, init: RequestInit = {}, retryOn401 = true): Promise<T> {
  const headers = new Headers(init.headers)
  if (!headers.has('Content-Type') && init.body) {
    headers.set('Content-Type', 'application/json')
  }
  if (accessToken) {
    headers.set('Authorization', `Bearer ${accessToken}`)
  }

  const response = await fetch(`${API_BASE}${path}`, {
    ...init,
    headers,
    credentials: 'same-origin',
  })

  if (response.status === 401 && retryOn401) {
    const refreshed = await refreshSession()
    if (refreshed) {
      return request<T>(path, init, false)
    }
  }

  if (!response.ok) {
    throw await parseError(response)
  }

  if (response.status === 204) {
    return undefined as T
  }
  return (await response.json()) as T
}

/**
 * Redeems the session for a new access token.
 *
 * The tab's own refresh token is sent in the body when it has one; otherwise the
 * shared httpOnly cookie is used. Concurrent callers share a single in-flight
 * request so a burst of 401s does not trigger a burst of refreshes.
 */
export async function refreshSession(): Promise<boolean> {
  if (!refreshPromise) {
    const tabToken = getTabRefreshToken()
    refreshPromise = fetch(`${API_BASE}/auth/refresh`, {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(tabToken ? { refreshToken: tabToken } : {}),
    })
      .then(async (response) => {
        if (!response.ok) {
          accessToken = null
          return false
        }
        const data = (await response.json()) as { accessToken: string; refreshToken?: string }
        accessToken = data.accessToken
        if (data.refreshToken) {
          setTabRefreshToken(data.refreshToken)
        }
        return true
      })
      .catch(() => {
        accessToken = null
        return false
      })
      .finally(() => {
        refreshPromise = null
      })
  }
  return refreshPromise
}

export const api = {
  get: <T,>(path: string) => request<T>(path),
  post: <T,>(path: string, body?: unknown) =>
    request<T>(path, { method: 'POST', body: body === undefined ? undefined : JSON.stringify(body) }),
  put: <T,>(path: string, body?: unknown) =>
    request<T>(path, { method: 'PUT', body: body === undefined ? undefined : JSON.stringify(body) }),
  delete: <T,>(path: string) => request<T>(path, { method: 'DELETE' }),
}

export function buildQuery(params: Record<string, string | number | undefined | null>): string {
  const search = new URLSearchParams()
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== '') {
      search.append(key, String(value))
    }
  })
  const query = search.toString()
  return query ? `?${query}` : ''
}
