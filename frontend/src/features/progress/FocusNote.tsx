import { useEffect, useState } from 'react'

import { fetchWeakPairs, formatPairLabel } from './weakPairApi'
import type { WeakPair } from './weakPairApi'

/**
 * A quiet line under the coach: which transitions the next drill will target.
 * It stays out of the way while loading and disappears if analysis is down.
 */
export function FocusNote({ tier }: { tier: number }) {
  const [pairs, setPairs] = useState<WeakPair[]>([])

  useEffect(() => {
    const controller = new AbortController()
    let active = true

    void fetchWeakPairs(tier, controller.signal)
      .then((report) => {
        if (active) setPairs(report.pairs)
      })
      .catch(() => {
        // Analysis is an accelerator; a failure simply leaves no note.
      })

    return () => {
      active = false
      controller.abort()
    }
  }, [tier])

  if (pairs.length === 0) {
    return null
  }

  const labels = pairs.slice(0, 3).map((pair) => formatPairLabel(pair.pair))
  const phrase =
    labels.length === 1
      ? labels[0]
      : `${labels.slice(0, -1).join(', ')} and ${labels[labels.length - 1]}`

  return (
    <p className="mt-6 text-xs leading-relaxed text-stone-400 dark:text-stone-500">
      <span className="tracking-[0.15em] uppercase">Focus</span>
      <span className="mx-2 text-stone-300 dark:text-stone-700">/</span>
      Your next drills lean on <span className="font-mono">{phrase}</span>.
    </p>
  )
}
