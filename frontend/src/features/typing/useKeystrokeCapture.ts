import { useCallback, useEffect, useMemo, useRef, useState, useSyncExternalStore } from 'react'
import type { RefObject } from 'react'

import { TelemetryBuffer } from '../telemetry/telemetryBuffer'
import { TypingEngine } from './typingEngine'
import type { KeystrokeEvent, TypingSnapshot } from './typingEngine'

export type CaptureNotice = 'none' | 'paste-blocked'

export interface UseKeystrokeCaptureOptions {
  /** A new id per drill. Session identity belongs to the caller. */
  sessionId: string
  text: string
  apiBaseUrl?: string
  allowBackspace?: boolean
  onComplete?: (snapshot: TypingSnapshot) => void
}

export interface KeystrokeCapture {
  containerRef: RefObject<HTMLDivElement | null>
  snapshot: TypingSnapshot
  notice: CaptureNotice
  pause: () => void
  resume: () => void
  flush: () => Promise<void>
}

const NOTICE_TIMEOUT_MS = 2_500

function isEditableTarget(target: EventTarget | null): boolean {
  if (!(target instanceof HTMLElement)) return false
  return Boolean(target.closest('input, textarea, select, [contenteditable="true"]'))
}

/**
 * High-performance keystroke capture.
 *
 * The engine is an external store: per-key work mutates it and schedules one
 * subscriber wakeup per animation frame. React therefore never renders per
 * keypress, even at 200 WPM. Every event is handed to the IndexedDB outbox
 * before upload.
 */
export function useKeystrokeCapture(options: UseKeystrokeCaptureOptions): KeystrokeCapture {
  const { sessionId, text, apiBaseUrl, allowBackspace = true, onComplete } = options

  // A new drill or session gets a new engine, sequence space, and clock.
  const engine = useMemo(
    () => new TypingEngine(text, { allowBackspace, sessionId }),
    [text, allowBackspace, sessionId],
  )

  const subscribe = useCallback(
    (listener: () => void) => engine.subscribe(listener),
    [engine],
  )
  const getSnapshot = useCallback(() => engine.getSnapshot(), [engine])
  const snapshot = useSyncExternalStore(subscribe, getSnapshot)

  const [notice, setNotice] = useState<CaptureNotice>('none')

  const containerRef = useRef<HTMLDivElement | null>(null)
  const bufferRef = useRef<TelemetryBuffer | null>(null)
  const noticeTimerRef = useRef<number | null>(null)
  const onCompleteRef = useRef(onComplete)

  useEffect(() => {
    onCompleteRef.current = onComplete
  }, [onComplete])

  const pause = useCallback(() => {
    if (engine.pause()) {
      void bufferRef.current?.flush()
    }
  }, [engine])

  const resume = useCallback(() => {
    engine.resume()
  }, [engine])

  const flush = useCallback(() => {
    return bufferRef.current?.flush() ?? Promise.resolve()
  }, [])

  useEffect(() => {
    const buffer = new TelemetryBuffer(sessionId, apiBaseUrl)
    bufferRef.current = buffer
    buffer.start()
    return () => {
      buffer.stop()
      bufferRef.current = null
    }
  }, [sessionId, apiBaseUrl])

  const showNotice = useCallback((next: CaptureNotice) => {
    setNotice(next)
    if (noticeTimerRef.current !== null) window.clearTimeout(noticeTimerRef.current)
    noticeTimerRef.current = window.setTimeout(() => {
      noticeTimerRef.current = null
      setNotice('none')
    }, NOTICE_TIMEOUT_MS)
  }, [])

  useEffect(() => {
    const record = (event: KeystrokeEvent | null) => {
      if (!event) return
      bufferRef.current?.push([event])

      if (engine.isComplete) {
        void bufferRef.current?.flush()
        onCompleteRef.current?.(engine.snapshot())
      }
    }

    const handleKeyDown = (event: KeyboardEvent) => {
      if (event.defaultPrevented || event.ctrlKey || event.metaKey || event.altKey) return
      if (event.repeat || event.isComposing) return
      if (isEditableTarget(event.target)) return
      if (event.key === 'Dead' || event.key === 'Process' || event.key === 'Unidentified') return
      if (engine.isComplete) return

      if (event.key === 'Escape') {
        pause()
        return
      }

      // Coming back from a pause: this key resumes and counts as warmup.
      if (engine.isPaused) {
        engine.resume()
      }

      if (event.key === 'Backspace') {
        event.preventDefault()
        record(engine.press('Backspace'))
        return
      }

      if (event.key.length !== 1) return
      event.preventDefault()
      record(engine.press(event.key))
    }

    const handleBlur = () => pause()

    const handleVisibility = () => {
      if (document.hidden) pause()
    }

    const handlePaste = (event: ClipboardEvent) => {
      if (isEditableTarget(event.target)) return
      event.preventDefault()
      showNotice('paste-blocked')
    }

    const handlePageHide = () => {
      pause()
      void bufferRef.current?.flush()
    }

    const handleOnline = () => {
      void flush()
    }

    window.addEventListener('keydown', handleKeyDown)
    window.addEventListener('blur', handleBlur)
    window.addEventListener('paste', handlePaste)
    window.addEventListener('pagehide', handlePageHide)
    window.addEventListener('online', handleOnline)
    document.addEventListener('visibilitychange', handleVisibility)

    return () => {
      window.removeEventListener('keydown', handleKeyDown)
      window.removeEventListener('blur', handleBlur)
      window.removeEventListener('paste', handlePaste)
      window.removeEventListener('pagehide', handlePageHide)
      window.removeEventListener('online', handleOnline)
      document.removeEventListener('visibilitychange', handleVisibility)
      if (noticeTimerRef.current !== null) {
        window.clearTimeout(noticeTimerRef.current)
        noticeTimerRef.current = null
      }
    }
  }, [engine, flush, pause, showNotice])

  useEffect(() => {
    const element = containerRef.current
    if (!element) return

    const handlePointerDown = () => {
      element.focus({ preventScroll: true })
      resume()
    }

    element.addEventListener('pointerdown', handlePointerDown)
    return () => element.removeEventListener('pointerdown', handlePointerDown)
  }, [resume])

  return { containerRef, snapshot, notice, pause, resume, flush }
}
