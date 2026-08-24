package dev.mintychochip.preferences.common.internal;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Trailing-window debouncer: dirty marks coalesce; flush runs async; disable flushes synchronously.
 * Snapshots are always taken on the main thread (in {@link #persist(String)} and
 * {@link #writeSync(String)}); async threads only serialize the immutable copy. Async writes
 * for one namespace are chained onto the previous write so they can never overlap.
 */
public class DebouncedFlusher {

    private static final long DEFAULT_JOIN_TIMEOUT_SECONDS = 10;
    private static final Logger LOG = Logger.getLogger("Preferences");

    private final YamlValueStore store;
    private final FlushScheduler scheduler;
    private final long delayTicks;
    private final Executor async;
    private final long joinTimeoutSeconds;
    private final Set<String> dirty = ConcurrentHashMap.newKeySet();
    private final Set<String> scheduled = ConcurrentHashMap.newKeySet();
    private final Map<String, FlushScheduler.Cancellable> timers = new ConcurrentHashMap<>();
    private final Map<String, CompletableFuture<Void>> inFlight = new ConcurrentHashMap<>();
    private volatile boolean shutdown;

    /**
     * Creates a debounced flusher with the default in-flight join timeout.
     *
     * @param store backing YAML store
     * @param scheduler debounce timer scheduler
     * @param delayTicks trailing debounce window in server ticks
     * @param async executor for snapshot serialization and file I/O (off main thread)
     */
    public DebouncedFlusher(YamlValueStore store, FlushScheduler scheduler, long delayTicks, Executor async) {
        this(store, scheduler, delayTicks, async, DEFAULT_JOIN_TIMEOUT_SECONDS);
    }

    /**
     * Package-visible for tests.
     *
     * @param store backing YAML store
     * @param scheduler debounce timer scheduler
     * @param delayTicks trailing debounce window in server ticks
     * @param async executor for snapshot serialization and file I/O
     * @param joinTimeoutSeconds max wait before abandoning a hung in-flight async write
     */
    DebouncedFlusher(YamlValueStore store, FlushScheduler scheduler, long delayTicks, Executor async,
                     long joinTimeoutSeconds) {
        this.store = store;
        this.scheduler = scheduler;
        this.delayTicks = delayTicks;
        this.async = async;
        this.joinTimeoutSeconds = joinTimeoutSeconds;
    }

    /**
     * Marks a namespace dirty and schedules a trailing debounce flush if none is pending.
     *
     * <p>Expected on the server main thread. After {@link #shutdown()}, further marks are ignored
     * so disable drains are not re-scheduled.
     *
     * @param ns plugin namespace
     */
    public synchronized void markDirty(String ns) {
        if (shutdown) return;
        dirty.add(ns);
        if (scheduled.add(ns)) {
            FlushScheduler.Cancellable timer = scheduler.schedule(() -> runScheduled(ns));
            timers.put(ns, timer);
        }
    }

    /**
     * Timer body; synchronized so the disable drains can never race a persist submission
     * (a runnable either runs fully before the drain or is a no-op after it).
     */
    private synchronized void runScheduled(String ns) {
        scheduled.remove(ns);
        timers.remove(ns);
        if (dirty.remove(ns)) persist(ns);
    }

    /**
     * Synchronously flushes exactly one namespace (plugin disable hook).
     *
     * <p>Cancels any pending debounce timer, joins or abandons in-flight async writes, then
     * writes the latest in-memory state on the calling thread. Writes unconditionally: after
     * join/abandon the latest in-memory state is authoritative.
     *
     * @param ns plugin namespace
     */
    public synchronized void flushNamespaceSync(String ns) {
        scheduled.remove(ns);
        cancelTimer(ns);
        awaitInFlight(ns);
        dirty.remove(ns);
        writeSync(ns);
    }

    /**
     * Final flush for plugin disable: sets the shutdown flag, cancels timers, and drains in-flight
     * writes and dirty namespaces until quiescent.
     *
     * <p>Blocks the caller until every namespace has been written synchronously. Idempotent with
     * {@link #shutdown()}.
     */
    public synchronized void flushAllSync() {
        shutdown = true;
        drainAll();
    }

    /**
     * Shuts down the flusher: sets the shutdown flag and performs the same synchronous drain as
     * {@link #flushAllSync()}.
     *
     * <p>After this returns, {@link #markDirty(String)} is a no-op.
     */
    public synchronized void shutdown() {
        shutdown = true;
        drainAll();
    }

    /** Cancel timers, join in-flight writes, write everything still dirty — repeat until quiescent. */
    private void drainAll() {
        for (String ns : Set.copyOf(scheduled)) cancelTimer(ns);
        while (!dirty.isEmpty() || !inFlight.isEmpty()) {
            for (String ns : Set.copyOf(inFlight.keySet())) awaitInFlight(ns);
            for (String ns : Set.copyOf(dirty)) {
                dirty.remove(ns);
                writeSync(ns);
            }
        }
    }

    private void cancelTimer(String ns) {
        scheduled.remove(ns);
        FlushScheduler.Cancellable timer = timers.remove(ns);
        if (timer != null) timer.cancel();
    }

    /**
     * Bounded join for an in-flight async write of this namespace. If the write does not
     * complete in time it is abandoned (cancelled and removed from {@link #inFlight}) and the
     * namespace is re-dirtied directly — NOT via {@link #markDirty}, which shutdown suppresses —
     * so the caller's synchronous path rewrites it. Termination: every await either completes
     * or abandons, so {@link #drainAll()} can never spin on a hung write.
     */
    private void awaitInFlight(String ns) {
        CompletableFuture<Void> f = inFlight.get(ns);
        if (f == null) return;
        try {
            f.get(joinTimeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            abandon(ns, f);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            abandon(ns, f);
        } finally {
            f.cancel(true);
            inFlight.remove(ns, f); // no-op if whenComplete already removed it on completion
        }
    }

    private void abandon(String ns, CompletableFuture<Void> f) {
        LOG.log(Level.SEVERE, "abandoning in-flight write for " + ns);
        dirty.add(ns);
    }

    /**
     * Debounced write path. Runs on the main thread when the scheduler fires: the snapshot is
     * taken HERE (main thread, no tearing) and the immutable copy is handed to the async executor.
     * The write is chained onto the namespace's previous in-flight write, so two debounced writes
     * for the same namespace can never overlap (different namespaces may write in parallel).
     * Overridable seam for tests.
     *
     * @param ns plugin namespace
     */
    protected void persist(String ns) {
        YamlValueStore.Snapshot snap = store.snapshot(ns);
        long token = store.nextEpoch();
        CompletableFuture<Void> future = inFlight.compute(ns, (k, prev) -> {
            Runnable write = () -> store.writeSnapshot(ns, snap, token);
            return prev == null
                ? CompletableFuture.runAsync(write, async)
                : prev.thenRunAsync(write, async);
        });
        future.whenComplete((v, t) -> inFlight.remove(ns, future));
    }

    /**
     * Single seam for the synchronous write step (disable paths): joins any in-flight async
     * write for this namespace, then writes synchronously. Overridable seam for tests.
     *
     * @param ns plugin namespace
     */
    protected void writeSync(String ns) {
        awaitInFlight(ns);
        store.write(ns);
    }

    /** Test-only visibility into pending async writes. */
    int inFlightCount() {
        return inFlight.size();
    }
}
