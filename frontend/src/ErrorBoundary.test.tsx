// @vitest-environment jsdom
import { Component } from 'react'
import type { ReactNode } from 'react'
import { act } from 'react'
import { createRoot } from 'react-dom/client'
import type { Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ErrorBoundary } from './ErrorBoundary'

const env = globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
env.IS_REACT_ACT_ENVIRONMENT = true

class Boom extends Component<{ children?: ReactNode }> {
  render(): ReactNode {
    throw new Error('render exploded')
  }
}

let root: Root | null = null
let container: HTMLDivElement | null = null

const text = () => container?.textContent ?? ''

beforeEach(() => {
  vi.spyOn(console, 'error').mockImplementation(() => undefined)
  container = document.createElement('div')
  document.body.append(container)
  root = createRoot(container)
})

afterEach(() => {
  if (root) act(() => root!.unmount())
  root = null
  container?.remove()
  container = null
  vi.restoreAllMocks()
})

describe('ErrorBoundary', () => {
  it('keeps a calm fallback instead of a blank page', () => {
    act(() => {
      root!.render(
        <ErrorBoundary onReload={() => undefined}>
          <Boom />
        </ErrorBoundary>,
      )
    })

    expect(text()).toContain('Something went wrong.')
    expect(text()).toContain('Your progress is safe.')
  })

  it('offers one reload action', () => {
    const reload = vi.fn()
    act(() => {
      root!.render(
        <ErrorBoundary onReload={reload}>
          <Boom />
        </ErrorBoundary>,
      )
    })

    const button = Array.from(container?.querySelectorAll('button') ?? []).find(
      (candidate) => candidate.textContent?.includes('Reload'),
    )
    expect(button).toBeDefined()

    act(() => {
      button?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
    })
    expect(reload).toHaveBeenCalledTimes(1)
  })
})
