package dev.mintychochip.preferences.internal;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;

/**
 * Per-namespace YAML storage using Bukkit's YamlConfiguration (bundled with Paper).
 *
 * <p>Globals stay in a concurrent map (small). Player rows are memory-bounded via Caffeine
 * (read-through from disk) plus a dirty map of recent mutations that must survive until the next
 * successful write. Snapshots merge on-disk players with dirty overlays so eviction never drops
 * persisted rows, and a post-write generation prune keeps the dirty map small.
 *
 * <p>Thread-safe: main thread mutates; async threads only serialize immutable snapshots.
 */
public final class YamlValueStore implements ValueStore {

    /** {@code dirtyThroughGen} is the mutation counter at snapshot time; {@code -1} skips dirty prune. */
    public record Snapshot(
            Map<String, String> global,
            Map<UUID, Map<String, String>> players,
            long dirtyThroughGen) {
        /** Test/helper ctor: no dirty-generation prune on write. */
        public Snapshot(Map<String, String> global, Map<UUID, Map<String, String>> players) {
            this(global, players, -1L);
        }
    }

    private record DirtyEntry(long gen, Map<String, String> values) {}

    static final long DEFAULT_MAX_CACHED_PLAYERS = 10_000L;
    static final Duration DEFAULT_EXPIRE_AFTER_ACCESS = Duration.ofMinutes(30);

    private static final Logger LOG = Logger.getLogger("Preferences");

    private final Path dataDir;
    private final long maxCachedPlayers;
    private final Duration expireAfterAccess;
    private final Map<String, Map<String, String>> globals = new ConcurrentHashMap<>();
    private final Map<String, Cache<UUID, Map<String, String>>> playerCaches = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, DirtyEntry>> dirtyPlayers = new ConcurrentHashMap<>();
    private final Map<String, Set<UUID>> removedPlayers = new ConcurrentHashMap<>();
    private final Map<String, Object> writeLocks = new ConcurrentHashMap<>();
    private final Map<String, Long> epochs = new ConcurrentHashMap<>();
    private final AtomicLong epochCounter = new AtomicLong();
    private final AtomicLong mutationCounter = new AtomicLong();

    /** Test hook: invoked inside the per-namespace write lock, before writing. */
    volatile Runnable onWriteStart;

    public YamlValueStore(Path dataDir) {
        this(dataDir, DEFAULT_MAX_CACHED_PLAYERS, DEFAULT_EXPIRE_AFTER_ACCESS);
    }

    /** Package-visible for tests (tiny caches / short TTLs). */
    YamlValueStore(Path dataDir, long maxCachedPlayers, Duration expireAfterAccess) {
        this.dataDir = dataDir;
        this.maxCachedPlayers = Math.max(1, maxCachedPlayers);
        this.expireAfterAccess = expireAfterAccess;
    }

    /** Next token in the single shared write sequence (both sync and async paths). */
    public long nextEpoch() {
        return epochCounter.incrementAndGet();
    }

    public void load(String ns) {
        globals.remove(ns);
        dirtyPlayers.remove(ns);
        removedPlayers.remove(ns);
        playerCaches.remove(ns);
        Path file = dataDir.resolve(ns + ".yml");
        if (!Files.exists(file)) return;
        YamlConfiguration config = readYamlFile(file);
        if (config == null) return;
        ConfigurationSection g = config.getConfigurationSection("global");
        if (g != null) {
            Map<String, String> target = globals.computeIfAbsent(ns, k -> new ConcurrentHashMap<>());
            for (String key : g.getKeys(false)) {
                String value = g.getString(key);
                if (value != null) target.put(key, value);
            }
        }
        // Player rows stay on disk and load lazily into the Caffeine cache.
    }

    public String getGlobal(String ns, String name) {
        Map<String, String> m = globals.get(ns);
        return m == null ? null : m.get(name);
    }

    public @Nullable String getPlayer(String ns, UUID uuid, String name) {
        Map<String, String> m = playerValues(ns, uuid);
        return m == null ? null : m.get(name);
    }

    public void setGlobal(String ns, String name, String value) {
        globals.computeIfAbsent(ns, k -> new ConcurrentHashMap<>()).put(name, value);
    }

    public void setPlayer(String ns, UUID uuid, String name, String value) {
        Set<UUID> removed = removedPlayers.get(ns);
        if (removed != null) removed.remove(uuid);

        long gen = mutationCounter.incrementAndGet();
        Map<UUID, DirtyEntry> dirty = dirtyPlayers.computeIfAbsent(ns, k -> new ConcurrentHashMap<>());
        dirty.compute(uuid, (u, prev) -> {
            Map<String, String> values;
            if (prev != null) {
                values = prev.values();
            } else {
                Map<String, String> seed = cleanCachedOrDisk(ns, uuid);
                values = seed == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(seed);
            }
            values.put(name, value);
            return new DirtyEntry(gen, values);
        });
        cacheFor(ns).invalidate(uuid);
    }

    /**
     * Permanently drops a player's stored prefs for this namespace (next write removes from file).
     */
    public void removePlayerData(String ns, UUID uuid) {
        Map<UUID, DirtyEntry> dirty = dirtyPlayers.get(ns);
        if (dirty != null) dirty.remove(uuid);
        cacheFor(ns).invalidate(uuid);
        removedPlayers.computeIfAbsent(ns, k -> ConcurrentHashMap.newKeySet()).add(uuid);
        mutationCounter.incrementAndGet();
    }

    /**
     * Drops a player from the hot cache only. Dirty mutations and on-disk rows are preserved.
     * Safe to call on quit for memory pressure relief.
     */
    public void evictPlayer(String ns, UUID uuid) {
        Map<UUID, DirtyEntry> dirty = dirtyPlayers.get(ns);
        if (dirty != null && dirty.containsKey(uuid)) return; // keep until flushed
        cacheFor(ns).invalidate(uuid);
    }

    /** Evict a player from every namespace cache (quit path). */
    public void evictPlayerAllNamespaces(UUID uuid) {
        for (String ns : playerCaches.keySet()) {
            evictPlayer(ns, uuid);
        }
        // Also check dirty-only namespaces not yet in playerCaches
        for (String ns : dirtyPlayers.keySet()) {
            evictPlayer(ns, uuid);
        }
    }

    /** Immutable copy for async serialization. Called on the main thread. */
    public Snapshot snapshot(String ns) {
        Map<String, String> g = new LinkedHashMap<>();
        Map<String, String> gm = globals.get(ns);
        if (gm != null) g.putAll(gm);

        long gen = mutationCounter.get();
        Map<UUID, Map<String, String>> p = readAllPlayersFromDisk(ns);

        Set<UUID> removed = removedPlayers.get(ns);
        if (removed != null) {
            for (UUID uuid : removed) p.remove(uuid);
        }

        Map<UUID, DirtyEntry> dirty = dirtyPlayers.get(ns);
        if (dirty != null) {
            dirty.forEach((uuid, entry) -> p.put(uuid, new LinkedHashMap<>(entry.values())));
        }

        return new Snapshot(g, p, gen);
    }

    /**
     * Serialize + atomic write of an already-taken snapshot, guarded by a per-namespace lock
     * and an epoch token. The lock serializes writers so temp-file moves can never interleave;
     * the epoch rejects writes whose token is older than the latest completed write, so an
     * abandoned (but still running) async writer can never clobber newer data. Tokens come
     * from {@link #nextEpoch()} — one shared monotonic sequence for sync and async paths.
     * The snapshot must be taken on the main thread (see {@link #snapshot(String)}).
     */
    public void writeSnapshot(String ns, Snapshot snap, long token) {
        synchronized (writeLocks.computeIfAbsent(ns, k -> new Object())) {
            long latest = epochs.getOrDefault(ns, 0L);
            if (token < latest) return; // stale abandoned write — skip
            epochs.put(ns, token);
            if (onWriteStart != null) onWriteStart.run();
            YamlConfiguration config = new YamlConfiguration();
            snap.global().forEach((name, value) -> config.set("global." + name, value));
            snap.players().forEach((uuid, values) ->
                values.forEach((name, value) -> config.set("players." + uuid + "." + name, value)));
            Path tmp = null;
            try {
                Files.createDirectories(dataDir);
                Path target = dataDir.resolve(ns + ".yml");
                tmp = Files.createTempFile(dataDir, ns + ".", ".tmp");
                Files.writeString(tmp, config.saveToString());
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
                tmp = null; // moved successfully
                pruneDirtyAfterWrite(ns, snap);
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "failed to persist " + ns + ".yml", e);
            } finally {
                if (tmp != null) {
                    try {
                        Files.deleteIfExists(tmp);
                    } catch (IOException suppressed) {
                        LOG.log(Level.WARNING, "failed to delete temp file " + tmp, suppressed);
                    }
                }
            }
        }
    }

    /** Serialize + atomic write with a freshly issued epoch. Called OFF the main thread. */
    public void writeSnapshot(String ns, Snapshot snap) {
        writeSnapshot(ns, snap, nextEpoch());
    }

    /** Snapshot + serialize + atomic write. Synchronous convenience for the disable paths. */
    public void write(String ns) {
        writeSnapshot(ns, snapshot(ns), nextEpoch());
    }

    /** Test visibility: how many player rows are hot in the Caffeine cache for a namespace. */
    int cachedPlayerCount(String ns) {
        Cache<UUID, Map<String, String>> cache = playerCaches.get(ns);
        return cache == null ? 0 : cache.asMap().size();
    }

    /** Test visibility: force Caffeine maintenance (size eviction is otherwise opportunistic). */
    void cacheForCleanup(String ns) {
        Cache<UUID, Map<String, String>> cache = playerCaches.get(ns);
        if (cache != null) cache.cleanUp();
    }

    /** Test visibility: dirty player rows awaiting a successful write. */
    int dirtyPlayerCount(String ns) {
        Map<UUID, DirtyEntry> dirty = dirtyPlayers.get(ns);
        return dirty == null ? 0 : dirty.size();
    }

    private void pruneDirtyAfterWrite(String ns, Snapshot snap) {
        if (snap.dirtyThroughGen() < 0) return;
        long through = snap.dirtyThroughGen();
        // Re-warm only the players we just flushed — never bulk-load the full file into cache.
        java.util.ArrayList<UUID> flushed = new java.util.ArrayList<>();
        Map<UUID, DirtyEntry> dirty = dirtyPlayers.get(ns);
        if (dirty != null) {
            dirty.entrySet().removeIf(e -> {
                if (e.getValue().gen() <= through) {
                    flushed.add(e.getKey());
                    return true;
                }
                return false;
            });
            if (dirty.isEmpty()) dirtyPlayers.remove(ns, dirty);
        }
        Cache<UUID, Map<String, String>> cache = cacheFor(ns);
        for (UUID uuid : flushed) {
            Map<String, String> values = snap.players().get(uuid);
            if (values != null) cache.put(uuid, new ConcurrentHashMap<>(values));
            else cache.invalidate(uuid);
        }
        Set<UUID> removed = removedPlayers.get(ns);
        if (removed != null) {
            for (UUID uuid : removed) cache.invalidate(uuid);
            // Removals are fully reflected on disk once the snapshot that included them lands.
            removed.clear();
        }
    }

    private @Nullable Map<String, String> playerValues(String ns, UUID uuid) {
        Set<UUID> removed = removedPlayers.get(ns);
        if (removed != null && removed.contains(uuid)) return null;

        Map<UUID, DirtyEntry> dirty = dirtyPlayers.get(ns);
        if (dirty != null) {
            DirtyEntry entry = dirty.get(uuid);
            if (entry != null) return entry.values();
        }

        Cache<UUID, Map<String, String>> cache = cacheFor(ns);
        Map<String, String> cached = cache.getIfPresent(uuid);
        if (cached != null) return cached;

        Map<String, String> fromDisk = readPlayerFromDisk(ns, uuid);
        if (fromDisk != null) {
            cache.put(uuid, fromDisk);
        }
        return fromDisk;
    }

    private @Nullable Map<String, String> cleanCachedOrDisk(String ns, UUID uuid) {
        Cache<UUID, Map<String, String>> cache = playerCaches.get(ns);
        if (cache != null) {
            Map<String, String> cached = cache.getIfPresent(uuid);
            if (cached != null) return cached;
        }
        return readPlayerFromDisk(ns, uuid);
    }

    private Cache<UUID, Map<String, String>> cacheFor(String ns) {
        return playerCaches.computeIfAbsent(ns, k -> Caffeine.newBuilder()
            .maximumSize(maxCachedPlayers)
            .expireAfterAccess(expireAfterAccess)
            .build());
    }

    private @Nullable Map<String, String> readPlayerFromDisk(String ns, UUID uuid) {
        Path file = dataDir.resolve(ns + ".yml");
        if (!Files.exists(file)) return null;
        YamlConfiguration config = readYamlFile(file);
        if (config == null) return null;
        ConfigurationSection prefs = config.getConfigurationSection("players." + uuid);
        if (prefs == null) return null;
        Map<String, String> values = new ConcurrentHashMap<>();
        for (String key : prefs.getKeys(false)) {
            String value = prefs.getString(key);
            if (value != null) values.put(key, value);
        }
        return values.isEmpty() ? null : values;
    }

    private Map<UUID, Map<String, String>> readAllPlayersFromDisk(String ns) {
        Map<UUID, Map<String, String>> result = new LinkedHashMap<>();
        Path file = dataDir.resolve(ns + ".yml");
        if (!Files.exists(file)) return result;
        YamlConfiguration config = readYamlFile(file);
        if (config == null) return result;
        ConfigurationSection p = config.getConfigurationSection("players");
        if (p == null) return result;
        for (String uuidKey : p.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(uuidKey);
                ConfigurationSection prefs = p.getConfigurationSection(uuidKey);
                if (prefs == null) continue;
                Map<String, String> values = new LinkedHashMap<>();
                for (String key : prefs.getKeys(false)) {
                    String value = prefs.getString(key);
                    if (value != null) values.put(key, value);
                }
                if (!values.isEmpty()) result.put(uuid, values);
            } catch (IllegalArgumentException e) {
                LOG.warning("Skipping unreadable player section '" + uuidKey + "' in " + ns + ".yml");
            }
        }
        return result;
    }

    private @Nullable YamlConfiguration readYamlFile(Path file) {
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(Files.readString(file));
            return config;
        } catch (IOException | InvalidConfigurationException e) {
            LOG.log(Level.SEVERE, "failed to load " + file + "; starting empty", e);
            return null;
        }
    }
}
