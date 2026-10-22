import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react'
import { refreshSession, setAccessToken } from '../api/client'
import { authApi } from '../api/endpoints'
import type { UserSummary } from '../api/types'

interface AuthState {
  user: UserSummary | null
  loading: boolean
  signIn: (email: string, password: string) => Promise<UserSummary>
  signOut: () => Promise<void>
}

const AuthContext = createContext<AuthState | undefined>(undefined)

/**
 * Holds the signed-in user and the in-memory access token.
 *
 * The access token lives in memory only (never in localStorage) so it cannot be
 * read by a third-party script. The refresh token is an httpOnly cookie the
 * browser sends automatically — on a cold page load we silently redeem it to
 * get an access token back, which keeps the user signed in across refreshes.
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
    setUser(response.user)
    return response.user
  }, [])

  const signOut = useCallback(async () => {
    try {
      await authApi.logout()
    } finally {
      setAccessToken(null)
      setUser(null)
    }
  }, [])

  const value = useMemo(() => ({ user, loading, signIn, signOut }), [user, loading, signIn, signOut])

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext)
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider')
  }
  return context
}
