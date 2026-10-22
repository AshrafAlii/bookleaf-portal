import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { authorApi } from '../../api/endpoints'
import { subscribeToTicketStream } from '../../api/stream'
import { Card } from '../../components/Card'
import { PriorityBadge, StatusBadge } from '../../components/Badges'
import { EmptyState, ErrorBanner } from '../../components/Feedback'
import { FullPageSpinner } from '../../components/Spinner'
import { formatAge, formatDateTime } from '../../lib/format'
import { cn } from '../../lib/cn'
import type { TicketStatus } from '../../api/types'

const FILTERS: { label: string; value: TicketStatus | 'ALL' }[] = [
  { label: 'All', value: 'ALL' },
  { label: 'Open', value: 'OPEN' },
  { label: 'In Progress', value: 'IN_PROGRESS' },
  { label: 'Resolved', value: 'RESOLVED' },
  { label: 'Closed', value: 'CLOSED' },
]

export function AuthorTickets() {
  const [filter, setFilter] = useState<TicketStatus | 'ALL'>('ALL')
  const queryClient = useQueryClient()

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['author', 'tickets'],
    queryFn: authorApi.tickets,
  })

  // Live updates: when the backend reports a change we refetch the list.
  useEffect(() => {
    const unsubscribe = subscribeToTicketStream(() => {
      void queryClient.invalidateQueries({ queryKey: ['author', 'tickets'] })
      void queryClient.invalidateQueries({ queryKey: ['author', 'dashboard'] })
    })
    return unsubscribe
  }, [queryClient])

  if (isLoading) return <FullPageSpinner label="Loading your tickets…" />
  if (error) {
    return (
      <ErrorBanner
        message={error instanceof Error ? error.message : 'Unable to load your tickets.'}
        onRetry={() => void refetch()}
      />
    )
  }

  const tickets = (data ?? []).filter((ticket) => filter === 'ALL' || ticket.status === filter)

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-ink-900">My Tickets</h1>
          <p className="mt-1 text-sm text-ink-500">
            Every query you have raised. Replies from BookLeaf appear here automatically.
          </p>
        </div>
        <Link
          to="/author/tickets/new"
          className="rounded-lg bg-leaf-600 px-4 py-2 text-sm font-medium text-white hover:bg-leaf-700"
        >
          Raise a support query
        </Link>
      </div>

      <div className="flex flex-wrap gap-2">
        {FILTERS.map((item) => (
          <button
            key={item.value}
            type="button"
            onClick={() => setFilter(item.value)}
            className={cn(
              'rounded-full px-3 py-1.5 text-xs font-medium transition',
              filter === item.value
                ? 'bg-ink-900 text-white'
                : 'bg-white text-ink-600 ring-1 ring-inset ring-ink-200 hover:bg-ink-50',
            )}
          >
            {item.label}
          </button>
        ))}
      </div>

      {tickets.length === 0 ? (
        <EmptyState
          title={filter === 'ALL' ? 'No tickets yet' : `No ${filter.toLowerCase().replace('_', ' ')} tickets`}
          description="Raise a query and our team will pick it up. You will see replies here in real time."
          icon="🎫"
          action={
            <Link
              to="/author/tickets/new"
              className="rounded-lg bg-leaf-600 px-3 py-1.5 text-xs font-medium text-white"
            >
              Raise a support query
            </Link>
          }
        />
      ) : (
        <Card padded={false}>
          <ul className="divide-y divide-ink-100">
            {tickets.map((ticket) => (
              <li key={ticket.id}>
                <Link
                  to={`/author/tickets/${ticket.id}`}
                  className="block px-5 py-4 transition hover:bg-ink-50/60"
                >
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0 flex-1">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-mono text-xs text-ink-400">{ticket.ticketCode}</span>
                        <StatusBadge status={ticket.status} label={ticket.statusLabel} />
                        <PriorityBadge
                          priority={ticket.priority}
                          label={ticket.priorityLabel}
                          source={ticket.prioritySource}
                        />
                      </div>
                      <p className="mt-1.5 font-medium text-ink-900">{ticket.subject}</p>
                      <p className="mt-0.5 text-xs text-ink-500">
                        {ticket.bookTitle ?? 'General / Account Level'} · {ticket.categoryLabel} · raised{' '}
                        {formatAge(ticket.ageHours)} ago
                      </p>
                      {ticket.lastMessagePreview ? (
                        <p className="mt-2 line-clamp-1 rounded-md bg-ink-50 px-2.5 py-1.5 text-xs text-ink-600">
                          {ticket.lastMessagePreview}
                        </p>
                      ) : null}
                    </div>
                    <div className="text-right text-xs text-ink-400">
                      {ticket.awaitingAdmin ? (
                        <span className="rounded-full bg-amber-50 px-2 py-0.5 font-medium text-amber-700">
                          Awaiting BookLeaf
                        </span>
                      ) : (
                        <span className="rounded-full bg-leaf-50 px-2 py-0.5 font-medium text-leaf-700">
                          {ticket.firstResponseAt ? 'Replied' : 'Sent'}
                        </span>
                      )}
                      <p className="mt-1.5">Updated {formatDateTime(ticket.updatedAt)}</p>
                    </div>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        </Card>
      )}
    </div>
  )
}
