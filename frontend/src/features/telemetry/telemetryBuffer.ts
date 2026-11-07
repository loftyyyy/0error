import type { KeystrokeEvent } from '../typing/typingEngine'
import {
  deleteBatch,
  enqueueBatch,
  markBatchAttempt,
  readOldestBatch,
} from './keystrokeQueue'
import type { KeystrokeBatch } from './keystrokeQueue'

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/+$/, '')
const BATCH_SIZE = 100
const FLUSH_INTERVAL_MS = 5_000
const MAX_BATCHES_PER_DRAIN = 25

export const GUEST_TOKEN_KEY = '0error.guestToken'

function readGuestToken(): string | null {
  try {
    return localStorage.getItem(GUEST_TOKEN_KEY)
  } catch {
    return null
  }
}

function newBatchId(): string {
  if (typeof crypto !== 'undefined' && typeof crypto.randomUUID === 'function') {
    return crypto.randomUUID()
  }
  return `${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 10)}`
}

type PostResult = 'ok' | 'retry' | 'drop'

async function postBatch(batch: KeystrokeBatch, apiBase: string): Promise<PostResult> {
  const token = readGuestToken()
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'X-Request-Id': batch.batchId,
  }
  if (token) headers.Authorization = `Bearer ${token}`

  let response: Response
  try {
    response = await fetch(
      `${apiBase}/api/sessions/${encodeURIComponent(batch.sessionId)}/telemetry`,
      {
        method: 'POST',
        headers,
        body: JSON.stringify({
          batchId: batch.batchId,
          sessionId: batch.sessionId,
          sentAt: new Date().toISOString(),
          events: batch.events,
        }),
        // Lets the final batch survive the page being torn down.
        keepalive: true,
      },
    )
  } catch {
    return 'retry'
  }

  if (response.ok || response.status === 409) return 'ok'
  if (response.status === 408 || response.status === 425 || response.status === 429) return 'retry'
  if (response.status >= 500) return 'retry'
  if (response.status === 401 || response.status === 403) return 'retry'

  // Schema or route errors are poison; retrying them forever would block the queue head.
  return 'drop'
}

let activeDrain: Promise<void> | null = null

/** Uploads queued batches oldest-first. Safe to call concurrently; one drain runs. */
export function drainOutbox(apiBase = API_BASE): Promise<void> {
  if (activeDrain) return activeDrain

  activeDrain = (async () => {
    try {
      for (let sent = 0; sent < MAX_BATCHES_PER_DRAIN; sent += 1) {
        const batch = await readOldestBatch()
        if (!batch || batch.id === undefined) return

        const result = await postBatch(batch, apiBase)
        if (result === 'retry') {
          await markBatchAttempt(batch.id)
          return
        }

        await deleteBatch(batch.id)
      }
    } finally {
      activeDrain = null
    }
  })()

  return activeDrain
}

/**
 * Per-session telemetry buffer. Events are persisted to IndexedDB before any
 * network call, so a closed tab or offline moment never drops raw keystrokes.
 */
export class TelemetryBuffer {
  private readonly sessionId: string
  private readonly apiBase: string
  private pending: KeystrokeEvent[] = []
  private timer: ReturnType<typeof setInterval> | null = null
  private onlineHandler: (() => void) | null = null

  constructor(sessionId: string, apiBase = API_BASE) {
    this.sessionId = sessionId
    this.apiBase = apiBase
  }

  start(): void {
    if (this.timer !== null) return

    this.timer = setInterval(() => {
      void this.flush()
    }, FLUSH_INTERVAL_MS)

    if (typeof window !== 'undefined') {
      this.onlineHandler = () => {
        void drainOutbox(this.apiBase)
      }
      window.addEventListener('online', this.onlineHandler)
    }

    // Flush leftovers from earlier sessions before adding new ones.
    void drainOutbox(this.apiBase)
  }

  push(events: KeystrokeEvent[]): void {
    if (events.length === 0) return
    this.pending.push(...events)
    if (this.pending.length >= BATCH_SIZE) {
      void this.flush()
    }
  }

  async flush(): Promise<void> {
    while (this.pending.length > 0) {
      const events = this.pending.splice(0, BATCH_SIZE)
      await enqueueBatch({
        batchId: newBatchId(),
        sessionId: this.sessionId,
        createdAt: Date.now(),
        events,
      })
    }
    await drainOutbox(this.apiBase)
  }

  stop(): void {
    if (this.timer !== null) {
      clearInterval(this.timer)
      this.timer = null
    }
    if (this.onlineHandler && typeof window !== 'undefined') {
      window.removeEventListener('online', this.onlineHandler)
      this.onlineHandler = null
    }
    // Persist the tail even though the component is going away.
    void this.flush()
  }
}
