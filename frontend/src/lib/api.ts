/**
 * API origin. Empty in development because Vite proxies /api to the backend.
 */
export const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? '').replace(/\/+$/, '')

export function apiUrl(path: string, base = API_BASE): string {
  return `${base}${path}`
}
