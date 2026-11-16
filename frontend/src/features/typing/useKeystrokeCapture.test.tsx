// @vitest-environment jsdom
import { act, useEffect } from 'react'
import { createRoot } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { countPendingBatches, readOldestBatch } from '../telemetry/keystrokeQueue'
import { drainOutbox } from '../telemetry/telemetryBuffer'
import { useKeystrokeCapture } from './useKeystrokeCapture'
import type { KeystrokeCapture } from './useKeystrokeCapture'
import type { TypingSnapshot } from './typingEngine'

// React skips effects and warns unless this flag is set outside its own renderer.
const env = globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
env.IS_REACT_ACT_ENVIRONMENT = true

interface HarnessProps {
  sessionId: string
  text: string
  onComplete?: (snapshot: TypingSnapshot) => void
  onReady?: (capture: KeystrokeCapture) => void
}

function Harness({ sessionId, text, onComplete, onReady }: HarnessProps) {
  const capture = useKeystrokeCapture({ sessionId, text, apiBaseUrl: '', onComplete })
  const { containerRef, snapshot, notice } = capture

  useEffect(() => {
    onReady?.(capture)
  }, [onReady, capture])

  return (
    <div ref={containerRef} tabIndex={-1}>
      <output data-testid="typed">{snapshot.typed}</output>
      <output data-testid="corrections">{snapshot.corrections}</output>
      <output data-testid="warmup">{snapshot.warmupRemaining}</output>
      <output data-testid="notice">{notice}</output>
    </div>
  )
}

let mounted: { container: HTMLDivElement; unmount: () => void } | null = null
let latestCapture: KeystrokeCapture | null = null

const keepLatest = (capture: KeystrokeCapture) => {
  latestCapture = capture
}

function captureNow(): KeystrokeCapture {
  if (latestCapture === null) throw new Error('capture is not mounted yet')
  return latestCapture
}

function mount(props: HarnessProps) {
  const container = document.createElement('div')
  document.body.append(container)
  const root = createRoot(container)

  act(() => {
    root.render(<Harness {...props} />)
  })

  mounted = {
    container,
    unmount: () => {
      act(() => root.unmount())
      container.remove()
    },
  }
  return container
}

const read = (container: HTMLElement, testId: string) =>
  container.querySelector(`[data-testid="${testId}"]`)?.textContent

/** Lets the engine's animation-frame wakeup and any flush promises settle. */
async function settle() {
  await act(async () => {
    await new Promise((resolve) => setTimeout(resolve, 30))
  })
}

async function press(key: string, init: KeyboardEventInit = {}) {
  const event = new KeyboardEvent('keydown', { key, cancelable: true, ...init })
  await act(async () => {
    window.dispatchEvent(event)
  })
  return event
}

beforeEach(async () => {
  vi.stubGlobal('fetch', vi.fn(async () => new Response('{}', { status: 200 })))
  await drainOutbox('')
})

afterEach(() => {
  mounted?.unmount()
  mounted = null
  latestCapture = null
  vi.unstubAllGlobals()
})

describe('useKeystrokeCapture', () => {
  it('keeps the raw expected and actual characters in the outbox', async () => {
    // A failing upload keeps the batch readable in the queue instead of deleting it.
    vi.stubGlobal('fetch', vi.fn(async () => new Response('{}', { status: 503 })))
    mount({ sessionId: 'capture-outbox', text: 'ab', onReady: keepLatest })

    await press('a')
    await press('x')
    await settle()

    await act(async () => {
      await captureNow().flush()
    })

    expect(await readOldestBatch()).toMatchObject({
      sessionId: 'capture-outbox',
      events: [
        expect.objectContaining({ expected: 'a', actual: 'a', correct: true }),
        expect.objectContaining({ expected: 'b', actual: 'x', correct: false }),
      ],
    })
  })

  it('types the drill text instead of letting the browser act on keys', async () => {
    const container = mount({ sessionId: 'capture-1', text: 'asdf' })

    const first = await press('a')
    const second = await press('s')
    await settle()

    expect(first.defaultPrevented).toBe(true)
    expect(second.defaultPrevented).toBe(true)
    expect(read(container, 'typed')).toBe('2')
  })

  it('ignores modified keys and keys aimed at an editable field', async () => {
    const container = mount({ sessionId: 'capture-2', text: 'asdf' })

    const input = document.createElement('input')
    document.body.append(input)
    const typing = new KeyboardEvent('keydown', { key: 'a', cancelable: true, bubbles: true })
    input.dispatchEvent(typing)
    input.remove()

    const shortcut = await press('a', { ctrlKey: true })
    await settle()

    expect(typing.defaultPrevented).toBe(false)
    expect(shortcut.defaultPrevented).toBe(false)
    expect(read(container, 'typed')).toBe('0')

    await press('a')
    await settle()
    expect(read(container, 'typed')).toBe('1')
  })

  it('records a correction for backspace and keeps the first-attempt score', async () => {
    const container = mount({ sessionId: 'capture-3', text: 'ab' })

    await press('a')
    const backspace = await press('Backspace')
    await press('b')
    await settle()

    expect(backspace.defaultPrevented).toBe(true)
    expect(read(container, 'typed')).toBe('2')
    expect(read(container, 'corrections')).toBe('1')
  })

  it('blocks paste and says so', async () => {
    const container = mount({ sessionId: 'capture-4', text: 'asdf' })

    const paste = new Event('paste', { cancelable: true })
    await act(async () => {
      window.dispatchEvent(paste)
    })

    expect(paste.defaultPrevented).toBe(true)
    expect(read(container, 'notice')).toBe('paste-blocked')
  })

  it('treats the keys after a focus loss as warmup instead of scoring them', async () => {
    const container = mount({ sessionId: 'capture-5', text: 'asdfdf' })

    await press('a')
    await act(async () => {
      window.dispatchEvent(new Event('blur'))
    })

    await press('s')
    await settle()

    expect(read(container, 'typed')).toBe('1')
    expect(read(container, 'warmup')).toBe('2')
  })

  it('hands the finished drill back only after the keystrokes are flushed', async () => {
    const onComplete = vi.fn()
    let serverUp = false
    const fetchMock = vi.fn(async () =>
      new Response('{}', { status: serverUp ? 200 : 503 }),
    )
    vi.stubGlobal('fetch', fetchMock)

    mount({
      sessionId: 'capture-6',
      text: 'ab',
      onComplete,
      onReady: keepLatest,
    })

    await press('a')
    await press('b')
    await settle()

    expect(captureNow().snapshot.status).toBe('complete')
    expect(fetchMock).toHaveBeenCalled()
    expect(onComplete).toHaveBeenCalledTimes(1)
    expect(onComplete.mock.calls[0][0]).toMatchObject({ typed: 2, accuracy: 1 })
    // A retryable failure leaves the batch queued, so the caller can refuse to score.
    expect(await countPendingBatches('capture-6')).toBe(1)

    serverUp = true
    await act(async () => {
      await captureNow().flush()
    })
    expect(await countPendingBatches('capture-6')).toBe(0)
  })
})
