export type CharacterState = 'done' | 'current' | 'todo'

/**
 * Errors over rhythm: a correct key stays amber, a wrong key stays grey, and
 * the next expected character is always the strong underlined one.
 */
export function characterClass(
  state: CharacterState,
  correct: boolean | null,
): string {
  if (state === 'current') {
    return 'border-b-2 border-stone-900 text-stone-900 dark:border-stone-100 dark:text-stone-100'
  }
  if (state === 'todo') return 'text-stone-400 dark:text-stone-500'
  if (correct === true) return 'text-amber-600 dark:text-amber-400'
  return 'text-stone-500 dark:text-stone-400'
}
