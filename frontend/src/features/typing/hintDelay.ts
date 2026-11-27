import type { StrokeQuality } from './typingEngine'

/** One shared threshold for the amber trace and the guide reveal. */
export const BASE_HINT_MS = 900
/** Helpful floor for a repeatedly stumbling beginner. */
export const MIN_HINT_MS = 650
/** Calm ceiling for a smooth streak. */
export const MAX_HINT_MS = 1200

const STUMBLE_STEP_MS = 100
const SMOOTH_STEP_MS = 50

/**
 * How long to wait before the next hint. Stumbles shorten the wait toward
 * 650 ms; smooth keys ease it back toward 1200 ms. The server still scores the
 * same gate; this only changes when the guide appears.
 */
export function nextHintDelay(
  current: number,
  quality: StrokeQuality | null,
): number {
  const bounded = Math.min(MAX_HINT_MS, Math.max(MIN_HINT_MS, current))
  if (quality === 'hesitant') {
    return Math.max(MIN_HINT_MS, bounded - STUMBLE_STEP_MS)
  }
  if (quality === 'smooth') {
    return Math.min(MAX_HINT_MS, bounded + SMOOTH_STEP_MS)
  }
  return bounded
}
