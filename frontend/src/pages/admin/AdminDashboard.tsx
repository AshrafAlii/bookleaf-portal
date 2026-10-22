import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { adminApi, aiStatusApi } from '../../api/endpoints'
import { Card, CardHeader } from '../../components/Card'
import { StatCard } from '../../components/StatCard'
import { PriorityBadge, StatusBadge } from '../../components/Badges'
import { EmptyState, ErrorBanner, InfoBanner } from '../../components/Feedback'
import { FullPageSpinner } from '../../components/Spinner'
import { formatAge, formatNumber } from '../../lib/format'
import { cn } from '../../lib/cn'

export function AdminDashboard() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['admin', 'dashboard'],
    queryFn: adminApi.dashboard,
  })
  const [aiStatus, setAiStatus] = useState<{ ok: boolean; message: string } | null>(null)

  if (isLoading) return <FullPageSpinner label="Loading the support desk…" />
  if (error) {
    return (
      <ErrorBanner
        message={error instanceof Error ? error.message : 'Unable to load the dashboard.'}
        onRetry={() => void refetch()}
      />
    )
  }
  if (!data) return null

  const { queue, ai } = data

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-ink-900">Support operations</h1>
          <p className="mt-1 text-sm text-ink-500">
            {formatNumber(data.totalAuthors)} authors · {formatNumber(data.totalBooks)} titles in the catalogue
          </p>
        </div>
        <Link
          to="/admin/tickets"
          className="rounded-lg bg-leaf-600 px-4 py-2 text-sm font-medium text-white hover:bg-leaf-700"
        >
          Open the ticket queue
        </Link>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Open"
          value={queue.open}
          hint={`${queue.inProgress} in progress`}
          tone={queue.open > 0 ? 'warning' : 'default'}
        />
        <StatCard
          label="Awaiting first reply"
          value={queue.awaitingFirstResponse}
          hint="Tickets with no reply yet"
          tone={queue.awaitingFirstResponse > 0 ? 'warning' : 'positive'}
        />
        <StatCard
          label="Overdue (>48h)"
          value={queue.overdue}
          hint="Breaching our 48-hour promise"
          tone={queue.overdue > 0 ? 'critical' : 'positive'}
        />
        <StatCard
          label="Avg. first response"
          value={`${queue.averageFirstResponseHours}h`}
          hint="Across all answered tickets"
          tone="brand"
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader
            title="Needs attention now"
            subtitle="Critical and high priority tickets that are still open, oldest first."
          />
          {data.urgentTickets.length === 0 ? (
            <EmptyState title="Nothing urgent" description="The queue is clear of critical work." icon="✅" />
          ) : (
            <ul className="divide-y divide-ink-100">
              {data.urgentTickets.map((ticket) => (
                <li key={ticket.id} className="py-3 first:pt-0 last:pb-0">
                  <Link
                    to={`/admin/tickets/${ticket.id}`}
                    className="group flex flex-wrap items-start justify-between gap-3"
                  >
                    <div className="min-w-0">
                      <div className="flex flex-wrap items-center gap-2">
                        <span className="font-mono text-xs text-ink-400">{ticket.ticketCode}</span>
                        <PriorityBadge
                          priority={ticket.priority}
                          label={ticket.priorityLabel}
                          source={ticket.prioritySource}
                        />
                        <StatusBadge status={ticket.status} label={ticket.statusLabel} />
                      </div>
                      <p className="mt-1 truncate text-sm font-medium text-ink-900 group-hover:text-leaf-700">
                        {ticket.subject}
                      </p>
                      <p className="mt-0.5 text-xs text-ink-500">
                        {ticket.authorName} · {ticket.bookTitle ?? 'General / Account Level'} ·{' '}
                        {formatAge(ticket.ageHours)} old
                      </p>
                    </div>
                    <span className="shrink-0 text-xs text-ink-400">
                      {ticket.assignedToName ? `Assigned to ${ticket.assignedToName}` : 'Unassigned'}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card>
          <CardHeader title="AI usage" subtitle="Cost and reliability of the assistance layer." />
          <dl className="space-y-3 text-sm">
            <div className="flex items-center justify-between">
              <dt className="text-ink-500">Provider</dt>
              <dd className="font-medium text-ink-900">{ai.provider ?? '—'}</dd>
            </div>
            <div className="flex items-center justify-between">
              <dt className="text-ink-500">Model</dt>
              <dd className="font-medium text-ink-900">{ai.model ?? '—'}</dd>
            </div>
            <div className="flex items-center justify-between">
              <dt className="text-ink-500">Calls logged</dt>
              <dd className="font-medium tabular-nums text-ink-900">{ai.totalCalls}</dd>
            </div>
            <div className="flex items-center justify-between">
              <dt className="text-ink-500">Served from cache</dt>
              <dd className="font-medium tabular-nums text-leaf-700">{ai.cachedCalls}</dd>
            </div>
            <div className="flex items-center justify-between">
              <dt className="text-ink-500">Prompt tokens</dt>
              <dd className="font-medium tabular-nums text-ink-900">{formatNumber(ai.promptTokens)}</dd>
            </div>
            <div className="flex items-center justify-between">
              <dt className="text-ink-500">Completion tokens</dt>
              <dd className="font-medium tabular-nums text-ink-900">{formatNumber(ai.completionTokens)}</dd>
            </div>
            <div className="flex items-center justify-between">
              <dt className="text-ink-500">Avg. latency</dt>
              <dd className="font-medium tabular-nums text-ink-900">{ai.averageLatencyMs} ms</dd>
            </div>
            <div className="flex items-center justify-between">
              <dt className="text-ink-500">Failed calls</dt>
              <dd className={`font-medium tabular-nums ${ai.failedCalls > 0 ? 'text-rose-600' : 'text-ink-900'}`}>
                {ai.failedCalls}
              </dd>
            </div>
          </dl>
          <button
            type="button"
            onClick={async () => {
              try {
                const status = await aiStatusApi.status()
                setAiStatus({
                  ok: status.enabled && status.message?.startsWith('Reachable') === true,
                  message: status.message ?? 'No response',
                })
              } catch (err) {
                setAiStatus({
                  ok: false,
                  message: err instanceof Error ? err.message : 'The check failed',
                })
              }
            }}
            className="mt-3 w-full rounded-lg border border-ink-200 px-3 py-1.5 text-xs font-medium text-ink-700 hover:bg-ink-50"
          >
            Test the AI connection
          </button>
          {aiStatus ? (
            <p
              className={cn(
                'mt-2 text-xs',
                aiStatus.ok ? 'text-leaf-700' : 'text-amber-700',
              )}
            >
              {aiStatus.message}
            </p>
          ) : null}

          {ai.provider === 'none' ? (
            <div className="mt-4">
              <InfoBanner tone="warning">
                No AI provider is configured. Tickets are classified with the built-in fallback rules and
                replies are written by hand. Set <code>ANTHROPIC_API_KEY</code> or{' '}
                <code>OPENAI_API_KEY</code> to enable AI assistance.
              </InfoBanner>
            </div>
          ) : null}
        </Card>
      </div>
    </div>
  )
}
