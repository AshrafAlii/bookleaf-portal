import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { authorApi } from '../../api/endpoints'
import { Card, CardHeader } from '../../components/Card'
import { EmptyState, ErrorBanner, InfoBanner } from '../../components/Feedback'
import { FullPageSpinner } from '../../components/Spinner'
import { formatDate, formatMoney, formatNumber } from '../../lib/format'
import { cn } from '../../lib/cn'

export function AuthorBooks() {
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['author', 'books'],
    queryFn: authorApi.books,
  })

  if (isLoading) return <FullPageSpinner label="Loading your books…" />
  if (error) {
    return (
      <ErrorBanner
        message={error instanceof Error ? error.message : 'Unable to load your books.'}
        onRetry={() => void refetch()}
      />
    )
  }
  if (!data) return null

  const selected = data.find((book) => book.id === selectedId) ?? null

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-ink-900">My Books</h1>
        <p className="mt-1 text-sm text-ink-500">
          Every title you publish with BookLeaf, with the royalty figures we hold for it.
        </p>
      </div>

      {data.length === 0 ? (
        <EmptyState
          title="No books yet"
          description="Titles appear here as soon as BookLeaf starts work on them."
          icon="📚"
        />
      ) : (
        <>
          <Card padded={false}>
            <div className="overflow-x-auto">
              <table className="w-full min-w-[900px] text-sm">
                <thead>
                  <tr className="border-b border-ink-200 bg-ink-50/60 text-left text-xs uppercase tracking-wide text-ink-500">
                    <th className="px-4 py-3 font-medium">Title</th>
                    <th className="px-4 py-3 font-medium">ISBN</th>
                    <th className="px-4 py-3 font-medium">Genre</th>
                    <th className="px-4 py-3 font-medium">Published</th>
                    <th className="px-4 py-3 font-medium">Status</th>
                    <th className="px-4 py-3 text-right font-medium">MRP</th>
                    <th className="px-4 py-3 text-right font-medium">Copies sold</th>
                    <th className="px-4 py-3 text-right font-medium">Royalty earned</th>
                    <th className="px-4 py-3 text-right font-medium">Paid</th>
                    <th className="px-4 py-3 text-right font-medium">Pending</th>
                    <th className="px-4 py-3" />
                  </tr>
                </thead>
                <tbody className="divide-y divide-ink-100">
                  {data.map((book) => (
                    <tr key={book.id} className="hover:bg-ink-50/50">
                      <td className="px-4 py-3">
                        <p className="font-medium text-ink-900">{book.title}</p>
                        <p className="text-xs text-ink-500">{book.bookCode}</p>
                      </td>
                      <td className="px-4 py-3 font-mono text-xs text-ink-600">{book.isbn}</td>
                      <td className="px-4 py-3 text-ink-600">{book.genre ?? '—'}</td>
                      <td className="px-4 py-3 text-ink-600">
                        {book.publicationDate ? formatDate(book.publicationDate) : '—'}
                      </td>
                      <td className="px-4 py-3">
                        <span
                          className={cn(
                            'rounded-full px-2 py-0.5 text-xs font-medium',
                            book.inProduction
                              ? 'bg-amber-50 text-amber-700'
                              : 'bg-leaf-50 text-leaf-700',
                          )}
                        >
                          {book.status}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right tabular-nums text-ink-700">
                        {formatMoney(book.mrp)}
                      </td>
                      <td className="px-4 py-3 text-right tabular-nums text-ink-700">
                        {formatNumber(book.totalCopiesSold)}
                      </td>
                      <td className="px-4 py-3 text-right tabular-nums text-ink-700">
                        {formatMoney(book.totalRoyaltyEarned)}
                      </td>
                      <td className="px-4 py-3 text-right tabular-nums text-leaf-700">
                        {formatMoney(book.royaltyPaid)}
                      </td>
                      <td className="px-4 py-3 text-right tabular-nums">
                        <span className={book.royaltyPending > 0 ? 'text-amber-700' : 'text-ink-400'}>
                          {formatMoney(book.royaltyPending)}
                        </span>
                      </td>
                      <td className="px-4 py-3 text-right">
                        <button
                          type="button"
                          onClick={() => setSelectedId(book.id)}
                          className="text-xs font-medium text-leaf-700 hover:underline"
                        >
                          Details
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </Card>

          {selected ? (
            <Card>
              <CardHeader
                title={selected.title}
                subtitle={`${selected.bookCode} · ${selected.isbn}`}
                action={
                  <button
                    type="button"
                    onClick={() => setSelectedId(null)}
                    className="text-sm text-ink-500 hover:text-ink-800"
                  >
                    Close
                  </button>
                }
              />
              <dl className="grid gap-x-8 gap-y-3 sm:grid-cols-2 lg:grid-cols-3">
                <Detail label="Status" value={selected.status} />
                <Detail label="Genre" value={selected.genre ?? '—'} />
                <Detail
                  label="Publication date"
                  value={selected.publicationDate ? formatDate(selected.publicationDate) : 'Not yet published'}
                />
                <Detail label="MRP" value={formatMoney(selected.mrp, true)} />
                <Detail label="Royalty per copy" value={formatMoney(selected.authorRoyaltyPerCopy, true)} />
                <Detail label="Print partner" value={selected.printPartner ?? 'Not assigned'} />
                <Detail label="Copies sold" value={formatNumber(selected.totalCopiesSold)} />
                <Detail label="Royalty earned" value={formatMoney(selected.totalRoyaltyEarned, true)} />
                <Detail label="Royalty paid" value={formatMoney(selected.royaltyPaid, true)} />
                <Detail label="Royalty pending" value={formatMoney(selected.royaltyPending, true)} />
                <Detail
                  label="Last payout"
                  value={selected.lastRoyaltyPayoutDate ? formatDate(selected.lastRoyaltyPayoutDate) : 'No payout yet'}
                />
              </dl>

              <div className="mt-5">
                <p className="text-xs font-medium uppercase tracking-wide text-ink-500">
                  Available on
                </p>
                {selected.availableOn.length === 0 ? (
                  <p className="mt-1 text-sm text-ink-500">
                    Not listed on any store yet — listings go live 7–10 business days after publication.
                  </p>
                ) : (
                  <div className="mt-2 flex flex-wrap gap-2">
                    {selected.availableOn.map((platform) => (
                      <span
                        key={platform}
                        className="rounded-full bg-leaf-50 px-2.5 py-1 text-xs font-medium text-leaf-700"
                      >
                        {platform}
                      </span>
                    ))}
                  </div>
                )}
              </div>

              {selected.inProduction ? (
                <div className="mt-5">
                  <InfoBanner tone="warning">
                    This title is still in production, so MRP and royalty figures are not applicable yet.
                    They appear here once the book reaches <strong>Published &amp; Live</strong>.
                  </InfoBanner>
                </div>
              ) : null}
            </Card>
          ) : null}
        </>
      )}
    </div>
  )
}

function Detail({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs uppercase tracking-wide text-ink-500">{label}</dt>
      <dd className="mt-0.5 text-sm font-medium text-ink-900">{value}</dd>
    </div>
  )
}
