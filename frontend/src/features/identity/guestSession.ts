import { apiUrl } from '../../lib/api'

/** Storage keys shared by the API client and the telemetry outbox. */
export const GUEST_TOKEN_KEY = '0error.guestToken'
export const GUEST_ID_KEY = '0error.guestId'

export interface GuestIdentity {
  guestId: string
  token: string
}

function storage(): Storage | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage
  } catch {
    return null
  }
}

export function readGuestToken(): string | null {
  try {
    return storage()?.getItem(GUEST_TOKEN_KEY) ?? null
  } catch {
    return null
  }
}

function readGuestId(): string | null {
  try {
    return storage()?.getItem(GUEST_ID_KEY) ?? null
  } catch {
    return null
  }
}

function persist(identity: GuestIdentity): void {
  try {
    storage()?.setItem(GUEST_TOKEN_KEY, identity.token)
    storage()?.setItem(GUEST_ID_KEY, identity.guestId)
  } catch {
    // Private mode: the token survives only for this page load.
  }
}

function clearGuestIdentity(): void {
  try {
    storage()?.removeItem(GUEST_TOKEN_KEY)
    storage()?.removeItem(GUEST_ID_KEY)
  } catch {
    // Nothing to clear.
  }
}

let pendingGuest: Promise<GuestIdentity> | null = null

/**
 * Returns the stored guest identity, issuing one on first use. Concurrent
 * callers share a single issuance request so the app never creates two guests
 * during startup.
 */
export function ensureGuest(): Promise<GuestIdentity> {
  const token = readGuestToken()
  const guestId = readGuestId()
  if (token && guestId) {
    return Promise.resolve({ guestId, token })
  }
  if (pendingGuest) return pendingGuest

  pendingGuest = requestGuest()
    .then((identity) => {
      persist(identity)
      return identity
    })
    .finally(() => {
      pendingGuest = null
    })

  return pendingGuest
}

async function requestGuest(): Promise<GuestIdentity> {
  const response = await fetch(apiUrl('/api/v1/auth/guest'), {
    method: 'POST',
    headers: { Accept: 'application/json' },
  })
  if (!response.ok) {
    throw new Error(`Guest sign-in failed (${response.status})`)
  }

  const body = (await response.json()) as Partial<GuestIdentity>
  if (typeof body.guestId !== 'string' || typeof body.token !== 'string') {
    throw new Error('Guest sign-in returned an invalid body')
  }
  return { guestId: body.guestId, token: body.token }
}

function withToken(init: RequestInit, token: string): RequestInit {
  const headers = new Headers(init.headers)
  headers.set('Authorization', `Bearer ${token}`)
  return { ...init, headers }
}

/**
 * Authenticated fetch. A rejected token is discarded once and reissued, which
 * covers a rotated or purged guest without a retry loop.
 */
export async function authedFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const identity = await ensureGuest()
  const response = await fetch(apiUrl(path), withToken(init, identity.token))
  if (response.status !== 401) return response

  clearGuestIdentity()
  const refreshed = await ensureGuest()
  return fetch(apiUrl(path), withToken(init, refreshed.token))
}
