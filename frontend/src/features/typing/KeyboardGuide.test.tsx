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
  it('lights exactly the target key once a hesitation is shown', () => {
    const markup = renderToStaticMarkup(
      <KeyboardGuide character=":" hesitating visible />,
    )

    expect(markup).toContain('Hold shift')
    expect(markup.match(/fill-stone-900/g)).toHaveLength(1)
    expect(markup).toContain('aria-label="Keyboard hint: press ; with Shift"')
  })

  it('stays unlit and quiet while the rhythm holds', () => {
    const markup = renderToStaticMarkup(
      <KeyboardGuide character="a" hesitating={false} visible />,
    )

    expect(markup).not.toContain('fill-stone-900')
    expect(markup).not.toContain('Hold shift')
    expect(markup).toContain('aria-label="Keyboard guide"')
  })
})
