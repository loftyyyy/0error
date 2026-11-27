import { useEffect, useState } from 'react'

import { BASE_HINT_MS, nextHintDelay } from './hintDelay'
import type { StrokeQuality } from './typingEngine'

export interface HesitationState {
  hesitating: boolean
  /** Current wait before the guide appears; adapts to rhythm. */
  delayMs: number
}

/**
 * True once the user has stopped typing for the adaptive delay. Every new
 * keystroke (`eventCount`) or a stop (`running` false) clears the hint. A
 * stumble shortens the next wait; smooth keys ease it back.
 */
export function useHesitation(
  eventCount: number,
  running: boolean,
  lastQuality: StrokeQuality | null = null,
): HesitationState {
  const [hintedAt, setHintedAt] = useState(-1)
  const [adaptation, setAdaptation] = useState({
    event: 0,
    delay: BASE_HINT_MS,
  })
  const hesitating = running && hintedAt === eventCount

  // Adjusting stored state while rendering is the React-sanctioned way to react
  // to a changed input without an extra effect pass.
  if (adaptation.event !== eventCount) {
    setAdaptation({
      event: eventCount,
      delay: nextHintDelay(adaptation.delay, lastQuality),
    })
  }
  const delayMs = adaptation.delay

  useEffect(() => {
    if (!running) return
    const timer = window.setTimeout(() => setHintedAt(eventCount), delayMs)
    return () => window.clearTimeout(timer)
  }, [running, eventCount, delayMs])

  return { hesitating, delayMs }
}
