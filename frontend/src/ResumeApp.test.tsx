// @vitest-environment jsdom
import { act } from 'react'
import { createRoot } from 'react-dom/client'
import type { Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from './App'
import type { ActiveSession, SessionCompletion } from './features/sessions/sessionApi'
import { drainOutbox } from './features/telemetry/telemetryBuffer'

const env = globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
env.IS_REACT_ACT_ENVIRONMENT = true

const TEXT = 'fj fj fj'
const SESSION_ID = 'session-resume'

const active: ActiveSession = {
  sessionId: SESSION_ID,
  tier: 0,
  tierName: 'Home row',
  text: TEXT,
  charset: 'fj ',
  startedAt: '2026-11-25T10:00:00Z',
  nextSeq: 5,
  keystrokes: 5,
  position: 3,
  elapsedMs: 1200,
  typedCount: 3,
  scoredCount: 3,
  correctCount: 2,
}

const progress = {
  tiers: [
    {
      tier: 0,
      name: 'Home row',
      charset: 'fj ',
      unlocked: true,
      consecutivePasses: 0,
      failedAttempts: 0,
      medianThresholdMs: 450,
      p95ThresholdMs: 900,
      unlockedAt: null,
    },
  ],
}

const completion: SessionCompletion = {
  sessionId: SESSION_ID,
  tier: 0,
  status: 'COMPLETED',
  metrics: {
    keystrokeCount: 8,
    correctCount: 8,
    accuracy: 100,
    medianInterKeyMs: 120,
    p95InterKeyMs: 200,
    hesitationCount: 0,
  },
  passed: true,
  consecutivePasses: 1,
  failedAttempts: 0,
  unlockedTier: null,
  medianThresholdMs: 450,
  p95ThresholdMs: 900,
  accuracyThreshold: 96,
  minimumKeystrokes: 120,
  failures: [],
}

interface RecordedCall {
  url: string
  method: string
  body: string | null
}

let calls: RecordedCall[] = []
/** Flips to false after abandon so the next active check starts fresh. */
let resumed = true

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
  const url = String(input)
  calls.push({
    url,
    method: init?.method ?? 'GET',
    body: typeof init?.body === 'string' ? init.body : null,
  })

  if (url.includes('/api/v1/sessions/active')) {
    return resumed ? json(active) : new Response(null, { status: 204 })
  }
  if (url.includes('/abandon')) {
    resumed = false
    return new Response(null, { status: 204 })
  }
  if (url.includes('/api/v1/progress/weak-pairs')) return json({ tier: 0, pairs: [] })
  if (url.includes('/api/v1/progress/fingers')) return json({ tier: 0, fingers: [] })
  if (url.includes('/api/v1/progress')) return json(progress)
  if (url.includes('/api/v1/sessions/recent')) return json({ sessions: [] })
  if (url.includes('/api/v1/auth/guest')) return json({ guestId: 'guest-1', token: 'token-1' })
  if (url.includes('/telemetry')) {
    return json({ sessionId: SESSION_ID, received: 1, inserted: 1, duplicates: 0 })
  }
  if (url.includes('/complete')) return json(completion)
  if (url.includes('/feedback')) {
    return new Response('event: token\ndata: Keep it smooth.\n\nevent: done\ndata: [DONE]\n\n', {
      status: 200,
      headers: { 'Content-Type': 'text/event-stream' },
    })
  }
  if (url.includes('/api/v1/drills/next')) {
    return json({
      drillId: 'new-drill',
      tier: 0,
      tierName: 'Home row',
      charset: 'fj ',
      source: 'STATIC',
      text: TEXT,
      length: TEXT.length,
    })
  }
  if (url.endsWith('/api/v1/sessions')) {
    return json({ id: 'new-session', status: 'ACTIVE' }, 201)
  }
  throw new Error(`Unexpected fetch: ${url}`)
})

let root: Root | null = null
let container: HTMLDivElement | null = null

const shownText = () =>
  container?.querySelector('p[aria-hidden="true"]')?.textContent?.replace(/\u00a0/g, ' ') ?? ''

const bodyText = () => document.body.textContent ?? ''

async function waitFor(check: () => boolean, label: string) {
  const deadline = Date.now() + 4_000
  while (!check()) {
    if (Date.now() > deadline) {
      throw new Error(`Timed out waiting for ${label}. Page said: ${bodyText()}`)
    }
    await act(async () => {
      await new Promise((resolve) => setTimeout(resolve, 20))
    })
  }
}

async function press(key: string) {
  await act(async () => {
    window.dispatchEvent(new KeyboardEvent('keydown', { key, cancelable: true }))
  })
}

beforeEach(async () => {
  window.localStorage.clear()
  calls = []
  resumed = true
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal(
    'matchMedia',
    vi.fn(() => ({
      matches: false,
      addEventListener: () => undefined,
      removeEventListener: () => undefined,
    })),
  )
  await drainOutbox('')

  container = document.createElement('div')
  document.body.append(container)
  root = createRoot(container)
  act(() => {
    root!.render(<App />)
  })
})

afterEach(() => {
  if (root) act(() => root!.unmount())
  root = null
  container?.remove()
  container = null
  vi.unstubAllGlobals()
})

describe('resume after reload', () => {
  it('continues the open drill without fetching or creating another', async () => {
    await waitFor(() => shownText() === TEXT, 'the resumed drill text')

    expect(bodyText()).toContain('Picked up where you left off.')
    expect(bodyText()).toContain('67% accurate')
    expect(bodyText()).toContain('3 keys')
    expect(calls.some((call) => call.url.includes('/drills/next'))).toBe(false)
    expect(
      calls.some((call) => call.url.endsWith('/api/v1/sessions') && call.method === 'POST'),
    ).toBe(false)
  })

  it('starts over and does not bring the abandoned drill back', async () => {
    await waitFor(() => shownText() === TEXT, 'the resumed drill text')

    const startOver = Array.from(container?.querySelectorAll('button') ?? []).find(
      (button) => button.textContent?.includes('Start over'),
    )
    expect(startOver).toBeDefined()

    await act(async () => {
      startOver?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    await waitFor(
      () => calls.some((call) => call.url.includes(`/sessions/${SESSION_ID}/abandon`)),
      'the abandon request',
    )
    await waitFor(
      () => calls.filter((call) => call.url.includes('/drills/next')).length === 3,
      'a fresh drill set',
    )

    expect(
      calls.some((call) => call.url.endsWith('/api/v1/sessions') && call.method === 'POST'),
    ).toBe(true)
    await waitFor(
      () => !bodyText().includes('Picked up where you left off.'),
      'the resume notice to clear',
    )
  })

  it('keeps the stored sequence when the drill completes', async () => {
    await waitFor(() => shownText() === TEXT, 'the resumed drill text')

    // The first three characters are already stored; finish the rest.
    for (const key of TEXT.slice(active.position)) await press(key)

    await waitFor(
      () => calls.some((call) => call.url.includes(`/sessions/${SESSION_ID}/complete`)),
      'the resumed completion',
    )

    const telemetry = calls.filter((call) => call.url.includes('/telemetry'))
    const sequences = telemetry.flatMap(
      (call) =>
        (JSON.parse(call.body ?? '{}') as { events?: { seq: number }[] }).events?.map(
          (event) => event.seq,
        ) ?? [],
    )
    expect(sequences[0]).toBe(5)
    expect(sequences).toContain(9)
  })
})
