/**
 * Typing drills need a real keyboard. A coarse pointer with no fine pointer is
 * the practical signature of a phone or tablet, where no key events exist.
 */
export function isTouchOnly(): boolean {
  if (typeof window === 'undefined' || typeof window.matchMedia !== 'function') {
    return false
  }
  return (
    window.matchMedia('(pointer: coarse)').matches &&
    !window.matchMedia('(any-pointer: fine)').matches
  )
}

export function useTouchOnly(): boolean {
  return isTouchOnly()
}
