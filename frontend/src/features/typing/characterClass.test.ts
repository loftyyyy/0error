import { describe, expect, it } from 'vitest'

import { characterClass } from './characterClass'

describe('characterClass', () => {
  it('keeps the current character strong and underlined', () => {
    expect(characterClass('current', null)).toContain('border-b-2')
    expect(characterClass('current', 'hesitant')).toContain('border-b-2')
  })

  it('keeps untyped text readable but quiet', () => {
    expect(characterClass('todo', null)).toBe('text-stone-400 dark:text-stone-500')
  })

  it('leaves a warm trace for a pause and fades fluent keys', () => {
    expect(characterClass('done', 'hesitant')).toContain('text-amber-600')
    expect(characterClass('done', 'steady')).toBe('text-stone-400 dark:text-stone-500')
    expect(characterClass('done', 'smooth')).toBe('text-stone-300 dark:text-stone-600')
  })

  it('treats warmup and unknown rhythm as neutral', () => {
    expect(characterClass('done', 'warmup')).toBe('text-stone-300 dark:text-stone-700')
    expect(characterClass('done', null)).toBe('text-stone-300 dark:text-stone-600')
  })
})
