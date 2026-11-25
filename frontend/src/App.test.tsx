// @vitest-environment jsdom
import { act } from 'react'
import { createRoot } from 'react-dom/client'
import type { Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import App from './App'
import type { FingerStat, FingerTrend } from './features/progress/fingerApi'
import type { WeakPair, WeakPairTrend } from './features/progress/weakPairApi'
import type {
  ActiveSession,
  RecentSession,
  SessionCompletion,
} from './features/sessions/sessionApi'
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

/** When true, the history request never settles, for the loading state. */
let recentPending = false

/** An open drill the server reports on load, or null for a fresh start. */
let activeSession: ActiveSession | null = null

/** The weakest pair the result panel follows; empty means no focus note. */
let focusPairs: WeakPair[] = []

/** The trend the panel reads for that pair; null means the request fails. */
let focusTrend: WeakPairTrend | null = null

/** The reaches the result panel reports; empty means no reach note. */
let fingerStats: FingerStat[] = []

/** The trend the panel reads for the weakest reach; null means it fails. */
let fingerTrend: FingerTrend | null = null

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

/** When set, the completion request fails with this status. */
let completionFailure: number | null = null

/** When true, the coaching stream is unavailable. */
let feedbackFailure = false

/** When true, the coaching stream never sends a token. */
let feedbackPending = false

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

  if (url.includes('/api/v1/progress/weak-pairs/trend')) {
    if (focusTrend === null) return json({ error: 'unavailable' }, 503)
    return json(focusTrend)
  }
  if (url.includes('/api/v1/progress/weak-pairs')) return json({ tier: 0, pairs: focusPairs })
  if (url.includes('/api/v1/progress/fingers/trend')) {
    if (fingerTrend === null) return json({ error: 'unavailable' }, 503)
    return json(fingerTrend)
  }
  if (url.includes('/api/v1/progress/fingers')) return json({ tier: 0, fingers: fingerStats })
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
  if (url.includes('/api/v1/sessions/active')) {
    if (activeSession === null) return new Response(null, { status: 204 })
    return json(activeSession)
  }
  if (url.includes('/api/v1/sessions/recent')) {
    if (recentPending) return new Promise<Response>(() => undefined)
    if (recentFailure !== null) return json({ error: 'temporarily unavailable' }, recentFailure)
    return json({ sessions: recentResponse })
  }
  if (url.includes('/complete')) {
    if (completionFailure !== null) return json({ error: 'closed' }, completionFailure)
    return json(completionResponse)
  }
  if (url.includes('/feedback')) {
    if (feedbackPending) return new Promise<Response>(() => undefined)
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
  completionFailure = null
  feedbackFailure = false
  feedbackPending = false
  completionResponse = completion
  progressResponse = progress
  recentResponse = recent
  recentFailure = null
  recentPending = false
  activeSession = null
  focusPairs = []
  focusTrend = null
  fingerStats = []
  fingerTrend = null
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

  it('starts a fresh drill when the attempt can no longer be scored', async () => {
    await waitFor(() => shownDrill() === DRILL_TEXT, 'the drill text')

    completionFailure = 409
    for (const key of DRILL_TEXT) await press(key)

    await waitFor(
      () => bodyText().includes('Start a fresh drill'),
      'the closed-attempt action',
    )
    expect(bodyText()).toContain('This attempt can no longer be scored.')
    expect(bodyText()).toContain('The server closed this drill before it was scored.')
    // A terminal close is not a network problem; queued keystrokes cannot help.
    expect(bodyText()).not.toContain('Nothing is lost')

    const issuedBefore = drillIssued
    const fresh = Array.from(container?.querySelectorAll('button') ?? []).find((button) =>
      button.textContent?.includes('Start a fresh drill'),
    )
    expect(fresh).toBeDefined()

    completionFailure = null
    await act(async () => {
      fresh?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    await waitFor(() => drillIssued > issuedBefore, 'a fresh drill request')
    await waitFor(() => shownDrill() === DRILL_TEXT, 'the fresh drill')
    expect(bodyText()).not.toContain('This attempt can no longer be scored.')
  })

  it('says the coach is reading until the first token', async () => {
    feedbackPending = true

    await waitFor(() => shownDrill() === DRILL_TEXT, 'the drill text')
    for (const key of DRILL_TEXT) await press(key)

    await waitFor(
      () => bodyText().includes('Coach is reading the run'),
      'the coach loading line',
    )
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

  it('shows each missed gate in plain words', async () => {
    completionResponse = {
      ...completion,
      passed: false,
      consecutivePasses: 0,
      unlockedTier: null,
      failures: [
        'Accuracy 94.00%: needs 96% or more.',
        'Median 512 ms: needs 450 ms or less.',
      ],
    }

    await waitFor(() => shownDrill() === DRILL_TEXT, 'the drill text')
    for (const key of DRILL_TEXT) await press(key)

    await waitFor(() => bodyText().includes('Not yet.'), 'the failed result')
    expect(bodyText()).toContain('Every third attempt eases the cadence gate by 15%.')
    expect(bodyText()).toContain('Accuracy 94.00%: needs 96% or more.')
    expect(bodyText()).toContain('Median 512 ms: needs 450 ms or less.')
  })

  it('lists the guests recent drills beside the score', async () => {
    const day = (value: string) =>
      new Date(value).toLocaleDateString(undefined, { month: 'short', day: 'numeric' })

    await waitFor(() => shownDrill() === DRILL_TEXT, 'the drill text')
    for (const key of DRILL_TEXT) await press(key)

    await waitFor(() => bodyText().includes('Recent drills'), 'the history list')
    expect(bodyText()).toContain('97.5%')
    expect(bodyText()).toContain('91.2%')
    expect(bodyText()).toContain('not yet')
    // A date gives each attempt a place in the past.
    expect(bodyText()).toContain(day(recent[0].completedAt))
    expect(bodyText()).toContain(day(recent[1].completedAt))
    // The tier trend reads the window oldest to newest.
    expect(bodyText()).toContain('Home row over your last 2 drills:')
    expect(bodyText()).toContain('accuracy 91.2% \u2192 97.5%')
    expect(bodyText()).toContain('median 610 ms \u2192 380 ms')
    // The list is the guest's own history, fetched with the bearer token.
    const historyCall = calls.find((call) => call.url.includes('/api/v1/sessions/recent'))
    expect(historyCall?.url).toContain('limit=20')
  })

  it('reads the tier trend from the practised tier only', async () => {
    recentResponse = [
      {
        ...recent[0],
        sessionId: 'trend-new',
        accuracy: 95,
        medianInterKeyMs: 420,
      },
      {
        ...recent[0],
        sessionId: 'trend-other-tier',
        tier: 2,
        tierName: 'Top row',
        accuracy: 40,
        medianInterKeyMs: 1900,
      },
      {
        ...recent[0],
        sessionId: 'trend-old',
        accuracy: 90,
        medianInterKeyMs: 600,
      },
    ]

    await waitFor(() => shownDrill() === DRILL_TEXT, 'the drill text')
    for (const key of DRILL_TEXT) await press(key)

    await waitFor(() => bodyText().includes('Recent drills'), 'the history list')
    expect(bodyText()).toContain('Home row over your last 2 drills:')
    expect(bodyText()).toContain('accuracy 90.0% \u2192 95.0%')
    expect(bodyText()).toContain('median 600 ms \u2192 420 ms')
    // Another tier's attempts never blend into the practised tier's trend.
    expect(bodyText()).not.toContain('40.0% \u2192')
    expect(bodyText()).not.toContain('1900 ms \u2192')
  })

  it('reveals older drills on request without leaving the result', async () => {
    recentResponse = Array.from({ length: 7 }, (_, index) => ({
      ...recent[0],
      sessionId: `recent-${index}`,
      completedAt: `2026-11-${String(18 - index).padStart(2, '0')}T10:00:00Z`,
    }))

    await waitFor(() => shownDrill() === DRILL_TEXT, 'the drill text')
    for (const key of DRILL_TEXT) await press(key)

    await waitFor(() => bodyText().includes('Recent drills'), 'the history list')
    const rows = () => container?.querySelectorAll('details li') ?? []
    expect(rows()).toHaveLength(5)
    expect(bodyText()).toContain('Show 2 more')

    const more = container?.querySelector('details button')
    expect(more).not.toBeNull()
    await act(async () => {
      ;(more as HTMLButtonElement).click()
    })

    expect(rows()).toHaveLength(7)
    expect(bodyText()).not.toContain('Show 2 more')
  })

  it('follows the weakest focus pair across recent drills', async () => {
    focusPairs = [
      {
        pair: 'dk',
        attempts: 6,
        errors: 2,
        pauses: 3,
        meanMs: 940,
        score: 0.48,
      },
      {
        pair: 'sl',
        attempts: 4,
        errors: 0,
        pauses: 1,
        meanMs: 520,
        score: 0.12,
      },
    ]
    focusTrend = {
      tier: 0,
      pair: 'dk',
      attempts: [
        {
          recordedAt: '2026-11-16T10:00:00Z',
          attempts: 2,
          errors: 1,
          pauses: 1,
          meanMs: 610,
        },
        {
          recordedAt: '2026-11-20T10:00:00Z',
          attempts: 2,
          errors: 0,
          pauses: 0,
          meanMs: 480,
        },
      ],
    }

    await waitFor(() => shownDrill() === DRILL_TEXT, 'the drill text')
    for (const key of DRILL_TEXT) await press(key)

    await waitFor(
      () => bodyText().includes('mean 610 ms \u2192 480 ms'),
      'the weakest pair trend',
    )
    expect(bodyText()).toContain('dk and sl')
    expect(bodyText()).toContain('dk: mean 610 ms \u2192 480 ms over your last 2 drills.')
    // Only the weakest pair is followed, and with a bounded window.
    const trendCall = calls.find((call) => call.url.includes('/weak-pairs/trend'))
    expect(trendCall?.url).toContain('pair=dk')
    expect(trendCall?.url).toContain('limit=5')
  })

  it('keeps the focus note when the pair trend cannot be read', async () => {
    focusPairs = [
      {
        pair: 'dk',
        attempts: 6,
        errors: 2,
        pauses: 3,
        meanMs: 940,
        score: 0.48,
      },
    ]

    await waitFor(() => shownDrill() === DRILL_TEXT, 'the drill text')
    for (const key of DRILL_TEXT) await press(key)

    await waitFor(() => bodyText().includes('Your next drills lean on'), 'the focus note')
    expect(bodyText()).toContain('dk')
    expect(bodyText()).not.toContain('mean')
  })

  it('follows the weakest reach across recent drills', async () => {
    fingerStats = [
      {
        finger: 'LEFT_RING',
        label: 'Left ring',
        attempts: 6,
        errors: 2,
        pauses: 3,
        meanMs: 640,
        score: 0.32,
      },
    ]
    fingerTrend = {
      tier: 0,
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

    await waitFor(() => shownDrill() === DRILL_TEXT, 'the drill text')
    for (const key of DRILL_TEXT) await press(key)

    await waitFor(
      () => bodyText().includes('mean 720 ms \u2192 560 ms'),
      'the weakest reach trend',
    )
    expect(bodyText()).toContain('Left ring, still finding its rhythm.')
    expect(bodyText()).toContain(
      'Left ring: mean 720 ms \u2192 560 ms over your last 2 drills.',
    )
    // Only the weakest repeated reach is followed, with a bounded window.
    const trendCall = calls.find((call) => call.url.includes('/fingers/trend'))
    expect(trendCall?.url).toContain('finger=LEFT_RING')
    expect(trendCall?.url).toContain('limit=5')
  })

  it('leaves out the reach note when the weak reach is unproven', async () => {
    fingerStats = [
      {
        finger: 'LEFT_RING',
        label: 'Left ring',
        attempts: 1,
        errors: 1,
        pauses: 0,
        meanMs: 640,
        score: 0.32,
      },
    ]
    fingerTrend = {
      tier: 0,
      finger: 'LEFT_RING',
      label: 'Left ring',
      attempts: [],
    }

    await waitFor(() => shownDrill() === DRILL_TEXT, 'the drill text')
    for (const key of DRILL_TEXT) await press(key)

    await waitFor(() => bodyText().includes('Gate passed.'), 'the result')
    expect(bodyText()).not.toContain('still finding its rhythm')
    expect(calls.some((call) => call.url.includes('/fingers/trend'))).toBe(false)
  })

  it('moves focus to the scored result', async () => {
    await waitFor(() => shownDrill() === DRILL_TEXT, 'the drill text')
    for (const key of DRILL_TEXT) await press(key)

    await waitFor(() => bodyText().includes('Gate passed.'), 'the result')
    const heading = Array.from(container?.querySelectorAll('h1') ?? []).find(
      (candidate) => candidate.textContent === 'Gate passed.',
    )
    expect(heading).toBeDefined()
    expect(document.activeElement).toBe(heading)
  })

  it('practises the weakest pair on request', async () => {
    focusPairs = [
      {
        pair: 'dk',
        attempts: 6,
        errors: 2,
        pauses: 3,
        meanMs: 940,
        score: 0.48,
      },
    ]

    await waitFor(() => shownDrill() === DRILL_TEXT, 'the drill text')
    for (const key of DRILL_TEXT) await press(key)

    await waitFor(() => bodyText().includes('Practise dk'), 'the practice action')
    const practice = Array.from(container?.querySelectorAll('button') ?? []).find(
      (button) => button.textContent?.includes('Practise dk'),
    )
    expect(practice).toBeDefined()

    await act(async () => {
      practice?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    await waitFor(
      () => calls.some((call) => call.url.includes('/drills/next') && call.url.includes('focus=dk')),
      'a focused drill request',
    )
    const focused = calls.filter(
      (call) => call.url.includes('/drills/next') && call.url.includes('focus=dk'),
    )
    expect(focused).toHaveLength(3)
    expect(focused.every((call) => !call.url.includes('finger='))).toBe(true)
    await waitFor(() => shownDrill() === DRILL_TEXT, 'the focused drill')
  })

  it('practises the weakest reach on request', async () => {
    fingerStats = [
      {
        finger: 'LEFT_RING',
        label: 'Left ring',
        attempts: 6,
        errors: 2,
        pauses: 3,
        meanMs: 640,
        score: 0.32,
      },
    ]

    await waitFor(() => shownDrill() === DRILL_TEXT, 'the drill text')
    for (const key of DRILL_TEXT) await press(key)

    await waitFor(
      () => bodyText().includes('Practise Left ring'),
      'the reach practice action',
    )
    const practice = Array.from(container?.querySelectorAll('button') ?? []).find(
      (button) => button.textContent?.includes('Practise Left ring'),
    )
    expect(practice).toBeDefined()

    await act(async () => {
      practice?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    await waitFor(
      () =>
        calls.some(
          (call) =>
            call.url.includes('/drills/next') && call.url.includes('finger=LEFT_RING'),
        ),
      'a reach-focused drill request',
    )
    const focused = calls.filter(
      (call) => call.url.includes('/drills/next') && call.url.includes('finger=LEFT_RING'),
    )
    expect(focused).toHaveLength(3)
    expect(focused.every((call) => !call.url.includes('focus='))).toBe(true)
  })

  it('says when the history is still loading', async () => {
    recentPending = true

    await waitFor(() => shownDrill() === DRILL_TEXT, 'the drill text')
    for (const key of DRILL_TEXT) await press(key)

    await waitFor(
      () => bodyText().includes('Loading recent drills'),
      'the history loading line',
    )
  })

  it('says when no finished drills exist yet', async () => {
    recentResponse = []

    await waitFor(() => shownDrill() === DRILL_TEXT, 'the drill text')
    for (const key of DRILL_TEXT) await press(key)

    await waitFor(
      () => bodyText().includes('No finished drills yet.'),
      'the empty history line',
    )
    expect(bodyText()).not.toContain('Loading recent drills')
  })

  it('filters recent drills to one tier', async () => {
    recentResponse = [
      { ...recent[0], sessionId: 'home-1', tier: 0, tierName: 'Home row' },
      { ...recent[0], sessionId: 'top-1', tier: 1, tierName: 'Top row' },
      { ...recent[0], sessionId: 'top-2', tier: 1, tierName: 'Top row' },
    ]

    await waitFor(() => shownDrill() === DRILL_TEXT, 'the drill text')
    for (const key of DRILL_TEXT) await press(key)

    await waitFor(() => bodyText().includes('Recent drills'), 'the history list')
    const rows = () => container?.querySelectorAll('details li') ?? []
    expect(rows()).toHaveLength(3)

    const filter = Array.from(container?.querySelectorAll('button') ?? []).find(
      (button) => button.textContent === 'Top row',
    )
    expect(filter).toBeDefined()
    await act(async () => {
      filter?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })

    expect(rows()).toHaveLength(2)
    expect(bodyText()).toContain('All')
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
