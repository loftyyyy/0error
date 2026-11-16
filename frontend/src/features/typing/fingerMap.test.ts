import { describe, expect, it } from 'vitest'

import { fingerForBase, fingerHint } from './fingerMap'
import { keyForCharacter, KEYBOARD_ROWS } from './keyboardLayout'

describe('fingerForBase', () => {
  it('covers every key the guide draws plus space', () => {
    for (const key of [...KEYBOARD_ROWS.flat(), ' ']) {
      expect(fingerForBase(key), key).not.toBeNull()
    }
  })

  it('matches the US layout for notable reaches', () => {
    expect(fingerForBase('a')).toBe('Left pinky')
    expect(fingerForBase('f')).toBe('Left index')
    expect(fingerForBase('j')).toBe('Right index')
    expect(fingerForBase(';')).toBe('Right pinky')
    expect(fingerForBase(' ')).toBe('Thumbs')
    expect(fingerForBase('\\')).toBe('Right pinky')
  })

  it('returns null for things a hand cannot own', () => {
    expect(fingerForBase(null)).toBeNull()
    expect(fingerForBase('ab')).toBeNull()
  })
})

describe('fingerHint', () => {
  it('words thumbs without a finger suffix', () => {
    expect(fingerHint('Thumbs')).toBe('with your thumbs')
    expect(fingerHint('Right ring')).toBe('with your right ring finger')
    expect(fingerHint(null)).toBeNull()
  })
})

describe('keyboard rows', () => {
  it('resolves every drawn key on the base layer', () => {
    for (const key of KEYBOARD_ROWS.flat()) {
      expect(keyForCharacter(key)).toEqual({ base: key, shift: false })
    }
  })
})
