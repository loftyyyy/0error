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

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const report = {
  tier: 2,
  fingers: [
    {
      finger: 'LEFT_RING',
      label: 'Left ring',
      attempts: 8,
      errors: 2,
      pauses: 3,
      meanMs: 640,
      score: 0.32,
    },
  ],
}

describe('fetchFingers', () => {
  beforeEach(() => {
    vi.resetModules()
    const storage = new MemoryStorage()
    storage.setItem(GUEST_TOKEN_KEY, 'g_existing')
    storage.setItem(GUEST_ID_KEY, 'guest-existing')
    vi.stubGlobal('localStorage', storage)
  })

  it('asks for the tier and returns the server ranking', async () => {
    const fetchMock = vi.fn<
      (url: string | URL | Request, init?: RequestInit) => Promise<Response>
    >(async () => jsonResponse(report))
    vi.stubGlobal('fetch', fetchMock)

    const { fetchFingers } = await import('./fingerApi')
    const fetched = await fetchFingers(2)

    expect(fetched.fingers).toHaveLength(1)
    expect(fetched.fingers[0].label).toBe('Left ring')

    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toBe('/api/v1/progress/fingers?tier=2')
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer g_existing')
  })

  it('rejects a malformed finger body instead of rendering it', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse({ tier: 2, fingers: [{ finger: 'LEFT_RING', attempts: 'eight' }] }),
      ),
    )

    const { fetchFingers } = await import('./fingerApi')

    await expect(fetchFingers(2)).rejects.toThrow(
      'The finger analysis response was invalid',
    )
  })

  it('reports a failed request', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ error: 'unavailable' }, 500)),
    )

    const { fetchFingers } = await import('./fingerApi')

    await expect(fetchFingers(2)).rejects.toThrow(
      'Could not load your finger analysis (500)',
    )
  })
})

describe('fetchFingerTrend', () => {
  beforeEach(() => {
    vi.resetModules()
    const storage = new MemoryStorage()
    storage.setItem(GUEST_TOKEN_KEY, 'g_existing')
    storage.setItem(GUEST_ID_KEY, 'guest-existing')
    vi.stubGlobal('localStorage', storage)
  })

  it('asks for one reach and its window, keeping the order the server sent', async () => {
    const trend = {
      tier: 2,
      finger: 'LEFT_RING',
      label: 'Left ring',
      attempts: [
        {
          recordedAt: '2026-11-16T10:00:00Z',
          attempts: 2,
          errors: 1,
          pauses: 1,
          meanMs: 720,
        },
        {
          recordedAt: '2026-11-20T10:00:00Z',
          attempts: 2,
          errors: 0,
          pauses: 0,
          meanMs: 560,
        },
      ],
    }
    const fetchMock = vi.fn<
      (url: string | URL | Request, init?: RequestInit) => Promise<Response>
    >(async () => jsonResponse(trend))
    vi.stubGlobal('fetch', fetchMock)

    const { fetchFingerTrend } = await import('./fingerApi')
    const fetched = await fetchFingerTrend(2, 'LEFT_RING', 5)

    expect(fetched.label).toBe('Left ring')
    expect(fetched.attempts.map((attempt) => attempt.meanMs)).toEqual([720, 560])

    const [url] = fetchMock.mock.calls[0]
    expect(String(url)).toBe(
      '/api/v1/progress/fingers/trend?tier=2&finger=LEFT_RING&limit=5',
    )
  })

  it('rejects an attempt without a mean instead of rendering it', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse({
          tier: 2,
          finger: 'LEFT_RING',
          label: 'Left ring',
          attempts: [{ recordedAt: '2026-11-20T10:00:00Z', attempts: 1 }],
        }),
      ),
    )

    const { fetchFingerTrend } = await import('./fingerApi')

    await expect(fetchFingerTrend(2, 'LEFT_RING')).rejects.toThrow(
      'The reach trend response was invalid',
    )
  })

  it('reports a failed request', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ error: 'unavailable' }, 500)),
    )

    const { fetchFingerTrend } = await import('./fingerApi')

    await expect(fetchFingerTrend(2, 'LEFT_RING')).rejects.toThrow(
      'Could not load the reach trend (500)',
    )
  })
})
