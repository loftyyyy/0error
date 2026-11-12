import { useEffect, useState } from 'react'
import { ArrowRight, RotateCcw } from 'lucide-react'

import { fetchDrillSet } from './features/drills/drillApi'
import { streamFeedback } from './features/feedback/feedbackApi'
import { activeTier, fetchProgress } from './features/progress/progressApi'
import { completeServerSession, createServerSession } from './features/sessions/sessionApi'
import type { SessionCompletion } from './features/sessions/sessionApi'
import { flushSession } from './features/telemetry/telemetryBuffer'
import { useKeystrokeCapture } from './features/typing/useKeystrokeCapture'

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
}

/** Where the next drill comes from. Omitted fields mean "ask the server". */
interface NextDrill {
  tier?: number
  exclude?: string
}

type Phase = 'loading' | 'ready' | 'scoring' | 'result' | 'error'

function App() {
  const [attempt, setAttempt] = useState(0)
  const [next, setNext] = useState<NextDrill>({})
  const [phase, setPhase] = useState<Phase>('loading')
  const [drill, setDrill] = useState<ActiveDrill | null>(null)
  const [result, setResult] = useState<SessionCompletion | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    let cancelled = false

    const bootstrap = async () => {
      try {
        const tier = next.tier ?? activeTier(await fetchProgress())
        const fetched = await fetchDrillSet(tier, next.exclude)
        const session = await createServerSession({
          clientSessionId: newClientSessionId(),
          tier: fetched.tier,
          targetCharset: fetched.charset,
          targetLength: fetched.text.length,
          drillSource: fetched.source,
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

  const startNext = (tier?: number, exclude?: string) => {
    setNext({ tier, exclude })
    setDrill(null)
    setResult(null)
    setError('')
    setPhase('loading')
    setAttempt((value) => value + 1)
  }

  const retry = () => {
    setError('')
    setPhase('loading')
    setAttempt((value) => value + 1)
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
    } catch (reason: unknown) {
      setError(reason instanceof Error ? reason.message : 'Could not score this drill.')
      setPhase('error')
    }
  }

  const isTyping = phase === 'ready' || phase === 'scoring'

  return (
    <main className="flex min-h-screen items-center justify-center bg-stone-50 px-6 py-16 text-stone-900 dark:bg-stone-950 dark:text-stone-100">
      <div className="w-full max-w-2xl">
        <header className="mb-16 flex items-baseline justify-between">
          <span className="text-sm font-medium tracking-[0.2em] text-stone-400 uppercase dark:text-stone-500">
            0Error
          </span>
          <span className="text-xs text-stone-400 dark:text-stone-500">
            {drill?.tierName ?? 'your pace'}
          </span>
        </header>

        {phase === 'loading' ? <LoadingDrill /> : null}
        {phase === 'error' ? (
          <ConnectionError
            message={error}
            scoring={Boolean(drill)}
            onRetry={() => {
              if (drill) {
                void score()
              } else {
                retry()
              }
            }}
          />
        ) : null}
        {phase === 'result' && result && drill ? (
          <ResultPanel
            drill={drill}
            result={result}
            onNext={() => startNext(result.unlockedTier ?? drill.tier, drill.tail)}
          />
        ) : null}
        {isTyping && drill ? (
          <Drill
            drill={drill}
            scoring={phase === 'scoring'}
            onComplete={() => {
              void score()
            }}
          />
        ) : null}

        <footer className="mt-20 text-xs leading-relaxed text-stone-400 dark:text-stone-600">
          Telemetry queues locally until the API acknowledges it. Focus loss pauses the clock. Three
          keystrokes after a pause are warmup and never scored.
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
  onRetry,
}: {
  message: string
  scoring: boolean
  onRetry: () => void
}) {
  return (
    <div className="rounded-2xl border border-stone-200 px-6 py-8 dark:border-stone-800">
      <p className="text-sm text-stone-500 dark:text-stone-400">
        {scoring
          ? 'The drill is not scored yet. Nothing is lost: your keystrokes stay queued.'
          : 'The server is not reachable, so this drill cannot be recorded.'}
      </p>
      <p className="mt-2 text-xs text-stone-400 dark:text-stone-500">{message}</p>
      <button
        type="button"
        onClick={onRetry}
        className="mt-6 flex items-center gap-1.5 rounded-full border border-stone-200 px-3 py-1.5 text-sm text-stone-600 transition hover:border-stone-300 hover:text-stone-900 dark:border-stone-800 dark:text-stone-300 dark:hover:border-stone-700 dark:hover:text-stone-50"
      >
        <RotateCcw size={13} strokeWidth={1.75} />
        Try again
      </button>
    </div>
  )
}

interface DrillProps {
  drill: ActiveDrill
  scoring: boolean
  onComplete: () => void
}

function Drill({ drill, scoring, onComplete }: DrillProps) {
  const { containerRef, snapshot, notice } = useKeystrokeCapture({
    sessionId: drill.sessionId,
    text: drill.text,
    onComplete,
  })

  const progress = snapshot.length === 0 ? 0 : (snapshot.index / snapshot.length) * 100
  const isPaused = snapshot.status === 'paused'
  const isComplete = snapshot.status === 'complete'

  return (
    <div
      ref={containerRef}
      role="application"
      aria-label="Typing practice. Type the text shown."
      tabIndex={0}
      className="cursor-text rounded-2xl outline-none focus-visible:ring-2 focus-visible:ring-stone-300 dark:focus-visible:ring-stone-700"
    >
      <p
        aria-hidden="true"
        className="font-mono text-3xl leading-[1.9] tracking-wide break-words select-none sm:text-4xl"
      >
        {drill.text.split('').map((character, index) => {
          const state =
            index < snapshot.index ? 'done' : index === snapshot.index ? 'current' : 'todo'
          return (
            <span
              key={`${character}-${index}`}
              className={
                state === 'done'
                  ? 'text-stone-300 dark:text-stone-700'
                  : state === 'current'
                    ? 'border-b-2 border-stone-900 text-stone-900 dark:border-stone-100 dark:text-stone-100'
                    : 'text-stone-400 dark:text-stone-500'
              }
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
    </div>
  )
}

function ResultPanel({
  drill,
  result,
  onNext,
}: {
  drill: ActiveDrill
  result: SessionCompletion
  onNext: () => void
}) {
  return (
    <div className="rounded-2xl border border-stone-200 px-6 py-8 dark:border-stone-800">
      <div className="flex flex-wrap items-baseline justify-between gap-3">
        <h1 className="text-lg font-medium">
          {result.passed ? 'Gate passed.' : 'Not yet.'}
        </h1>
        <span className="text-xs tabular-nums text-stone-400 dark:text-stone-500">
          {result.consecutivePasses} of 2 consecutive passes
        </span>
      </div>

      {result.unlockedTier !== null ? (
        <p className="mt-3 text-sm text-stone-600 dark:text-stone-300">
          Tier {result.unlockedTier + 1} unlocked. Your next drill moves on.
        </p>
      ) : result.passed ? (
        <p className="mt-3 text-sm text-stone-500 dark:text-stone-400">
          One more clean pass unlocks your next tier.
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
        <ul className="mt-4 space-y-1 font-mono text-xs text-stone-500 dark:text-stone-400">
          {result.failures.map((failure) => (
            <li key={failure}>{failure}</li>
          ))}
        </ul>
      ) : null}

      <CoachNote sessionId={drill.sessionId} />

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
            ? 'Reading your run\u2026'
            : text}
        {state === 'streaming' && text !== '' ? (
          <span className="ml-0.5 inline-block h-3.5 w-1.5 animate-pulse bg-stone-300 align-text-bottom dark:bg-stone-600" />
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

function formatMs(value: number | null): string {
  return value === null ? '\u2014' : `${value} ms`
}

export default App
