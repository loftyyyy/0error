import type { StrokeQuality } from './typingEngine'

export type CharacterState = 'done' | 'current' | 'todo'

/**
 * Rhythm over errors: a fluent key fades, a steady one stays quiet, and a
 * pause leaves a warm trace. Nothing turns red for a mistake.
 */
export function characterClass(
  state: CharacterState,
  quality: StrokeQuality | null,
): string {
  if (state === 'current') {
    return 'border-b-2 border-stone-900 text-stone-900 dark:border-stone-100 dark:text-stone-100'
  }
  if (state === 'todo') return 'text-stone-400 dark:text-stone-500'

  switch (quality) {
    case 'hesitant':
      return 'text-amber-600 dark:text-amber-400'
    case 'steady':
      return 'text-stone-400 dark:text-stone-500'
    case 'warmup':
      return 'text-stone-300 dark:text-stone-700'
    default:
      return 'text-stone-300 dark:text-stone-600'
  }
}
