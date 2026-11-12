/**
 * Maps a drill character to the physical key that types it. Pure so the
 * keyboard guide stays a thin rendering concern.
 */

export interface KeyboardTarget {
  /** The physical key to press, using the unshifted character. */
  base: string
  /** True when the target needs Shift held. */
  shift: boolean
}

/** How long an idle pause may last before the guide offers help. */
export const HESITATION_HINT_MS = 1_200

const UNSHIFTED = new Set(
  "abcdefghijklmnopqrstuvwxyz0123456789`-=[]\\;,./'".split(''),
)

const SHIFTED: Record<string, string> = {
  '`': '~',
  '1': '!',
  '2': '@',
  '3': '#',
  '4': '$',
  '5': '%',
  '6': '^',
  '7': '&',
  '8': '*',
  '9': '(',
  '0': ')',
  '-': '_',
  '=': '+',
  '[': '{',
  ']': '}',
  '\\': '|',
  ';': ':',
  "'": '"',
  ',': '<',
  '.': '>',
  '/': '?',
}

/** Resolves a drill character to the key that types it. */
export function keyForCharacter(character: string | null): KeyboardTarget | null {
  if (character === null || character.length !== 1) return null
  if (character === ' ') return { base: ' ', shift: false }
  if (character >= 'A' && character <= 'Z') {
    return { base: character.toLowerCase(), shift: true }
  }
  if (UNSHIFTED.has(character)) return { base: character, shift: false }

  const base = Object.keys(SHIFTED).find((key) => SHIFTED[key] === character)
  return base ? { base, shift: true } : null
}
