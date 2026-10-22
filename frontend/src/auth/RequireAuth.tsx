import { Navigate, useLocation } from 'react-router-dom'
import type { ReactNode } from 'react'
import { useAuth } from './AuthContext'
import { FullPageSpinner } from '../components/Spinner'
import type { Role } from '../api/types'

/**
 * Route guard. Sends unauthenticated visitors to the login screen and keeps
 * authors out of admin routes (and vice versa) — the API enforces the same
 * rules server side, this is only to avoid showing a user a page they cannot use.
 */
export function RequireAuth({ children, role }: { children: ReactNode; role?: Role }) {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) {
    return <FullPageSpinner label="Restoring your session…" />
  }

  if (!user) {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />
  }

  if (role && user.role !== role) {
    return <Navigate to={user.role === 'ADMIN' ? '/admin' : '/author'} replace />
  }

  return <>{children}</>
}
