/** Client-side crash reporting seam. No network call is made here. */

export const CLIENT_ERROR_EVENT = '0error:client-error'

export const APP_VERSION =
  typeof __APP_VERSION__ === 'undefined' ? 'dev' : __APP_VERSION__

export type ClientErrorKind = 'error' | 'rejection' | 'render'

export interface ClientErrorDetail {
  kind: ClientErrorKind
  message: string
}

function messageOf(error: unknown): string {
  if (error instanceof Error && error.message) return error.message
  if (typeof error === 'string' && error.length > 0) return error
  return 'Unknown client error'
}

/**
 * Emits one event on the window. A host, extension, or future reporter can
 * listen without coupling the app to a vendor.
 */
export function reportClientError(
  error: unknown,
  kind: ClientErrorKind = 'error',
): ClientErrorDetail {
  const detail: ClientErrorDetail = { kind, message: messageOf(error) }
  if (typeof window !== 'undefined' && typeof window.dispatchEvent === 'function') {
    window.dispatchEvent(new CustomEvent<ClientErrorDetail>(CLIENT_ERROR_EVENT, { detail }))
  }
  return detail
}

/** Installs the two global hooks. Returns a cleanup for tests and teardown. */
export function installClientErrorReporting(): () => void {
  if (typeof window === 'undefined') return () => undefined

  const onError = (event: ErrorEvent) => {
    reportClientError(event.error ?? event.message, 'error')
  }
  const onRejection = (event: Event) => {
    const reason = (event as Event & { reason?: unknown }).reason
    reportClientError(reason, 'rejection')
  }

  window.addEventListener('error', onError)
  window.addEventListener('unhandledrejection', onRejection)

  return () => {
    window.removeEventListener('error', onError)
    window.removeEventListener('unhandledrejection', onRejection)
  }
}
