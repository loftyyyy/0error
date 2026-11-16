// @vitest-environment jsdom
import { act } from 'react'
import { createRoot } from 'react-dom/client'
import type { Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { HESITATION_HINT_MS } from './keyboardLayout'
import { useHesitation } from './useHesitation'

// React skips effects and warns unless this flag is set outside its own renderer.
const env = globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
env.IS_REACT_ACT_ENVIRONMENT = true

function Harness({ eventCount, running }: { eventCount: number; running: boolean }) {
  const hesitating = useHesitation(eventCount, running)
  return <output data-testid="state">{hesitating ? 'hint' : 'quiet'}</output>
}

let root: Root | null = null
let container: HTMLDivElement | null = null

function render(props: { eventCount: number; running: boolean }) {
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

  it('never shows the hint once the drill stops', () => {
    render({ eventCount: 4, running: false })
    wait(HESITATION_HINT_MS * 2)
    expect(state()).toBe('quiet')
  })
})
