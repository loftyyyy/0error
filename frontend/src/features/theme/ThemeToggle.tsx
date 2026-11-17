import { Moon, Sun } from 'lucide-react'

import { useTheme } from './useTheme'

/** A quiet switch between the light and dark palettes. */
export function ThemeToggle() {
  const { theme, toggle } = useTheme()
  const next = theme === 'dark' ? 'light' : 'dark'
  const label = `Switch to ${next} theme`

  return (
    <button
      type="button"
      onClick={toggle}
      aria-label={label}
      title={label}
      className="rounded-full border border-stone-200 p-1.5 text-stone-500 transition outline-none hover:border-stone-300 hover:text-stone-900 focus-visible:ring-2 focus-visible:ring-stone-300 dark:border-stone-800 dark:text-stone-400 dark:hover:border-stone-700 dark:hover:text-stone-100 dark:focus-visible:ring-stone-700"
    >
      {theme === 'dark' ? (
        <Sun size={14} strokeWidth={1.5} aria-hidden="true" />
      ) : (
        <Moon size={14} strokeWidth={1.5} aria-hidden="true" />
      )}
    </button>
  )
}
