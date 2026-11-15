import { useEffect, useState } from 'react'

import { fetchFingers } from './fingerApi'
import type { FingerStat } from './fingerApi'

/** One repeated attempt is noise; a scored reach is worth a quiet line. */
const MIN_ATTEMPTS = 2
const MIN_SCORE = 0.1

/**
 * A quiet line under the focus note: which reach is still finding its rhythm.
 * It ignores a single stumble and disappears if analysis is down.
 */
export function FingerNote({ tier }: { tier: number }) {
  const [weakest, setWeakest] = useState<FingerStat | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    let active = true

    void fetchFingers(tier, controller.signal)
      .then((report) => {
        if (active) setWeakest(report.fingers[0] ?? null)
      })
      .catch(() => {
        // Analysis is an accelerator; a failure simply leaves no note.
      })

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

  return (
    <p className="mt-2 text-xs leading-relaxed text-stone-400 dark:text-stone-500">
      <span className="tracking-[0.15em] uppercase">Reach</span>
      <span className="mx-2 text-stone-300 dark:text-stone-700">/</span>
      {weakest.label}, still finding its rhythm.
    </p>
  )
}
