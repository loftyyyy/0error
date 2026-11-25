import { useSyncExternalStore } from 'react'

import { getSyncState, subscribeToSync } from './telemetryBuffer'

/**
 * A quiet line under the drill: the keys are safe locally while the queue
 * catches up. It disappears the moment the server has acknowledged everything.
 */
export function SyncLine() {
  const state = useSyncExternalStore(subscribeToSync, getSyncState)

  if (state === 'synced') return null

  return (
    <p
      aria-live="polite"
      className="mt-1 text-xs text-stone-400 dark:text-stone-500"
    >
      {state === 'offline'
        ? 'Offline. Your keys are saved on this device.'
        : 'Keys are saved here. Sending when the server answers.'}
    </p>
  )
}
