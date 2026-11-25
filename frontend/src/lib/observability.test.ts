// @vitest-environment jsdom
import { afterEach, describe, expect, it, vi } from 'vitest'

import {
  CLIENT_ERROR_EVENT,
  APP_VERSION,
  installClientErrorReporting,
  reportClientError,
} from './observability'

interface Reported {
  kind: string
  message: string
}

function listen(): Reported[] {
  const seen: Reported[] = []
  window.addEventListener(CLIENT_ERROR_EVENT, (event) => {
    seen.push((event as CustomEvent<Reported>).detail)
  })
  return seen
}

afterEach(() => {
  vi.restoreAllMocks()
})

describe('client error reporting', () => {
  it('announces a render error with its message and kind', () => {
    const seen = listen()

    reportClientError(new Error('render exploded'), 'render')

    expect(seen).toEqual([{ kind: 'render', message: 'render exploded' }])
  })

  it('captures window errors and promise rejections, then detaches', () => {
    const seen = listen()
    const remove = installClientErrorReporting()

    window.dispatchEvent(new ErrorEvent('error', { message: 'script failed' }))
    expect(seen[0]).toMatchObject({ kind: 'error', message: 'script failed' })

    const rejection = new Event('unhandledrejection') as Event & { reason?: unknown }
    rejection.reason = 'promise failed'
    window.dispatchEvent(rejection)
    expect(seen[1]).toMatchObject({ kind: 'rejection', message: 'promise failed' })

    remove()
    window.dispatchEvent(new ErrorEvent('error', { message: 'ignored' }))
    expect(seen).toHaveLength(2)
  })

  it('exposes a non-empty build version', () => {
    expect(APP_VERSION.length).toBeGreaterThan(0)
  })
})
