/**
 * Theme preference for the app. The class on <html> decides the palette, and
 * the choice is stored so it survives a reload. Nothing here touches React.
 */

export type ResolvedTheme = 'light' | 'dark'

/** localStorage key, kept in sync with the inline script in index.html. */
export const THEME_STORAGE_KEY = '0error-theme'

const DARK_QUERY = '(prefers-color-scheme: dark)'

/** The stored choice, or null when the user never picked one. */
export function readStoredTheme(): ResolvedTheme | null {
  try {
    const stored = window.localStorage.getItem(THEME_STORAGE_KEY)
    return stored === 'light' || stored === 'dark' ? stored : null
  } catch {
    // Storage can be blocked; the system preference is still usable.
    return null
  }
}

export function storeTheme(theme: ResolvedTheme): void {
  try {
    window.localStorage.setItem(THEME_STORAGE_KEY, theme)
  } catch {
    // A blocked store only means the choice lasts until reload.
  }
}

export function systemPrefersDark(): boolean {
  return window.matchMedia(DARK_QUERY).matches
}

/** An explicit choice wins; otherwise the operating system decides. */
export function resolveTheme(
  preference: ResolvedTheme | null,
  systemDark: boolean,
): ResolvedTheme {
  if (preference !== null) return preference
  return systemDark ? 'dark' : 'light'
}

export function applyTheme(theme: ResolvedTheme): void {
  document.documentElement.classList.toggle('dark', theme === 'dark')
}
