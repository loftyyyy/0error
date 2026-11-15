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
