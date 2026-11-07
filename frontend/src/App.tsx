import { useState } from 'react'
import { RotateCcw } from 'lucide-react'

import { useKeystrokeCapture } from './features/typing/useKeystrokeCapture'

const DEMO_TEXT = 'asdf jkl; asdf jkl; fj fj dk dk sl sl a; a;'

function newSessionId(): string {
  return crypto.randomUUID()
}

function App() {
  const [sessionId, setSessionId] = useState(newSessionId)
  const { containerRef, snapshot, notice } = useKeystrokeCapture({
    sessionId,
    text: DEMO_TEXT,
  })

  const progress = snapshot.length === 0 ? 0 : (snapshot.index / snapshot.length) * 100
  const isPaused = snapshot.status === 'paused'
  const isComplete = snapshot.status === 'complete'

  return (
    <main className="flex min-h-screen items-center justify-center bg-stone-50 px-6 py-16 text-stone-900 dark:bg-stone-950 dark:text-stone-100">
      <div className="w-full max-w-2xl">
        <header className="mb-16 flex items-baseline justify-between">
          <span className="text-sm font-medium tracking-[0.2em] text-stone-400 uppercase dark:text-stone-500">
            0Error
          </span>
          <span className="text-xs text-stone-400 dark:text-stone-500">home row</span>
        </header>

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
            {DEMO_TEXT.split('').map((character, index) => {
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
              {isComplete
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
              {isComplete ? (
                <button
                  type="button"
                  onClick={() => setSessionId(newSessionId())}
                  className="flex items-center gap-1.5 rounded-full border border-stone-200 px-3 py-1.5 text-stone-600 transition hover:border-stone-300 hover:text-stone-900 dark:border-stone-800 dark:text-stone-300 dark:hover:border-stone-700 dark:hover:text-stone-50"
                >
                  <RotateCcw size={13} strokeWidth={1.75} />
                  Again
                </button>
              ) : null}
            </span>
          </div>

          <div className="mt-2 h-5 text-xs text-stone-400 dark:text-stone-500" aria-live="polite">
            {notice === 'paste-blocked' ? 'Paste is off. Type it out.' : null}
          </div>
        </div>

        <footer className="mt-20 text-xs leading-relaxed text-stone-400 dark:text-stone-600">
          Telemetry stays local until the API is reachable. Focus loss pauses the clock. Three
          keystrokes after a pause are warmup and never scored.
        </footer>
      </div>
    </main>
  )
}

export default App
