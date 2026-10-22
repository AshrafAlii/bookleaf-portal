import { useState, type FormEvent } from 'react'
import { Navigate, useLocation } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import { ApiError } from '../api/client'
import { metaApi } from '../api/endpoints'
import { Button, Field, inputClass } from '../components/Button'
import { useQuery } from '@tanstack/react-query'

export function LoginPage() {
  const { user, signIn, loading } = useAuth()
  const location = useLocation()
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const { data: meta } = useQuery({ queryKey: ['meta'], queryFn: metaApi.meta })

  if (loading) return null
  if (user) {
    const from = (location.state as { from?: string } | null)?.from
    return <Navigate to={from ?? (user.role === 'ADMIN' ? '/admin' : '/author')} replace />
  }

  const handleSubmit = async (event: FormEvent) => {
    event.preventDefault()
    setError(null)
    setSubmitting(true)
    try {
      await signIn(email.trim(), password)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'Unable to sign in. Please try again.')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      <div className="hidden flex-col justify-between bg-leaf-800 p-10 text-leaf-50 lg:flex">
        <div className="flex items-center gap-3">
          <span className="grid h-10 w-10 place-items-center rounded-xl bg-leaf-600 text-xl">🌿</span>
          <div>
            <p className="font-semibold">BookLeaf Publishing</p>
            <p className="text-sm text-leaf-200">Author Support &amp; Communication Portal</p>
          </div>
        </div>

        <div className="max-w-md">
          <h1 className="text-3xl font-semibold leading-tight">
            Everything you need to know about your books, royalties and support requests.
          </h1>
          <p className="mt-4 text-leaf-100">
            Track royalties, follow every query in real time, and get answers from the BookLeaf team —
            all in one place.
          </p>
          <ul className="mt-8 space-y-3 text-sm text-leaf-100">
            <li>• Live royalty figures for every title</li>
            <li>• Raise a query and watch it move in real time</li>
            <li>• AI-drafted replies from the BookLeaf support team</li>
          </ul>
        </div>

        <p className="text-xs text-leaf-300">
          Self-publishing since 2014 · Delhi, India · Amazon, Flipkart &amp; the BookLeaf Store
        </p>
      </div>

      <div className="flex items-center justify-center px-4 py-12">
        <div className="w-full max-w-sm">
          <div className="mb-8 flex items-center gap-3 lg:hidden">
            <span className="grid h-10 w-10 place-items-center rounded-xl bg-leaf-600 text-xl">🌿</span>
            <div>
              <p className="font-semibold text-ink-900">BookLeaf Publishing</p>
              <p className="text-sm text-ink-500">Author Support Portal</p>
            </div>
          </div>

          <h2 className="text-2xl font-semibold text-ink-900">Sign in</h2>
          <p className="mt-1 text-sm text-ink-500">
            Use the email address BookLeaf has on file for your account.
          </p>

          {error ? (
            <div className="mt-5 rounded-lg border border-rose-200 bg-rose-50 px-3 py-2 text-sm text-rose-700">
              {error}
            </div>
          ) : null}

          <form onSubmit={handleSubmit} className="mt-6 space-y-4">
            <Field label="Email address" required>
              <input
                type="email"
                className={inputClass}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@example.com"
                autoComplete="username"
                required
              />
            </Field>

            <Field label="Password" required>
              <input
                type="password"
                className={inputClass}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="••••••••"
                autoComplete="current-password"
                required
              />
            </Field>

            <Button type="submit" loading={submitting} className="w-full">
              Sign in
            </Button>
          </form>

          <div className="mt-8 rounded-lg border border-ink-200 bg-white p-4 text-xs leading-relaxed text-ink-600">
            <p className="font-semibold text-ink-800">Demo credentials</p>
            <p className="mt-1">
              <strong>Author:</strong> priya.sharma@email.com / Author@1234
            </p>
            <p>
              <strong>Admin:</strong> ops@bookleaf.in / Admin@1234
            </p>
          </div>

          {meta ? (
            <p className="mt-4 text-center text-xs text-ink-400">
              AI assistance is{' '}
              <span className={meta.aiEnabled ? 'text-leaf-600' : 'text-amber-600'}>
                {meta.aiEnabled ? `enabled (${meta.aiProvider})` : 'running in fallback mode'}
              </span>
            </p>
          ) : null}
        </div>
      </div>
    </div>
  )
}
