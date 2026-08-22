package dev.mintychochip.preferences.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
/** Verifies YAML persistence, caching, dirty overlays, and epoch guards. */
class YamlValueStoreTest {

    @Test void setAndSnapshot(@TempDir Path dir) {
        YamlValueStore store = new YamlValueStore(dir);
        UUID uuid = UUID.randomUUID();
        store.setGlobal("demo", "announce_logins", "true");
        store.setPlayer("demo", uuid, "draw_distance", "12");

        YamlValueStore.Snapshot snap = store.snapshot("demo");
        assertEquals("true", snap.global().get("announce_logins"));
        assertEquals("12", snap.players().get(uuid).get("draw_distance"));
    }

    @Test void persistAndReload(@TempDir Path dir) throws IOException {
        YamlValueStore store = new YamlValueStore(dir);
        UUID uuid = UUID.randomUUID();
        store.setGlobal("demo", "a", "1");
        store.setPlayer("demo", uuid, "b", "2");
        store.write("demo"); // synchronous write for tests

        assertTrue(Files.exists(dir.resolve("demo.yml")));

        YamlValueStore reloaded = new YamlValueStore(dir);
        reloaded.load("demo");
        assertEquals("1", reloaded.getGlobal("demo", "a"));
        assertEquals("2", reloaded.getPlayer("demo", uuid, "b"));
    }

    @Test void corruptPlayerUuidSectionSkipped(@TempDir Path dir) throws IOException {
        Files.createDirectories(dir);
        Files.writeString(dir.resolve("demo.yml"), """
            players:
              not-a-uuid:
                x: \"1\"
            """);
        YamlValueStore store = new YamlValueStore(dir);
        store.load("demo"); // must not throw; bad uuid section skipped with warning
        assertNull(store.getGlobal("demo", "anything"));
    }

    @Test void missingFileLoadsEmpty(@TempDir Path dir) {
        YamlValueStore store = new YamlValueStore(dir);
        store.load("nope");
        assertNull(store.getGlobal("nope", "x"));
    }

    @Test void reloadDropsStaleKeys(@TempDir Path dir) throws IOException {
        YamlValueStore store = new YamlValueStore(dir);
        store.setGlobal("demo", "keep", "1");
        store.setGlobal("demo", "drop", "2");
        store.write("demo");

        Files.writeString(dir.resolve("demo.yml"), """
            global:
              keep: "1"
            """);
        store.load("demo"); // must replace in-memory state, not merge
        assertEquals("1", store.getGlobal("demo", "keep"));
        assertNull(store.getGlobal("demo", "drop"));
    }

    @Test void staleTokenWriteIsSkipped(@TempDir Path dir) throws IOException {
        YamlValueStore store = new YamlValueStore(dir);
        store.writeSnapshot("demo", new YamlValueStore.Snapshot(Map.of("k", "new"), Map.of()), 10L);
        // A stale (abandoned) writer finishing late must not overwrite newer data...
        store.writeSnapshot("demo", new YamlValueStore.Snapshot(Map.of("k", "stale"), Map.of()), 5L);
        // ...but a fresh token must still win (epochs are not pinned).
        store.writeSnapshot("demo", new YamlValueStore.Snapshot(Map.of("k", "newer"), Map.of()), 11L);
        YamlValueStore reloaded = new YamlValueStore(dir);
        reloaded.load("demo");
        assertEquals("newer", reloaded.getGlobal("demo", "k"));
    }

    @Test void writeMergesCachedPlayersWithOnDiskPlayers(@TempDir Path dir) throws IOException {
        UUID cold = UUID.randomUUID();
        UUID hot = UUID.randomUUID();
        YamlValueStore seed = new YamlValueStore(dir);
        seed.setPlayer("demo", cold, "a", "cold");
        seed.write("demo");

        // Fresh store: cold lives only on disk; hot is a new dirty mutation.
        YamlValueStore store = new YamlValueStore(dir);
        store.load("demo");
        store.setPlayer("demo", hot, "b", "hot");
        store.write("demo");

        YamlValueStore reloaded = new YamlValueStore(dir);
        reloaded.load("demo");
        assertEquals("cold", reloaded.getPlayer("demo", cold, "a"));
        assertEquals("hot", reloaded.getPlayer("demo", hot, "b"));
    }

    @Test void successfulWritePrunesDirtyMap(@TempDir Path dir) {
        YamlValueStore store = new YamlValueStore(dir);
        UUID uuid = UUID.randomUUID();
        store.setPlayer("demo", uuid, "k", "v");
        assertEquals(1, store.dirtyPlayerCount("demo"));
        store.write("demo");
        assertEquals(0, store.dirtyPlayerCount("demo"));
        assertEquals("v", store.getPlayer("demo", uuid, "k"));
    }

    @Test void evictPlayerDropsCacheButKeepsDisk(@TempDir Path dir) {
        YamlValueStore store = new YamlValueStore(dir);
        UUID uuid = UUID.randomUUID();
        store.setPlayer("demo", uuid, "k", "v");
        store.write("demo");
        assertTrue(store.cachedPlayerCount("demo") >= 1);
        store.evictPlayer("demo", uuid);
        assertEquals(0, store.cachedPlayerCount("demo"));
        assertEquals("v", store.getPlayer("demo", uuid, "k"), "read-through must reload from disk");
    }

    @Test void evictPlayerSkipsDirtyEntries(@TempDir Path dir) {
        YamlValueStore store = new YamlValueStore(dir);
        UUID uuid = UUID.randomUUID();
        store.setPlayer("demo", uuid, "k", "dirty");
        store.evictPlayer("demo", uuid);
        assertEquals(1, store.dirtyPlayerCount("demo"));
        assertEquals("dirty", store.getPlayer("demo", uuid, "k"));
    }

    @Test void removePlayerDataDropsFromNextWrite(@TempDir Path dir) throws IOException {
        YamlValueStore store = new YamlValueStore(dir);
        UUID uuid = UUID.randomUUID();
        store.setPlayer("demo", uuid, "k", "v");
        store.write("demo");
        store.removePlayerData("demo", uuid);
        store.write("demo");

        YamlValueStore reloaded = new YamlValueStore(dir);
        reloaded.load("demo");
        assertNull(reloaded.getPlayer("demo", uuid, "k"));
    }

    @Test void caffeineMaximumSizeBoundsHotCache(@TempDir Path dir) {
        // max 2 hot players after flush re-warm; further reads must not grow past max.
        YamlValueStore store = new YamlValueStore(dir, 2, java.time.Duration.ofHours(1));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        store.setPlayer("demo", a, "k", "1");
        store.setPlayer("demo", b, "k", "2");
        store.setPlayer("demo", c, "k", "3");
        store.write("demo"); // re-warms only the 3 flushed rows; caffeine caps at 2
        store.cacheForCleanup("demo");
        assertTrue(store.cachedPlayerCount("demo") <= 2,
            "caffeine must bound hot player rows, was " + store.cachedPlayerCount("demo"));
        // All three still readable via disk read-through.
        assertEquals("1", store.getPlayer("demo", a, "k"));
        assertEquals("2", store.getPlayer("demo", b, "k"));
        assertEquals("3", store.getPlayer("demo", c, "k"));
        store.cacheForCleanup("demo");
        assertTrue(store.cachedPlayerCount("demo") <= 2,
            "after read-through cache must still be bounded, was " + store.cachedPlayerCount("demo"));
    }
}
