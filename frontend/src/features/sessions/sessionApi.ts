import { authedFetch } from '../identity/guestSession'

export interface CreateSessionInput {
  clientSessionId: string
  /** The server-issued drills this session claims, in typing order. */
  drillIds: string[]
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
      drillIds: input.drillIds,
    }),
  })

  if (response.status === 403) {
    throw new Error('This tier is still locked')
  }
  if (response.status === 400) {
    throw new Error('This drill set expired. Load a fresh drill.')
  }
  if (response.status === 409) {
    throw new Error('This drill was already used. Load a fresh drill.')
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

/** One finished drill as the history list shows it. */
export interface RecentSession {
  sessionId: string
  tier: number
  tierName: string
  completedAt: string
  accuracy: number | null
  medianInterKeyMs: number | null
  p95InterKeyMs: number | null
  keystrokeCount: number
  /** Null for a session completed before the gate decision was stored. */
  passed: boolean | null
}

/**
 * The guest's own finished drills, newest first. Read-only context for the
 * result panel; nothing else depends on it.
 */
export async function fetchRecentSessions(limit = 5): Promise<RecentSession[]> {
  const response = await authedFetch(
    `/api/v1/sessions/recent?limit=${encodeURIComponent(String(limit))}`,
    { headers: { Accept: 'application/json' } },
  )

  if (!response.ok) {
    throw new Error(`Could not load recent drills (${response.status})`)
  }
  const body = (await response.json()) as { sessions: RecentSession[] }
  return body.sessions
}

/**
 * The server can no longer score this attempt: it was closed by the
 * abandonment sweep, already completed without a stored answer, or belongs to
 * a guest identity this browser no longer holds. Retrying cannot help, so the
 * app starts a fresh drill instead of looping on the same request.
 */
export class SessionGoneError extends Error {}

/**
 * The drill a reloaded tab can continue. The cursor and sequence come from the
 * server so a resumed attempt appends to the same telemetry stream.
 */
export interface ActiveSession {
  sessionId: string
  tier: number
  tierName: string
  text: string
  charset: string
  startedAt: string
  nextSeq: number
  keystrokes: number
  position: number
  elapsedMs: number
}

function isCount(value: unknown): value is number {
  return typeof value === 'number' && Number.isInteger(value) && value >= 0
}

/**
 * Returns the open drill, or null when nothing is active. A failed check is
 * surfaced so the caller can decide whether to start fresh or show an error.
 */
export async function fetchActiveSession(): Promise<ActiveSession | null> {
  const response = await authedFetch('/api/v1/sessions/active', {
    headers: { Accept: 'application/json' },
  })

  if (response.status === 204) return null
  if (!response.ok) {
    throw new Error(`Could not check for an open drill (${response.status})`)
  }

  const body = (await response.json()) as Partial<ActiveSession>
  if (
    typeof body.sessionId !== 'string' ||
    body.sessionId.length === 0 ||
    !isCount(body.tier) ||
    typeof body.tierName !== 'string' ||
    typeof body.text !== 'string' ||
    body.text.length === 0 ||
    typeof body.charset !== 'string' ||
    typeof body.startedAt !== 'string' ||
    !isCount(body.nextSeq) ||
    !isCount(body.keystrokes) ||
    !isCount(body.position) ||
    body.position > body.text.length ||
    !isCount(body.elapsedMs)
  ) {
    throw new Error('The resume response was invalid')
  }
  return body as ActiveSession
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

  if (response.status === 404) {
    throw new SessionGoneError('The server no longer has this drill.')
  }
  if (response.status === 409) {
    throw new SessionGoneError('The server closed this drill before it was scored.')
  }
  if (!response.ok) {
    throw new Error(`Could not score this drill (${response.status})`)
  }
  return (await response.json()) as SessionCompletion
}
