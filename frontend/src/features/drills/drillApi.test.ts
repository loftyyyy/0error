import { beforeEach, describe, expect, it, vi } from 'vitest'

import { GUEST_ID_KEY, GUEST_TOKEN_KEY } from '../identity/guestSession'
import type { Drill } from './drillApi'

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

const drill: Drill = {
  tier: 1,
  tierName: 'Top row',
  charset: 'qwertyuiopasdfghjkl; ',
  source: 'STATIC',
  text: 'qwer asdf jkl; uiop qwer asdf g h jkl; uiop qwer',
  length: 51,
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

describe('fetchNextDrill', () => {
  let storage: MemoryStorage

  beforeEach(() => {
    vi.resetModules()
    storage = new MemoryStorage()
    storage.setItem(GUEST_TOKEN_KEY, 'g_existing')
    storage.setItem(GUEST_ID_KEY, 'guest-existing')
    vi.stubGlobal('localStorage', storage)
  })

  it('asks for the tier and excludes the previous drill', async () => {
    const fetchMock = vi.fn<
      (url: string | URL | Request, init?: RequestInit) => Promise<Response>
    >(async () => jsonResponse(drill))
    vi.stubGlobal('fetch', fetchMock)

    const { fetchNextDrill } = await import('./drillApi')
    const fetched = await fetchNextDrill(1, 'qwer asdf')

    expect(fetched.text).toBe(drill.text)
    expect(fetchMock).toHaveBeenCalledTimes(1)

    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toBe('/api/v1/drills/next?tier=1&exclude=qwer+asdf')
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer g_existing')
  })

  it('reports a locked tier instead of showing a drill', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ error: 'forbidden' }, 403)),
    )

    const { fetchNextDrill } = await import('./drillApi')

    await expect(fetchNextDrill(2)).rejects.toThrow('Tier 2 is still locked')
  })

  it('rejects an invalid drill body', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ tier: 1, source: 'STATIC' })),
    )

    const { fetchNextDrill } = await import('./drillApi')

    await expect(fetchNextDrill(1)).rejects.toThrow('The drill response was invalid')
  })
})

const secondDrill: Drill = {
  ...drill,
  text: 'qwerty uiop asdfg hjkl; qwerty uiop asdfg hjkl;',
  length: 50,
}

const thirdDrill: Drill = {
  ...drill,
  text: 'qq ww ee rr tt yy uu ii oo pp aa ss dd ff gg hh jj kk ll',
  length: 56,
}

describe('fetchDrillSet', () => {
  beforeEach(() => {
    vi.resetModules()
    const storage = new MemoryStorage()
    storage.setItem(GUEST_TOKEN_KEY, 'g_existing')
    storage.setItem(GUEST_ID_KEY, 'guest-existing')
    vi.stubGlobal('localStorage', storage)
  })

  it('composes three drills into one attempt long enough to pass the gate', async () => {
    const queue = [drill, secondDrill, thirdDrill]
    const fetchMock = vi.fn<
      (url: string | URL | Request, init?: RequestInit) => Promise<Response>
    >(async () => jsonResponse(queue.shift()))
    vi.stubGlobal('fetch', fetchMock)

    const { DRILLS_PER_ATTEMPT, fetchDrillSet } = await import('./drillApi')
    const set = await fetchDrillSet(1, 'previous drill')

    expect(DRILLS_PER_ATTEMPT).toBe(3)
    expect(set.text).toBe([drill.text, secondDrill.text, thirdDrill.text].join(' '))
    expect(set.text.length).toBeGreaterThanOrEqual(120)
    expect(set.tail).toBe(thirdDrill.text)
    expect(set.charset).toBe(drill.charset)
    expect(fetchMock).toHaveBeenCalledTimes(3)

    const urls = fetchMock.mock.calls.map(([url]) => String(url))
    const exclude = (value: string) => new URLSearchParams({ exclude: value }).toString()
    expect(urls[0]).toContain(exclude('previous drill'))
    expect(urls[1]).toContain(exclude(drill.text))
    expect(urls[2]).toContain(exclude(secondDrill.text))
  })
})
