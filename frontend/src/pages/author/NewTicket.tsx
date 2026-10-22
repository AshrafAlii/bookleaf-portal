import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useMutation, useQuery } from '@tanstack/react-query'
import { authorApi } from '../../api/endpoints'
import { ApiError } from '../../api/client'
import { Card, CardHeader } from '../../components/Card'
import { Button, Field, inputClass } from '../../components/Button'
import { ErrorBanner, InfoBanner } from '../../components/Feedback'
import { FullPageSpinner } from '../../components/Spinner'

export function NewTicket() {
  const navigate = useNavigate()
  const [bookId, setBookId] = useState<string>('')
  const [subject, setSubject] = useState('')
  const [description, setDescription] = useState('')
  const [attachment, setAttachment] = useState<File | null>(null)
  const [errors, setErrors] = useState<Record<string, string>>({})

  const { data: books, isLoading } = useQuery({
    queryKey: ['author', 'books'],
    queryFn: authorApi.books,
  })

  const createTicket = useMutation({
    mutationFn: () =>
      authorApi.createTicket({
        bookId: bookId || null,
        subject: subject.trim(),
        description: description.trim(),
        attachmentName: attachment?.name ?? null,
      }),
    onSuccess: (ticket) => {
      navigate(`/author/tickets/${ticket.id}`, { replace: true })
    },
    onError: (error) => {
      if (error instanceof ApiError) {
        setErrors(error.fieldErrors)
      }
    },
  })

  const handleSubmit = (event: FormEvent) => {
    event.preventDefault()
    setErrors({})
    createTicket.mutate()
  }

  if (isLoading) return <FullPageSpinner label="Loading your books…" />

  return (
    <div className="mx-auto max-w-3xl space-y-6">
      <div>
        <h1 className="text-2xl font-semibold text-ink-900">Raise a support query</h1>
        <p className="mt-1 text-sm text-ink-500">
          Tell us what is happening. The query is categorised and prioritised automatically, and you will
          see replies here in real time.
        </p>
      </div>

      <Card>
        <CardHeader
          title="New ticket"
          subtitle="The more detail you give, the faster we can resolve it."
        />

        {createTicket.isError && !(createTicket.error instanceof ApiError && Object.keys(createTicket.error.fieldErrors).length > 0) ? (
          <div className="mb-4">
            <ErrorBanner
              title="We could not create your ticket"
              message={
                createTicket.error instanceof Error
                  ? createTicket.error.message
                  : 'Please try again in a moment.'
              }
            />
          </div>
        ) : null}

        <form onSubmit={handleSubmit} className="space-y-5">
          <Field
            label="Which book is this about?"
            hint="Choose General / Account Level for questions about your account, royalties or profile."
            error={errors.bookId}
          >
            <select
              className={inputClass}
              value={bookId}
              onChange={(e) => setBookId(e.target.value)}
            >
              <option value="">General / Account Level</option>
              {(books ?? []).map((book) => (
                <option key={book.id} value={book.id}>
                  {book.title} ({book.bookCode}) — {book.status}
                </option>
              ))}
            </select>
          </Field>

          <Field label="Subject" required error={errors.subject} hint="A short summary, e.g. “Royalty not received for my second book”.">
            <input
              className={inputClass}
              value={subject}
              onChange={(e) => setSubject(e.target.value)}
              placeholder="What is your query about?"
              maxLength={200}
              required
            />
          </Field>

          <Field
            label="Describe the issue"
            required
            error={errors.description}
            hint="Include dates, amounts or references — the more specific, the faster we can help."
          >
            <textarea
              className={`${inputClass} min-h-[160px] resize-y`}
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Tell us what happened, what you expected, and what you have already tried."
              maxLength={8000}
              required
            />
          </Field>

          <Field
            label="Attachment (optional)"
            hint="Add a screenshot or document if it helps. Accepted: images and PDFs up to 5 MB."
          >
            <div className="flex flex-wrap items-center gap-3">
              <input
                type="file"
                accept="image/*,.pdf,.doc,.docx"
                onChange={(e) => setAttachment(e.target.files?.[0] ?? null)}
                className="block w-full text-sm text-ink-600 file:mr-3 file:rounded-lg file:border-0 file:bg-ink-100 file:px-3 file:py-2 file:text-xs file:font-medium file:text-ink-700 hover:file:bg-ink-200"
              />
              {attachment ? (
                <span className="rounded-md bg-leaf-50 px-2 py-1 text-xs font-medium text-leaf-700">
                  {attachment.name}
                </span>
              ) : null}
            </div>
          </Field>

          <InfoBanner>
            Our support team works 6 days a week. Queries about royalties are usually answered within one
            working day; anything needing investigation comes with a 48-hour update.
          </InfoBanner>

          <div className="flex items-center justify-end gap-3">
            <Button type="button" variant="secondary" onClick={() => navigate('/author/tickets')}>
              Cancel
            </Button>
            <Button type="submit" loading={createTicket.isPending}>
              Submit query
            </Button>
          </div>
        </form>
      </Card>
    </div>
  )
}
