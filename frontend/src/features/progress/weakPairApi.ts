import { authedFetch } from '../identity/guestSession'

/** One ordered transition the server scored from real keystrokes. */
export interface WeakPair {
  /** The two expected characters that form the pair. */
  pair: string
  attempts: number
  errors: number
  pauses: number
  meanMs: number
  score: number
}

export interface WeakPairReport {
  tier: number
  pairs: WeakPair[]
}

function isCount(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0
}

function isWeakPair(value: unknown): value is WeakPair {
  if (typeof value !== 'object' || value === null) return false
  const pair = value as Partial<WeakPair>
  return (
    typeof pair.pair === 'string' &&
    pair.pair.length === 2 &&
    isCount(pair.attempts) &&
    isCount(pair.errors) &&
    isCount(pair.pauses) &&
    isCount(pair.meanMs) &&
    typeof pair.score === 'number' &&
    Number.isFinite(pair.score) &&
    pair.score >= 0
  )
}

export async function fetchWeakPairs(
  tier: number,
  signal?: AbortSignal,
): Promise<WeakPairReport> {
  const query = new URLSearchParams({ tier: String(tier) })
  const response = await authedFetch(`/api/v1/progress/weak-pairs?${query.toString()}`, {
    headers: { Accept: 'application/json' },
    signal,
  })
  if (!response.ok) {
    throw new Error(`Could not load your focus pairs (${response.status})`)
  }

  const body = (await response.json()) as Partial<WeakPairReport>
  if (
    typeof body.tier !== 'number' ||
    !Array.isArray(body.pairs) ||
    !body.pairs.every(isWeakPair)
  ) {
    throw new Error('The focus pair response was invalid')
  }
  return { tier: body.tier, pairs: body.pairs }
}

/** Makes a space or other invisible character legible in a pair label. */
export function formatPairLabel(pair: string): string {
  return pair
    .split('')
    .map((character) => (character === ' ' ? '\u2423' : character))
    .join('')
}
