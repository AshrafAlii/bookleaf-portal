import type { ReactNode } from 'react'
import { cn } from '../lib/cn'

export function StatCard({
  label,
  value,
  hint,
  icon,
  tone = 'default',
}: {
  label: string
  value: ReactNode
  hint?: ReactNode
  icon?: ReactNode
  tone?: 'default' | 'positive' | 'warning' | 'critical' | 'brand'
}) {
  const toneClasses = {
    default: 'text-ink-900',
    positive: 'text-leaf-600',
    warning: 'text-amber-600',
    critical: 'text-rose-600',
    brand: 'text-leaf-700',
  }[tone]

  return (
    <div className="rounded-xl border border-ink-200/70 bg-white p-4 shadow-card">
      <div className="flex items-start justify-between gap-2">
        <p className="text-xs font-medium uppercase tracking-wide text-ink-500">{label}</p>
        {icon ? <span className="text-ink-400">{icon}</span> : null}
      </div>
      <p className={cn('mt-2 text-2xl font-semibold tabular-nums', toneClasses)}>{value}</p>
      {hint ? <p className="mt-1 text-xs text-ink-500">{hint}</p> : null}
    </div>
  )
}
