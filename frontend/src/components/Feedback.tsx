import type { ReactNode } from 'react'

export function EmptyState({
  title,
  description,
  action,
  icon = '📭',
}: {
  title: string
  description?: string
  action?: ReactNode
  icon?: string
}) {
  return (
    <div className="flex flex-col items-center justify-center gap-2 rounded-xl border border-dashed border-ink-200 bg-ink-50/50 px-6 py-12 text-center">
      <span className="text-2xl" aria-hidden>
        {icon}
      </span>
      <p className="text-sm font-medium text-ink-800">{title}</p>
      {description ? <p className="max-w-md text-sm text-ink-500">{description}</p> : null}
      {action ? <div className="mt-2">{action}</div> : null}
    </div>
  )
}

export function ErrorBanner({
  title = 'Something went wrong',
  message,
  onRetry,
}: {
  title?: string
  message: string
  onRetry?: () => void
}) {
  return (
    <div className="rounded-xl border border-rose-200 bg-rose-50 px-4 py-3">
      <p className="text-sm font-semibold text-rose-800">{title}</p>
      <p className="mt-0.5 text-sm text-rose-700">{message}</p>
      {onRetry ? (
        <button
          type="button"
          onClick={onRetry}
          className="mt-2 rounded-lg bg-rose-600 px-3 py-1.5 text-xs font-semibold text-white hover:bg-rose-700"
        >
          Try again
        </button>
      ) : null}
    </div>
  )
}

export function InfoBanner({
  tone = 'info',
  children,
}: {
  tone?: 'info' | 'warning' | 'success'
  children: ReactNode
}) {
  const tones = {
    info: 'border-sky-200 bg-sky-50 text-sky-900',
    warning: 'border-amber-200 bg-amber-50 text-amber-900',
    success: 'border-leaf-200 bg-leaf-50 text-leaf-900',
  }[tone]
  return (
    <div className={`rounded-xl border px-4 py-3 text-sm ${tones}`}>{children}</div>
  )
}
