import { useEffect, useMemo, useState } from 'react'
import { Link } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi, filterApi, type QueueFilters } from '../../api/endpoints'
import { subscribeToTicketStream } from '../../api/stream'
import { Card } from '../../components/Card'
import { CategoryBadge, PriorityBadge, StatusBadge } from '../../components/Badges'
import { EmptyState, ErrorBanner, InfoBanner } from '../../components/Feedback'
import { FullPageSpinner, InlineLoader } from '../../components/Spinner'
import { Button, inputClass } from '../../components/Button'
import { formatAge, formatDateTime } from '../../lib/format'
import { cn } from '../../lib/cn'
import type { SavedFilter } from '../../api/types'

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

const DEFAULT_FILTERS: QueueFilters = { sort: 'priorityRank,desc', size: 25, page: 0 }

export function AdminQueue() {
  const queryClient = useQueryClient()
  const [filters, setFilters] = useState<QueueFilters>(DEFAULT_FILTERS)
  const [searchDraft, setSearchDraft] = useState('')
  const [selected, setSelected] = useState<Set<string>>(new Set())
  const [bulkNote, setBulkNote] = useState('')
  const [banner, setBanner] = useState<{ tone: 'success' | 'warning'; message: string } | null>(null)
  const [savingName, setSavingName] = useState<string | null>(null)

  const { data, isLoading, isFetching, error, refetch } = useQuery({
    queryKey: ['admin', 'queue', filters],
    queryFn: () => adminApi.queue(filters),
  })

  const { data: savedFilters } = useQuery({
    queryKey: ['admin', 'filters'],
    queryFn: filterApi.list,
  })

  useEffect(() => {
    const unsubscribe = subscribeToTicketStream(() => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'queue'] })
      void queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] })
    })
    return unsubscribe
  }, [queryClient])

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['admin', 'queue'] })
    void queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] })
  }

  const bulk = useMutation({
    mutationFn: adminApi.bulkUpdate,
    onSuccess: (result) => {
      setBanner({
        tone: 'success',
        message: `Updated ${result.updated} of ${result.requested} ticket${
          result.requested === 1 ? '' : 's'
        }: ${result.statusChanged} status, ${result.priorityChanged} priority, ${result.assigned} assigned, ${result.noted} noted.`,
      })
      setSelected(new Set())
      setBulkNote('')
      invalidate()
    },
    onError: (err) => {
      setBanner({ tone: 'warning', message: err instanceof Error ? err.message : 'The bulk action failed.' })
    },
  })

  const saveFilter = useMutation({
    mutationFn: (name: string) => filterApi.create(name, filters),
    onSuccess: () => {
      setSavingName(null)
      void queryClient.invalidateQueries({ queryKey: ['admin', 'filters'] })
      setBanner({ tone: 'success', message: 'View saved.' })
    },
    onError: (err) => {
      setBanner({ tone: 'warning', message: err instanceof Error ? err.message : 'Could not save the view.' })
    },
  })

  const deleteFilter = useMutation({
    mutationFn: (id: string) => filterApi.remove(id),
    onSuccess: () => {
      void queryClient.invalidateQueries({ queryKey: ['admin', 'filters'] })
    },
  })

  const update = (patch: Partial<QueueFilters>) =>
    setFilters((current) => ({ ...current, page: 0, ...patch }))

  const applySaved = (filter: SavedFilter) => {
    try {
      const parsed = JSON.parse(filter.filters) as QueueFilters
      setFilters({ ...DEFAULT_FILTERS, ...parsed })
      setSearchDraft(parsed.search ?? '')
      setSelected(new Set())
    } catch {
      setBanner({ tone: 'warning', message: 'That saved view could not be read.' })
    }
  }

  const rows = data?.content ?? []
  const pageIds = useMemo(() => rows.map((ticket) => ticket.id), [rows])
  const allOnPageSelected = pageIds.length > 0 && pageIds.every((id) => selected.has(id))

  const toggleAll = () => {
    setSelected((current) => {
      const next = new Set(current)
      if (allOnPageSelected) {
        pageIds.forEach((id) => next.delete(id))
      } else {
        pageIds.forEach((id) => next.add(id))
      }
      return next
    })
  }

  const toggleOne = (id: string) => {
    setSelected((current) => {
      const next = new Set(current)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })
  }

  const selectedIds = Array.from(selected)

  const runBulk = (payload: Parameters<typeof adminApi.bulkUpdate>[0]) =>
    bulk.mutate(payload)

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

      {banner ? (
        <div
          className={cn(
            'rounded-xl border px-4 py-3 text-sm',
            banner.tone === 'success'
              ? 'border-leaf-200 bg-leaf-50 text-leaf-900'
              : 'border-amber-200 bg-amber-50 text-amber-900',
          )}
        >
          <div className="flex items-start justify-between gap-3">
            <span>{banner.message}</span>
            <button
              type="button"
              onClick={() => setBanner(null)}
              className="shrink-0 text-xs font-medium opacity-70 hover:opacity-100"
            >
              Dismiss
            </button>
          </div>
        </div>
      ) : null}

      <Card padded={false}>
        {/* Saved views */}
        <div className="flex flex-wrap items-center gap-2 border-b border-ink-100 bg-ink-50/50 px-4 py-2.5">
          <span className="text-xs font-medium uppercase tracking-wide text-ink-500">Saved views</span>
          {(savedFilters ?? []).length === 0 ? (
            <span className="text-xs text-ink-400">
              None yet — set up the filters you use daily and save them here.
            </span>
          ) : (
            (savedFilters ?? []).map((filter) => (
              <span
                key={filter.id}
                className="inline-flex items-center gap-1 rounded-full bg-white py-1 pl-2.5 pr-1 text-xs font-medium text-ink-700 ring-1 ring-inset ring-ink-200"
              >
                <button type="button" onClick={() => applySaved(filter)} className="hover:text-leaf-700">
                  {filter.name}
                </button>
                <button
                  type="button"
                  onClick={() => deleteFilter.mutate(filter.id)}
                  title="Delete this saved view"
                  className="rounded-full px-1 text-ink-400 hover:bg-ink-100 hover:text-rose-600"
                >
                  ×
                </button>
              </span>
            ))
          )}

          <div className="ml-auto flex items-center gap-2">
            {savingName === null ? (
              <button
                type="button"
                onClick={() => setSavingName('')}
                className="rounded-lg border border-ink-200 bg-white px-2.5 py-1 text-xs font-medium text-ink-700 hover:bg-ink-50"
              >
                Save this view
              </button>
            ) : (
              <span className="inline-flex items-center gap-1.5">
                <input
                  autoFocus
                  className="w-44 rounded-lg border border-ink-300 px-2 py-1 text-xs"
                  placeholder="Name this view…"
                  value={savingName}
                  onChange={(e) => setSavingName(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && savingName.trim().length >= 2) {
                      saveFilter.mutate(savingName.trim())
                    }
                    if (e.key === 'Escape') setSavingName(null)
                  }}
                />
                <Button
                  size="sm"
                  loading={saveFilter.isPending}
                  disabled={savingName.trim().length < 2}
                  onClick={() => saveFilter.mutate(savingName.trim())}
                >
                  Save
                </Button>
                <button
                  type="button"
                  onClick={() => setSavingName(null)}
                  className="text-xs text-ink-500 hover:text-ink-800"
                >
                  Cancel
                </button>
              </span>
            )}
          </div>
        </div>

        {/* Filters */}
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

        {/* Bulk action bar */}
        {selectedIds.length > 0 ? (
          <div className="flex flex-wrap items-center gap-2 border-b border-leaf-200 bg-leaf-50/70 px-4 py-2.5">
            <span className="text-sm font-medium text-leaf-900">
              {selectedIds.length} selected
            </span>
            <Button
              size="sm"
              variant="secondary"
              loading={bulk.isPending}
              onClick={() => runBulk({ ticketIds: selectedIds, assignToMe: true })}
            >
              Assign to me
            </Button>
            <select
              className="rounded-lg border border-ink-300 bg-white px-2 py-1 text-xs"
              value=""
              disabled={bulk.isPending}
              onChange={(e) => {
                if (e.target.value) {
                  runBulk({ ticketIds: selectedIds, status: e.target.value })
                }
              }}
            >
              <option value="">Set status…</option>
              {STATUS_OPTIONS.filter((o) => o.value).map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <select
              className="rounded-lg border border-ink-300 bg-white px-2 py-1 text-xs"
              value=""
              disabled={bulk.isPending}
              onChange={(e) => {
                if (e.target.value) {
                  runBulk({ ticketIds: selectedIds, category: e.target.value })
                }
              }}
            >
              <option value="">Set category…</option>
              {CATEGORY_OPTIONS.filter((o) => o.value).map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <select
              className="rounded-lg border border-ink-300 bg-white px-2 py-1 text-xs"
              value=""
              disabled={bulk.isPending}
              onChange={(e) => {
                if (e.target.value) {
                  runBulk({ ticketIds: selectedIds, priority: e.target.value })
                }
              }}
            >
              <option value="">Set priority…</option>
              {PRIORITY_OPTIONS.filter((o) => o.value).map((option) => (
                <option key={option.value} value={option.value}>
                  {option.label}
                </option>
              ))}
            </select>
            <input
              className="w-48 rounded-lg border border-ink-300 bg-white px-2 py-1 text-xs"
              placeholder="Add an internal note…"
              value={bulkNote}
              onChange={(e) => setBulkNote(e.target.value)}
            />
            <Button
              size="sm"
              variant="secondary"
              loading={bulk.isPending}
              disabled={bulkNote.trim().length < 2}
              onClick={() => runBulk({ ticketIds: selectedIds, note: bulkNote.trim() })}
            >
              Add note
            </Button>
            <button
              type="button"
              onClick={() => setSelected(new Set())}
              className="ml-auto text-xs font-medium text-ink-600 hover:text-ink-900"
            >
              Clear selection
            </button>
            {bulk.isPending ? <InlineLoader label="Applying…" /> : null}
          </div>
        ) : null}

        {isLoading ? (
          <FullPageSpinner label="Loading the queue…" />
        ) : error ? (
          <div className="p-5">
            <ErrorBanner
              message={error instanceof Error ? error.message : 'Unable to load the queue.'}
              onRetry={() => void refetch()}
            />
          </div>
        ) : rows.length === 0 ? (
          <div className="p-5">
            <EmptyState
              title="No tickets match these filters"
              description="Try widening the status, category or date filters."
              icon="🔍"
              action={
                <button
                  type="button"
                  onClick={() => {
                    setFilters(DEFAULT_FILTERS)
                    setSearchDraft('')
                  }}
                  className="rounded-lg bg-ink-900 px-3 py-1.5 text-xs font-medium text-white"
                >
                  Clear filters
                </button>
              }
            />
          </div>
        ) : (
          <ul className="divide-y divide-ink-100">
            <li className="flex items-center gap-3 bg-ink-50/40 px-5 py-2 text-xs text-ink-500">
              <input
                type="checkbox"
                checked={allOnPageSelected}
                onChange={toggleAll}
                className="h-3.5 w-3.5 rounded border-ink-300 text-leaf-600 focus:ring-leaf-500"
              />
              <span>
                {allOnPageSelected ? 'Deselect all on this page' : 'Select all on this page'}
              </span>
            </li>
            {rows.map((ticket) => (
              <li key={ticket.id} className="flex items-start gap-3 px-5 py-4 transition hover:bg-ink-50/60">
                <input
                  type="checkbox"
                  checked={selected.has(ticket.id)}
                  onChange={() => toggleOne(ticket.id)}
                  className="mt-1 h-3.5 w-3.5 shrink-0 rounded border-ink-300 text-leaf-600 focus:ring-leaf-500"
                />
                <Link to={`/admin/tickets/${ticket.id}`} className="min-w-0 flex-1">
                  <div className="flex flex-wrap items-start justify-between gap-3">
                    <div className="min-w-0">
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
              {selectedIds.length > 0 ? (
                <span className="ml-2 text-leaf-700">· {selectedIds.length} selected</span>
              ) : null}
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
                onClick={() => setFilters((current) => ({ ...current, page: (current.page ?? 0) + 1 }))}
                className="rounded-lg border border-ink-200 px-3 py-1.5 text-xs font-medium text-ink-700 disabled:opacity-40"
              >
                Next
              </button>
            </div>
          </div>
        ) : null}
      </Card>

      <InfoBanner>
        Bulk actions write an audit event for every ticket they touch, exactly as a single-ticket
        action does. Saved views are private to your account.
      </InfoBanner>
    </div>
  )
}
