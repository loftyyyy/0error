import { describe, expect, it } from 'vitest'

import { characterClass } from './characterClass'

describe('characterClass', () => {
  it('keeps the current character strong and underlined', () => {
    expect(characterClass('current', null)).toContain('border-b-2')
    expect(characterClass('current', true)).toContain('border-b-2')
    expect(characterClass('current', false)).toContain('border-b-2')
  })

  it('keeps untyped text readable but quiet', () => {
    expect(characterClass('todo', null)).toBe('text-stone-400 dark:text-stone-500')
  })

  it('marks a correct typed character amber', () => {
    expect(characterClass('done', true)).toContain('text-amber-600')
  })

  it('leaves a wrong typed character grey', () => {
    expect(characterClass('done', false)).toContain('text-stone-500')
    expect(characterClass('done', null)).toContain('text-stone-500')
  })
})
