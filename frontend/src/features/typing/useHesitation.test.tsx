// @vitest-environment jsdom
import { act } from 'react'
import { createRoot } from 'react-dom/client'
import type { Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { HESITATION_HINT_MS } from './keyboardLayout'
import { MAX_HINT_MS, MIN_HINT_MS, nextHintDelay } from './hintDelay'
import type { StrokeQuality } from './typingEngine'
import { useHesitation } from './useHesitation'

// React skips effects and warns unless this flag is set outside its own renderer.
const env = globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
env.IS_REACT_ACT_ENVIRONMENT = true

function Harness({
  eventCount,
  running,
  lastQuality = null,
}: {
  eventCount: number
  running: boolean
  lastQuality?: StrokeQuality | null
}) {
  const { hesitating, delayMs } = useHesitation(eventCount, running, lastQuality)
  return (
    <>
      <output data-testid="state">{hesitating ? 'hint' : 'quiet'}</output>
      <output data-testid="delay">{delayMs}</output>
    </>
  )
}

let root: Root | null = null
let container: HTMLDivElement | null = null

function render(props: {
  eventCount: number
  running: boolean
  lastQuality?: StrokeQuality | null
}) {
  if (root === null || container === null) {
    container = document.createElement('div')
    document.body.append(container)
    root = createRoot(container)
  }

  act(() => {
    root!.render(<Harness {...props} />)
  })

  return container
}

const state = () => container?.querySelector('[data-testid="state"]')?.textContent
const delay = () => Number(container?.querySelector('[data-testid="delay"]')?.textContent)

function wait(ms: number) {
  act(() => {
    vi.advanceTimersByTime(ms)
  })
}

beforeEach(() => {
  vi.useFakeTimers()
})

afterEach(() => {
  if (root) act(() => root!.unmount())
  root = null
  container?.remove()
  container = null
  vi.useRealTimers()
})

describe('useHesitation', () => {
  it('stays quiet for the whole delay and then offers the hint', () => {
    render({ eventCount: 0, running: true })
    expect(state()).toBe('quiet')

    wait(HESITATION_HINT_MS - 1)
    expect(state()).toBe('quiet')

    wait(1)
    expect(state()).toBe('hint')
  })

  it('clears the hint on the next key and restarts the delay', () => {
    render({ eventCount: 0, running: true })
    wait(HESITATION_HINT_MS)
    expect(state()).toBe('hint')

    render({ eventCount: 1, running: true })
    expect(state()).toBe('quiet')

    wait(HESITATION_HINT_MS - 1)
    expect(state()).toBe('quiet')

    wait(1)
    expect(state()).toBe('hint')
  })

  it('shortens the next wait after a stumbling key', () => {
    render({ eventCount: 0, running: true })
    expect(delay()).toBe(HESITATION_HINT_MS)

    render({ eventCount: 1, running: true, lastQuality: 'hesitant' })
    const shortened = nextHintDelay(HESITATION_HINT_MS, 'hesitant')
    expect(delay()).toBe(shortened)
    expect(shortened).toBeLessThan(HESITATION_HINT_MS)

    wait(shortened - 1)
    expect(state()).toBe('quiet')
    wait(1)
    expect(state()).toBe('hint')
  })

  it('eases the wait back toward the ceiling on a smooth key', () => {
    render({ eventCount: 1, running: true, lastQuality: 'smooth' })
    const eased = nextHintDelay(HESITATION_HINT_MS, 'smooth')
    expect(delay()).toBe(eased)
    expect(eased).toBeGreaterThan(HESITATION_HINT_MS)
    expect(eased).toBeLessThanOrEqual(MAX_HINT_MS)
  })

  it('keeps the adaptive delay inside its bounds', () => {
    expect(MIN_HINT_MS).toBe(650)
    expect(MAX_HINT_MS).toBe(1200)
  })

  it('never shows the hint once the drill stops', () => {
    render({ eventCount: 4, running: false })
    wait(HESITATION_HINT_MS * 2)
    expect(state()).toBe('quiet')
  })
})
