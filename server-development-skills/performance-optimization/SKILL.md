---
name: performance-optimization
description: Use when diagnosing server lag or tick loss, profiling a Paper server or plugin, optimizing main-thread or async chunk-loading work, fixing listener leaks, tuning Paper configuration, or when a claimed performance optimization needs A/B proof with before-and-after graphs.
---

# Performance Optimization (Paper Server)

Diagnose and fix Paper server lag with profiling, main-thread discipline, safe async patterns, and A/B proof.

Core principle: **keep the old code path, build the new one, run real load through both, and only call it an optimization when A/B graphs prove it. The main thread is the bottleneck — never make it wait.**

Pins verified 2026-08-21 against PaperMC docs and the Spark project.

## A/B test before you ship

A change is not a performance optimization until an A/B test with graphs either affirms or disproves it.

1. Keep the old path as the control.
2. Build the candidate alongside it, behind a toggle or config flag.
3. Pick one primary metric and guardrails. Primary metric: MSPT for tick lag, TPS when tick rate is the target metric, latency if the change targets a request path. Guardrails: same-metric p95, flame graph, CPU, and memory must not regress.
4. Use a repeatable harness: same world, player count, tick, and seeded load script. Let both paths warm up, then measure.
5. Run identical peak load through both for multiple trials. Empty servers and micro-benchmarks do not count.
6. Capture distributions, not single numbers: primary metric, guardrails, and hot-path samples. Render comparison graphs on shared axes.
7. Apply the pass/disprove rule: the primary metric must improve and all guardrails must hold. Otherwise reject it and keep the old path.

### A/B report checklist

Record this for every test:

- Workload / seed: exact script, player count, world, and seed.
- Warmup: duration discarded before measuring.
- Trials: count and order (e.g., interleaved old/new, randomized).
- Primary metric: MSPT for tick lag, TPS when tick rate is the target metric, latency if the change targets a request path.
- Distributions: old vs new median / p95 / p5 for the primary metric and guardrails.
- Graphs: shared-axis comparison plots.
- Verdict: `PASS`, `NO IMPROVEMENT`, or `REGRESSION`.

### Pass / disprove rule

- `PASS`: the primary metric improves and all guardrails hold.
- `NO IMPROVEMENT`: the primary metric is within noise or the gain is not clear. Reject and keep the old path.
- `REGRESSION`: the primary metric is worse or any guardrail fails. Reject immediately and keep the old path.

Guardrails:

- Same-metric p95 does not regress.
- No new hot path in the flame graph.
- CPU and memory do not increase meaningfully.

A result that is not a clear `PASS` is a rejection. The test disproves the claim as often as it affirms it.

A code trace, a smaller flame-graph bar, or "it looks faster" is not proof. Data flowing through both paths is.

## Profiling with Spark

Spark is the standard profiler and is bundled with modern Paper. Profile during peak load, not on an empty server, for 60–180 seconds:

```text
/spark health          # quick TPS, MSPT, memory snapshot
/spark profiler start  # begin sampling
/spark profiler stop   # end sampling, prints a shareable URL
```

Read the flame graph: the widest bars at the top of the call stack are the methods consuming the most CPU. If the main-thread stack is dominated by scheduler execution, batch the work; if it is chunk generation/loading, reduce request rate and avoid urgent loads; if it is event dispatch or listener collections, inspect listener registration and unregistering.

A Spark trace or flame graph of the new code alone is insufficient. It is one side of the A/B graph and cannot prove an optimization.

## Main-thread discipline

Time-consuming work — database queries, file I/O, complex calculations — must run off the main thread. Use the Paper async scheduler:

```java
Bukkit.getAsyncScheduler().runNow(plugin, task -> {
    // heavy work
});
```

Bring results back to the main thread only when a Bukkit API call requires it, and never call Bukkit APIs from an async task:

```java
Bukkit.getAsyncScheduler().runNow(plugin, task -> {
    String result = expensiveWork();
    Bukkit.getScheduler().runTask(plugin, () -> {
        // main-thread work with result
    });
});
```

## Async chunk loading

Load chunks asynchronously and never retain live `Chunk` objects. Deduplicate in-flight requests so the same chunk is not loaded repeatedly:

```java
private final ConcurrentHashMap<ChunkKey, CompletableFuture<Chunk>> inFlight =
        new ConcurrentHashMap<>();

private record ChunkKey(UUID worldId, int x, int z) {}

public CompletableFuture<Chunk> loadChunk(World world, int x, int z) {
    ChunkKey key = new ChunkKey(world.getUID(), x, z);
    return inFlight.computeIfAbsent(key, ignored -> {
        CompletableFuture<Chunk> future = world.getChunkAtAsync(x, z, true, false);
        future.whenComplete((chunk, error) -> inFlight.remove(key, future));
        return future;
    });
}
```

Process only a bounded number of completed chunks per tick to avoid a completion burst turning into one massive tick:

```java
private final Queue<Chunk> completedChunks = new ConcurrentLinkedQueue<>();

public void startProcessor() {
    Bukkit.getScheduler().runTaskTimer(plugin, task -> {
        int budget = 2; // tune experimentally
        while (budget-- > 0) {
            Chunk chunk = completedChunks.poll();
            if (chunk == null) break;
            processChunk(chunk);
        }
    }, 1L, 1L);
}
```

Do not use per-request `ChunkLoadEvent` listeners — they leak unless unregistered on every path. Prefer the returned future, or one global listener routing through a map. Add timeouts and clear all maps/queues on plugin disable and world unload.

## Listener and cache hygiene

- Unregister listeners on plugin disable; never register a listener per request.
- Do not store live `Chunk` or `Player` objects in long-lived caches; store immutable summaries instead.
- If you must keep a chunk loaded, use a deliberate plugin chunk ticket and remove it later.
- Clear caches, in-flight maps, and queues on disable and world unload.

## Paper configuration

- Leave `async-chunks` at `-1` (auto-detect) unless there is a specific reason to change it.
- Use `alternate-current` for redstone and adjust entity/mob spawn limits only when entity-related lag is confirmed.
- Run the recommended Java version for the server (e.g. 25 for modern versions) to benefit from GC and performance improvements.

## Verify

```bash
./gradlew build spotlessCheck
./gradlew runServer
```

Load the server with players or a load generator and run the A/B test: keep the old path as the control, enable the new path as the candidate, pick a primary metric and guardrails, run identical seeded peak load through both for multiple trials, and capture TPS, MSPT, CPU, memory, and hot-path graphs. Fill out the A/B report checklist and apply the pass/disprove rule. Only switch the default if the verdict is `PASS`. Confirm `inFlight` and `completedChunks` counts stay bounded under sustained load.

## Common mistakes

| Wrong | Right | Why |
|---|---|---|
| Optimizing without profiling | Spark profile first | Guessing wastes effort and misses the real bottleneck |
| Blocking the main thread | Async scheduler + sync hop | Main-thread blocking drops TPS |
| Calling Bukkit APIs from async tasks | Hop to main thread first | Unsafe cross-thread access corrupts world state |
| One chunk load per request, no dedup | Deduplicating in-flight map | Duplicate loads and callback bursts spike the tick |
| Per-request `ChunkLoadEvent` listener | Future-based load or one global listener | Listeners leak unless unregistered on every path |
| Storing live `Chunk`/`Player` in caches | Store immutable summaries | Retains chunks/entities and leaks memory |
| Processing all completed chunks in one tick | Bounded per-tick budget | A completion burst becomes one massive tick |
| Ignoring chunk tickets | Remove tickets deliberately | Accidental tickets keep chunks loaded forever |
| Profiling an empty server | Profile during peak load | Empty-server data misses real bottlenecks |
| Claiming an optimization without A/B data | Keep old path, build new path, test, graph | Code traces and cleaner logic do not prove speedup |
| Replacing the old path before measuring | Keep old path until data says ship | Without a fallback, a bad change becomes the only option |
| A Spark trace or flame graph of only the new code | Profile both under identical load | One-sided measurements miss regressions |
