import { authedFetch } from '../identity/guestSession'

/** One tier as the server sees it, including relaxed gate thresholds. */
export interface TierStatus {
  tier: number
  name: string
  charset: string
  unlocked: boolean
  consecutivePasses: number
  failedAttempts: number
  medianThresholdMs: number
  p95ThresholdMs: number
  unlockedAt: string | null
}

export interface ProgressSummary {
  tiers: TierStatus[]
}

export async function fetchProgress(): Promise<ProgressSummary> {
  const response = await authedFetch('/api/v1/progress', {
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) {
    throw new Error(`Could not load your progress (${response.status})`)
  }
  return (await response.json()) as ProgressSummary
}

/** The practice tier: the highest tier the server has unlocked, else home row. */
export function activeTier(progress: ProgressSummary): number {
  return progress.tiers.reduce(
    (highest, tier) => (tier.unlocked && tier.tier > highest ? tier.tier : highest),
    0,
  )
}
