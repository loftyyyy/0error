import { authedFetch } from '../identity/guestSession'

export interface CreateSessionInput {
  clientSessionId: string
  tier: number
  targetCharset: string
  targetLength: number
  drillSource?: 'STATIC' | 'AI'
}

export interface ServerSession {
  id: string
  clientSessionId: string
  tier: number
  targetCharset: string
  targetLength: number
  drillSource: string
  status: string
  startedAt: string
}

/**
 * Registers the drill with the server. The returned id owns the telemetry
 * sequence space, so the client must wait for it before buffering keystrokes.
 */
export async function createServerSession(input: CreateSessionInput): Promise<ServerSession> {
  const response = await authedFetch('/api/v1/sessions', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json' },
    body: JSON.stringify({
      clientSessionId: input.clientSessionId,
      tier: input.tier,
      targetCharset: input.targetCharset,
      targetLength: input.targetLength,
      drillSource: input.drillSource ?? 'STATIC',
    }),
  })

  if (response.status === 403) {
    throw new Error(`Tier ${input.tier} is still locked`)
  }
  if (!response.ok) {
    throw new Error(`Could not start a session (${response.status})`)
  }
  return (await response.json()) as ServerSession
}

/** Server-computed aggregates. The client never recomputes these itself. */
export interface SessionMetrics {
  keystrokeCount: number
  correctCount: number
  accuracy: number | null
  medianInterKeyMs: number | null
  p95InterKeyMs: number | null
  hesitationCount: number
}

/** Gate result for the attempt that just finished. */
export interface SessionCompletion {
  sessionId: string
  tier: number
  status: string
  metrics: SessionMetrics
  passed: boolean
  consecutivePasses: number
  failedAttempts: number
  unlockedTier: number | null
  medianThresholdMs: number
  p95ThresholdMs: number
  accuracyThreshold: number
  minimumKeystrokes: number
  failures: string[]
}

/**
 * Closes the session and asks the server to score it. Telemetry must be
 * flushed first: the server recomputes every metric from the raw events.
 */
export async function completeServerSession(sessionId: string): Promise<SessionCompletion> {
  const response = await authedFetch(
    `/api/v1/sessions/${encodeURIComponent(sessionId)}/complete`,
    { method: 'POST', headers: { Accept: 'application/json' } },
  )

  if (response.status === 409) {
    throw new Error('This drill was already scored')
  }
  if (!response.ok) {
    throw new Error(`Could not score this drill (${response.status})`)
  }
  return (await response.json()) as SessionCompletion
}
