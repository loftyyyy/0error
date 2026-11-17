import { describe, expect, it } from 'vitest'

import { resolveTheme } from './theme'

describe('resolveTheme', () => {
  it('follows the operating system until the user chooses', () => {
    expect(resolveTheme(null, true)).toBe('dark')
    expect(resolveTheme(null, false)).toBe('light')
  })

  it('keeps an explicit choice whatever the system says', () => {
    expect(resolveTheme('light', true)).toBe('light')
    expect(resolveTheme('dark', false)).toBe('dark')
  })
})
