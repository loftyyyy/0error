// @vitest-environment jsdom
import { act } from 'react'
import { createRoot } from 'react-dom/client'
import type { Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { enqueueBatch } from './keystrokeQueue'
import { SyncLine } from './SyncLine'
import { drainOutbox } from './telemetryBuffer'
import type { KeystrokeEvent } from '../typing/typingEngine'

const env = globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
env.IS_REACT_ACT_ENVIRONMENT = true

const events: KeystrokeEvent[] = [
  {
    seq: 0,
    position: 0,
    kind: 'KEY',
    expected: 'a',
    actual: 'a',
    correct: true,
    t: 100,
    displayMs: 100,
    warmup: false,
  },
]

let root: Root | null = null
let container: HTMLDivElement | null = null

const text = () => container?.textContent ?? ''

async function drain() {
  await act(async () => {
    await drainOutbox('')
  })
}

beforeEach(() => {
  window.localStorage.clear()
  container = document.createElement('div')
  document.body.append(container)
  root = createRoot(container)
  act(() => {
    root!.render(<SyncLine />)
  })
})

afterEach(() => {
  if (root) act(() => root!.unmount())
  root = null
  container?.remove()
  container = null
  vi.unstubAllGlobals()
})

describe('SyncLine', () => {
  it('names a saved queue while offline and disappears once synced', async () => {
    expect(text()).toBe('')

    vi.stubGlobal(
      'fetch',
      vi.fn(async () => {
        throw new TypeError('Failed to fetch')
      }),
    )
    await enqueueBatch({
      batchId: 'batch-offline-ui',
      sessionId: 'session-offline-ui',
      createdAt: 1,
      events,
    })
    await drain()

    expect(text()).toContain('Offline. Your keys are saved on this device.')

    vi.stubGlobal('fetch', vi.fn(async () => new Response('{}', { status: 200 })))
    await drain()

    expect(text()).toBe('')
  })

  it('says the queue is waiting when the server asks for a retry', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(async () => new Response('{}', { status: 503 })),
    )
    await enqueueBatch({
      batchId: 'batch-pending-ui',
      sessionId: 'session-pending-ui',
      createdAt: 1,
      events,
    })
    await drain()

    expect(text()).toContain('Keys are saved here. Sending when the server answers.')
  })
})
