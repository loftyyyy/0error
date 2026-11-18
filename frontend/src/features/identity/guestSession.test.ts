import { describe, expect, it, vi } from 'vitest'

describe('authedFetch', () => {
  it('names a dropped connection in words a typist can act on', async () => {
    vi.resetModules()
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

    vi.unstubAllGlobals()
  })
})
