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

/** One completed drill's behaviour for a pair, not a single keystroke. */
export interface WeakPairTrendAttempt {
  recordedAt: string
  attempts: number
  errors: number
  pauses: number
  /** Mean display time in milliseconds across this session's attempts. */
  meanMs: number
}

/** A pair's recent sessions, oldest first. */
export interface WeakPairTrend {
  tier: number
  pair: string
  attempts: WeakPairTrendAttempt[]
}

function isTrendAttempt(value: unknown): value is WeakPairTrendAttempt {
  if (typeof value !== 'object' || value === null) return false
  const attempt = value as Partial<WeakPairTrendAttempt>
  return (
    typeof attempt.recordedAt === 'string' &&
    isCount(attempt.attempts) &&
    attempt.attempts > 0 &&
    isCount(attempt.errors) &&
    isCount(attempt.pauses) &&
    isCount(attempt.meanMs)
  )
}

/**
 * Follows one transition across the guest's recent completed drills. The
 * result panel asks for the weakest focus pair only, so this stays one small
 * request and a failed read simply leaves the focus note as it was.
 */
export async function fetchWeakPairTrend(
  tier: number,
  pair: string,
  limit = 5,
  signal?: AbortSignal,
): Promise<WeakPairTrend> {
  const query = new URLSearchParams({
    tier: String(tier),
    pair,
    limit: String(limit),
  })
  const response = await authedFetch(
    `/api/v1/progress/weak-pairs/trend?${query.toString()}`,
    { headers: { Accept: 'application/json' }, signal },
  )
  if (!response.ok) {
    throw new Error(`Could not load the pair trend (${response.status})`)
  }

  const body = (await response.json()) as Partial<WeakPairTrend>
  if (
    typeof body.tier !== 'number' ||
    typeof body.pair !== 'string' ||
    body.pair.length !== 2 ||
    !Array.isArray(body.attempts) ||
    !body.attempts.every(isTrendAttempt)
  ) {
    throw new Error('The pair trend response was invalid')
  }
  return { tier: body.tier, pair: body.pair, attempts: body.attempts }
}
