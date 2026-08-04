package dev.jlo.preferences.internal;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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

/**
 * Per-namespace YAML storage using Bukkit's YamlConfiguration (bundled with Paper).
 * Thread-safe: main thread mutates maps; async threads only read snapshots.
 */
public final class YamlValueStore implements ValueStore {

    public record Snapshot(Map<String, String> global, Map<UUID, Map<String, String>> players) {}

    private static final Logger LOG = Logger.getLogger("Preferences");
    private final Path dataDir;
    private final Map<String, Map<String, String>> globals = new ConcurrentHashMap<>();
    private final Map<String, Map<UUID, Map<String, String>>> players = new ConcurrentHashMap<>();
    private final Map<String, Object> writeLocks = new ConcurrentHashMap<>();
    private final Map<String, Long> epochs = new ConcurrentHashMap<>();
    private final AtomicLong epochCounter = new AtomicLong();

    /** Test hook: invoked inside the per-namespace write lock, before writing. */
    volatile Runnable onWriteStart;

    public YamlValueStore(Path dataDir) { this.dataDir = dataDir; }

    /** Next token in the single shared write sequence (both sync and async paths). */
    public long nextEpoch() {
        return epochCounter.incrementAndGet();
    }

    public void load(String ns) {
        globals.remove(ns);
        players.remove(ns);
        Path file = dataDir.resolve(ns + ".yml");
        if (!Files.exists(file)) return;
        YamlConfiguration config = new YamlConfiguration();
        try {
            config.loadFromString(Files.readString(file));
        } catch (IOException | InvalidConfigurationException e) {
            LOG.log(Level.SEVERE, "failed to load " + file + "; starting empty", e);
            return;
        }
        ConfigurationSection g = config.getConfigurationSection("global");
        if (g != null) {
            Map<String, String> target = globals.computeIfAbsent(ns, k -> new ConcurrentHashMap<>());
            for (String key : g.getKeys(false)) {
                String value = g.getString(key);
                if (value != null) target.put(key, value);
            }
        }
        ConfigurationSection p = config.getConfigurationSection("players");
        if (p != null) {
            Map<UUID, Map<String, String>> target = players.computeIfAbsent(ns, k -> new ConcurrentHashMap<>());
            for (String uuidKey : p.getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(uuidKey);
                    ConfigurationSection prefs = p.getConfigurationSection(uuidKey);
                    if (prefs == null) continue;
                    Map<String, String> values = target.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>());
                    for (String key : prefs.getKeys(false)) {
                        String value = prefs.getString(key);
                        if (value != null) values.put(key, value);
                    }
                } catch (IllegalArgumentException e) {
                    LOG.warning("Skipping unreadable player section '" + uuidKey + "' in " + ns + ".yml");
                }
            }
        }
    }

    public String getGlobal(String ns, String name) {
        Map<String, String> m = globals.get(ns);
        return m == null ? null : m.get(name);
    }

    public String getPlayer(String ns, UUID uuid, String name) {
        Map<UUID, Map<String, String>> nsMap = players.get(ns);
        if (nsMap == null) return null;
        Map<String, String> m = nsMap.get(uuid);
        return m == null ? null : m.get(name);
    }

    public void setGlobal(String ns, String name, String value) {
        globals.computeIfAbsent(ns, k -> new ConcurrentHashMap<>()).put(name, value);
    }

    public void setPlayer(String ns, UUID uuid, String name, String value) {
        players.computeIfAbsent(ns, k -> new ConcurrentHashMap<>())
            .computeIfAbsent(uuid, k -> new ConcurrentHashMap<>())
            .put(name, value);
    }

    public void removePlayerData(String ns, UUID uuid) {
        Map<UUID, Map<String, String>> nsMap = players.get(ns);
        if (nsMap != null) nsMap.remove(uuid);
    }

    /** Immutable copy for async serialization. Called on the main thread. */
    public Snapshot snapshot(String ns) {
        Map<String, String> g = new LinkedHashMap<>();
        Map<String, String> gm = globals.get(ns);
        if (gm != null) g.putAll(gm);
        Map<UUID, Map<String, String>> p = new LinkedHashMap<>();
        Map<UUID, Map<String, String>> pm = players.get(ns);
        if (pm != null) pm.forEach((uuid, values) -> p.put(uuid, new LinkedHashMap<>(values)));
        return new Snapshot(g, p);
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
            try {
                Files.createDirectories(dataDir);
                Path target = dataDir.resolve(ns + ".yml");
                Path tmp = Files.createTempFile(dataDir, ns + ".", ".tmp");
                Files.writeString(tmp, config.saveToString());
                try {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException e) {
                    Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
                }
            } catch (IOException e) {
                LOG.log(Level.SEVERE, "failed to persist " + ns + ".yml", e);
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
}
