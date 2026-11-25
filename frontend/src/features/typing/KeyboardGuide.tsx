/**
 * A quiet on-screen keyboard. It stays faint while the rhythm holds and lights
 * up the next key only after a hesitation, so it never competes with the text.
 */

import { fingerForBase, fingerHint } from './fingerMap'
import { keyForCharacter, KEYBOARD_ROWS } from './keyboardLayout'

export interface KeyboardGuideProps {
  /** The next character in the drill; null when the drill is over. */
  character: string | null
  hesitating: boolean
  visible?: boolean
}

/** Physical row offsets give the keyboard its familiar stagger. */
const ROW_OFFSETS = [0, 8, 20, 34]

const KEY_SIZE = 38
const KEY_GAP = 6
const ROW_STEP = 44
const SPACE_WIDTH = 200
const VIEW_WIDTH = 540
const VIEW_HEIGHT = 214

export function KeyboardGuide({
  character,
  hesitating,
  visible = true,
}: KeyboardGuideProps) {
  const target = keyForCharacter(character)
  const highlight = hesitating ? target : null
  const finger = highlight ? fingerForBase(highlight.base) : null
  const fingerLabel =
    finger === null ? null : finger === 'Thumbs' ? 'Thumbs' : `${finger} finger`
  const hint = fingerHint(finger)
  const hintParts = [fingerLabel, highlight?.shift ? 'Hold shift' : null].filter(
    (part): part is string => part !== null,
  )
  const label = highlight
    ? `Keyboard hint: press ${highlight.base === ' ' ? 'space' : highlight.base}${
        hint ? ` ${hint}` : ''
      }${highlight.shift ? ' and Shift' : ''}`
    : 'Keyboard guide'

  const keyClass = (active: boolean) =>
    active
      ? 'fill-stone-900 stroke-stone-900 transition-colors duration-300 dark:fill-stone-100 dark:stroke-stone-100'
      : 'fill-transparent stroke-stone-300 transition-colors duration-300 dark:stroke-stone-700'

  const labelClass = (active: boolean) =>
    active ? 'fill-stone-50 dark:fill-stone-950' : 'fill-stone-500 dark:fill-stone-400'

  return (
    <div
      className={`mt-12 flex flex-col items-center gap-2 transition-opacity duration-500 motion-reduce:transition-none ${
        visible ? (hesitating ? 'opacity-100' : 'opacity-40') : 'opacity-0'
      }`}
      aria-hidden={visible ? undefined : 'true'}
    >
      {/* The drawing is visual; this is what a screen reader announces. */}
      <p role="status" aria-live="polite" className="sr-only">
        {visible && hesitating && highlight ? label : ''}
      </p>
      <svg
        viewBox={`0 0 ${VIEW_WIDTH} ${VIEW_HEIGHT}`}
        role="img"
        aria-label={label}
        className="w-full max-w-[30rem] select-none"
      >
        {KEYBOARD_ROWS.map((row, rowIndex) => (
          <g key={row.join('')}>
            {row.map((base, index) => {
              const x = ROW_OFFSETS[rowIndex] + index * (KEY_SIZE + KEY_GAP)
              const y = rowIndex * ROW_STEP
              const active = highlight?.base === base
              return (
                <g key={`${rowIndex}-${index}-${base}`}>
                  <rect
                    x={x}
                    y={y}
                    width={KEY_SIZE}
                    height={KEY_SIZE}
                    rx={7}
                    className={keyClass(active)}
                  />
                  <text
                    x={x + KEY_SIZE / 2}
                    y={y + KEY_SIZE / 2}
                    textAnchor="middle"
                    dominantBaseline="central"
                    className={`font-mono text-[13px] ${labelClass(active)}`}
                  >
                    {base}
                  </text>
                  {active && highlight.shift ? (
                    <text
                      x={x + KEY_SIZE - 8}
                      y={y + 9}
                      textAnchor="middle"
                      className="text-[8px] fill-stone-50 dark:fill-stone-950"
                    >
                      &#8679;
                    </text>
                  ) : null}
                </g>
              )
            })}
          </g>
        ))}

        <g>
          <rect
            x={(VIEW_WIDTH - SPACE_WIDTH) / 2}
            y={4 * ROW_STEP}
            width={SPACE_WIDTH}
            height={KEY_SIZE}
            rx={7}
            className={keyClass(highlight?.base === ' ')}
          />
          <text
            x={VIEW_WIDTH / 2}
            y={4 * ROW_STEP + KEY_SIZE / 2}
            textAnchor="middle"
            dominantBaseline="central"
            className={`font-mono text-[10px] ${labelClass(highlight?.base === ' ')}`}
          >
            space
          </text>
        </g>
      </svg>

      {hintParts.length > 0 ? (
        <p className="text-[0.65rem] tracking-[0.15em] text-stone-400 uppercase dark:text-stone-500">
          {hintParts.join(' · ')}
        </p>
      ) : null}
    </div>
  )
}
