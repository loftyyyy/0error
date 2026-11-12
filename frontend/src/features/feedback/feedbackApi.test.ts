import { beforeEach, describe, expect, it, vi } from 'vitest'

import { GUEST_ID_KEY, GUEST_TOKEN_KEY } from '../identity/guestSession'

class MemoryStorage {
  private readonly values = new Map<string, string>()

  getItem(key: string): string | null {
    return this.values.get(key) ?? null
  }

  setItem(key: string, value: string): void {
    this.values.set(key, value)
  }

  removeItem(key: string): void {
    this.values.delete(key)
  }
}

const SESSION_ID = '6f1c9f1e-4f9a-4f6f-9b1c-2f7f3e5d8a10'

function sseResponse(chunks: string[], status = 200): Response {
  const encoder = new TextEncoder()
  const stream = new ReadableStream<Uint8Array>({
    start(controller) {
      for (const chunk of chunks) controller.enqueue(encoder.encode(chunk))
      controller.close()
    },
  })
  return new Response(stream, {
    status,
    headers: { 'Content-Type': 'text/event-stream' },
  })
}

function record(): {
  onToken: ReturnType<typeof vi.fn>
  onDone: ReturnType<typeof vi.fn>
  handlers: { onToken: (text: string) => void; onDone: () => void }
} {
  const onToken = vi.fn<(text: string) => void>()
  const onDone = vi.fn<() => void>()
  return { onToken, onDone, handlers: { onToken, onDone } }
}

describe('parseFeedbackFrame', () => {
  it('reads event name and data', async () => {
    const { parseFeedbackFrame } = await import('./feedbackApi')
    expect(parseFeedbackFrame('event: token\ndata: Steady.')).toEqual({
      event: 'token',
      data: 'Steady.',
    })
  })

  it('joins multi-line data with newlines and strips one leading space', async () => {
    const { parseFeedbackFrame } = await import('./feedbackApi')
    expect(parseFeedbackFrame('event: token\ndata:  line one\ndata: line two')).toEqual({
      event: 'token',
      data: ' line one\nline two',
    })
  })

  it('ignores comments and empty frames', async () => {
    const { parseFeedbackFrame } = await import('./feedbackApi')
    expect(parseFeedbackFrame(': keep-alive')).toBeNull()
    expect(parseFeedbackFrame('event: done\ndata: [DONE]')).toEqual({
      event: 'done',
      data: '[DONE]',
    })
  })
})

describe('streamFeedback', () => {
  beforeEach(() => {
    vi.resetModules()
    const storage = new MemoryStorage()
    storage.setItem(GUEST_TOKEN_KEY, 'g_existing')
    storage.setItem(GUEST_ID_KEY, 'guest-existing')
    vi.stubGlobal('localStorage', storage)
  })

  it('delivers tokens even when frames split across chunks', async () => {
    const fetchMock = vi.fn<
      (url: string | URL | Request, init?: RequestInit) => Promise<Response>
    >(async () =>
      sseResponse([
        'event: token\ndata: Nice rhy',
        'thm.\n\nevent: token\ndata: Keep it.\n',
        '\nevent: done\ndata: [DONE]\n\n',
      ]),
    )
    vi.stubGlobal('fetch', fetchMock)

    const { streamFeedback } = await import('./feedbackApi')
    const { onToken, onDone, handlers } = record()
    await streamFeedback(SESSION_ID, new AbortController().signal, handlers)

    expect(onToken.mock.calls).toEqual([['Nice rhythm.'], ['Keep it.']])
    expect(onDone).toHaveBeenCalledTimes(1)

    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toBe(`/api/v1/sessions/${SESSION_ID}/feedback`)
    expect(new Headers(init?.headers).get('Accept')).toBe('text/event-stream')
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer g_existing')
  })

  it('handles CRLF frames', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => sseResponse(['event: token\r\ndata: Calm.\r\n\r\n'])),
    )

    const { streamFeedback } = await import('./feedbackApi')
    const { onToken, handlers } = record()
    await streamFeedback(SESSION_ID, new AbortController().signal, handlers)

    expect(onToken).toHaveBeenCalledWith('Calm.')
  })

  it('throws when coaching cannot be loaded', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response('', { status: 404 })))

    const { streamFeedback } = await import('./feedbackApi')
    const { handlers } = record()

    await expect(
      streamFeedback(SESSION_ID, new AbortController().signal, handlers),
    ).rejects.toThrow('Could not load coaching (404)')
  })
})
