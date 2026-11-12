import { authedFetch } from '../identity/guestSession'

export interface FeedbackEvent {
  event: string
  data: string
}

/** Handlers for one coaching stream. Tokens are concatenated verbatim. */
export interface FeedbackHandlers {
  onToken: (text: string) => void
  onDone?: () => void
}

/**
 * Parses one complete SSE frame. Multi-line data is joined with newlines, as
 * the SSE spec requires. An empty frame yields null.
 */
export function parseFeedbackFrame(frame: string): FeedbackEvent | null {
  let event = 'message'
  const data: string[] = []

  for (const line of frame.split('\n')) {
    if (line.startsWith(':')) continue
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
      continue
    }
    if (line.startsWith('data:')) {
      data.push(line.slice(5).replace(/^ /, ''))
    }
  }

  return data.length === 0 ? null : { event, data: data.join('\n') }
}

/**
 * Streams a completed session's coaching. The server always sends text,
 * generated or deterministic, so the client never invents its own fallback.
 */
export async function streamFeedback(
  sessionId: string,
  signal: AbortSignal,
  handlers: FeedbackHandlers,
): Promise<void> {
  const response = await authedFetch(
    `/api/v1/sessions/${encodeURIComponent(sessionId)}/feedback`,
    { headers: { Accept: 'text/event-stream' }, signal },
  )

  if (!response.ok || !response.body) {
    throw new Error(`Could not load coaching (${response.status})`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  const deliver = (frame: string) => {
    const parsed = parseFeedbackFrame(frame)
    if (!parsed) return
    if (parsed.event === 'token') handlers.onToken(parsed.data)
    else if (parsed.event === 'done') handlers.onDone?.()
  }

  try {
    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      // Normalize CRLF so frame boundaries are always "\n\n".
      buffer += decoder.decode(value, { stream: true }).replace(/\r/g, '')

      let boundary = buffer.indexOf('\n\n')
      while (boundary >= 0) {
        deliver(buffer.slice(0, boundary))
        buffer = buffer.slice(boundary + 2)
        boundary = buffer.indexOf('\n\n')
      }
    }

    buffer += decoder.decode().replace(/\r/g, '')
    if (buffer.trim() !== '') deliver(buffer)
  } finally {
    reader.releaseLock()
  }
}
