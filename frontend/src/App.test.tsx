// @vitest-environment jsdom
import { act } from 'react'
import { createRoot } from 'react-dom/client'
import type { Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from './App'
import type { RecentSession, SessionCompletion } from './features/sessions/sessionApi'
import { countPendingBatches } from './features/telemetry/keystrokeQueue'
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
    {
      tier: 1,
      name: 'Top row',
      charset: 'fj qwer ',
      unlocked: false,
      consecutivePasses: 0,
      failedAttempts: 3,
      medianThresholdMs: 383,
      p95ThresholdMs: 765,
      unlockedAt: null,
    },
    {
      tier: 2,
      name: 'Bottom row',
      charset: 'fj qwer vbnm ',
      unlocked: false,
      consecutivePasses: 0,
      failedAttempts: 0,
      medianThresholdMs: 450,
      p95ThresholdMs: 900,
      unlockedAt: null,
    },
    {
      tier: 3,
      name: 'Punctuation',
      charset: "fj qwer vbnm .,;'",
      unlocked: false,
      consecutivePasses: 0,
      failedAttempts: 0,
      medianThresholdMs: 450,
      p95ThresholdMs: 900,
      unlockedAt: null,
    },
    {
      tier: 4,
      name: 'Full sentences',
      charset: 'abcdefghijklmnopqrstuvwxyz ',
      unlocked: false,
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

const completion: SessionCompletion = {
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

/** The completion the fake server answers with; one test swaps in an unlock. */
let completionResponse: SessionCompletion = completion

/** The ladder the fake server answers with; one test completes the curriculum. */
let progressResponse = progress

/** Finished drills the history list shows. */
const recent: RecentSession[] = [
  {
    sessionId: 'recent-1',
    tier: 0,
    tierName: 'Home row',
    completedAt: '2026-11-18T10:00:00Z',
    accuracy: 97.5,
    medianInterKeyMs: 380,
    p95InterKeyMs: 720,
    keystrokeCount: 128,
    passed: true,
  },
  {
    sessionId: 'recent-0',
    tier: 0,
    tierName: 'Home row',
    completedAt: '2026-11-17T10:00:00Z',
    accuracy: 91.2,
    medianInterKeyMs: 610,
    p95InterKeyMs: 1200,
    keystrokeCount: 84,
    passed: false,
  },
]

let recentResponse: RecentSession[] = recent

/** When set, the history request fails with this status. */
let recentFailure: number | null = null

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

/** When set, telemetry batches fail with this status, as a flaky network would. */
let telemetryFailure: number | null = null

/** When true, the coaching stream is unavailable. */
let feedbackFailure = false

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
  if (url.includes('/api/v1/progress')) return json(progressResponse)
  if (url.includes('/api/v1/drills/next')) {
    drillIssued += 1
    return json({ ...drill, drillId: `drill-${drillIssued}` })
  }
  if (url.includes('/api/v1/auth/guest')) return json({ guestId: 'guest-1', token: 'token-1' })
  if (url.includes('/telemetry')) {
    if (telemetryFailure !== null) return json({ error: 'temporarily unavailable' }, telemetryFailure)
    return json({ sessionId: 'session-1', received: 8, inserted: 8, duplicates: 0 })
  }
  if (url.includes('/api/v1/sessions/recent')) {
    if (recentFailure !== null) return json({ error: 'temporarily unavailable' }, recentFailure)
    return json({ sessions: recentResponse })
  }
  if (url.includes('/complete')) return json(completionResponse)
  if (url.includes('/feedback')) {
    if (feedbackFailure) return json({ error: 'temporarily unavailable' }, 503)
    return eventStream(FEEDBACK)
  }
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
  telemetryFailure = null
  feedbackFailure = false
  completionResponse = completion
  progressResponse = progress
  recentResponse = recent
  recentFailure = null
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

    // The typing surface takes focus, so a new drill is announced with its text
    // instead of leaving focus on the body after the previous button unmounted.
    const surface = container?.querySelector('[role="application"]')
    expect(document.activeElement).toBe(surface)
    expect(surface?.getAttribute('aria-describedby')).toBe('drill-text-session-1')
    expect(container?.querySelector('#drill-text-session-1')?.textContent).toBe(DRILL_TEXT)

    for (const key of DRILL_TEXT) await press(key)

    await waitFor(() => bodyText().includes('Gate passed.'), 'the scored result')
    expect(bodyText()).toContain('One more clean pass unlocks your next tier.')
    expect(bodyText()).toContain('100.0%')
    expect(bodyText()).toContain('Coach')

    // The ladder names every tier and the gate the next one still needs.
    expect(container?.querySelector('section[aria-label="Tier ladder"]')).not.toBeNull()
    expect(bodyText()).toContain('Full sentences')
    expect(bodyText()).toContain(
      'Top row opens after 383 ms median and 765 ms p95 with two clean passes in a row. Cadence eased after 3 failed attempts.',
    )

    await waitFor(() => bodyText().includes('Keep it smooth.'), 'the coach note')
    expect(
      container?.querySelector('button:not([aria-label])')?.textContent,
    ).toContain('Next drill')

    // The outcome is announced politely, not only drawn.
    const announced = Array.from(container?.querySelectorAll('[role="status"]') ?? [])
      .map((node) => node.textContent ?? '')
      .join(' ')
    expect(announced).toContain('Gate passed.')
    expect(announced).toContain('1 of 2 consecutive passes.')
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

  it('holds the result back until the queued keystrokes reach the server', async () => {
    await waitFor(
      () => shownDrill() === DRILL_TEXT,
      'the drill text',
    )

    telemetryFailure = 503
    for (const key of DRILL_TEXT) await press(key)

    await waitFor(
      () => bodyText().includes('The drill is not scored yet.'),
      'the scoring error',
    )
    // No result is invented from a session the server has not fully seen.
    expect(bodyText()).not.toContain('Gate passed.')
    expect(calls.some((call) => call.url.includes('/complete'))).toBe(false)
    // The outbox still holds the batch, so nothing typed is lost.
    expect(await countPendingBatches('session-1')).toBe(1)

    // The connection comes back; the retry rescores from the queued batch.
    telemetryFailure = null
    const retry = Array.from(container?.querySelectorAll('button') ?? []).find((button) =>
      button.textContent?.includes('Try again'),
    )
    expect(retry).toBeDefined()
    await act(async () => {
      retry?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    await waitFor(() => bodyText().includes('Gate passed.'), 'the rescored result')
    expect(await countPendingBatches('session-1')).toBe(0)
    expect(
      calls.filter(
        (call) => call.url.includes('/telemetry') && call.method === 'POST',
      ).length,
    ).toBeGreaterThan(1)
  })

  it('keeps the scored result when the coach stream fails', async () => {
    await waitFor(
      () => shownDrill() === DRILL_TEXT,
      'the drill text',
    )

    feedbackFailure = true
    for (const key of DRILL_TEXT) await press(key)

    await waitFor(() => bodyText().includes('Gate passed.'), 'the scored result')
    await waitFor(
      () =>
        bodyText().includes(
          'Coaching is unavailable right now. Your metrics are saved.',
        ),
      'the quiet coach note',
    )
    // Coaching is not on the critical path; the numbers still stand.
    expect(bodyText()).toContain('100.0%')
  })

  it('names the tier an unlock opens', async () => {
    completionResponse = { ...completion, consecutivePasses: 2, unlockedTier: 1 }

    await waitFor(
      () => shownDrill() === DRILL_TEXT,
      'the drill text',
    )
    for (const key of DRILL_TEXT) await press(key)

    await waitFor(() => bodyText().includes('Top row unlocked.'), 'the unlock line')
    expect(bodyText()).not.toContain('Tier 2 unlocked.')
  })

  it('does not promise another tier once every tier is open', async () => {
    progressResponse = {
      ...progress,
      tiers: progress.tiers.map((tier) => ({ ...tier, unlocked: true })),
    }
    completionResponse = { ...completion, tier: 4, consecutivePasses: 2 }

    await waitFor(() => shownDrill() === DRILL_TEXT, 'the drill text')
    for (const key of DRILL_TEXT) await press(key)

    await waitFor(
      () => bodyText().includes('Every tier is open. Keep the rhythm.'),
      'the final-tier copy',
    )
    expect(bodyText()).not.toContain('One more clean pass unlocks your next tier.')
  })

  it('lists the guests recent drills beside the score', async () => {
    await waitFor(() => shownDrill() === DRILL_TEXT, 'the drill text')
    for (const key of DRILL_TEXT) await press(key)

    await waitFor(() => bodyText().includes('Recent drills'), 'the history list')
    expect(bodyText()).toContain('97.5%')
    expect(bodyText()).toContain('91.2%')
    expect(bodyText()).toContain('not yet')
    // The list is the guest's own history, fetched with the bearer token.
    const historyCall = calls.find((call) => call.url.includes('/api/v1/sessions/recent'))
    expect(historyCall?.url).toContain('limit=5')
  })

  it('still shows the score when the history request fails', async () => {
    recentFailure = 500

    await waitFor(() => shownDrill() === DRILL_TEXT, 'the drill text')
    for (const key of DRILL_TEXT) await press(key)

    await waitFor(() => bodyText().includes('Gate passed.'), 'the scored result')
    expect(bodyText()).toContain('100.0%')
    expect(bodyText()).not.toContain('Recent drills')
  })
})
