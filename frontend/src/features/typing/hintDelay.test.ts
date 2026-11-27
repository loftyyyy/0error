import { describe, expect, it } from 'vitest'

import {
  BASE_HINT_MS,
  MAX_HINT_MS,
  MIN_HINT_MS,
  nextHintDelay,
} from './hintDelay'

describe('nextHintDelay', () => {
  it('starts at the shared hesitation threshold', () => {
    expect(BASE_HINT_MS).toBe(900)
  })

  it('shortens after a stumble and clamps at the floor', () => {
    let delay = BASE_HINT_MS
    for (let index = 0; index < 10; index += 1) {
      delay = nextHintDelay(delay, 'hesitant')
    }
    expect(delay).toBe(MIN_HINT_MS)
    expect(MIN_HINT_MS).toBeGreaterThanOrEqual(500)
  })

  it('returns toward the ceiling after smooth keys and clamps there', () => {
    let delay = BASE_HINT_MS
    for (let index = 0; index < 10; index += 1) {
      delay = nextHintDelay(delay, 'smooth')
    }
    expect(delay).toBe(MAX_HINT_MS)
    expect(MAX_HINT_MS).toBeLessThanOrEqual(1200)
  })

  it('leaves steady, warmup, and unknown keys unchanged', () => {
    expect(nextHintDelay(800, 'steady')).toBe(800)
    expect(nextHintDelay(800, 'warmup')).toBe(800)
    expect(nextHintDelay(800, null)).toBe(800)
  })
})
