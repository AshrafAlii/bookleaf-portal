import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { clearTabRefreshToken, refreshSession, setAccessToken, setTabRefreshToken } from '../api/client'
import { authApi } from '../api/endpoints'
import type { UserSummary } from '../api/types'

interface AuthState {
  user: UserSummary | null
  loading: boolean
  signIn: (email: string, password: string) => Promise<UserSummary>
  signOut: () => Promise<void>
  /**
   * Signs this tab out only, leaving any other tab signed in — then returns to
   * the login screen so a different account can be used here.
   */
  switchAccount: () => void
}

const AuthContext = createContext<AuthState | undefined>(undefined)

/**
 * Holds the signed-in user and the in-memory access token.
 *
 * The access token lives in memory only (never in localStorage) so it cannot be
 * read by a third-party script. The refresh token has two homes: an httpOnly
 * cookie shared by the browser, and a copy in `sessionStorage` scoped to this
 * tab. The tab-scoped copy is what makes it possible to keep an author session
 * and an admin session open side by side.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<UserSummary | null>(null)
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    let cancelled = false
    ;(async () => {
      try {
        const refreshed = await refreshSession()
        if (refreshed && !cancelled) {
          const profile = await authApi.me()
          if (!cancelled) setUser(profile)
        }
      } catch {
        // Not signed in — the login screen takes over.
      } finally {
        if (!cancelled) setLoading(false)
      }
    })()
    return () => {
      cancelled = true
    }
  }, [])

  const signIn = useCallback(async (email: string, password: string) => {
    const response = await authApi.login(email, password)
    setAccessToken(response.accessToken)
    setTabRefreshToken(response.refreshToken)
    setUser(response.user)
    return response.user
  }, [])

  const signOut = useCallback(async () => {
    try {
      await authApi.logout()
    } finally {
      setAccessToken(null)
      clearTabRefreshToken()
      setUser(null)
    }
  }, [])

  const switchAccount = useCallback(() => {
    // Deliberately does not call the logout endpoint: the shared cookie must
    // survive so that other tabs keep their session.
    setAccessToken(null)
    clearTabRefreshToken()
    setUser(null)
  }, [])

  const value = useMemo(
    () => ({ user, loading, signIn, signOut, switchAccount }),
    [user, loading, signIn, signOut, switchAccount],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider')
  }
  return context
}
