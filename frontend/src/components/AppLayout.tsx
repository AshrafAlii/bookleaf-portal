import { NavLink, Outlet, useNavigate } from 'react-router-dom'
import { useEffect, useState, type ReactNode } from 'react'
import { useAuth } from '../auth/AuthContext'
import { subscribeToTicketStream } from '../api/stream'
import { cn } from '../lib/cn'

interface NavItem {
  to: string
  label: string
  icon: string
  end?: boolean
}

const AUTHOR_NAV: NavItem[] = [
  { to: '/author', label: 'Dashboard', icon: '🏠', end: true },
  { to: '/author/books', label: 'My Books', icon: '📚' },
  { to: '/author/tickets', label: 'My Tickets', icon: '🎫' },
  { to: '/author/tickets/new', label: 'Raise a Query', icon: '✍️' },
]

const ADMIN_NAV: NavItem[] = [
  { to: '/admin', label: 'Dashboard', icon: '📊', end: true },
  { to: '/admin/tickets', label: 'Ticket Queue', icon: '🗂️' },
  { to: '/admin/knowledge', label: 'Knowledge Base', icon: '📚' },
]

export function AppLayout({ children }: { children?: ReactNode }) {
  const { user, signOut } = useAuth()
  const navigate = useNavigate()
  const [live, setLive] = useState(false)

  const isAdmin = user?.role === 'ADMIN'
  const navItems = isAdmin ? ADMIN_NAV : AUTHOR_NAV

  // One shared connection per session; pages decide what to do with the events.
  useEffect(() => {
    const unsubscribe = subscribeToTicketStream(() => undefined, setLive)
    return unsubscribe
  }, [])

  return (
    <div className="min-h-screen bg-ink-50">
      <header className="sticky top-0 z-30 border-b border-ink-200 bg-white/90 backdrop-blur">
        <div className="mx-auto flex max-w-7xl items-center justify-between gap-4 px-4 py-3 sm:px-6">
          <div className="flex items-center gap-3">
            <span className="grid h-9 w-9 place-items-center rounded-lg bg-leaf-600 text-lg text-white">
              🌿
            </span>
            <div className="leading-tight">
              <p className="text-sm font-semibold text-ink-900">BookLeaf Publishing</p>
              <p className="text-xs text-ink-500">
                {isAdmin ? 'Support Operations' : 'Author Support Portal'}
              </p>
            </div>
          </div>

          <div className="flex items-center gap-3">
            <span
              title={live ? 'Live updates connected' : 'Live updates reconnecting…'}
              className={cn(
                'inline-flex items-center gap-1.5 rounded-full px-2.5 py-1 text-xs font-medium',
                live ? 'bg-leaf-50 text-leaf-700' : 'bg-ink-100 text-ink-500',
              )}
            >
              <span
                className={cn(
                  'h-1.5 w-1.5 rounded-full',
                  live ? 'bg-leaf-500 animate-pulse' : 'bg-ink-400',
                )}
              />
              {live ? 'Live' : 'Offline'}
            </span>

            <div className="hidden text-right sm:block">
              <p className="text-sm font-medium text-ink-900">{user?.fullName}</p>
              <p className="text-xs text-ink-500">{user?.email}</p>
            </div>

            <button
              type="button"
              onClick={async () => {
                await signOut()
                navigate('/login', { replace: true })
              }}
              className="rounded-lg border border-ink-200 px-3 py-1.5 text-sm font-medium text-ink-700 hover:bg-ink-50"
            >
              Sign out
            </button>
          </div>
        </div>
      </header>

      <div className="mx-auto flex max-w-7xl flex-col gap-6 px-4 py-6 sm:px-6 lg:flex-row">
        <aside className="lg:w-60 lg:shrink-0">
          <nav className="flex gap-1 overflow-x-auto lg:flex-col lg:overflow-visible">
            {navItems.map((item) => (
              <NavLink
                key={item.to}
                to={item.to}
                end={item.end}
                className={({ isActive }) =>
                  cn(
                    'flex shrink-0 items-center gap-2.5 rounded-lg px-3 py-2 text-sm font-medium transition',
                    isActive
                      ? 'bg-leaf-600 text-white shadow-sm'
                      : 'text-ink-600 hover:bg-white hover:text-ink-900',
                  )
                }
              >
                <span aria-hidden>{item.icon}</span>
                {item.label}
              </NavLink>
            ))}
          </nav>
        </aside>

        <main className="min-w-0 flex-1">
          {children ?? <Outlet />}
        </main>
      </div>
    </div>
  )
}
