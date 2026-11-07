import { describe, expect, it } from 'vitest'

import { HESITATION_GAP_MS, TypingEngine, WARMUP_KEYSTROKES } from './typingEngine'

function fakeClock(start = 0) {
  let value = start
  return {
    now: () => value,
    set: (next: number) => {
      value = next
    },
    advance: (ms: number) => {
      value += ms
    },
  }
}

describe('TypingEngine', () => {
  it('records display-to-key and inter-key time while advancing', () => {
    const clock = fakeClock()
    const engine = new TypingEngine('as', { now: clock.now })

    const first = engine.press('a', 100)
    const second = engine.press('s', 250)

    expect(first).toMatchObject({ position: 0, correct: true, t: 100, displayMs: 100 })
    expect(second).toMatchObject({ position: 1, correct: true, t: 250, displayMs: 150 })
    expect(engine.getSnapshot()).toMatchObject({
      index: 2,
      status: 'complete',
      accuracy: 1,
      typed: 2,
    })
    expect(engine.press('a', 300)).toBeNull()
  })

  it('keeps the first-attempt error when a character is corrected', () => {
    const clock = fakeClock()
    const engine = new TypingEngine('as', { now: clock.now })

    engine.press('x', 100)
    const backspace = engine.press('Backspace', 200)
    engine.press('a', 300)
    engine.press('s', 400)

    expect(backspace).toMatchObject({
      kind: 'BACKSPACE',
      position: 0,
      expected: 'a',
      actual: null,
    })
    expect(engine.getSnapshot()).toMatchObject({
      accuracy: 0.5,
      errors: 1,
      corrections: 1,
      typed: 3,
    })
  })

  it('freezes the clock across a pause and marks the next keys as warmup', () => {
    const clock = fakeClock()
    const engine = new TypingEngine('asdf', { now: clock.now })

    engine.press('a', 100)
    engine.pause(200)
    clock.set(5_200)
    engine.resume(5_200)

    const afterResume = engine.press('s', 5_300)

    expect(engine.getSnapshot()).toMatchObject({
      status: 'running',
      warmupRemaining: WARMUP_KEYSTROKES - 1,
      typed: 1,
    })
    expect(afterResume).toMatchObject({ warmup: true, t: 300 })
  })

  it('excludes warmup keystrokes from scoring', () => {
    const clock = fakeClock()
    const engine = new TypingEngine('asdfg', { now: clock.now })

    engine.pause(0)
    engine.resume(0)

    for (const [index, key] of ['a', 's', 'd', 'f'].entries()) {
      engine.press(key, 100 + index * 100)
    }

    expect(engine.getSnapshot()).toMatchObject({
      index: 4,
      typed: 1,
      accuracy: 1,
      warmupRemaining: 0,
    })
  })

  it('counts a gap over the hesitation threshold without pausing', () => {
    const clock = fakeClock()
    const engine = new TypingEngine('asdf', { now: clock.now })

    const hesitationAt = 100 + HESITATION_GAP_MS + 1
    engine.press('a', 0)
    engine.press('s', 100)
    engine.press('d', hesitationAt)
    engine.press('f', hesitationAt + 100)

    expect(engine.hesitationCountValue).toBe(1)
  })

  it('ignores backspace at the start and modifier-style keys', () => {
    const clock = fakeClock()
    const engine = new TypingEngine('as', { now: clock.now })

    expect(engine.press('Backspace', 10)).toBeNull()
    expect(engine.press('Shift', 20)).toBeNull()
    expect(engine.getSnapshot().eventCount).toBe(0)
  })

  it('returns a stable snapshot until the next mutation', () => {
    const clock = fakeClock()
    const engine = new TypingEngine('as', { now: clock.now })

    const first = engine.getSnapshot()
    expect(engine.getSnapshot()).toBe(first)

    engine.press('a', 100)
    const second = engine.getSnapshot()
    expect(second).not.toBe(first)
    expect(second.index).toBe(1)
  })
})
