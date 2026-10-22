import { cn } from '../lib/cn'
import type { DecisionSource, TicketCategory, TicketPriority, TicketStatus } from '../api/types'

const STATUS_STYLES: Record<TicketStatus, string> = {
  OPEN: 'bg-sky-50 text-sky-700 ring-sky-600/20',
  IN_PROGRESS: 'bg-amber-50 text-amber-700 ring-amber-600/20',
  RESOLVED: 'bg-leaf-50 text-leaf-700 ring-leaf-600/20',
  CLOSED: 'bg-ink-100 text-ink-600 ring-ink-500/20',
}

export function StatusBadge({ status, label }: { status: TicketStatus; label?: string }) {
  return (
    <span
      className={cn(
        'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset',
        STATUS_STYLES[status],
      )}
    >
      {label ?? status}
    </span>
  )
}

const PRIORITY_STYLES: Record<TicketPriority, string> = {
  CRITICAL: 'bg-rose-50 text-rose-700 ring-rose-600/25',
  HIGH: 'bg-orange-50 text-orange-700 ring-orange-600/25',
  MEDIUM: 'bg-sky-50 text-sky-700 ring-sky-600/20',
  LOW: 'bg-ink-100 text-ink-600 ring-ink-500/20',
}

export function PriorityBadge({
  priority,
  label,
  source,
}: {
  priority: TicketPriority
  label?: string
  source?: DecisionSource
}) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span
        className={cn(
          'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset',
          PRIORITY_STYLES[priority],
        )}
      >
        {label ?? priority}
      </span>
      {source === 'MANUAL' ? (
        <span
          title="Manually overridden by a BookLeaf admin"
          className="rounded-full bg-ink-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-ink-500"
        >
          Override
        </span>
      ) : null}
      {source === 'HEURISTIC' ? (
        <span
          title="Classified by the built-in fallback rules because the AI provider was unavailable"
          className="rounded-full bg-amber-50 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-amber-700"
        >
          Fallback
        </span>
      ) : null}
    </span>
  )
}

const CATEGORY_STYLES: Record<TicketCategory, string> = {
  ROYALTY_PAYMENTS: 'bg-emerald-50 text-emerald-700 ring-emerald-600/20',
  ISBN_METADATA: 'bg-violet-50 text-violet-700 ring-violet-600/20',
  PRINTING_QUALITY: 'bg-teal-50 text-teal-700 ring-teal-600/20',
  DISTRIBUTION_AVAILABILITY: 'bg-blue-50 text-blue-700 ring-blue-600/20',
  PRODUCTION_UPDATES: 'bg-amber-50 text-amber-700 ring-amber-600/20',
  GENERAL_INQUIRY: 'bg-ink-100 text-ink-600 ring-ink-500/20',
}

export function CategoryBadge({
  category,
  label,
  source,
}: {
  category: TicketCategory
  label?: string
  source?: DecisionSource
}) {
  return (
    <span className="inline-flex items-center gap-1.5">
      <span
        className={cn(
          'inline-flex items-center rounded-full px-2.5 py-0.5 text-xs font-medium ring-1 ring-inset',
          CATEGORY_STYLES[category],
        )}
      >
        {label ?? category}
      </span>
      {source === 'MANUAL' ? (
        <span
          title="Manually overridden by a BookLeaf admin"
          className="rounded-full bg-ink-100 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-ink-500"
        >
          Override
        </span>
      ) : null}
      {source === 'HEURISTIC' ? (
        <span
          title="Classified by the built-in fallback rules because the AI provider was unavailable"
          className="rounded-full bg-amber-50 px-1.5 py-0.5 text-[10px] font-semibold uppercase tracking-wide text-amber-700"
        >
          Fallback
        </span>
      ) : null}
    </span>
  )
}
