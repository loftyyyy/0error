import { useEffect, useRef, useState } from 'react'
import { ArrowRight, RotateCcw } from 'lucide-react'

import { fetchDrillSet } from './features/drills/drillApi'
import { streamFeedback } from './features/feedback/feedbackApi'
import { FingerNote } from './features/progress/FingerNote'
import { FocusNote } from './features/progress/FocusNote'
import { activeTier, fetchProgress } from './features/progress/progressApi'
import type { ProgressSummary } from './features/progress/progressApi'
import {
  abandonServerSession,
  completeServerSession,
  createServerSession,
  fetchActiveSession,
  fetchRecentSessions,
  SessionGoneError,
} from './features/sessions/sessionApi'
import type { RecentSession, SessionCompletion } from './features/sessions/sessionApi'
import { flushSession } from './features/telemetry/telemetryBuffer'
import { SyncLine } from './features/telemetry/SyncLine'
import { KeyboardGuide } from './features/typing/KeyboardGuide'
import { TouchNotice } from './features/typing/TouchNotice'
import { characterClass } from './features/typing/characterClass'
import { useHesitation } from './features/typing/useHesitation'
import { useKeystrokeCapture } from './features/typing/useKeystrokeCapture'
import { useTouchOnly } from './features/typing/useTouchOnly'
import { ThemeToggle } from './features/theme/ThemeToggle'

function newClientSessionId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

interface ActiveDrill {
  sessionId: string
  tier: number
  tierName: string
  text: string
  /** Last drill in the set, used to avoid repeating it on the next attempt. */
  tail: string
  /** Present when the drill was restored from the server after a reload. */
  resume?: {
    position: number
    seq: number
    elapsedMs: number
    typed: number
    scored: number
    correct: number
  }
}

/** Where the next drill comes from. Omitted fields mean "ask the server". */
interface NextDrill {
  tier?: number
  exclude?: string
  /** One pair the attempt should exercise, when practising a weakness. */
  focus?: string
  /** One reach the attempt should exercise. */
  finger?: string
}

type Phase = 'loading' | 'ready' | 'scoring' | 'result' | 'error'

function App() {
  const touchOnly = useTouchOnly()
  const [attempt, setAttempt] = useState(0)
  const [next, setNext] = useState<NextDrill>({})
  const [phase, setPhase] = useState<Phase>('loading')
  const [drill, setDrill] = useState<ActiveDrill | null>(null)
  const [result, setResult] = useState<SessionCompletion | null>(null)
  const [progress, setProgress] = useState<ProgressSummary | null>(null)
  const [error, setError] = useState('')
  /** The attempt is closed on the server; retrying the score cannot help. */
  const [terminal, setTerminal] = useState(false)

  useEffect(() => {
    let cancelled = false

    const bootstrap = async () => {
      try {
        const summary = await fetchProgress()
        if (cancelled) return
        setProgress(summary)

        // A reload should continue the drill it already started. A failed
        // check is not fatal: the guest still gets a fresh drill.
        const active = await fetchActiveSession().catch(() => null)
        if (cancelled) return
        if (active) {
          setDrill({
            sessionId: active.sessionId,
            tier: active.tier,
            tierName: active.tierName,
            text: active.text,
            tail: '',
            resume: {
              position: active.position,
              seq: active.nextSeq,
              elapsedMs: active.elapsedMs,
              typed: active.typedCount,
              scored: active.scoredCount,
              correct: active.correctCount,
            },
          })
          setPhase('ready')
          return
        }

        const tier = next.tier ?? activeTier(summary)
        const fetched = await fetchDrillSet(tier, next.exclude, next.focus, next.finger)
        const session = await createServerSession({
          clientSessionId: newClientSessionId(),
          drillIds: fetched.drillIds,
        })

        if (cancelled) return
        setDrill({
          sessionId: session.id,
          tier: fetched.tier,
          tierName: fetched.tierName,
          text: fetched.text,
          tail: fetched.tail,
        })
        setPhase('ready')
      } catch (reason: unknown) {
        if (cancelled) return
        setError(reason instanceof Error ? reason.message : 'Could not reach the server.')
        setPhase('error')
      }
    }

    void bootstrap()

    return () => {
      cancelled = true
    }
  }, [attempt, next])

  const startNext = (
    tier?: number,
    exclude?: string,
    focus?: string,
    finger?: string,
  ) => {
    setNext({ tier, exclude, focus, finger })
    setDrill(null)
    setResult(null)
    setError('')
    setTerminal(false)
    setPhase('loading')
    setAttempt((value) => value + 1)
  }

  const retry = () => {
    setError('')
    setTerminal(false)
    setDrill(null)
    setResult(null)
    setPhase('loading')
    setAttempt((value) => value + 1)
  }

  /** Close a resumed drill on the server, then start a clean attempt. */
  const startOver = async () => {
    const current = drill
    if (!current) return
    await abandonServerSession(current.sessionId).catch(() => undefined)
    startNext(current.tier)
  }

  const score = async () => {
    if (!drill) return
    setPhase('scoring')

    try {
      const saved = await flushSession(drill.sessionId)
      if (!saved) {
        throw new Error('Your keystrokes have not all reached the server yet. Retry when online.')
      }
      const completion = await completeServerSession(drill.sessionId)
      setResult(completion)
      setPhase('result')

      // The ladder only changes here, so refresh it once the score is in.
      // A failed refresh leaves the last known ladder on screen.
      void fetchProgress()
        .then(setProgress)
        .catch(() => undefined)
    } catch (reason: unknown) {
      if (reason instanceof SessionGoneError) {
        setTerminal(true)
        setError(reason.message)
      } else {
        setTerminal(false)
        setError(reason instanceof Error ? reason.message : 'Could not score this drill.')
      }
      setPhase('error')
    }
  }

  const isTyping = phase === 'ready' || phase === 'scoring'

  return (
    <main className="flex min-h-screen items-center justify-center bg-stone-50 px-6 py-16 text-stone-900 dark:bg-stone-950 dark:text-stone-100">
      <div className="w-full max-w-2xl">
        {/* Always mounted so the scored outcome is announced when it changes. */}
        <p role="status" aria-live="polite" className="sr-only">
          {phase === 'result' && result
            ? `${result.passed ? 'Gate passed.' : 'Not yet.'} ${result.consecutivePasses} of 2 consecutive passes.`
            : ''}
        </p>
        <header className="mb-16 flex items-center justify-between">
          <span className="text-sm font-medium tracking-[0.2em] text-stone-400 uppercase dark:text-stone-500">
            0Error
          </span>
          <div className="flex items-center gap-3">
            <span className="text-xs text-stone-400 dark:text-stone-500">
              {drill?.tierName ?? 'your pace'}
            </span>
            <ThemeToggle />
          </div>
        </header>

        {phase === 'loading' ? <LoadingDrill /> : null}
        {phase === 'error' ? (
          <ConnectionError
            message={error}
            scoring={Boolean(drill) && !terminal}
            terminal={terminal}
            onRetry={() => {
              if (terminal || !drill) {
                retry()
              } else {
                void score()
              }
            }}
          />
        ) : null}
        {phase === 'result' && result && drill ? (
          <ResultPanel
            drill={drill}
            result={result}
            progress={progress}
            onNext={() => startNext(result.unlockedTier ?? drill.tier, drill.tail)}
            onPracticePair={(pair) => startNext(drill.tier, drill.tail, pair)}
            onPracticeFinger={(finger) => startNext(drill.tier, drill.tail, undefined, finger)}
          />
        ) : null}
        {isTyping && drill ? (
          touchOnly ? (
            <TouchNotice />
          ) : (
            <Drill
              drill={drill}
              scoring={phase === 'scoring'}
              onStartOver={drill.resume ? () => void startOver() : undefined}
              onComplete={() => {
                void score()
              }}
            />
          )
        ) : null}

        <footer className="mt-20 text-xs leading-relaxed text-stone-400 dark:text-stone-600">
          Clean keys fade; a warm letter marks a pause. Telemetry queues locally until the API
          acknowledges it. Focus loss pauses the clock. Three keystrokes after a pause are warmup
          and never scored.
        </footer>
      </div>
    </main>
  )
}

function LoadingDrill() {
  return (
    <div className="py-12 text-sm text-stone-400 dark:text-stone-500" aria-live="polite">
      Setting up your session&hellip;
    </div>
  )
}

function ConnectionError({
  message,
  scoring,
  terminal,
  onRetry,
}: {
  message: string
  scoring: boolean
  terminal: boolean
  onRetry: () => void
}) {
  return (
    <div className="rounded-2xl border border-stone-200 px-6 py-8 dark:border-stone-800">
      <p className="text-sm text-stone-500 dark:text-stone-400">
        {terminal
          ? 'This attempt can no longer be scored. Your tier has not moved.'
          : scoring
            ? 'The drill is not scored yet. Nothing is lost: your keystrokes stay queued.'
            : 'The server is not reachable, so this drill cannot be recorded.'}
      </p>
      <p className="mt-2 text-xs text-stone-400 dark:text-stone-500">{message}</p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-6 flex items-center gap-1.5 rounded-full border border-stone-200 px-3 py-1.5 text-sm text-stone-600 transition hover:border-stone-300 hover:text-stone-900 dark:border-stone-800 dark:text-stone-300 dark:hover:border-stone-700 dark:hover:text-stone-50"
      >
        {terminal ? (
          <ArrowRight size={13} strokeWidth={1.75} />
        ) : (
          <RotateCcw size={13} strokeWidth={1.75} />
        )}
        {terminal ? 'Start a fresh drill' : 'Try again'}
      </button>
    </div>
  )
}

interface DrillProps {
  drill: ActiveDrill
  scoring: boolean
  onComplete: () => void
  onStartOver?: () => void
}

function Drill({ drill, scoring, onComplete, onStartOver }: DrillProps) {
  const { containerRef, snapshot, notice } = useKeystrokeCapture({
    sessionId: drill.sessionId,
    text: drill.text,
    onComplete,
    initialPosition: drill.resume?.position,
    initialSeq: drill.resume?.seq,
    initialElapsedMs: drill.resume?.elapsedMs,
    initialTyped: drill.resume?.typed,
    initialScored: drill.resume?.scored,
    initialCorrect: drill.resume?.correct,
    warmupOnStart: Boolean(drill.resume),
  })

  // "Next drill" and "Try again" unmount the button that was pressed, so park
  // focus on the typing surface instead of dropping it back onto the body.
  useEffect(() => {
    containerRef.current?.focus({ preventScroll: true })
  }, [containerRef])

  const descriptionId = `drill-text-${drill.sessionId}`

  const progress = snapshot.length === 0 ? 0 : (snapshot.index / snapshot.length) * 100
  const isPaused = snapshot.status === 'paused'
  const isComplete = snapshot.status === 'complete'
  const running = snapshot.status === 'running' && !scoring

  // Each keystroke restarts the timer; only a real pause reveals the guide.
  const hesitating = useHesitation(snapshot.eventCount, running)

  return (
    <div
      ref={containerRef}
      role="application"
      aria-label="Typing practice. Type the text shown."
      aria-describedby={descriptionId}
      tabIndex={0}
      className="cursor-text rounded-2xl outline-none focus-visible:ring-2 focus-visible:ring-stone-300 dark:focus-visible:ring-stone-700"
    >
      {/* The drawn text is split into per-character spans, so screen readers
          get the drill as one plain paragraph instead. */}
      <p id={descriptionId} className="sr-only">
        {drill.text}
      </p>
      <p
        aria-hidden="true"
        data-testid="drill-text"
        className="font-mono text-3xl leading-[1.9] tracking-wide break-words select-none sm:text-4xl"
      >
        {drill.text.split('').map((character, index) => {
          const state =
            index < snapshot.index ? 'done' : index === snapshot.index ? 'current' : 'todo'
          return (
            <span
              key={`${character}-${index}`}
              className={characterClass(state, snapshot.quality[index] ?? null)}
            >
              {character === ' ' ? '\u00a0' : character}
            </span>
          )
        })}
      </p>

      <div className="mt-12 h-px w-full bg-stone-200 dark:bg-stone-800">
        <div
          className="h-px bg-stone-900 transition-[width] duration-200 ease-out dark:bg-stone-100"
          style={{ width: `${progress}%` }}
        />
      </div>

      <div className="mt-5 flex min-h-12 flex-wrap items-center justify-between gap-3 text-sm">
        <span className="text-stone-500 dark:text-stone-400">
          {scoring
            ? 'Scoring on the server\u2026'
            : isComplete
              ? 'Drill complete. Nothing to rush.'
              : isPaused
                ? 'Paused. Click here to continue.'
                : drill.resume && snapshot.index === drill.resume.position
                  ? 'Picked up where you left off.'
                  : snapshot.warmupRemaining > 0
                    ? 'Back in rhythm.'
                    : 'Keep it smooth.'}
        </span>

        <span className="flex items-center gap-5 tabular-nums text-stone-400 dark:text-stone-500">
          <span>{Math.round(snapshot.accuracy * 100)}% accurate</span>
          <span>{snapshot.typed} keys</span>
        </span>
      </div>

      <div className="mt-2 h-5 text-xs text-stone-400 dark:text-stone-500" aria-live="polite">
        {notice === 'paste-blocked' ? 'Paste is off. Type it out.' : null}
      </div>

      <SyncLine />

      {onStartOver ? (
        <button
          type="button"
          onClick={onStartOver}
          className="mt-1 text-xs text-stone-400 underline underline-offset-2 hover:text-stone-600 dark:text-stone-500 dark:hover:text-stone-300"
        >
          Start over
        </button>
      ) : null}

      <KeyboardGuide
        character={drill.text[snapshot.index] ?? null}
        hesitating={hesitating}
        visible={!scoring}
      />
    </div>
  )
}

function ResultPanel({
  drill,
  result,
  progress,
  onNext,
  onPracticePair,
  onPracticeFinger,
}: {
  drill: ActiveDrill
  result: SessionCompletion
  progress: ProgressSummary | null
  onNext: () => void
  onPracticePair: (pair: string) => void
  onPracticeFinger: (finger: string) => void
}) {
  const heading = useRef<HTMLHeadingElement | null>(null)

  // A new result is the next thing to read; park focus on its heading instead
  // of leaving it on the typing surface that just unmounted.
  useEffect(() => {
    heading.current?.focus({ preventScroll: true })
  }, [])

  // Once nothing above this tier is locked there is no next unlock to promise.
  const nextLocked = progress?.tiers.find(
    (tier) => tier.tier > result.tier && !tier.unlocked,
  )

  return (
    <div className="rounded-2xl border border-stone-200 px-6 py-8 dark:border-stone-800">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1
          ref={heading}
          tabIndex={-1}
          className="text-lg font-medium outline-none"
        >
          {result.passed ? 'Gate passed.' : 'Not yet.'}
        </h1>
        <span className="text-xs tabular-nums text-stone-400 dark:text-stone-500">
          {result.consecutivePasses} of 2 consecutive passes
        </span>
      </div>

      {result.unlockedTier !== null ? (
        <p className="mt-3 text-sm text-stone-600 dark:text-stone-300">
          {progress?.tiers.find((tier) => tier.tier === result.unlockedTier)?.name ??
            `Tier ${result.unlockedTier + 1}`}{' '}
          unlocked. Your next drill moves on.
        </p>
      ) : result.passed ? (
        <p className="mt-3 text-sm text-stone-500 dark:text-stone-400">
          {progress && !nextLocked
            ? 'Every tier is open. Keep the rhythm.'
            : 'One more clean pass unlocks your next tier.'}
        </p>
      ) : (
        <p className="mt-3 text-sm text-stone-500 dark:text-stone-400">
          Every third attempt eases the cadence gate by 15%.
        </p>
      )}

      <dl className="mt-8 grid grid-cols-2 gap-x-6 gap-y-5 tabular-nums sm:grid-cols-4">
        <Metric label="Accuracy" value={formatPercent(result.metrics.accuracy)} />
        <Metric label="Median" value={formatMs(result.metrics.medianInterKeyMs)} />
        <Metric label="p95" value={formatMs(result.metrics.p95InterKeyMs)} />
        <Metric label="Keystrokes" value={String(result.metrics.keystrokeCount)} />
      </dl>

      <p className="mt-5 text-xs text-stone-400 dark:text-stone-500">
        {drill.tierName} gate: {result.accuracyThreshold}% accurate,{' '}
        {result.medianThresholdMs} ms median, {result.p95ThresholdMs} ms p95, at least{' '}
        {result.minimumKeystrokes} keys.
      </p>

      {result.failures.length > 0 ? (
        <ul className="mt-4 space-y-1 text-xs text-stone-500 dark:text-stone-400">
          {result.failures.map((failure) => (
            <li key={failure}>{failure}</li>
          ))}
        </ul>
      ) : null}

      {progress ? <TierLadder progress={progress} currentTier={result.tier} /> : null}

      <CoachNote sessionId={drill.sessionId} />
      <FocusNote key={`focus-${result.tier}`} tier={result.tier} onPractice={onPracticePair} />
      <FingerNote key={`finger-${result.tier}`} tier={result.tier} onPractice={onPracticeFinger} />
      <RecentDrills tier={result.tier} />

      <button
        type="button"
        onClick={onNext}
        className="mt-8 flex items-center gap-1.5 rounded-full border border-stone-200 px-3 py-1.5 text-sm text-stone-600 transition hover:border-stone-300 hover:text-stone-900 dark:border-stone-800 dark:text-stone-300 dark:hover:border-stone-700 dark:hover:text-stone-50"
      >
        {result.passed ? 'Next drill' : 'Try again'}
        <ArrowRight size={13} strokeWidth={1.75} />
      </button>
    </div>
  )
}

/**
 * The curriculum at a glance. Every tier the server knows is drawn, the one
 * being practised is marked, and the next locked tier states the gate it still
 * has to clear with the cadence it is actually held to.
 */
function TierLadder({
  progress,
  currentTier,
}: {
  progress: ProgressSummary
  currentTier: number
}) {
  const next = progress.tiers.find((tier) => !tier.unlocked)

  return (
    <section
      aria-label="Tier ladder"
      className="mt-8 border-t border-stone-200 pt-6 dark:border-stone-800"
    >
      <ol className="flex flex-wrap gap-x-4 gap-y-2 text-xs">
        {progress.tiers.map((tier) => {
          const current = tier.tier === currentTier
          return (
            <li
              key={tier.tier}
              aria-current={current ? 'step' : undefined}
              className={
                tier.unlocked
                  ? 'text-stone-600 dark:text-stone-300'
                  : 'text-stone-300 dark:text-stone-600'
              }
            >
              <span aria-hidden="true">{tier.unlocked ? '\u25cf' : '\u25cb'}</span>{' '}
              {tier.name}
              {current ? <span className="sr-only"> (current)</span> : null}
            </li>
          )
        })}
      </ol>

      {next ? (
        <p className="mt-3 text-xs text-stone-400 dark:text-stone-500">
          {next.name} opens after {next.medianThresholdMs} ms median and{' '}
          {next.p95ThresholdMs} ms p95 with two clean passes in a row.
          {next.failedAttempts >= 3
            ? ` Cadence eased after ${next.failedAttempts} failed attempts.`
            : null}
        </p>
      ) : (
        <p className="mt-3 text-xs text-stone-400 dark:text-stone-500">
          Every tier is open.
        </p>
      )}
    </section>
  )
}

/**
 * A one-line read on the tier just practised, from the same history fetch as
 * the list below. It compares the oldest and newest attempt in the window
 * rather than drawing a chart: the point is direction, not noise. Every value
 * is the server's; this only puts two of them side by side.
 */
function tierTrend(sessions: RecentSession[], tier: number): string | null {
  const mine = sessions
    .filter((entry) => entry.tier === tier)
    .slice(0, 5)
    .reverse()
  if (mine.length < 2) return null

  const first = mine[0]
  const last = mine[mine.length - 1]
  const parts: string[] = []

  if (first.accuracy !== null && last.accuracy !== null) {
    parts.push(
      `accuracy ${first.accuracy.toFixed(1)}% \u2192 ${last.accuracy.toFixed(1)}%`,
    )
  }
  if (first.medianInterKeyMs !== null && last.medianInterKeyMs !== null) {
    parts.push(
      `median ${first.medianInterKeyMs} ms \u2192 ${last.medianInterKeyMs} ms`,
    )
  }
  if (parts.length === 0) return null

  return `${last.tierName} over your last ${mine.length} drills: ${parts.join(' \u00b7 ')}`
}

/**
 * The finished drills the result panel offers: five at first, up to twenty
 * behind a click. It is context, not a target: a failed request leaves the
 * result panel exactly as it was.
 */
const HISTORY_VISIBLE = 5
const HISTORY_LIMIT = 20

function RecentDrills({ tier }: { tier: number }) {
  const [sessions, setSessions] = useState<RecentSession[] | null>(null)
  const [failed, setFailed] = useState(false)
  const [expanded, setExpanded] = useState(false)

  useEffect(() => {
    let active = true

    fetchRecentSessions(HISTORY_LIMIT)
      .then((entries) => {
        if (active) setSessions(entries)
      })
      .catch(() => {
        if (active) setFailed(true)
      })

    return () => {
      active = false
    }
  }, [])

  if (failed) return null

  if (sessions === null) {
    return (
      <p className="mt-6 text-xs text-stone-400 dark:text-stone-500">
        Loading recent drills&hellip;
      </p>
    )
  }

  if (sessions.length === 0) {
    return (
      <p className="mt-6 text-xs text-stone-400 dark:text-stone-500">
        No finished drills yet.
      </p>
    )
  }

  const visible = expanded ? sessions : sessions.slice(0, HISTORY_VISIBLE)
  const hidden = sessions.length - visible.length
  const trend = tierTrend(sessions, tier)

  return (
    <div className="mt-6 border-t border-stone-200 pt-4 dark:border-stone-800">
      {trend ? (
        <p className="text-xs text-stone-400 dark:text-stone-500">{trend}</p>
      ) : null}
      <details className={trend ? 'mt-3' : undefined}>
        <summary className="cursor-pointer text-[0.65rem] font-medium tracking-[0.2em] text-stone-400 uppercase dark:text-stone-500">
          Recent drills
        </summary>
        <ul className="mt-3 space-y-1.5 text-xs tabular-nums">
          {visible.map((entry) => (
            <li key={entry.sessionId} className="flex items-baseline justify-between gap-4">
              <span className="text-stone-600 dark:text-stone-300">
                {entry.tierName}
                <span className="ml-2 text-stone-400 dark:text-stone-500">
                  {shortDate(entry.completedAt)}
                </span>
              </span>
              <span className="text-stone-400 dark:text-stone-500">
                {formatPercent(entry.accuracy)} &middot; {formatMs(entry.medianInterKeyMs)}{' '}
                &middot;{' '}
                {entry.passed === null ? 'gate unknown' : entry.passed ? 'passed' : 'not yet'}
              </span>
            </li>
          ))}
        </ul>
        {hidden > 0 ? (
          <button
            type="button"
            onClick={() => setExpanded(true)}
            className="mt-2 text-xs text-stone-400 underline underline-offset-2 hover:text-stone-600 dark:text-stone-500 dark:hover:text-stone-300"
          >
            Show {hidden} more
          </button>
        ) : null}
      </details>
    </div>
  )
}

/**
 * Streams the post-session coaching note. The server always sends something:
 * model tokens when configured, deterministic sentences otherwise.
 */
function CoachNote({ sessionId }: { sessionId: string }) {
  const [text, setText] = useState('')
  const [state, setState] = useState<'streaming' | 'done' | 'error'>('streaming')

  useEffect(() => {
    const controller = new AbortController()
    let active = true

    void streamFeedback(sessionId, controller.signal, {
      onToken: (token) => setText((current) => current + token),
      onDone: () => setState('done'),
    }).catch(() => {
      if (!active || controller.signal.aborted) return
      setState('error')
    })

    return () => {
      active = false
      controller.abort()
    }
  }, [sessionId])

  return (
    <div
      className="mt-8 rounded-xl bg-stone-100/70 px-5 py-4 dark:bg-stone-900/70"
      aria-busy={state === 'streaming'}
    >
      <p className="text-[0.65rem] font-medium tracking-[0.2em] text-stone-400 uppercase dark:text-stone-500">
        Coach
      </p>
      <p className="mt-2 min-h-10 text-sm leading-relaxed text-stone-600 dark:text-stone-300">
        {state === 'error'
          ? 'Coaching is unavailable right now. Your metrics are saved.'
          : text === '' && state === 'streaming'
            ? 'Coach is reading the run'
            : text}
        {state === 'streaming' && text !== '' ? (
          <span className="ml-0.5 inline-block h-3.5 w-1.5 animate-pulse bg-stone-300 align-text-bottom motion-reduce:animate-none dark:bg-stone-600" />
        ) : null}
      </p>
    </div>
  )
}

function Metric({ label, value }: { label: string; value: string }) {
  return (
    <div>
      <dt className="text-xs text-stone-400 dark:text-stone-500">{label}</dt>
      <dd className="mt-1 text-lg">{value}</dd>
    </div>
  )
}

function formatPercent(value: number | null): string {
  return value === null ? '\u2014' : `${value.toFixed(1)}%`
}

/** A short calendar date for the history list, in the guest's own locale. */
function shortDate(value: string): string {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return date.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

function formatMs(value: number | null): string {
  return value === null ? '\u2014' : `${value} ms`
}

export default App
