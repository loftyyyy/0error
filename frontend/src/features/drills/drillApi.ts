import { authedFetch } from '../identity/guestSession'

/** A drill the server has already validated against the tier charset. */
export interface Drill {
  tier: number
  tierName: string
  charset: string
  source: 'STATIC' | 'AI'
  text: string
  length: number
}

/**
 * One attempt spans three drills. The unlock gate needs 120 scored keystrokes,
 * and a single drill is only 40-60 characters, so one drill could never pass.
 * Joining three keeps every drill inside the validated tier charset.
 */
export const DRILLS_PER_ATTEMPT = 3

export interface DrillSet {
  tier: number
  tierName: string
  charset: string
  source: Drill['source']
  /** The combined text the user types. */
  text: string
  /** Last drill in the set, so a retry can avoid starting with it again. */
  tail: string
}

export async function fetchDrillSet(tier: number, exclude?: string): Promise<DrillSet> {
  const drills: Drill[] = []
  let previous = exclude

  for (let index = 0; index < DRILLS_PER_ATTEMPT; index += 1) {
    const drill = await fetchNextDrill(tier, previous)
    drills.push(drill)
    previous = drill.text
  }

  const [first] = drills
  return {
    tier: first.tier,
    tierName: first.tierName,
    charset: first.charset,
    source: drills.every((drill) => drill.source === 'STATIC') ? 'STATIC' : 'AI',
    text: drills.map((drill) => drill.text).join(' '),
    tail: drills[drills.length - 1].text,
  }
}

/**
 * Fetches the next drill. `exclude` asks the server to avoid the previous
 * text, so a retry does not repeat the same practice string.
 */
export async function fetchNextDrill(tier: number, exclude?: string): Promise<Drill> {
  const query = new URLSearchParams({ tier: String(tier) })
  if (exclude) query.set('exclude', exclude)

  const response = await authedFetch(`/api/v1/drills/next?${query.toString()}`, {
    headers: { Accept: 'application/json' },
  })

  if (response.status === 403) {
    throw new Error(`Tier ${tier} is still locked`)
  }
  if (!response.ok) {
    throw new Error(`Could not load a drill (${response.status})`)
  }

  const drill = (await response.json()) as Partial<Drill>
  if (typeof drill.text !== 'string' || typeof drill.charset !== 'string') {
    throw new Error('The drill response was invalid')
  }
  return drill as Drill
}
