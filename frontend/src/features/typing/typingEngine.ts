/**
 * Framework-free typing state machine.
 *
 * The session clock only advances while the drill is active: pauses freeze it,
 * so a cadence gap never contains time spent in another tab.
 */

export type TypingStatus = 'running' | 'paused' | 'complete'

export type KeystrokeKind = 'KEY' | 'BACKSPACE'

export interface KeystrokeEvent {
  /** Monotonic per-session sequence. Part of the server-side idempotency key. */
  seq: number
  /** Index into the drill text. A corrected character is typed again at the same position. */
  position: number
  kind: KeystrokeKind
  expected: string | null
  actual: string | null
  correct: boolean
  /** Active session time in milliseconds. Pauses are already subtracted. */
  t: number
  /** Time from the target character becoming visible to this keypress. */
  displayMs: number | null
  /** The first 3 keystrokes after a resume are stored but excluded from scoring. */
  warmup: boolean
}

export interface TypingSnapshot {
  status: TypingStatus
  index: number
  length: number
  typed: number
  correct: number
  errors: number
  accuracy: number
  corrections: number
  warmupRemaining: number
  eventCount: number
  elapsedMs: number
}

export interface TypingEngineOptions {
  now?: () => number
  allowBackspace?: boolean
  /** Scopes the event sequence to one drill session. */
  sessionId?: string
}

export const WARMUP_KEYSTROKES = 3
export const HESITATION_GAP_MS = 1500

export class TypingEngine {
  readonly text: string
  readonly sessionId: string

  private readonly now: () => number
  private readonly allowBackspace: boolean

  private position = 0
  private seq = 0
  private status: TypingStatus = 'running'

  private readonly startedAt: number
  private pausedAt: number | null = null
  private pausedTotal = 0
  private targetShownAt = 0

  private warmupRemaining = 0
  private typed = 0
  private scoredTotal = 0
  private scoredCorrect = 0
  private errors = 0
  private corrections = 0
  private eventCount = 0
  private lastKeyAt: number | null = null
  private hesitationCount = 0
  /** Accuracy is first-attempt per position; corrections never erase an error. */
  private readonly scoredPositions = new Set<number>()

  private readonly listeners = new Set<() => void>()
  private cachedSnapshot: TypingSnapshot | null = null
  private notifyScheduled = false

  constructor(text: string, options: TypingEngineOptions = {}) {
    this.text = text
    this.sessionId = options.sessionId ?? ''
    this.now = options.now ?? (() => performance.now())
    this.allowBackspace = options.allowBackspace ?? true
    this.startedAt = this.now()
    this.targetShownAt = 0
  }

  get isComplete(): boolean {
    return this.status === 'complete'
  }

  get isPaused(): boolean {
    return this.status === 'paused'
  }

  get hesitationCountValue(): number {
    return this.hesitationCount
  }

  /** Active milliseconds since the drill started, excluding paused spans. */
  elapsed(at = this.now()): number {
    const paused = this.pausedAt === null ? 0 : at - this.pausedAt
    return Math.max(0, at - this.startedAt - this.pausedTotal - paused)
  }

  pause(at = this.now()): boolean {
    if (this.status !== 'running') return false
    this.status = 'paused'
    this.pausedAt = at
    this.targetShownAt = this.elapsed(at)
    this.changed()
    return true
  }

  resume(at = this.now()): boolean {
    if (this.status !== 'paused') return false
    this.pausedTotal += at - (this.pausedAt ?? at)
    this.pausedAt = null
    this.status = 'running'
    this.warmupRemaining = WARMUP_KEYSTROKES
    this.targetShownAt = this.elapsed(at)
    this.changed()
    return true
  }

  /**
   * Record one keypress. Returns null when the key is ignored (paused engine,
   * completed drill, unsupported key, or backspace with nowhere to go).
   */
  press(key: string, at = this.now()): KeystrokeEvent | null {
    if (this.status !== 'running') return null
    if (key.length !== 1 && key !== 'Backspace') return null

    const t = this.elapsed(at)

    if (key === 'Backspace') {
      if (!this.allowBackspace || this.position === 0) return null
      this.position -= 1
      this.corrections += 1
      this.targetShownAt = t
      return this.emit({
        position: this.position,
        kind: 'BACKSPACE',
        expected: this.text[this.position] ?? null,
        actual: null,
        correct: true,
        t,
        displayMs: null,
        warmup: this.warmupRemaining > 0,
      })
    }

    const expected = this.text[this.position] ?? null
    if (expected === null) return null

    const isWarmup = this.warmupRemaining > 0
    const correct = key === expected
    const displayMs = Math.max(0, Math.round(t - this.targetShownAt))

    if (isWarmup) {
      this.warmupRemaining -= 1
    } else {
      this.typed += 1
      if (!this.scoredPositions.has(this.position)) {
        this.scoredPositions.add(this.position)
        this.scoredTotal += 1
        if (correct) {
          this.scoredCorrect += 1
        } else {
          this.errors += 1
        }
      }
      if (this.lastKeyAt !== null && t - this.lastKeyAt > HESITATION_GAP_MS) {
        this.hesitationCount += 1
      }
      this.lastKeyAt = t
    }

    const event = this.emit({
      position: this.position,
      kind: 'KEY',
      expected,
      actual: key,
      correct,
      t,
      displayMs,
      warmup: isWarmup,
    })

    this.position += 1
    this.targetShownAt = t

    if (this.position >= this.text.length) {
      this.status = 'complete'
      this.pausedAt = at
    }

    return event
  }

  snapshot(at = this.now()): TypingSnapshot {
    return {
      status: this.status,
      index: this.position,
      length: this.text.length,
      typed: this.typed,
      correct: this.scoredCorrect,
      errors: this.errors,
      accuracy: this.scoredTotal === 0 ? 1 : this.scoredCorrect / this.scoredTotal,
      corrections: this.corrections,
      warmupRemaining: this.warmupRemaining,
      eventCount: this.eventCount,
      elapsedMs: Math.round(this.elapsed(at)),
    }
  }

  /** Stable identity between notifications; required by useSyncExternalStore. */
  getSnapshot(): TypingSnapshot {
    if (this.cachedSnapshot === null) {
      this.cachedSnapshot = this.snapshot()
    }
    return this.cachedSnapshot
  }

  subscribe(listener: () => void): () => void {
    this.listeners.add(listener)
    return () => {
      this.listeners.delete(listener)
    }
  }

  /** Batches subscriber wakeups to one per animation frame. */
  private changed(): void {
    this.cachedSnapshot = null
    if (this.notifyScheduled) return
    this.notifyScheduled = true

    const flush = () => {
      this.notifyScheduled = false
      for (const listener of this.listeners) listener()
    }

    if (typeof requestAnimationFrame === 'function') {
      requestAnimationFrame(flush)
    } else {
      setTimeout(flush, 0)
    }
  }

  private emit(
    event: Omit<KeystrokeEvent, 'seq'>,
  ): KeystrokeEvent {
    const recorded: KeystrokeEvent = { ...event, seq: this.seq }
    this.seq += 1
    this.eventCount += 1
    this.changed()
    return recorded
  }
}
