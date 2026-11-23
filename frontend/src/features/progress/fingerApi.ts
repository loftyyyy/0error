import { authedFetch } from '../identity/guestSession'

/** One finger's rolling aggregates, scored by the server from real keystrokes. */
export interface FingerStat {
  /** Stable code such as LEFT_RING. */
  finger: string
  /** Human label such as "Left ring". */
  label: string
  attempts: number
  errors: number
  pauses: number
  meanMs: number
  score: number
}

export interface FingerReport {
  tier: number
  fingers: FingerStat[]
}

function isCount(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0
}

function isFingerStat(value: unknown): value is FingerStat {
  if (typeof value !== 'object' || value === null) return false
  const stat = value as Partial<FingerStat>
  return (
    typeof stat.finger === 'string' &&
    stat.finger.length > 0 &&
    typeof stat.label === 'string' &&
    stat.label.length > 0 &&
    isCount(stat.attempts) &&
    isCount(stat.errors) &&
    isCount(stat.pauses) &&
    isCount(stat.meanMs) &&
    typeof stat.score === 'number' &&
    Number.isFinite(stat.score) &&
    stat.score >= 0
  )
}

export async function fetchFingers(
  tier: number,
  signal?: AbortSignal,
): Promise<FingerReport> {
  const query = new URLSearchParams({ tier: String(tier) })
  const response = await authedFetch(`/api/v1/progress/fingers?${query.toString()}`, {
    headers: { Accept: 'application/json' },
    signal,
  })
  if (!response.ok) {
    throw new Error(`Could not load your finger analysis (${response.status})`)
  }

  const body = (await response.json()) as Partial<FingerReport>
  if (
    typeof body.tier !== 'number' ||
    !Array.isArray(body.fingers) ||
    !body.fingers.every(isFingerStat)
  ) {
    throw new Error('The finger analysis response was invalid')
  }
  return { tier: body.tier, fingers: body.fingers }
}

/** One completed drill's behaviour for a finger, not a single keystroke. */
export interface FingerTrendAttempt {
  recordedAt: string
  attempts: number
  errors: number
  pauses: number
  /** Mean display time in milliseconds across this session's attempts. */
  meanMs: number
}

/** A reach's recent sessions, oldest first. */
export interface FingerTrend {
  tier: number
  finger: string
  label: string
  attempts: FingerTrendAttempt[]
}

function isTrendAttempt(value: unknown): value is FingerTrendAttempt {
  if (typeof value !== 'object' || value === null) return false
  const attempt = value as Partial<FingerTrendAttempt>
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
 * Follows one reach across the guest's recent completed drills. The result
 * panel asks for the weakest repeated reach only, so this stays one small
 * request and a failed read simply leaves the reach note as it was.
 */
export async function fetchFingerTrend(
  tier: number,
  finger: string,
  limit = 5,
  signal?: AbortSignal,
): Promise<FingerTrend> {
  const query = new URLSearchParams({
    tier: String(tier),
    finger,
    limit: String(limit),
  })
  const response = await authedFetch(
    `/api/v1/progress/fingers/trend?${query.toString()}`,
    { headers: { Accept: 'application/json' }, signal },
  )
  if (!response.ok) {
    throw new Error(`Could not load the reach trend (${response.status})`)
  }

  const body = (await response.json()) as Partial<FingerTrend>
  if (
    typeof body.tier !== 'number' ||
    typeof body.finger !== 'string' ||
    body.finger.length === 0 ||
    typeof body.label !== 'string' ||
    body.label.length === 0 ||
    !Array.isArray(body.attempts) ||
    !body.attempts.every(isTrendAttempt)
  ) {
    throw new Error('The reach trend response was invalid')
  }
  return {
    tier: body.tier,
    finger: body.finger,
    label: body.label,
    attempts: body.attempts,
  }
}
