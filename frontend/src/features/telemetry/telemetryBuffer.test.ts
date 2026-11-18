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
