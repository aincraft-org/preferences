package dev.mintychochip.preferences.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DebouncedFlusherTest {

    static final class ManualScheduler implements FlushScheduler {
        final List<Runnable> pending = new ArrayList<>();
        @Override public Cancellable schedule(Runnable r) { pending.add(r); return () -> pending.remove(r); }
        void fireAll() { for (Runnable r : List.copyOf(pending)) r.run(); pending.clear(); }
    }

    @Test void coalescesMarksIntoOneFlush(@TempDir Path dir) {
        YamlValueStore store = new YamlValueStore(dir);
        ManualScheduler scheduler = new ManualScheduler();
        List<String> flushed = new ArrayList<>();
        DebouncedFlusher flusher = new DebouncedFlusher(store, scheduler, 100, Runnable::run) {
            @Override protected void persist(String ns) { flushed.add(ns); }
        };

        flusher.markDirty("demo");
        flusher.markDirty("demo"); // second mark within window: no new schedule
        assertEquals(1, scheduler.pending.size());

        scheduler.fireAll();
        assertEquals(List.of("demo"), flushed);
    }

    @Test void flushAllSyncWritesEverythingAndCancelsTimers(@TempDir Path dir) {
        YamlValueStore store = new YamlValueStore(dir);
        ManualScheduler scheduler = new ManualScheduler();
        List<String> flushed = new ArrayList<>();
        DebouncedFlusher flusher = new DebouncedFlusher(store, scheduler, 100, Runnable::run) {
            @Override protected void writeSync(String ns) { flushed.add(ns); }
        };
        flusher.markDirty("a");
        flusher.markDirty("b");
        flusher.flushAllSync();
        assertEquals(List.of("a", "b"), flushed.stream().sorted().toList());
        assertTrue(scheduler.pending.isEmpty(), "timers cancelled");
    }

    @Test void flushAllSyncAwaitsInFlightWrite(@TempDir Path dir) throws Exception {
        YamlValueStore store = new YamlValueStore(dir);
        store.setGlobal("demo", "k", "v");
        ManualScheduler scheduler = new ManualScheduler();
        CountDownLatch writeStarted = new CountDownLatch(1);
        CountDownLatch releaseWrite = new CountDownLatch(1);
        Executor gating = task -> {
            Thread t = new Thread(() -> {
                writeStarted.countDown();
                try {
                    releaseWrite.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                task.run();
            });
            t.setDaemon(true);
            t.start();
        };
        DebouncedFlusher flusher = new DebouncedFlusher(store, scheduler, 100, gating);

        flusher.markDirty("demo");
        scheduler.fireAll(); // timer fires: dirty consumed, async write is gated in flight
        assertTrue(writeStarted.await(5, TimeUnit.SECONDS));

        Thread flusherThread = new Thread(flusher::flushAllSync);
        flusherThread.start();
        Thread.sleep(200);
        assertTrue(flusherThread.isAlive(), "flushAllSync must block until the in-flight write completes");
        releaseWrite.countDown();
        flusherThread.join(5_000);
        assertFalse(flusherThread.isAlive(), "flushAllSync must return after the in-flight write completes");

        YamlValueStore reloaded = new YamlValueStore(dir);
        reloaded.load("demo");
        assertEquals("v", reloaded.getGlobal("demo", "k"), "write must be on disk before flushAllSync returns");
    }

    @Test void flushNamespaceSyncAwaitsInFlightWrite(@TempDir Path dir) throws Exception {
        YamlValueStore store = new YamlValueStore(dir);
        store.setGlobal("demo", "k", "v");
        ManualScheduler scheduler = new ManualScheduler();
        CountDownLatch writeStarted = new CountDownLatch(1);
        CountDownLatch releaseWrite = new CountDownLatch(1);
        Executor gating = task -> {
            Thread t = new Thread(() -> {
                writeStarted.countDown();
                try {
                    releaseWrite.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                task.run();
            });
            t.setDaemon(true);
            t.start();
        };
        DebouncedFlusher flusher = new DebouncedFlusher(store, scheduler, 100, gating);

        flusher.markDirty("demo");
        scheduler.fireAll(); // timer fired already: dirty is clear, async write is in flight
        assertTrue(writeStarted.await(5, TimeUnit.SECONDS));

        Thread flusherThread = new Thread(() -> flusher.flushNamespaceSync("demo"));
        flusherThread.start();
        Thread.sleep(200);
        assertTrue(flusherThread.isAlive(), "flushNamespaceSync must await the in-flight write even when dirty is empty");
        releaseWrite.countDown();
        flusherThread.join(5_000);
        assertFalse(flusherThread.isAlive(), "flushNamespaceSync must return after the in-flight write completes");

        YamlValueStore reloaded = new YamlValueStore(dir);
        reloaded.load("demo");
        assertEquals("v", reloaded.getGlobal("demo", "k"), "write must be on disk before flushNamespaceSync returns");
    }

    @Test void sameNamespaceWritesSerialize(@TempDir Path dir) throws Exception {
        YamlValueStore store = new YamlValueStore(dir);
        ManualScheduler scheduler = new ManualScheduler();
        List<String> order = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger started = new AtomicInteger();
        CountDownLatch firstStarted = new CountDownLatch(1);
        CountDownLatch releaseFirst = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(2);
        Executor executor = task -> {
            Thread t = new Thread(() -> {
                int n = started.incrementAndGet();
                order.add("start" + n);
                if (n == 1) {
                    firstStarted.countDown();
                    try {
                        releaseFirst.await();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }
                task.run();
                order.add("finish" + n);
                completed.countDown();
            });
            t.setDaemon(true);
            t.start();
        };
        DebouncedFlusher flusher = new DebouncedFlusher(store, scheduler, 100, executor);

        flusher.markDirty("demo");
        scheduler.fireAll(); // debounced cycle 1: write task starts and is held
        assertTrue(firstStarted.await(5, TimeUnit.SECONDS));

        flusher.markDirty("demo");
        scheduler.fireAll(); // debounced cycle 2: write must chain onto cycle 1's write
        Thread.sleep(300);   // window for a buggy implementation to start a concurrent write
        assertEquals(1, started.get(), "second write for the same namespace must not start before the first finishes");

        releaseFirst.countDown();
        assertTrue(completed.await(5, TimeUnit.SECONDS), "both writes must complete");
        assertEquals(2, started.get());
        assertTrue(order.containsAll(List.of("start1", "finish1", "start2", "finish2")));
    }

    @Test void shutdownDrainsPendingWrites(@TempDir Path dir) {
        YamlValueStore store = new YamlValueStore(dir);
        ManualScheduler scheduler = new ManualScheduler();
        List<String> flushed = new ArrayList<>();
        DebouncedFlusher flusher = new DebouncedFlusher(store, scheduler, 100, Runnable::run) {
            @Override protected void writeSync(String ns) { flushed.add(ns); store.write(ns); }
        };
        flusher.markDirty("a");
        flusher.markDirty("b");
        flusher.shutdown();
        assertEquals(List.of("a", "b"), flushed.stream().sorted().toList(), "shutdown must drain pending writes");
        assertTrue(scheduler.pending.isEmpty(), "timers cancelled");
    }

    @Test void drainAbandonsHungWriteAndRewritesSync(@TempDir Path dir) throws Exception {
        YamlValueStore store = new YamlValueStore(dir);
        store.setGlobal("demo", "k", "old");
        ManualScheduler scheduler = new ManualScheduler();
        CountDownLatch slowWriteStarted = new CountDownLatch(1);
        CountDownLatch releaseSlowWrite = new CountDownLatch(1);
        CountDownLatch rewriteStarted = new CountDownLatch(1);
        store.onWriteStart = () -> {
            slowWriteStarted.countDown();
            try {
                releaseSlowWrite.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        Executor asyncThreads = task -> {
            Thread t = new Thread(task);
            t.setDaemon(true);
            t.start();
        };
        List<String> flushed = new ArrayList<>();
        DebouncedFlusher flusher = new DebouncedFlusher(store, scheduler, 100, asyncThreads, 1) {
            @Override protected void writeSync(String ns) {
                flushed.add(ns);
                rewriteStarted.countDown();
                store.write(ns);
            }
        };

        // Old writer: stale snapshot, slow — holds the per-namespace write lock.
        YamlValueStore.Snapshot stale = store.snapshot("demo"); // k = "old"
        Thread staleWriter = new Thread(() -> store.writeSnapshot("demo", stale, 1L));
        staleWriter.setDaemon(true);
        staleWriter.start();
        assertTrue(slowWriteStarted.await(5, TimeUnit.SECONDS), "old writer must hold the write lock");

        // New value + debounced cycle: the async write submits and queues on the lock.
        store.setGlobal("demo", "k", "new");
        flusher.markDirty("demo");
        scheduler.fireAll();

        Thread flusherThread = new Thread(flusher::flushAllSync);
        flusherThread.start();
        assertTrue(rewriteStarted.await(5, TimeUnit.SECONDS), "sync rewrite must run after the join timeout");
        Thread.sleep(200);
        assertTrue(flusherThread.isAlive(), "flushAllSync must wait for the slow writer to release the lock");
        releaseSlowWrite.countDown();
        flusherThread.join(5_000);
        assertFalse(flusherThread.isAlive(), "flushAllSync must return once the rewrite completes");
        assertEquals(List.of("demo"), flushed, "hung write must be abandoned and rewritten synchronously");
        assertEquals(0, flusher.inFlightCount(), "hung write must be removed from in-flight tracking");

        YamlValueStore reloaded = new YamlValueStore(dir);
        reloaded.load("demo");
        assertEquals("new", reloaded.getGlobal("demo", "k"),
            "late-finishing stale writer must not clobber the synchronous rewrite");
        staleWriter.join(5_000);
    }
}
