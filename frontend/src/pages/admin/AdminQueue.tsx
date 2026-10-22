import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi, type QueueFilters } from '../../api/endpoints'
import { subscribeToTicketStream } from '../../api/stream'
import { Card } from '../../components/Card'
import { CategoryBadge, PriorityBadge, StatusBadge } from '../../components/Badges'
import { EmptyState, ErrorBanner } from '../../components/Feedback'
import { FullPageSpinner } from '../../components/Spinner'
import { inputClass } from '../../components/Button'
import { formatAge, formatDateTime } from '../../lib/format'

const STATUS_OPTIONS = [
  { label: 'All statuses', value: '' },
  { label: 'Open', value: 'OPEN' },
  { label: 'In Progress', value: 'IN_PROGRESS' },
  { label: 'Resolved', value: 'RESOLVED' },
  { label: 'Closed', value: 'CLOSED' },
]

const CATEGORY_OPTIONS = [
  { label: 'All categories', value: '' },
  { label: 'Royalty & Payments', value: 'ROYALTY_PAYMENTS' },
  { label: 'ISBN & Metadata Issues', value: 'ISBN_METADATA' },
  { label: 'Printing & Quality', value: 'PRINTING_QUALITY' },
  { label: 'Distribution & Availability', value: 'DISTRIBUTION_AVAILABILITY' },
  { label: 'Book Status & Production Updates', value: 'PRODUCTION_UPDATES' },
  { label: 'General Inquiry', value: 'GENERAL_INQUIRY' },
]

const PRIORITY_OPTIONS = [
  { label: 'All priorities', value: '' },
  { label: 'Critical', value: 'CRITICAL' },
  { label: 'High', value: 'HIGH' },
  { label: 'Medium', value: 'MEDIUM' },
  { label: 'Low', value: 'LOW' },
]

const SORT_OPTIONS = [
  { label: 'Urgency first', value: 'priorityRank,desc' },
  { label: 'Oldest first', value: 'createdAt,asc' },
  { label: 'Newest first', value: 'createdAt,desc' },
  { label: 'Recently updated', value: 'updatedAt,desc' },
]

export function AdminQueue() {
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState<QueueFilters>({ sort: 'priorityRank,desc', size: 25, page: 0 })
  const [searchDraft, setSearchDraft] = useState('')

  const { data, isLoading, isFetching, error, refetch } = useQuery({
    queryKey: ['admin', 'queue', filters],
    queryFn: () => adminApi.queue(filters),
  })

  useEffect(() => {
    const unsubscribe = subscribeToTicketStream(() => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'queue'] })
      void queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] })
    })
    return unsubscribe
  }, [queryClient])

  const update = (patch: Partial<QueueFilters>) =>
    setFilters((current) => ({ ...current, page: 0, ...patch }))

  const total = data?.totalElements ?? 0
  const page = data?.page ?? 0
  const pages = data?.totalPages ?? 1

  return (
    <div className="space-y-5">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-ink-900">Ticket queue</h1>
          <p className="mt-1 text-sm text-ink-500">
            Every author query across all accounts. Filter, triage and respond.
          </p>
        </div>
        <p className="text-sm text-ink-500">
          {total} ticket{total === 1 ? '' : 's'}
          {isFetching ? <span className="ml-2 text-xs text-ink-400">updating…</span> : null}
        </p>
      </div>

      <Card padded={false}>
        <div className="grid gap-3 border-b border-ink-100 p-4 sm:grid-cols-2 lg:grid-cols-5">
          <input
            className={inputClass}
            placeholder="Search subject, author or code…"
            value={searchDraft}
            onChange={(e) => setSearchDraft(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') update({ search: searchDraft })
            }}
            onBlur={() => update({ search: searchDraft })}
          />
          <select
            className={inputClass}
            value={filters.status ?? ''}
            onChange={(e) => update({ status: e.target.value })}
          >
            {STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <select
            className={inputClass}
            value={filters.category ?? ''}
            onChange={(e) => update({ category: e.target.value })}
          >
            {CATEGORY_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <select
            className={inputClass}
            value={filters.priority ?? ''}
            onChange={(e) => update({ priority: e.target.value })}
          >
            {PRIORITY_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
          <select
            className={inputClass}
            value={filters.sort ?? 'priorityRank,desc'}
            onChange={(e) => update({ sort: e.target.value })}
          >
            {SORT_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </div>

        {isLoading ? (
          <FullPageSpinner label="Loading the queue…" />
        ) : error ? (
          <div className="p-5">
            <ErrorBanner
              message={error instanceof Error ? error.message : 'Unable to load the queue.'}
              onRetry={() => void refetch()}
            />
          </div>
        ) : !data || data.content.length === 0 ? (
          <div className="p-5">
            <EmptyState
              title="No tickets match these filters"
              description="Try widening the status, category or date filters."
              icon="🔍"
              action={
                <button
                  type="button"
                  onClick={() => setFilters({ sort: 'priorityRank,desc', size: 25, page: 0 })}
                  className="rounded-lg bg-ink-900 px-3 py-1.5 text-xs font-medium text-white"
                >
                  Clear filters
                </button>
              }
            />
          </div>
        ) : (
          <ul className="divide-y divide-ink-100">
            {data.content.map((ticket) => (
              <li key={ticket.id}>
                <Link
                  to={`/admin/tickets/${ticket.id}`}
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
                        <CategoryBadge
                          category={ticket.category}
                          label={ticket.categoryLabel}
                          source={ticket.categorySource}
                        />
                      </div>
                      <p className="mt-1.5 font-medium text-ink-900">{ticket.subject}</p>
                      <p className="mt-0.5 text-xs text-ink-500">
                        {ticket.authorName} · {ticket.bookTitle ?? 'General / Account Level'} ·{' '}
                        {formatAge(ticket.ageHours)} old
                        {ticket.assignedToName ? ` · ${ticket.assignedToName}` : ' · Unassigned'}
                      </p>
                      {ticket.lastMessagePreview ? (
                        <p className="mt-2 line-clamp-1 rounded-md bg-ink-50 px-2.5 py-1.5 text-xs text-ink-600">
                          {ticket.lastMessagePreview}
                        </p>
                      ) : null}
                    </div>
                    <div className="shrink-0 text-right text-xs">
                      {ticket.awaitingAdmin ? (
                        <span className="rounded-full bg-amber-50 px-2 py-0.5 font-medium text-amber-700">
                          Awaiting reply
                        </span>
                      ) : (
                        <span className="rounded-full bg-leaf-50 px-2 py-0.5 font-medium text-leaf-700">
                          Answered
                        </span>
                      )}
                      <p className="mt-1.5 text-ink-400">{formatDateTime(ticket.updatedAt)}</p>
                    </div>
                  </div>
                </Link>
              </li>
            ))}
          </ul>
        )}

        {pages > 1 ? (
          <div className="flex items-center justify-between border-t border-ink-100 px-5 py-3 text-sm">
            <span className="text-ink-500">
              Page {page + 1} of {pages}
            </span>
            <div className="flex gap-2">
              <button
                type="button"
                disabled={data?.first}
                onClick={() => setFilters((current) => ({ ...current, page: Math.max((current.page ?? 0) - 1, 0) }))}
                className="rounded-lg border border-ink-200 px-3 py-1.5 text-xs font-medium text-ink-700 disabled:opacity-40"
              >
                Previous
              </button>
              <button
                type="button"
                disabled={data?.last}
                onClick={() =>
                  setFilters((current) => ({ ...current, page: (current.page ?? 0) + 1 }))
                }
                className="rounded-lg border border-ink-200 px-3 py-1.5 text-xs font-medium text-ink-700 disabled:opacity-40"
              >
                Next
              </button>
            </div>
          </div>
        ) : null}
      </Card>
    </div>
  )
}
