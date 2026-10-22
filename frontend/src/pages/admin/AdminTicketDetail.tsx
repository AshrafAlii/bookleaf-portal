import { useEffect, useState, type FormEvent } from 'react'
import { Link, useParams } from 'react-router-dom'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { adminApi } from '../../api/endpoints'
import { subscribeToTicketStream } from '../../api/stream'
import { Card, CardHeader } from '../../components/Card'
import { CategoryBadge, PriorityBadge, StatusBadge } from '../../components/Badges'
import { ErrorBanner, InfoBanner } from '../../components/Feedback'
import { FullPageSpinner, InlineLoader } from '../../components/Spinner'
import { Button, inputClass } from '../../components/Button'
import { formatDate, formatDateTime, formatMoney, formatNumber } from '../../lib/format'
import { cn } from '../../lib/cn'
import type { TicketCategory, TicketPriority, TicketStatus } from '../../api/types'

const STATUSES: TicketStatus[] = ['OPEN', 'IN_PROGRESS', 'RESOLVED', 'CLOSED']
const CATEGORIES: TicketCategory[] = [
  'ROYALTY_PAYMENTS',
  'ISBN_METADATA',
  'PRINTING_QUALITY',
  'DISTRIBUTION_AVAILABILITY',
  'PRODUCTION_UPDATES',
  'GENERAL_INQUIRY',
]
const PRIORITIES: TicketPriority[] = ['CRITICAL', 'HIGH', 'MEDIUM', 'LOW']

export function AdminTicketDetail() {
  const { ticketId = '' } = useParams()
  const queryClient = useQueryClient()
  const [replyBody, setReplyBody] = useState('')
  const [noteBody, setNoteBody] = useState('')
  const [draft, setDraft] = useState<string | null>(null)
  const [draftMeta, setDraftMeta] = useState<string | null>(null)

  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['admin', 'ticket', ticketId],
    queryFn: () => adminApi.ticket(ticketId),
    enabled: Boolean(ticketId),
  })

  useEffect(() => {
    const unsubscribe = subscribeToTicketStream((event) => {
      if (!event.ticketId || event.ticketId === ticketId) {
        void queryClient.invalidateQueries({ queryKey: ['admin', 'ticket', ticketId] })
        void queryClient.invalidateQueries({ queryKey: ['admin', 'queue'] })
        void queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] })
      }
    })
    return unsubscribe
  }, [ticketId, queryClient])

  const invalidate = () => {
    void queryClient.invalidateQueries({ queryKey: ['admin', 'ticket', ticketId] })
    void queryClient.invalidateQueries({ queryKey: ['admin', 'queue'] })
    void queryClient.invalidateQueries({ queryKey: ['admin', 'dashboard'] })
  }

  const changeStatus = useMutation({
    mutationFn: (status: TicketStatus) => adminApi.updateStatus(ticketId, status),
    onSuccess: invalidate,
  })
  const overrideCategory = useMutation({
    mutationFn: (category: TicketCategory) => adminApi.overrideCategory(ticketId, category),
    onSuccess: invalidate,
  })
  const overridePriority = useMutation({
    mutationFn: (priority: TicketPriority) => adminApi.overridePriority(ticketId, priority),
    onSuccess: invalidate,
  })
  const assign = useMutation({
    mutationFn: () => adminApi.assignToMe(ticketId),
    onSuccess: invalidate,
  })
  const sendReply = useMutation({
    mutationFn: () => adminApi.reply(ticketId, replyBody.trim()),
    onSuccess: () => {
      setReplyBody('')
      setDraft(null)
      invalidate()
    },
  })
  const addNote = useMutation({
    mutationFn: () => adminApi.note(ticketId, noteBody.trim()),
    onSuccess: () => {
      setNoteBody('')
      invalidate()
    },
  })
  const generateDraft = useMutation({
    mutationFn: () => adminApi.draft(ticketId),
    onSuccess: (result) => {
      if (result.available && result.body) {
        setDraft(result.body)
        setDraftMeta(
          `Drafted with ${result.provider}/${result.model} · ${result.contextTokens} tokens of knowledge-base context${
            result.note ? ` · ${result.note}` : ''
          }`,
        )
      } else {
        setDraft(null)
        setDraftMeta(result.note ?? 'No draft was generated.')
      }
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
        <Link to="/admin/tickets" className="text-sm text-ink-500 hover:text-ink-800">
          ← Back to the queue
        </Link>
        <div className="mt-2 flex flex-wrap items-start justify-between gap-3">
          <div>
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-mono text-xs text-ink-400">{ticket.ticketCode}</span>
              <StatusBadge status={ticket.status} label={ticket.statusLabel} />
              <PriorityBadge priority={ticket.priority} label={ticket.priorityLabel} source={ticket.prioritySource} />
              <CategoryBadge category={ticket.category} label={ticket.categoryLabel} source={ticket.categorySource} />
            </div>
            <h1 className="mt-2 text-xl font-semibold text-ink-900">{ticket.subject}</h1>
            <p className="mt-1 text-sm text-ink-500">
              {ticket.authorName} · {ticket.bookTitle ?? 'General / Account Level'} · raised{' '}
              {formatDateTime(ticket.createdAt)} · {ticket.ageHours}h old
            </p>
          </div>
          <Button
            variant="secondary"
            size="sm"
            loading={assign.isPending}
            onClick={() => assign.mutate()}
          >
            {ticket.assignedToName ? `Assigned to ${ticket.assignedToName}` : 'Assign to me'}
          </Button>
        </div>
      </div>

      <div className="grid gap-6 lg:grid-cols-3">
        <div className="space-y-6 lg:col-span-2">
          <Card>
            <CardHeader title="The query" />
            <p className="whitespace-pre-wrap text-sm leading-relaxed text-ink-700">{data.description}</p>
            {data.attachmentName ? (
              <p className="mt-4 inline-flex items-center gap-2 rounded-lg bg-ink-100 px-3 py-1.5 text-xs font-medium text-ink-700">
                📎 {data.attachmentName}
              </p>
            ) : null}
          </Card>

          <Card>
            <CardHeader
              title="AI draft"
              subtitle="Grounded in the BookLeaf knowledge base and this author's live account data. Edit before sending."
              action={
                <Button
                  size="sm"
                  variant="secondary"
                  loading={generateDraft.isPending}
                  onClick={() => generateDraft.mutate()}
                >
                  {draft ? 'Regenerate' : 'Generate draft'}
                </Button>
              }
            />
            {generateDraft.isPending ? (
              <InlineLoader label="Asking the model…" />
            ) : draft ? (
              <>
                <textarea
                  className={`${inputClass} min-h-[170px] resize-y leading-relaxed`}
                  value={draft}
                  onChange={(e) => setDraft(e.target.value)}
                />
                {draftMeta ? <p className="mt-2 text-xs text-ink-500">{draftMeta}</p> : null}
                <div className="mt-3 flex items-center justify-end gap-2">
                  <Button variant="ghost" size="sm" onClick={() => setDraft(null)}>
                    Discard
                  </Button>
                  <Button
                    size="sm"
                    loading={sendReply.isPending}
                    onClick={() => {
                      setReplyBody(draft)
                      sendReply.mutate()
                    }}
                  >
                    Use this draft
                  </Button>
                </div>
              </>
            ) : draftMeta ? (
              <InfoBanner tone="warning">{draftMeta}</InfoBanner>
            ) : (
              <InfoBanner>
                Generate a draft to get a suggested reply. The model only states facts that appear in the
                knowledge base or in this author&apos;s account data.
              </InfoBanner>
            )}
          </Card>

          <Card>
            <CardHeader title="Reply to the author" subtitle="This is visible to the author in their portal." />
            <textarea
              className={`${inputClass} min-h-[130px] resize-y`}
              value={replyBody}
              onChange={(e) => setReplyBody(e.target.value)}
              placeholder="Write your reply…"
              maxLength={8000}
            />
            <div className="mt-3 flex items-center justify-between">
              <p className="text-xs text-ink-500">
                Follow the BookLeaf tone: acknowledge first, be specific, own our mistakes, always end with a
                next step and a timeline.
              </p>
              <Button
                size="sm"
                loading={sendReply.isPending}
                disabled={replyBody.trim().length < 2}
                onClick={() => sendReply.mutate()}
              >
                Send reply
              </Button>
            </div>
          </Card>

          <Card>
            <CardHeader title="Conversation" subtitle="Internal notes are never shown to the author." />
            {data.messages.length === 0 ? (
              <p className="text-sm text-ink-500">No messages yet.</p>
            ) : (
              <ul className="space-y-4">
                {data.messages.map((message) => (
                  <li
                    key={message.id}
                    className={cn(
                      'rounded-xl border px-4 py-3',
                      message.internal
                        ? 'border-amber-200 bg-amber-50'
                        : message.authorRole === 'AUTHOR'
                          ? 'border-leaf-200 bg-leaf-50/60'
                          : 'border-ink-200 bg-white',
                    )}
                  >
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-sm font-medium text-ink-900">
                        {message.authorName}
                        {message.internal ? (
                          <span className="ml-2 rounded-full bg-amber-200/70 px-2 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-amber-900">
                            Internal note
                          </span>
                        ) : null}
                      </p>
                      <p className="text-xs text-ink-400">{formatDateTime(message.createdAt)}</p>
                    </div>
                    <p className="mt-1.5 whitespace-pre-wrap text-sm leading-relaxed text-ink-700">
                      {message.body}
                    </p>
                  </li>
                ))}
              </ul>
            )}

            <form
              className="mt-5 border-t border-ink-100 pt-5"
              onSubmit={(event: FormEvent) => {
                event.preventDefault()
                if (noteBody.trim().length >= 2) addNote.mutate()
              }}
            >
              <label className="mb-1.5 block text-sm font-medium text-ink-800">
                Add an internal note
                <span className="ml-2 text-xs font-normal text-ink-500">visible to BookLeaf staff only</span>
              </label>
              <textarea
                className={`${inputClass} min-h-[80px] resize-y`}
                value={noteBody}
                onChange={(e) => setNoteBody(e.target.value)}
                placeholder="Context for the team…"
                maxLength={8000}
              />
              <div className="mt-2 flex justify-end">
                <Button type="submit" size="sm" variant="secondary" loading={addNote.isPending} disabled={noteBody.trim().length < 2}>
                  Add note
                </Button>
              </div>
            </form>
          </Card>
        </div>

        <div className="space-y-6">
          <Card>
            <CardHeader title="Triage" subtitle="Override the model when it gets it wrong." />
            <div className="space-y-4">
              <div>
                <p className="mb-1.5 text-xs font-medium uppercase tracking-wide text-ink-500">Status</p>
                <div className="flex flex-wrap gap-1.5">
                  {STATUSES.map((status) => (
                    <button
                      key={status}
                      type="button"
                      disabled={changeStatus.isPending}
                      onClick={() => changeStatus.mutate(status)}
                      className={cn(
                        'rounded-lg px-2.5 py-1 text-xs font-medium ring-1 ring-inset transition',
                        ticket.status === status
                          ? 'bg-ink-900 text-white ring-ink-900'
                          : 'bg-white text-ink-600 ring-ink-200 hover:bg-ink-50',
                      )}
                    >
                      {status.replace('_', ' ')}
                    </button>
                  ))}
                </div>
              </div>

              <div>
                <p className="mb-1.5 text-xs font-medium uppercase tracking-wide text-ink-500">Category</p>
                <select
                  className={inputClass}
                  value={ticket.category}
                  disabled={overrideCategory.isPending}
                  onChange={(e) => overrideCategory.mutate(e.target.value as TicketCategory)}
                >
                  {CATEGORIES.map((category) => (
                    <option key={category} value={category}>
                      {category.replace(/_/g, ' ')}
                    </option>
                  ))}
                </select>
              </div>

              <div>
                <p className="mb-1.5 text-xs font-medium uppercase tracking-wide text-ink-500">Priority</p>
                <select
                  className={inputClass}
                  value={ticket.priority}
                  disabled={overridePriority.isPending}
                  onChange={(e) => overridePriority.mutate(e.target.value as TicketPriority)}
                >
                  {PRIORITIES.map((priority) => (
                    <option key={priority} value={priority}>
                      {priority}
                    </option>
                  ))}
                </select>
              </div>

              {data.priorityRationale ? (
                <div className="rounded-lg bg-ink-50 px-3 py-2.5">
                  <p className="text-xs font-medium uppercase tracking-wide text-ink-500">
                    Model rationale
                  </p>
                  <p className="mt-1 text-xs leading-relaxed text-ink-600">{data.priorityRationale}</p>
                </div>
              ) : null}
            </div>
          </Card>

          <Card>
            <CardHeader title="Author" subtitle={`${data.author.authorCode} · ${data.author.city ?? '—'}`} />
            <p className="text-sm font-medium text-ink-900">{data.author.name}</p>
            <p className="text-xs text-ink-500">{data.author.email}</p>
          </Card>

          {data.book ? (
            <Card>
              <CardHeader title="Book" subtitle={data.book.bookCode} />
              <p className="text-sm font-medium text-ink-900">{data.book.title}</p>
              <p className="mt-0.5 font-mono text-xs text-ink-500">{data.book.isbn}</p>
              <dl className="mt-3 space-y-2 text-sm">
                <Row label="Status" value={data.book.status} />
                <Row label="MRP" value={formatMoney(data.book.mrp)} />
                <Row label="Royalty / copy" value={formatMoney(data.book.authorRoyaltyPerCopy)} />
                <Row label="Copies sold" value={formatNumber(data.book.totalCopiesSold)} />
                <Row label="Royalty earned" value={formatMoney(data.book.totalRoyaltyEarned)} />
                <Row label="Royalty paid" value={formatMoney(data.book.royaltyPaid)} />
                <Row
                  label="Royalty pending"
                  value={formatMoney(data.book.royaltyPending)}
                  highlight={data.book.royaltyPending > 0}
                />
                <Row
                  label="Last payout"
                  value={data.book.lastRoyaltyPayoutDate ? formatDate(data.book.lastRoyaltyPayoutDate) : 'None yet'}
                />
              </dl>
            </Card>
          ) : null}

          <Card>
            <CardHeader title="Audit trail" />
            {data.events.length === 0 ? (
              <p className="text-sm text-ink-500">No activity yet.</p>
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

function Row({ label, value, highlight }: { label: string; value: string; highlight?: boolean }) {
  return (
    <div className="flex items-center justify-between gap-3">
      <dt className="text-ink-500">{label}</dt>
      <dd className={cn('font-medium tabular-nums', highlight ? 'text-amber-700' : 'text-ink-900')}>{value}</dd>
    </div>
  )
}
