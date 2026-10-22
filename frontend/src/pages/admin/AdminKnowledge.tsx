import { useQuery } from '@tanstack/react-query'
import { metaApi } from '../../api/endpoints'
import { Card, CardHeader } from '../../components/Card'
import { ErrorBanner, InfoBanner } from '../../components/Feedback'
import { FullPageSpinner } from '../../components/Spinner'
import { formatNumber } from '../../lib/format'

/**
 * Shows the knowledge base the AI drafts from, with the token cost of each
 * section. Useful for the ops team to see exactly what the model is allowed to
 * rely on, and for a reviewer to judge the retrieval strategy.
 */
export function AdminKnowledge() {
  const { data, isLoading, error, refetch } = useQuery({
    queryKey: ['meta', 'knowledge-base'],
    queryFn: metaApi.knowledgeBase,
  })

  if (isLoading) return <FullPageSpinner label="Loading the knowledge base…" />
  if (error) {
    return (
      <ErrorBanner
        message={error instanceof Error ? error.message : 'Unable to load the knowledge base.'}
        onRetry={() => void refetch()}
      />
    )
  }
  if (!data) return null

  const policy = data.sections.filter((section) => !section.title.startsWith('Example - '))
  const examples = data.sections.filter((section) => section.title.startsWith('Example - '))

  return (
    <div className="space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-ink-900">Knowledge base</h1>
        <p className="mt-1 text-sm text-ink-500">
          The single source of truth the AI is allowed to quote. Version {data.version} ·{' '}
          {formatNumber(data.totalTokens)} tokens in total.
        </p>
      </div>

      <InfoBanner>
        When a draft is generated, only the 2–3 most relevant policy sections are sent to the model (plus the
        tone guide and one calibration example) instead of the whole knowledge base. That keeps the prompt
        under ~900 tokens instead of ~1,500, and editing any section below automatically invalidates cached
        drafts.
      </InfoBanner>

      <Card>
        <CardHeader title="Policy sections" subtitle="Company overview, royalty, ISBN, printing, distribution, production and tone." />
        <div className="space-y-4">
          {policy.map((section) => (
            <section key={section.title} className="rounded-lg border border-ink-100">
              <div className="flex items-center justify-between gap-3 border-b border-ink-100 bg-ink-50/60 px-4 py-2">
                <h3 className="text-sm font-semibold text-ink-900">{section.title}</h3>
                <span className="rounded-full bg-white px-2 py-0.5 text-[11px] font-medium text-ink-500 ring-1 ring-inset ring-ink-200">
                  ~{section.approxTokens} tokens
                </span>
              </div>
              <pre className="whitespace-pre-wrap px-4 py-3 font-sans text-xs leading-relaxed text-ink-600">
                {section.body}
              </pre>
            </section>
          ))}
        </div>
      </Card>

      <Card>
        <CardHeader
          title="Calibration examples"
          subtitle="Query → response patterns the model mirrors for the matching category."
        />
        <div className="space-y-4">
          {examples.map((section) => (
            <section key={section.title} className="rounded-lg border border-ink-100">
              <div className="flex items-center justify-between gap-3 border-b border-ink-100 bg-ink-50/60 px-4 py-2">
                <h3 className="text-sm font-semibold text-ink-900">{section.title}</h3>
                <span className="rounded-full bg-white px-2 py-0.5 text-[11px] font-medium text-ink-500 ring-1 ring-inset ring-ink-200">
                  ~{section.approxTokens} tokens
                </span>
              </div>
              <pre className="whitespace-pre-wrap px-4 py-3 font-sans text-xs leading-relaxed text-ink-600">
                {section.body}
              </pre>
            </section>
          ))}
        </div>
      </Card>
    </div>
  )
}
