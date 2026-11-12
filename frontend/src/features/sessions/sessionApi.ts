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

  if (!response.ok) {
    throw new Error(`Could not start a session (${response.status})`)
  }
  return (await response.json()) as ServerSession
}
