import { useEffect, useState } from 'react'

import { HESITATION_HINT_MS } from './keyboardLayout'

/**
 * True once the user has stopped typing for the hint delay. Every new
 * keystroke (`eventCount`) or a stop (`running` false) clears the hint, so the
 * guide only appears for a real pause and never while the rhythm holds.
 */
export function useHesitation(eventCount: number, running: boolean): boolean {
  const [hintedAt, setHintedAt] = useState(-1)
  const hesitating = running && hintedAt === eventCount

  useEffect(() => {
    if (!running) return
    const timer = window.setTimeout(() => setHintedAt(eventCount), HESITATION_HINT_MS)
    return () => window.clearTimeout(timer)
  }, [running, eventCount])

  return hesitating
}
