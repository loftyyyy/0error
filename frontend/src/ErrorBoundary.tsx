import { Component } from 'react'
import type { ErrorInfo, ReactNode } from 'react'

interface ErrorBoundaryProps {
  children: ReactNode
  /** Overridable so the fallback can be tested without a real navigation. */
  onReload?: () => void
}

interface ErrorBoundaryState {
  failed: boolean
}

/**
 * Last resort for a render crash. One calm line and one reload button, with the
 * same palette as the rest of the app. Keystrokes already queued stay in the
 * browser; this component never clears storage.
 */
export class ErrorBoundary extends Component<ErrorBoundaryProps, ErrorBoundaryState> {
  state: ErrorBoundaryState = { failed: false }

  static getDerivedStateFromError(): ErrorBoundaryState {
    return { failed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    console.error('0Error render failed', error, info.componentStack)
  }

  private reload = () => {
    if (this.props.onReload) {
      this.props.onReload()
      return
    }
    window.location.reload()
  }

  render() {
    if (!this.state.failed) return this.props.children

    return (
      <main className="flex min-h-screen items-center justify-center bg-stone-50 px-6 py-16 text-stone-900 dark:bg-stone-950 dark:text-stone-100">
        <div className="w-full max-w-md rounded-2xl border border-stone-200 px-6 py-8 dark:border-stone-800">
          <h1 className="text-lg font-medium">Something went wrong.</h1>
          <p className="mt-2 text-sm text-stone-500 dark:text-stone-400">
            Your progress is safe. Reload to pick up the open drill.
          </p>
          <button
            type="button"
            onClick={this.reload}
            className="mt-6 rounded-full border border-stone-200 px-3 py-1.5 text-sm text-stone-600 transition hover:border-stone-300 hover:text-stone-900 dark:border-stone-800 dark:text-stone-300 dark:hover:border-stone-700 dark:hover:text-stone-50"
          >
            Reload 0Error
          </button>
        </div>
      </main>
    )
  }
}
