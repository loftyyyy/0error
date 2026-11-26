import { useEffect, useState } from 'react'

import { fetchFingerTrend, fetchFingers } from './fingerApi'
import type { FingerStat, FingerTrend } from './fingerApi'

/** One repeated attempt is noise; a scored reach is worth a quiet line. */
const MIN_ATTEMPTS = 2
const MIN_SCORE = 0.1

/** Compares the first and latest session for a reach, oldest to newest. */
function trendLine(trend: FingerTrend): string | null {
  if (trend.attempts.length < 2) return null

  const first = trend.attempts[0]
  const last = trend.attempts[trend.attempts.length - 1]
  return `${trend.label}: mean ${first.meanMs} ms \u2192 ${last.meanMs} ms over your last ${trend.attempts.length} drills.`
}

/**
 * A quiet line under the focus note: which reach is still finding its rhythm,
 * followed by how it has moved across recent sessions. It ignores a single
 * stumble and disappears if analysis is down.
 */
export function FingerNote({
  tier,
  onPractice,
}: {
  tier: number
  onPractice?: (finger: string) => void
}) {
  const [weakest, setWeakest] = useState<FingerStat | null>(null)
  const [trend, setTrend] = useState<FingerTrend | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    let active = true

    const load = async () => {
      try {
        const report = await fetchFingers(tier, controller.signal)
        if (!active) return

        const candidate = report.fingers[0] ?? null
        setWeakest(candidate)
        if (
          !candidate ||
          candidate.attempts < MIN_ATTEMPTS ||
          candidate.score < MIN_SCORE
        ) {
          return
        }

        try {
          const fetched = await fetchFingerTrend(
            tier,
            candidate.finger,
            5,
            controller.signal,
          )
          if (active) setTrend(fetched)
        } catch {
          // The trend is extra context; the reach line still stands without it.
        }
      } catch {
        // Analysis is an accelerator; a failure simply leaves no note.
      }
    }

    void load()

    return () => {
      active = false
      controller.abort()
    }
  }, [tier])

  if (
    !weakest ||
    weakest.attempts < MIN_ATTEMPTS ||
    weakest.score < MIN_SCORE
  ) {
    return null
  }

  const direction = trend ? trendLine(trend) : null

  return (
    <>
      <p className="mt-2 text-xs leading-relaxed text-stone-400 dark:text-stone-500">
        <span className="text-[0.65rem] font-medium tracking-[0.2em] uppercase">Reach</span>
        <span className="mx-2 text-stone-300 dark:text-stone-700">/</span>
        {weakest.label}, still finding its rhythm.
      </p>
      {direction ? (
        <p className="mt-1.5 text-xs text-stone-400 dark:text-stone-500">
          {direction}
        </p>
      ) : null}
      {onPractice ? (
        <button
          type="button"
          onClick={() => onPractice(weakest.finger)}
          className="mt-2 text-xs text-stone-400 underline underline-offset-2 hover:text-stone-600 dark:text-stone-500 dark:hover:text-stone-300"
        >
          Practise {weakest.label}
        </button>
      ) : null}
    </>
  )
}
