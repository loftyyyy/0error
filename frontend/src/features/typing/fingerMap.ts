/**
 * The finger that presses a base key on a US keyboard. Kept separate from the
 * key mapping so the guide can name the reach without knowing the layout.
 *
 * The groups mirror the server's `Finger.baseKeys()` so coaching and the guide
 * agree on which finger owns a key.
 */

export type FingerName =
  | 'Left pinky'
  | 'Left ring'
  | 'Left middle'
  | 'Left index'
  | 'Thumbs'
  | 'Right index'
  | 'Right middle'
  | 'Right ring'
  | 'Right pinky'

const GROUPS: ReadonlyArray<readonly [string, FingerName]> = [
  ['`1qaz', 'Left pinky'],
  ['2wsx', 'Left ring'],
  ['3edc', 'Left middle'],
  ['45rtfgvb', 'Left index'],
  [' ', 'Thumbs'],
  ['67yuhjnm', 'Right index'],
  ['8ik,', 'Right middle'],
  ['9ol.', 'Right ring'],
  ["0p;/-=[]\\'", 'Right pinky'],
]

const BY_BASE = new Map<string, FingerName>(
  GROUPS.flatMap(([keys, finger]) =>
    keys.split('').map((key) => [key, finger] as const),
  ),
)

/** Returns null for a key a hand cannot own, such as a two-character string. */
export function fingerForBase(base: string | null): FingerName | null {
  if (base === null || base.length !== 1) return null
  return BY_BASE.get(base) ?? null
}

/** Human wording for the hint: "with your right pinky finger", or thumbs. */
export function fingerHint(finger: FingerName | null): string | null {
  if (finger === null) return null
  if (finger === 'Thumbs') return 'with your thumbs'
  return `with your ${finger.toLowerCase()} finger`
}
