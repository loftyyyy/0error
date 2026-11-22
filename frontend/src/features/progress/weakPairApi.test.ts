import { beforeEach, describe, expect, it, vi } from 'vitest'

import { GUEST_ID_KEY, GUEST_TOKEN_KEY } from '../identity/guestSession'
import { formatPairLabel } from './weakPairApi'

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
  pairs: [
    {
      pair: 'bn',
      attempts: 6,
      errors: 2,
      pauses: 3,
      meanMs: 940,
      score: 0.48,
    },
  ],
}

describe('fetchWeakPairs', () => {
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

    const { fetchWeakPairs } = await import('./weakPairApi')
    const fetched = await fetchWeakPairs(2)

    expect(fetched.pairs).toHaveLength(1)
    expect(fetched.pairs[0].pair).toBe('bn')

    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toBe('/api/v1/progress/weak-pairs?tier=2')
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer g_existing')
  })

  it('rejects a malformed pair body instead of rendering it', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse({ tier: 2, pairs: [{ pair: 'bn', attempts: 'six' }] }),
      ),
    )

    const { fetchWeakPairs } = await import('./weakPairApi')

    await expect(fetchWeakPairs(2)).rejects.toThrow(
      'The focus pair response was invalid',
    )
  })

  it('reports a failed request', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ error: 'unavailable' }, 500)),
    )

    const { fetchWeakPairs } = await import('./weakPairApi')

    await expect(fetchWeakPairs(2)).rejects.toThrow(
      'Could not load your focus pairs (500)',
    )
  })
})

describe('fetchWeakPairTrend', () => {
  beforeEach(() => {
    vi.resetModules()
    const storage = new MemoryStorage()
    storage.setItem(GUEST_TOKEN_KEY, 'g_existing')
    storage.setItem(GUEST_ID_KEY, 'guest-existing')
    vi.stubGlobal('localStorage', storage)
  })

  it('asks for one pair and its window, keeping the order the server sent', async () => {
    const trend = {
      tier: 2,
      pair: 'bn',
      attempts: [
        {
          recordedAt: '2026-11-16T10:00:00Z',
          attempts: 1,
          errors: 0,
          pauses: 0,
          meanMs: 940,
        },
        {
          recordedAt: '2026-11-20T10:00:00Z',
          attempts: 2,
          errors: 1,
          pauses: 1,
          meanMs: 610,
        },
      ],
    }
    const fetchMock = vi.fn<
      (url: string | URL | Request, init?: RequestInit) => Promise<Response>
    >(async () => jsonResponse(trend))
    vi.stubGlobal('fetch', fetchMock)

    const { fetchWeakPairTrend } = await import('./weakPairApi')
    const fetched = await fetchWeakPairTrend(2, 'bn', 5)

    expect(fetched.attempts.map((attempt) => attempt.meanMs)).toEqual([940, 610])

    const [url] = fetchMock.mock.calls[0]
    expect(String(url)).toBe('/api/v1/progress/weak-pairs/trend?tier=2&pair=bn&limit=5')
  })

  it('rejects an attempt without a mean instead of rendering it', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () =>
        jsonResponse({
          tier: 2,
          pair: 'bn',
          attempts: [{ recordedAt: '2026-11-20T10:00:00Z', attempts: 1 }],
        }),
      ),
    )

    const { fetchWeakPairTrend } = await import('./weakPairApi')

    await expect(fetchWeakPairTrend(2, 'bn')).rejects.toThrow(
      'The pair trend response was invalid',
    )
  })

  it('reports a failed request', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ error: 'unavailable' }, 500)),
    )

    const { fetchWeakPairTrend } = await import('./weakPairApi')

    await expect(fetchWeakPairTrend(2, 'bn')).rejects.toThrow(
      'Could not load the pair trend (500)',
    )
  })
})

describe('formatPairLabel', () => {
  it('makes a space visible inside a pair', () => {
    expect(formatPairLabel('a ')).toBe('a\u2423')
    expect(formatPairLabel('dk')).toBe('dk')
  })
})
