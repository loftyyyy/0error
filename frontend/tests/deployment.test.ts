import { readFileSync } from 'node:fs'

import { describe, expect, it } from 'vitest'

import { apiUrl } from '../src/lib/api'

interface VercelConfig {
  framework?: string
  outputDirectory?: string
  rewrites?: { source: string; destination: string }[]
  headers?: { source: string; headers: { key: string; value: string }[] }[]
}

const config = JSON.parse(
  readFileSync(new URL('../vercel.json', import.meta.url), 'utf8'),
) as VercelConfig

function header(source: string, key: string): string | undefined {
  return config.headers
    ?.find((entry) => entry.source === source)
    ?.headers.find((entry) => entry.key === key)?.value
}

describe('production deployment config', () => {
  it('builds Vite and serves the SPA entry for app routes', () => {
    expect(config.framework).toBe('vite')
    expect(config.outputDirectory).toBe('dist')
    expect(
      config.rewrites?.some(
        (rewrite) =>
          rewrite.destination === '/index.html' &&
          rewrite.source.includes('assets') &&
          rewrite.source.includes('?!'),
      ),
    ).toBe(true)
  })

  it('sets the baseline browser hardening headers', () => {
    expect(header('/(.*)', 'X-Content-Type-Options')).toBe('nosniff')
    expect(header('/(.*)', 'X-Frame-Options')).toBe('DENY')
    expect(header('/(.*)', 'Referrer-Policy')).toBe('strict-origin-when-cross-origin')
    expect(header('/(.*)', 'Permissions-Policy')).toContain('camera=()')
  })

  it('keeps hashed assets cacheable for a year', () => {
    expect(header('/assets/(.*)', 'Cache-Control')).toContain('immutable')
  })

  it('joins an absolute API origin without doubling slashes', () => {
    expect(apiUrl('/api/v1/progress', 'https://api.example.com')).toBe(
      'https://api.example.com/api/v1/progress',
    )
  })
})
