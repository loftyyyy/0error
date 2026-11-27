import { beforeEach, describe, expect, it, vi } from 'vitest'

import type { KeystrokeEvent } from '../typing/typingEngine'

const events: KeystrokeEvent[] = [
  {
    seq: 0,
    position: 0,
    kind: 'KEY',
    expected: 'a',
    actual: 'a',
    correct: true,
    t: 0,
    displayMs: 120,
    warmup: false,
  },
]

describe('sync state', () => {
  beforeEach(() => {
    vi.resetModules()
  })

  it('reports offline when the batch cannot reach the network, then synced', async () => {
    const failing = vi.fn(async () => {
      throw new TypeError('Failed to fetch')
    })
    vi.stubGlobal('fetch', failing)

    const { enqueueBatch } = await import('./keystrokeQueue')
    const {
      drainOutbox,
      getSyncState,
      subscribeToSync,
    } = await import('./telemetryBuffer')
    const listener = vi.fn()
    const unsubscribe = subscribeToSync(listener)

    await enqueueBatch({
      batchId: 'batch-offline',
      sessionId: 'session-offline',
      createdAt: 1,
      events,
    })
    await drainOutbox('')

    expect(getSyncState()).toBe('offline')
    expect(listener).toHaveBeenCalled()

    vi.stubGlobal('fetch', vi.fn(async () => new Response('{}', { status: 200 })))
    await drainOutbox('')

    expect(getSyncState()).toBe('synced')
    unsubscribe()
  })

  it('reports pending when the server asks for a retry', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response('{}', { status: 503 })),
    )

    const { enqueueBatch } = await import('./keystrokeQueue')
    const { drainOutbox, getSyncState } = await import('./telemetryBuffer')

    await enqueueBatch({
      batchId: 'batch-pending',
      sessionId: 'session-pending',
      createdAt: 1,
      events,
    })
    await drainOutbox('')

    expect(getSyncState()).toBe('pending')
  })

  it('returns to synced when a closed session batch is dropped', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response('{}', { status: 409 })),
    )

    const { enqueueBatch } = await import('./keystrokeQueue')
    const { drainOutbox, getSyncState } = await import('./telemetryBuffer')

    await enqueueBatch({
      batchId: 'batch-closed',
      sessionId: 'session-closed',
      createdAt: 1,
      events,
    })
    await drainOutbox('')

    expect(getSyncState()).toBe('synced')
  })
})

describe('flushSession', () => {
  beforeEach(() => {
    vi.resetModules()
  })

  it('reports success once every batch for the session is acknowledged', async () => {
    const fetchMock = vi.fn(async () => new Response('{}', { status: 200 }))
    vi.stubGlobal('fetch', fetchMock)

    const { enqueueBatch, countPendingBatches } = await import('./keystrokeQueue')
    const { flushSession } = await import('./telemetryBuffer')

    await enqueueBatch({
      batchId: 'batch-1',
      sessionId: 'session-1',
      createdAt: 1,
      events,
    })

    await expect(flushSession('session-1', '', 1)).resolves.toBe(true)
    expect(await countPendingBatches('session-1')).toBe(0)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })

  it('reports failure and keeps the batch when the upload is retryable', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response('{}', { status: 503 })),
    )

    const { enqueueBatch, countPendingBatches } = await import('./keystrokeQueue')
    const { flushSession } = await import('./telemetryBuffer')

    await enqueueBatch({
      batchId: 'batch-2',
      sessionId: 'session-2',
      createdAt: 1,
      events,
    })

    await expect(flushSession('session-2', '', 1)).resolves.toBe(false)
    expect(await countPendingBatches('session-2')).toBe(1)
  })

  it('retries a transient upload before reporting failure', async () => {
    let calls = 0
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        calls += 1
        return new Response('{}', { status: calls === 1 ? 503 : 200 })
      }),
    )

    const { enqueueBatch } = await import('./keystrokeQueue')
    const { flushSession } = await import('./telemetryBuffer')

    await enqueueBatch({
      batchId: 'batch-transient',
      sessionId: 'session-transient',
      createdAt: 1,
      events,
    })

    await expect(flushSession('session-transient', '', 2)).resolves.toBe(true)
    expect(calls).toBe(2)
  })

  it('flushes a final tail added while an earlier flush is running', async () => {
    const sent: number[] = []
    vi.stubGlobal(
      'fetch',
      vi.fn(async (_url: RequestInfo | URL, init?: RequestInit) => {
        const body = JSON.parse(String(init?.body ?? '{}')) as {
          events?: { seq: number }[]
        }
        for (const event of body.events ?? []) sent.push(event.seq)
        return new Response('{}', { status: 200 })
      }),
    )

    const { countPendingBatches } = await import('./keystrokeQueue')
    const { TelemetryBuffer } = await import('./telemetryBuffer')
    const buffer = new TelemetryBuffer('session-fast', '')

    buffer.push([events[0]])
    const first = buffer.flush()
    buffer.push([{ ...events[0], seq: 1 }])
    const second = buffer.flush()

    await expect(Promise.all([first, second])).resolves.toEqual([true, true])
    expect(sent.sort()).toEqual([0, 1])
    expect(await countPendingBatches('session-fast')).toBe(0)
  })

  it('drops a batch for a session the server has already closed', async () => {
    const fetchMock = vi.fn(async () => new Response('{}', { status: 409 }))
    vi.stubGlobal('fetch', fetchMock)

    const { enqueueBatch, countPendingBatches } = await import('./keystrokeQueue')
    const { flushSession } = await import('./telemetryBuffer')

    await enqueueBatch({
      batchId: 'batch-3',
      sessionId: 'session-3',
      createdAt: 1,
      events,
    })

    // 409 means the batch lost the race with completion; retrying it forever
    // would only block the queue.
    await expect(flushSession('session-3', '', 1)).resolves.toBe(true)
    expect(await countPendingBatches('session-3')).toBe(0)
    expect(fetchMock).toHaveBeenCalledTimes(1)
  })
})
