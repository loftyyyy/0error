// @vitest-environment jsdom
import { act } from 'react'
import { createRoot } from 'react-dom/client'
import type { Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from './App'
import { drainOutbox } from './features/telemetry/telemetryBuffer'

// React skips effects and warns unless this flag is set outside its own renderer.
const env = globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
env.IS_REACT_ACT_ENVIRONMENT = true

const DRILL_TEXT = 'fj fj fj'

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

const drill = {
  tier: 0,
  tierName: 'Home row',
  charset: 'fj ',
  source: 'STATIC',
  text: 'fj',
  length: 2,
}

let drillIssued = 0

const session = {
  id: 'session-1',
  clientSessionId: 'client-1',
  tier: 0,
  targetCharset: 'fj ',
  targetLength: DRILL_TEXT.length,
  drillSource: 'STATIC',
  status: 'ACTIVE',
  startedAt: new Date().toISOString(),
}

const completion = {
  sessionId: 'session-1',
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

const FEEDBACK = 'event: token\ndata: Keep it smooth.\n\nevent: done\ndata: [DONE]\n\n'

function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
}

function eventStream(body: string) {
  const encoder = new TextEncoder()
  return new Response(
    new ReadableStream({
      start(controller) {
        controller.enqueue(encoder.encode(body))
        controller.close()
      },
    }),
    { status: 200, headers: { 'Content-Type': 'text/event-stream' } },
  )
}

interface RecordedCall {
  url: string
  method: string
  body: string | null
}

let calls: RecordedCall[] = []

function record(input: RequestInfo | URL, init?: RequestInit) {
  calls.push({
    url: String(input),
    method: init?.method ?? 'GET',
    body: typeof init?.body === 'string' ? init.body : null,
  })
}

const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
  const url = String(input)
  record(input, init)

  if (url.includes('/api/v1/progress/weak-pairs')) return json({ tier: 0, pairs: [] })
  if (url.includes('/api/v1/progress/fingers')) return json({ tier: 0, fingers: [] })
  if (url.includes('/api/v1/progress')) return json(progress)
  if (url.includes('/api/v1/drills/next')) {
    drillIssued += 1
    return json({ ...drill, drillId: `drill-${drillIssued}` })
  }
  if (url.includes('/api/v1/auth/guest')) return json({ guestId: 'guest-1', token: 'token-1' })
  if (url.includes('/telemetry')) return json({ sessionId: 'session-1', received: 8, inserted: 8, duplicates: 0 })
  if (url.includes('/complete')) return json(completion)
  if (url.includes('/feedback')) return eventStream(FEEDBACK)
  if (url.endsWith('/api/v1/sessions')) return json(session, 201)

  throw new Error(`Unexpected fetch: ${init?.method ?? 'GET'} ${url}`)
})

let root: Root | null = null
let container: HTMLDivElement | null = null

const bodyText = () => document.body.textContent ?? ''

/** The drill pads spaces with non-breaking spaces, so normalize before comparing. */
const shownDrill = () =>
  container?.querySelector('p[aria-hidden="true"]')?.textContent?.replace(/\u00a0/g, ' ') ?? ''

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
  drillIssued = 0
  vi.stubGlobal('fetch', fetchMock)
  vi.stubGlobal('matchMedia', vi.fn(() => ({
    matches: false,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  })))
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

describe('App', () => {
  it('takes a guest from an empty page through a drill to a scored result', async () => {
    await waitFor(
      () => shownDrill() === DRILL_TEXT,
      'the drill text',
    )
    expect(bodyText()).toContain('Home row')

    for (const key of DRILL_TEXT) await press(key)

    await waitFor(() => bodyText().includes('Gate passed.'), 'the scored result')
    expect(bodyText()).toContain('One more clean pass unlocks your next tier.')
    expect(bodyText()).toContain('100.0%')
    expect(bodyText()).toContain('Coach')

    await waitFor(() => bodyText().includes('Keep it smooth.'), 'the coach note')
    expect(
      container?.querySelector('button:not([aria-label])')?.textContent,
    ).toContain('Next drill')
  })

  it('sends the typed drill as raw events and completes the session', async () => {
    await waitFor(
      () => shownDrill() === DRILL_TEXT,
      'the drill text',
    )

    for (const key of DRILL_TEXT) await press(key)
    await waitFor(
      () => calls.some((call) => call.url.includes('/complete')),
      'the completion call',
    )

    const telemetry = calls
      .filter((call) => call.url.includes('/telemetry'))
      .map((call) => JSON.parse(call.body ?? '{}') as { events: { expected: string; actual: string }[] })
      .flatMap((batch) => batch.events)

    const creation = calls.find(
      (call) => call.url.endsWith('/api/v1/sessions') && call.method === 'POST',
    )
    expect(creation).toBeDefined()
    expect(JSON.parse(creation?.body ?? '{}')).toMatchObject({
      drillIds: ['drill-1', 'drill-2', 'drill-3'],
    })

    expect(telemetry.map((event) => event.expected).join('')).toBe(DRILL_TEXT)
    expect(telemetry.map((event) => event.actual).join('')).toBe(DRILL_TEXT)
  })
})
