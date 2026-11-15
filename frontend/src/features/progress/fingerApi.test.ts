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
