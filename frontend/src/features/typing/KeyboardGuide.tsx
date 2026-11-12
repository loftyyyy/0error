/**
 * A quiet on-screen keyboard. It stays faint while the rhythm holds and lights
 * up the next key only after a hesitation, so it never competes with the text.
 */

import { keyForCharacter } from './keyboardLayout'

export interface KeyboardGuideProps {
  /** The next character in the drill; null when the drill is over. */
  character: string | null
  hesitating: boolean
  visible?: boolean
}

const ROWS: string[][] = [
  '1234567890-='.split(''),
  'qwertyuiop[]'.split(''),
  "asdfjkl;'".split(''),
  'zxcvbnm,./'.split(''),
]

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
  const label = highlight
    ? `Keyboard hint: press ${highlight.base === ' ' ? 'space' : highlight.base}${
        highlight.shift ? ' with Shift' : ''
      }`
    : 'Keyboard guide'

  const keyClass = (active: boolean) =>
    active
      ? 'fill-stone-900 stroke-stone-900 transition-colors duration-300 dark:fill-stone-100 dark:stroke-stone-100'
      : 'fill-transparent stroke-stone-300 transition-colors duration-300 dark:stroke-stone-700'

  const labelClass = (active: boolean) =>
    active ? 'fill-stone-50 dark:fill-stone-950' : 'fill-stone-400 dark:fill-stone-500'

  return (
    <div
      className={`mt-12 flex flex-col items-center gap-2 transition-opacity duration-500 ${
        visible ? (hesitating ? 'opacity-100' : 'opacity-40') : 'opacity-0'
      }`}
      aria-hidden={visible ? undefined : 'true'}
    >
      <svg
        viewBox={`0 0 ${VIEW_WIDTH} ${VIEW_HEIGHT}`}
        role="img"
        aria-label={label}
        className="w-full max-w-[30rem] select-none"
      >
        {ROWS.map((row, rowIndex) => (
          <g key={row.join('')}>
            {row.map((base, index) => {
              const x = ROW_OFFSETS[rowIndex] + index * (KEY_SIZE + KEY_GAP)
              const y = rowIndex * ROW_STEP
              const active = highlight?.base === base
              return (
                <g key={base}>
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

      {highlight?.shift ? (
        <p className="text-[0.65rem] tracking-[0.15em] text-stone-400 uppercase dark:text-stone-500">
          Hold shift
        </p>
      ) : null}
    </div>
  )
}
