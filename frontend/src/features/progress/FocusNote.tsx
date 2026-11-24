import { useEffect, useState } from 'react'

import { fetchWeakPairTrend, fetchWeakPairs, formatPairLabel } from './weakPairApi'
import type { WeakPair, WeakPairTrend } from './weakPairApi'

/** Compares the first and latest session for a pair, oldest to newest. */
function trendLine(trend: WeakPairTrend): string | null {
  if (trend.attempts.length < 2) return null

  const first = trend.attempts[0]
  const last = trend.attempts[trend.attempts.length - 1]
  return `${formatPairLabel(trend.pair)}: mean ${first.meanMs} ms \u2192 ${last.meanMs} ms over your last ${trend.attempts.length} drills.`
}

/**
 * A quiet line under the coach: which transitions the next drill will target,
 * followed by how the weakest one has moved across recent sessions. It stays
 * out of the way while loading and disappears if analysis is down.
 */
export function FocusNote({
  tier,
  onPractice,
}: {
  tier: number
  onPractice?: (pair: string) => void
}) {
  const [pairs, setPairs] = useState<WeakPair[]>([])
  const [trend, setTrend] = useState<WeakPairTrend | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    let active = true

    const load = async () => {
      try {
        const report = await fetchWeakPairs(tier, controller.signal)
        if (!active) return
        setPairs(report.pairs)

        const weakest = report.pairs[0]
        if (!weakest) return

        try {
          const fetched = await fetchWeakPairTrend(
            tier,
            weakest.pair,
            5,
            controller.signal,
          )
          if (active) setTrend(fetched)
        } catch {
          // The trend is extra context; the focus line still stands without it.
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

  if (pairs.length === 0) {
    return null
  }

  const labels = pairs.slice(0, 3).map((pair) => formatPairLabel(pair.pair))
  const phrase =
    labels.length === 1
      ? labels[0]
      : `${labels.slice(0, -1).join(', ')} and ${labels[labels.length - 1]}`
  const direction = trend ? trendLine(trend) : null
  const weakest = pairs[0]

  return (
    <>
      <p className="mt-6 text-xs leading-relaxed text-stone-400 dark:text-stone-500">
        <span className="tracking-[0.15em] uppercase">Focus</span>
        <span className="mx-2 text-stone-300 dark:text-stone-700">/</span>
        Your next drills lean on <span className="font-mono">{phrase}</span>.
      </p>
      {direction ? (
        <p className="mt-1.5 text-xs text-stone-400 dark:text-stone-500">
          {direction}
        </p>
      ) : null}
      {onPractice && weakest ? (
        <button
          type="button"
          onClick={() => onPractice(weakest.pair)}
          className="mt-2 text-xs text-stone-400 underline underline-offset-2 hover:text-stone-600 dark:text-stone-500 dark:hover:text-stone-300"
        >
          Practise {formatPairLabel(weakest.pair)}
        </button>
      ) : null}
    </>
  )
}
