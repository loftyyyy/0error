import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

describe('guest requests', () => {
  beforeEach(() => {
    vi.resetModules()
  })

  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('names a dropped connection in words a typist can act on', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('Failed to fetch')
      }),
    )

    const { authedFetch } = await import('./guestSession')

    // A bare "Failed to fetch" would reach the error panel verbatim.
    await expect(authedFetch('/api/v1/progress')).rejects.toThrow(
      'The server is not reachable. Check your connection and try again.',
    )
  })

  it('asks the visitor to wait when the network is over its sign-in limit', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => ({ ok: false, status: 429 })),
    )

    const { authedFetch } = await import('./guestSession')

    // A bare "Guest sign-in failed (429)" would not say what to do next.
    await expect(authedFetch('/api/v1/progress')).rejects.toThrow(
      'Too many new visitors from this network. Wait a moment and try again.',
    )
  })
})
