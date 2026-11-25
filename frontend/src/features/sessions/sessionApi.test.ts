import { beforeEach, describe, expect, it, vi } from 'vitest'

import { GUEST_ID_KEY, GUEST_TOKEN_KEY } from '../identity/guestSession'
import type { CreateSessionInput } from './sessionApi'

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

const input: CreateSessionInput = {
  clientSessionId: '11111111-1111-4111-8111-111111111111',
  drillIds: ['aaaa', 'bbbb', 'cccc'],
}

function jsonResponse(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const guestBody = { guestId: 'guest-1', token: 'g_test', tokenType: 'Bearer' }
const sessionBody = {
  id: '22222222-2222-4222-8222-222222222222',
  clientSessionId: input.clientSessionId,
  tier: 0,
  targetCharset: 'asdfjkl;',
  targetLength: 43,
  drillSource: 'STATIC',
  status: 'ACTIVE',
  startedAt: '2026-11-07T00:00:00Z',
}

describe('createServerSession', () => {
  let storage: MemoryStorage

  beforeEach(() => {
    vi.resetModules()
    storage = new MemoryStorage()
    vi.stubGlobal('localStorage', storage)
  })

  it('issues a guest token before creating the session', async () => {
    const fetchMock = vi.fn<
      (url: string | URL | Request, init?: RequestInit) => Promise<Response>
    >(async (url) => {
      const path = String(url)
      if (path === '/api/v1/auth/guest') return jsonResponse(guestBody)
      if (path === '/api/v1/sessions') return jsonResponse(sessionBody, 201)
      throw new Error(`Unexpected request ${path}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    const { createServerSession } = await import('./sessionApi')
    const session = await createServerSession(input)

    expect(session.id).toBe(sessionBody.id)
    expect(fetchMock).toHaveBeenCalledTimes(2)
    expect(storage.getItem(GUEST_TOKEN_KEY)).toBe('g_test')
    expect(storage.getItem(GUEST_ID_KEY)).toBe('guest-1')

    const sessionCall = fetchMock.mock.calls[1]
    expect(sessionCall[0]).toBe('/api/v1/sessions')
    const init = sessionCall[1] as RequestInit
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer g_test')
    expect(JSON.parse(String(init.body))).toMatchObject({
      clientSessionId: input.clientSessionId,
      drillIds: input.drillIds,
    })
  })

  it('reuses a stored guest token', async () => {
    storage.setItem(GUEST_TOKEN_KEY, 'g_existing')
    storage.setItem(GUEST_ID_KEY, 'guest-existing')

    const fetchMock = vi.fn<
      (url: string | URL | Request, init?: RequestInit) => Promise<Response>
    >(async () => jsonResponse(sessionBody, 201))
    vi.stubGlobal('fetch', fetchMock)

    const { createServerSession } = await import('./sessionApi')
    await createServerSession(input)

    expect(fetchMock).toHaveBeenCalledTimes(1)
    const init = fetchMock.mock.calls[0][1] as RequestInit
    expect(new Headers(init.headers).get('Authorization')).toBe('Bearer g_existing')
  })

  it('reissues the guest once when the stored token is rejected', async () => {
    storage.setItem(GUEST_TOKEN_KEY, 'g_stale')
    storage.setItem(GUEST_ID_KEY, 'guest-stale')

    const fetchMock = vi.fn<
      (url: string | URL | Request, init?: RequestInit) => Promise<Response>
    >(async (url, init) => {
      const path = String(url)
      const auth = new Headers(init?.headers).get('Authorization')
      if (path === '/api/v1/sessions' && auth === 'Bearer g_stale') {
        return jsonResponse({ error: 'unauthorized' }, 401)
      }
      if (path === '/api/v1/sessions' && auth === 'Bearer g_fresh') {
        return jsonResponse(sessionBody, 201)
      }
      if (path === '/api/v1/auth/guest') return jsonResponse({ ...guestBody, token: 'g_fresh' })
      throw new Error(`Unexpected request ${path} with ${auth}`)
    })
    vi.stubGlobal('fetch', fetchMock)

    const { createServerSession } = await import('./sessionApi')
    await createServerSession(input)

    expect(fetchMock).toHaveBeenCalledTimes(3)
    expect(storage.getItem(GUEST_TOKEN_KEY)).toBe('g_fresh')
  })
})

const completionBody = {
  sessionId: sessionBody.id,
  tier: 0,
  status: 'COMPLETED',
  metrics: {
    keystrokeCount: 150,
    correctCount: 149,
    accuracy: 99.33,
    medianInterKeyMs: 240,
    p95InterKeyMs: 410,
    hesitationCount: 0,
  },
  passed: true,
  consecutivePasses: 2,
  failedAttempts: 0,
  unlockedTier: 1,
  medianThresholdMs: 450,
  p95ThresholdMs: 900,
  accuracyThreshold: 96,
  minimumKeystrokes: 120,
  failures: [],
}

describe('completeServerSession', () => {
  beforeEach(() => {
    vi.resetModules()
    const storage = new MemoryStorage()
    storage.setItem(GUEST_TOKEN_KEY, 'g_existing')
    storage.setItem(GUEST_ID_KEY, 'guest-existing')
    vi.stubGlobal('localStorage', storage)
  })

  it('posts to the completion route and returns server truth', async () => {
    const fetchMock = vi.fn<
      (url: string | URL | Request, init?: RequestInit) => Promise<Response>
    >(async () => jsonResponse(completionBody))
    vi.stubGlobal('fetch', fetchMock)

    const { completeServerSession } = await import('./sessionApi')
    const completion = await completeServerSession(sessionBody.id)

    expect(completion).toMatchObject({
      passed: true,
      consecutivePasses: 2,
      unlockedTier: 1,
    })
    expect(completion.metrics.medianInterKeyMs).toBe(240)

    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toBe(`/api/v1/sessions/${sessionBody.id}/complete`)
    expect(init?.method).toBe('POST')
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer g_existing')
  })

  it('marks a closed session as gone instead of retryable', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ error: 'conflict' }, 409)),
    )

    const { completeServerSession, SessionGoneError } = await import('./sessionApi')
    const error = await completeServerSession(sessionBody.id).catch((reason: unknown) => reason)

    expect(error).toBeInstanceOf(SessionGoneError)
    expect((error as Error).message).toBe('The server closed this drill before it was scored.')
  })

  it('marks a session the guest no longer holds as gone', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ error: 'missing' }, 404)),
    )

    const { completeServerSession, SessionGoneError } = await import('./sessionApi')
    const error = await completeServerSession(sessionBody.id).catch((reason: unknown) => reason)

    expect(error).toBeInstanceOf(SessionGoneError)
    expect((error as Error).message).toBe('The server no longer has this drill.')
  })
})

describe('fetchRecentSessions', () => {
  beforeEach(() => {
    vi.resetModules()
    const storage = new MemoryStorage()
    storage.setItem(GUEST_TOKEN_KEY, 'g_existing')
    storage.setItem(GUEST_ID_KEY, 'guest-existing')
    vi.stubGlobal('localStorage', storage)
  })

  const entry = {
    sessionId: '33333333-3333-4333-8333-333333333333',
    tier: 0,
    tierName: 'Home row',
    completedAt: '2026-11-18T10:00:00Z',
    accuracy: 97.5,
    medianInterKeyMs: 380,
    p95InterKeyMs: 720,
    keystrokeCount: 128,
    passed: true,
  }

  it('asks for the requested window and returns the entries', async () => {
    const fetchMock = vi.fn<
      (url: string | URL | Request, init?: RequestInit) => Promise<Response>
    >(async () => jsonResponse({ sessions: [entry, { ...entry, sessionId: 'older', passed: null }] }))
    vi.stubGlobal('fetch', fetchMock)

    const { fetchRecentSessions } = await import('./sessionApi')
    const sessions = await fetchRecentSessions(3)

    expect(sessions).toHaveLength(2)
    expect(sessions[0]).toMatchObject({ tierName: 'Home row', passed: true })
    // A row from before the gate decision existed stays unknown.
    expect(sessions[1].passed).toBeNull()

    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toBe('/api/v1/sessions/recent?limit=3')
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer g_existing')
  })

  it('reports a history that cannot be loaded', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ error: 'unavailable' }, 503)),
    )

    const { fetchRecentSessions } = await import('./sessionApi')

    await expect(fetchRecentSessions()).rejects.toThrow(
      'Could not load recent drills (503)',
    )
  })
})

describe('fetchActiveSession', () => {
  const active = {
    sessionId: 'session-resume',
    tier: 0,
    tierName: 'Home row',
    text: 'asdf jkl; asdf',
    charset: 'asdfjkl; ',
    startedAt: '2026-11-25T10:00:00Z',
    nextSeq: 7,
    keystrokes: 7,
    position: 5,
    elapsedMs: 4200,
    typedCount: 6,
    scoredCount: 5,
    correctCount: 4,
  }

  beforeEach(() => {
    vi.resetModules()
    const storage = new MemoryStorage()
    storage.setItem(GUEST_TOKEN_KEY, 'g_existing')
    storage.setItem(GUEST_ID_KEY, 'guest-existing')
    vi.stubGlobal('localStorage', storage)
  })

  it('returns the open drill with its cursor and sequence', async () => {
    const fetchMock = vi.fn<
      (url: string | URL | Request, init?: RequestInit) => Promise<Response>
    >(async () => jsonResponse(active))
    vi.stubGlobal('fetch', fetchMock)

    const { fetchActiveSession } = await import('./sessionApi')
    const resumed = await fetchActiveSession()

    expect(resumed).toEqual(active)
    const [url, init] = fetchMock.mock.calls[0]
    expect(String(url)).toBe('/api/v1/sessions/active')
    expect(new Headers(init?.headers).get('Authorization')).toBe('Bearer g_existing')
  })

  it('returns null when nothing is open', async () => {
    vi.stubGlobal('fetch', vi.fn(async () => new Response(null, { status: 204 })))

    const { fetchActiveSession } = await import('./sessionApi')

    await expect(fetchActiveSession()).resolves.toBeNull()
  })

  it('rejects an incomplete resume body', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ ...active, position: 'five' })),
    )

    const { fetchActiveSession } = await import('./sessionApi')

    await expect(fetchActiveSession()).rejects.toThrow(
      'The resume response was invalid',
    )
  })

  it('rejects a resume body without stored attempt counts', async () => {
    const { typedCount: _typed, ...missing } = active
    vi.stubGlobal('fetch', vi.fn(async () => jsonResponse(missing)))

    const { fetchActiveSession } = await import('./sessionApi')

    await expect(fetchActiveSession()).rejects.toThrow(
      'The resume response was invalid',
    )
  })

  it('reports a resume that cannot be loaded', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => jsonResponse({ error: 'unavailable' }, 503)),
    )

    const { fetchActiveSession } = await import('./sessionApi')

    await expect(fetchActiveSession()).rejects.toThrow(
      'Could not check for an open drill (503)',
    )
  })
})
