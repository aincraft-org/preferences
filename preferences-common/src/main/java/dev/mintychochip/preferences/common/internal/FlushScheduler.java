package dev.mintychochip.preferences.common.internal;

/**
 * Scheduler abstraction so {@link DebouncedFlusher} is unit-testable without Bukkit.
 *
 * <p>Production wiring schedules flush tasks on the server main thread after a tick delay;
 * tests substitute a manual scheduler that fires runnables on demand.
 */
public interface FlushScheduler {
    /** Handle returned from {@link #schedule(Runnable)}; cancelling suppresses a not-yet-run task. */
    interface Cancellable { void cancel(); }

    /**
     * Schedules {@code task} to run after the configured debounce window.
     *
     * @param task flush runnable (typically invokes {@link DebouncedFlusher} timer body)
     * @return cancellable handle for the scheduled task
     */
    Cancellable schedule(Runnable task);
}
