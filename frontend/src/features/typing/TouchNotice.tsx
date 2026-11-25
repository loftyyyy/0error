/**
 * Shown instead of a drill on touch-only devices, where key events never fire.
 */
export function TouchNotice() {
  return (
    <div className="rounded-2xl border border-stone-200 px-6 py-8 dark:border-stone-800">
      <h1 className="text-lg font-medium">A physical keyboard is needed.</h1>
      <p className="mt-2 max-w-md text-sm text-stone-500 dark:text-stone-400">
        Open 0Error on a laptop or desktop to type this drill. Your progress is
        saved by this browser and will be here when you return.
      </p>
    </div>
  )
}
