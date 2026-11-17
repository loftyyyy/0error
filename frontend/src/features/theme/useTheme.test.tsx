// @vitest-environment jsdom
import { act } from 'react'
import type { ReactElement } from 'react'
import { createRoot } from 'react-dom/client'
import type { Root } from 'react-dom/client'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'

import { ThemeToggle } from './ThemeToggle'
import { THEME_STORAGE_KEY, applyTheme } from './theme'
import { useTheme } from './useTheme'

// React skips effects and warns unless this flag is set outside its own renderer.
const env = globalThis as unknown as { IS_REACT_ACT_ENVIRONMENT: boolean }
env.IS_REACT_ACT_ENVIRONMENT = true

type ThemeChange = (event: MediaQueryListEvent) => void

/** jsdom has no matchMedia, so the OS preference is simulated here. */
function stubMedia(initialDark: boolean) {
  const listeners = new Set<ThemeChange>()
  const media = {
    matches: initialDark,
    addEventListener: (_type: string, listener: ThemeChange) => listeners.add(listener),
    removeEventListener: (_type: string, listener: ThemeChange) => listeners.delete(listener),
  }

  vi.stubGlobal('matchMedia', vi.fn(() => media))

  return {
    setDark(dark: boolean) {
      media.matches = dark
      act(() => {
        for (const listener of listeners) listener({ matches: dark } as MediaQueryListEvent)
      })
    },
  }
}

function ThemeProbe() {
  const { theme } = useTheme()
  return <output data-testid="theme">{theme}</output>
}

let root: Root | null = null
let container: HTMLDivElement | null = null

function mount(ui: ReactElement) {
  if (root === null || container === null) {
    container = document.createElement('div')
    document.body.append(container)
    root = createRoot(container)
  }

  act(() => {
    root!.render(ui)
  })

  return container
}

const shownTheme = () =>
  container?.querySelector('[data-testid="theme"]')?.textContent

const darkClass = () => document.documentElement.classList.contains('dark')

beforeEach(() => {
  window.localStorage.clear()
  document.documentElement.classList.remove('dark')
})

afterEach(() => {
  if (root) act(() => root!.unmount())
  root = null
  container?.remove()
  container = null
  document.documentElement.classList.remove('dark')
  vi.unstubAllGlobals()
})

describe('useTheme', () => {
  it('follows the operating system until the user picks a side', () => {
    const media = stubMedia(true)
    mount(<ThemeProbe />)

    expect(shownTheme()).toBe('dark')
    expect(darkClass()).toBe(true)

    media.setDark(false)
    expect(shownTheme()).toBe('light')
    expect(darkClass()).toBe(false)
  })

  it('uses a stored choice instead of the operating system', () => {
    window.localStorage.setItem(THEME_STORAGE_KEY, 'light')
    stubMedia(true)

    mount(<ThemeProbe />)

    expect(shownTheme()).toBe('light')
    expect(darkClass()).toBe(false)
  })

  it('stores the toggled theme and then stops following the system', () => {
    const media = stubMedia(true)
    mount(<ThemeToggle />)

    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBeNull()

    act(() => {
      container!.querySelector('button')!.click()
    })

    expect(window.localStorage.getItem(THEME_STORAGE_KEY)).toBe('light')
    expect(darkClass()).toBe(false)
    expect(container!.querySelector('button')!.getAttribute('aria-label')).toBe(
      'Switch to dark theme',
    )

    // The operating system flipping back is no longer the user's preference.
    media.setDark(true)
    expect(darkClass()).toBe(false)
  })
})

describe('applyTheme', () => {
  it('sets the class the palette keys on', () => {
    applyTheme('dark')
    expect(document.documentElement.classList.contains('dark')).toBe(true)

    applyTheme('light')
    expect(document.documentElement.classList.contains('dark')).toBe(false)
  })
})
