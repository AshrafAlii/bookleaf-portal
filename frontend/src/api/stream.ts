import { getAccessToken } from './client'
import type { TicketStreamEvent } from './types'

/**
 * Server-Sent Events client.
 *
 * `EventSource` cannot send an `Authorization` header, and our SSE endpoint is
 * bearer-protected, so we read the event stream with `fetch` and parse the
 * `text/event-stream` framing ourselves. It also gives us an AbortController we
 * can use to reconnect with backoff.
 */
export function subscribeToTicketStream(
  onEvent: (event: TicketStreamEvent) => void,
  onStatus?: (connected: boolean) => void,
): () => void {
  let controller: AbortController | null = null
  let closed = false

  const connect = async () => {
    while (!closed) {
      controller = new AbortController()
      const token = getAccessToken()
      if (!token) {
        // The access token may not be in memory yet when this effect first runs.
        // Wait for it rather than giving up — otherwise the portal silently loses
        // live updates for the rest of the session.
        await new Promise((resolve) => setTimeout(resolve, 1000))
        continue
      }
      try {
        const response = await fetch('/api/v1/stream/tickets', {
          headers: { Authorization: `Bearer ${token}`, Accept: 'text/event-stream' },
          credentials: 'same-origin',
          signal: controller.signal,
        })
        if (!response.ok || !response.body) {
          throw new Error(`stream failed with ${response.status}`)
        }
        onStatus?.(true)

        const reader = response.body.getReader()
        const decoder = new TextDecoder()
        let buffer = ''

        for (;;) {
          const { done, value } = await reader.read()
          if (done) break
          buffer += decoder.decode(value, { stream: true })

          // Events are separated by a blank line.
          const chunks = buffer.split('\n\n')
          buffer = chunks.pop() ?? ''

          for (const chunk of chunks) {
            const dataLines = chunk
              .split('\n')
              .filter((line) => line.startsWith('data:'))
              .map((line) => line.slice(5).trim())
            if (dataLines.length === 0) continue
            try {
              onEvent(JSON.parse(dataLines.join('\n')) as TicketStreamEvent)
            } catch {
              // Ignore malformed frames rather than tearing down the stream.
            }
          }
        }
      } catch (error) {
        if ((error as Error)?.name === 'AbortError') return
        onStatus?.(false)
      }

      if (closed) return
      // Reconnect after a short delay (the server also heartbeats every 25s).
      await new Promise((resolve) => setTimeout(resolve, 3000))
    }
  }

  void connect()

  return () => {
    closed = true
    controller?.abort()
    onStatus?.(false)
  }
}
