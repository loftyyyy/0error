import { renderToStaticMarkup } from 'react-dom/server'
import { describe, expect, it } from 'vitest'

import { KeyboardGuide } from './KeyboardGuide'
import { keyForCharacter } from './keyboardLayout'

describe('keyForCharacter', () => {
  it('maps letters and punctuation on the base layer', () => {
    expect(keyForCharacter('a')).toEqual({ base: 'a', shift: false })
    expect(keyForCharacter(';')).toEqual({ base: ';', shift: false })
    expect(keyForCharacter('.')).toEqual({ base: '.', shift: false })
  })

  it('maps uppercase letters and shifted symbols to a base key plus Shift', () => {
    expect(keyForCharacter('S')).toEqual({ base: 's', shift: true })
    expect(keyForCharacter(':')).toEqual({ base: ';', shift: true })
    expect(keyForCharacter('?')).toEqual({ base: '/', shift: true })
    expect(keyForCharacter('$')).toEqual({ base: '4', shift: true })
  })

  it('maps space to the space bar', () => {
    expect(keyForCharacter(' ')).toEqual({ base: ' ', shift: false })
  })

  it('returns null for characters a keyboard cannot type', () => {
    expect(keyForCharacter(null)).toBeNull()
    expect(keyForCharacter('\n')).toBeNull()
    expect(keyForCharacter('ab')).toBeNull()
    expect(keyForCharacter('é')).toBeNull()
  })
})

describe('KeyboardGuide', () => {
  it('names the key, the shift, and the finger once a hesitation is shown', () => {
    const markup = renderToStaticMarkup(
      <KeyboardGuide character=":" hesitating visible />,
    )

    expect(markup).toContain('Hold shift')
    expect(markup).toContain('Right pinky finger')
    expect(markup.match(/fill-stone-900/g)).toHaveLength(1)
    expect(markup).toContain(
      'aria-label="Keyboard hint: press ; with your right pinky finger and Shift"',
    )
  })

  it('names the finger for a plain letter', () => {
    const markup = renderToStaticMarkup(
      <KeyboardGuide character="f" hesitating visible />,
    )

    expect(markup).toContain('Left index finger')
    expect(markup).toContain(
      'aria-label="Keyboard hint: press f with your left index finger"',
    )
  })

  it('says thumbs for the space bar', () => {
    const markup = renderToStaticMarkup(
      <KeyboardGuide character=" " hesitating visible />,
    )

    expect(markup).toContain('Thumbs')
    expect(markup).toContain(
      'aria-label="Keyboard hint: press space with your thumbs"',
    )
  })

  it('stays unlit and quiet while the rhythm holds', () => {
    const markup = renderToStaticMarkup(
      <KeyboardGuide character="a" hesitating={false} visible />,
    )

    expect(markup).not.toContain('fill-stone-900')
    expect(markup).not.toMatch(/finger|Hold shift/)
    expect(markup).toContain('aria-label="Keyboard guide"')
  })
})
