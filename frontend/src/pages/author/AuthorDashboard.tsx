import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { authorApi } from '../../api/endpoints'
import { Card, CardHeader } from '../../components/Card'
import { StatCard } from '../../components/StatCard'
import { StatusBadge, PriorityBadge } from '../../components/Badges'
import { EmptyState, ErrorBanner } from '../../components/Feedback'
import { FullPageSpinner } from '../../components/Spinner'
import { formatDate, formatMoney, formatNumber } from '../../lib/format'

export function AuthorDashboard() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['author', 'dashboard'],
    queryFn: authorApi.dashboard,
  })

  if (isLoading) return <FullPageSpinner label="Loading your dashboard…" />
  if (error) {
    return (
      <ErrorBanner
        message={error instanceof Error ? error.message : 'Unable to load your dashboard.'}
        onRetry={() => void refetch()}
      />
    )
  }
  if (!data) return null

  const { stats } = data

  return (
    <div className="space-y-6">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div>
          <h1 className="text-2xl font-semibold text-ink-900">
            Welcome back, {data.name.split(' ')[0]}
          </h1>
          <p className="mt-1 text-sm text-ink-500">
            {data.authorCode} · {data.city ?? '—'} · Author since{' '}
            {formatDate(data.joinedDate)}
          </p>
        </div>
        <Link
          to="/author/tickets/new"
          className="rounded-lg bg-leaf-600 px-4 py-2 text-sm font-medium text-white hover:bg-leaf-700"
        >
          Raise a support query
        </Link>
      </div>

      <div className="grid gap-4 sm:grid-cols-2 xl:grid-cols-4">
        <StatCard
          label="Royalty earned"
          value={formatMoney(stats.totalRoyaltyEarned)}
          hint={`${formatNumber(stats.totalCopiesSold)} copies sold across ${stats.bookCount} titles`}
          tone="brand"
        />
        <StatCard
          label="Royalty paid"
          value={formatMoney(stats.royaltyPaid)}
          hint="Transferred to your linked bank account"
          tone="positive"
        />
        <StatCard
          label="Royalty pending"
          value={formatMoney(stats.royaltyPending)}
          hint={stats.royaltyPending > 0 ? 'Included in the next quarterly payout' : 'Nothing outstanding'}
          tone={stats.royaltyPending > 0 ? 'warning' : 'default'}
        />
        <StatCard
          label="Open tickets"
          value={stats.openTicketCount}
          hint={stats.openTicketCount > 0 ? 'We are working on these' : 'Nothing needs your attention'}
          tone={stats.openTicketCount > 0 ? 'warning' : 'default'}
        />
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <Card className="lg:col-span-2">
          <CardHeader
            title="Recent tickets"
            subtitle="Updates appear here live — no need to refresh."
            action={
              <Link to="/author/tickets" className="text-sm font-medium text-leaf-700 hover:underline">
                View all
              </Link>
            }
          />
          {data.recentTickets.length === 0 ? (
            <EmptyState
              title="No tickets yet"
              description="When you raise a query it will appear here with its live status."
              icon="🎫"
            />
          ) : (
            <ul className="divide-y divide-ink-100">
              {data.recentTickets.map((ticket) => (
                <li key={ticket.id} className="py-3 first:pt-0 last:pb-0">
                  <Link
                    to={`/author/tickets/${ticket.id}`}
                    className="group flex flex-wrap items-start justify-between gap-2"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-ink-900 group-hover:text-leaf-700">
                        {ticket.subject}
                      </p>
                      <p className="mt-0.5 text-xs text-ink-500">
                        {ticket.ticketCode} · {ticket.categoryLabel} ·{' '}
                        {ticket.bookTitle ?? 'General / Account Level'}
                      </p>
                    </div>
                    <div className="flex shrink-0 items-center gap-2">
                      <PriorityBadge
                        priority={ticket.priority}
                        label={ticket.priorityLabel}
                        source={ticket.prioritySource}
                      />
                      <StatusBadge status={ticket.status} label={ticket.statusLabel} />
                    </div>
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card>
          <CardHeader title="Your titles" subtitle={`${stats.liveBookCount} live · ${stats.inProductionBookCount} in production`} />
          {data.books.length === 0 ? (
            <EmptyState title="No books yet" icon="📚" />
          ) : (
            <ul className="space-y-3">
              {data.books.map((book) => (
                <li key={book.id} className="rounded-lg border border-ink-100 bg-ink-50/40 p-3">
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium text-ink-900">{book.title}</p>
                      <p className="mt-0.5 text-xs text-ink-500">{book.bookCode} · {book.genre ?? '—'}</p>
                    </div>
                    {book.inProduction ? (
                      <span className="shrink-0 rounded-full bg-amber-50 px-2 py-0.5 text-[11px] font-medium text-amber-700">
                        In production
                      </span>
                    ) : (
                      <span className="shrink-0 rounded-full bg-leaf-50 px-2 py-0.5 text-[11px] font-medium text-leaf-700">
                        Live
                      </span>
                    )}
                  </div>
                  <div className="mt-2 flex items-center justify-between text-xs text-ink-500">
                    <span>{formatNumber(book.totalCopiesSold)} copies sold</span>
                    <span className="tabular-nums">
                      {formatMoney(book.royaltyPending)} pending
                    </span>
                  </div>
                </li>
              ))}
            </ul>
          )}
          <Link
            to="/author/books"
            className="mt-4 block text-center text-sm font-medium text-leaf-700 hover:underline"
          >
            View royalty breakdown
          </Link>
        </Card>
      </div>

      {stats.inProductionBookCount > 0 ? (
        <Card>
          <CardHeader
            title="Titles still in production"
            subtitle="These have no MRP or royalty rate yet — the numbers appear once the book goes live."
          />
          <ul className="space-y-2">
            {data.books
              .filter((book) => book.inProduction)
              .map((book) => (
                <li key={book.id} className="flex items-center justify-between text-sm">
                  <span className="text-ink-800">{book.title}</span>
                  <span className="text-ink-500">{book.status}</span>
                </li>
              ))}
          </ul>
        </Card>
      ) : null}
    </div>
  )
}
