import { useCallback, useEffect, useState } from 'react'

import { applyTheme, readStoredTheme, resolveTheme, storeTheme, systemPrefersDark } from './theme'
import type { ResolvedTheme } from './theme'

export interface ThemeControls {
  theme: ResolvedTheme
  toggle: () => void
}

/**
 * Keeps <html> in step with the chosen theme. Until the user picks one, the
 * operating system decides and a live OS change is followed.
 */
export function useTheme(): ThemeControls {
  const [preference, setPreference] = useState<ResolvedTheme | null>(() => readStoredTheme())
  const [systemDark, setSystemDark] = useState(() => systemPrefersDark())
  const theme = resolveTheme(preference, systemDark)

  useEffect(() => {
    applyTheme(theme)
  }, [theme])

  useEffect(() => {
    const media = window.matchMedia('(prefers-color-scheme: dark)')
    const handleChange = (event: MediaQueryListEvent) => setSystemDark(event.matches)
    media.addEventListener('change', handleChange)
    return () => media.removeEventListener('change', handleChange)
  }, [])

  const toggle = useCallback(() => {
    const next: ResolvedTheme = resolveTheme(preference, systemDark) === 'dark' ? 'light' : 'dark'
    storeTheme(next)
    setPreference(next)
  }, [preference, systemDark])

  return { theme, toggle }
}
