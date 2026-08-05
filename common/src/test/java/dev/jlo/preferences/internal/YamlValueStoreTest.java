package dev.jlo.preferences.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
}
