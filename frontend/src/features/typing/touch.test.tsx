// @vitest-environment jsdom
import { act } from 'react'
import { createRoot } from 'react-dom/client'
import type { Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { TouchNotice } from './TouchNotice'
import { useTouchOnly } from './useTouchOnly'

const env = globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
env.IS_REACT_ACT_ENVIRONMENT = true

function media(matches: Record<string, boolean>) {
  return vi.fn((query: string) => ({
    matches: matches[query] ?? false,
    media: query,
    addEventListener: () => undefined,
    removeEventListener: () => undefined,
  }))
}

function Harness({ touchOnly }: { touchOnly: boolean }) {
  return <span>{useTouchOnly() === touchOnly ? 'matches' : 'wrong'}</span>
}

let root: Root | null = null
let container: HTMLDivElement | null = null

beforeEach(() => {
  container = document.createElement('div')
  document.body.append(container)
  root = createRoot(container)
})

afterEach(() => {
  if (root) act(() => root!.unmount())
  root = null
  container?.remove()
  container = null
  vi.unstubAllGlobals()
})

describe('useTouchOnly', () => {
  it('is true for a coarse pointer without any fine pointer', () => {
    vi.stubGlobal(
      'matchMedia',
      media({ '(pointer: coarse)': true, '(any-pointer: fine)': false }),
    )
    act(() => root!.render(<Harness touchOnly />))
    expect(container?.textContent).toBe('matches')
  })

  it('is false when a mouse or trackpad is present', () => {
    vi.stubGlobal(
      'matchMedia',
      media({ '(pointer: coarse)': true, '(any-pointer: fine)': true }),
    )
    act(() => root!.render(<Harness touchOnly={false} />))
    expect(container?.textContent).toBe('matches')
  })
})

describe('TouchNotice', () => {
  it('asks for a physical keyboard instead of drawing a drill', () => {
    act(() => root!.render(<TouchNotice />))
    expect(container?.textContent).toContain('A physical keyboard is needed.')
    expect(container?.textContent).toContain('Open 0Error on a laptop or desktop')
    expect(container?.querySelector('[data-testid="drill-text"]')).toBeNull()
  })
})
