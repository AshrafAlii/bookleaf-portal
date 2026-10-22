import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { authorApi } from '../../api/endpoints'
import { subscribeToTicketStream } from '../../api/stream'
import { Card, CardHeader } from '../../components/Card'
import { PriorityBadge, StatusBadge } from '../../components/Badges'
import { ErrorBanner, InfoBanner } from '../../components/Feedback'
import { FullPageSpinner } from '../../components/Spinner'
import { Button, inputClass } from '../../components/Button'
import { formatDate, formatDateTime, formatMoney } from '../../lib/format'
import { cn } from '../../lib/cn'

export function AuthorTicketDetail() {
  const { ticketId = '' } = useParams()
  const queryClient = useQueryClient()
  const [reply, setReply] = useState('')
  const [justReplied, setJustReplied] = useState(false)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['author', 'ticket', ticketId],
    queryFn: () => authorApi.ticket(ticketId),
    enabled: Boolean(ticketId),
  })

  useEffect(() => {
    const unsubscribe = subscribeToTicketStream((event) => {
      if (!event.ticketId || event.ticketId === ticketId) {
        void queryClient.invalidateQueries({ queryKey: ['author', 'ticket', ticketId] })
        void queryClient.invalidateQueries({ queryKey: ['author', 'tickets'] })
      }
    })
    return unsubscribe
  }, [ticketId, queryClient])

  const sendReply = useMutation({
    mutationFn: () => authorApi.reply(ticketId, reply.trim()),
    onSuccess: () => {
      setReply('')
      setJustReplied(true)
      void queryClient.invalidateQueries({ queryKey: ['author', 'ticket', ticketId] })
    },
  })

  if (isLoading) return <FullPageSpinner label="Loading ticket…" />
  if (error) {
    return (
      <ErrorBanner
        message={error instanceof Error ? error.message : 'Unable to load this ticket.'}
        onRetry={() => void refetch()}
      />
    )
  }
  if (!data) return null

  const { ticket } = data

  return (
    <div className="space-y-6">
      <div>
        <Link to="/author/tickets" className="text-sm text-ink-500 hover:text-ink-800">
          ← Back to my tickets
        </Link>
        <div className="mt-2 flex flex-wrap items-start justify-between gap-3">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-mono text-xs text-ink-400">{ticket.ticketCode}</span>
              <StatusBadge status={ticket.status} label={ticket.statusLabel} />
              <PriorityBadge
                priority={ticket.priority}
                label={ticket.priorityLabel}
                source={ticket.prioritySource}
              />
            </div>
            <h1 className="mt-2 text-xl font-semibold text-ink-900">{ticket.subject}</h1>
            <p className="mt-1 text-sm text-ink-500">
              {ticket.bookTitle ?? 'General / Account Level'} · raised {formatDateTime(ticket.createdAt)}
            </p>
          </div>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <Card>
            <CardHeader title="Your query" />
            <p className="whitespace-pre-wrap text-sm leading-relaxed text-ink-700">{data.description}</p>
            {data.attachmentName ? (
              <p className="mt-4 inline-flex items-center gap-2 rounded-lg bg-ink-100 px-3 py-1.5 text-xs font-medium text-ink-700">
                📎 {data.attachmentName}
              </p>
            ) : null}
          </Card>

          <Card>
            <CardHeader
              title="Conversation"
              subtitle="Replies from the BookLeaf team appear here automatically."
            />
            {data.messages.length === 0 ? (
              <InfoBanner>No replies yet. Our team will respond here.</InfoBanner>
            ) : (
              <ul className="space-y-4">
                {data.messages.map((message) => {
                  const fromAuthor = message.authorRole === 'AUTHOR'
                  return (
                    <li
                      key={message.id}
                      className={cn(
                        'rounded-xl border px-4 py-3',
                        fromAuthor ? 'border-leaf-200 bg-leaf-50/60' : 'border-ink-200 bg-white',
                      )}
                    >
                      <div className="flex items-center justify-between gap-2">
                        <p className="text-sm font-medium text-ink-900">{message.authorName}</p>
                        <p className="text-xs text-ink-400">{formatDateTime(message.createdAt)}</p>
                      </div>
                      <p className="mt-1.5 whitespace-pre-wrap text-sm leading-relaxed text-ink-700">
                        {message.body}
                      </p>
                    </li>
                  )
                })}
              </ul>
            )}

            {!ticket.status || ticket.status !== 'CLOSED' ? (
              <form
                className="mt-5 border-t border-ink-100 pt-5"
                onSubmit={(event: FormEvent) => {
                  event.preventDefault()
                  if (reply.trim().length >= 2) sendReply.mutate()
                }}
              >
                <label className="mb-1.5 block text-sm font-medium text-ink-800">Add a follow-up</label>
                <textarea
                  className={`${inputClass} min-h-[90px] resize-y`}
                  value={reply}
                  onChange={(e) => {
                    setReply(e.target.value)
                    setJustReplied(false)
                  }}
                  placeholder="Reply to the BookLeaf team…"
                  maxLength={8000}
                />
                <div className="mt-2 flex items-center justify-between">
                  <p className="text-xs text-ink-500">
                    {justReplied ? 'Reply sent — thank you.' : 'Replies are visible to the BookLeaf team.'}
                  </p>
                  <Button type="submit" size="sm" loading={sendReply.isPending} disabled={reply.trim().length < 2}>
                    Send reply
                  </Button>
                </div>
              </form>
            ) : (
              <p className="mt-5 border-t border-ink-100 pt-5 text-sm text-ink-500">
                This ticket is closed. Raise a new query if you need further help.
              </p>
            )}
          </Card>
        </div>

        <div className="space-y-6">
          {data.book ? (
            <Card>
              <CardHeader title="Book" subtitle={data.book.bookCode} />
              <p className="text-sm font-medium text-ink-900">{data.book.title}</p>
              <dl className="mt-3 space-y-2 text-sm">
                <Row label="Status" value={data.book.status} />
                <Row label="MRP" value={formatMoney(data.book.mrp)} />
                <Row label="Copies sold" value={String(data.book.totalCopiesSold)} />
                <Row label="Royalty earned" value={formatMoney(data.book.totalRoyaltyEarned)} />
                <Row label="Royalty paid" value={formatMoney(data.book.royaltyPaid)} />
                <Row label="Royalty pending" value={formatMoney(data.book.royaltyPending)} />
                <Row
                  label="Last payout"
                  value={data.book.lastRoyaltyPayoutDate ? formatDate(data.book.lastRoyaltyPayoutDate) : 'None yet'}
                />
              </dl>
            </Card>
          ) : null}

          <Card>
            <CardHeader title="Activity" />
            {data.events.length === 0 ? (
              <p className="text-sm text-ink-500">No activity recorded yet.</p>
            ) : (
              <ol className="space-y-3">
                {data.events.map((event) => (
                  <li key={event.id} className="border-l-2 border-ink-100 pl-3">
                    <p className="text-xs font-medium uppercase tracking-wide text-ink-500">
                      {event.eventType.replace(/_/g, ' ')}
                    </p>
                    {event.detail ? <p className="text-sm text-ink-700">{event.detail}</p> : null}
                    <p className="text-xs text-ink-400">
                      {event.actorName} · {formatDateTime(event.createdAt)}
                    </p>
                  </li>
                ))}
              </ol>
            )}
          </Card>
        </div>
      </div>
    </div>
  )
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <dt className="text-ink-500">{label}</dt>
      <dd className="font-medium tabular-nums text-ink-900">{value}</dd>
    </div>
  )
}
