package dev.jlo.preferences.internal;

/** Scheduler abstraction so the flusher is unit-testable without Bukkit. */
public interface FlushScheduler {
    interface Cancellable { void cancel(); }
    Cancellable schedule(Runnable task);
}
