package dev.jlo.preferences;

import dev.jlo.preferences.api.PreferencesService;
import dev.jlo.preferences.internal.DebouncedFlusher;
import dev.jlo.preferences.internal.FlushScheduler;
import dev.jlo.preferences.internal.PreferenceRegistry;
import dev.jlo.preferences.internal.PreferencesServiceImpl;
import dev.jlo.preferences.internal.RegisteredPreference;
import dev.jlo.preferences.internal.YamlValueStore;
import dev.jlo.preferences.internal.command.PreferencesCommand;
import dev.jlo.preferences.internal.dialog.ClickRouter;
import dev.jlo.preferences.internal.dialog.DialogScreens;
import dev.jlo.preferences.internal.session.DialogSessionManager;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.PluginDisableEvent;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class PreferencesPlugin extends JavaPlugin implements Listener {

    private PreferenceRegistry registry;
    private YamlValueStore store;
    private DebouncedFlusher flusher;
    private ExecutorService io;
    private final Set<String> loadedNamespaces = ConcurrentHashMap.newKeySet();

    @Override
    public void onEnable() {
        saveDefaultConfig();
        int flushSeconds = getConfig().getInt("storage.flush-seconds", 5);

        registry = new PreferenceRegistry();
        store = new YamlValueStore(getDataFolder().toPath().resolve("data"));
        io = Executors.newSingleThreadExecutor(r -> new Thread(r, "Preferences IO"));
        flusher = new DebouncedFlusher(store, new BukkitFlushScheduler(), flushDelayTicks(), io);

        DialogSessionManager sessions = new DialogSessionManager();
        DialogScreens screens = new DialogScreens(registry, sessions, getConfig().getInt("gui.page-size", 20));

        wireStorageLookup();

        PreferencesService service = new PreferencesServiceImpl(registry);
        Bukkit.getServicesManager().register(PreferencesService.class, service, this, ServicePriority.Normal);

        ClickRouter router = new ClickRouter(registry, sessions, screens);
        Bukkit.getPluginManager().registerEvents(router, this);
        Bukkit.getPluginManager().registerEvents(this, this);

        var command = getCommand("preferences");
        if (command != null) {
            PreferencesCommand exec = new PreferencesCommand(screens);
            command.setExecutor(exec);
            command.setTabCompleter(exec);
        }
        getLogger().info("Preferences enabled.");
    }

    /** Bridges RegisteredPreference lazy loads + persistence into the store/flusher. */
    private void wireStorageLookup() {
        registry.onRegister(this::wire);
    }

    private void wire(RegisteredPreference<?> pref) {
        String ns = pref.key().namespace();
        // First registration from a namespace loads its persisted data file (globals + all
        // players) so lazy reads observe saved values instead of defaults. Idempotent per
        // namespace; repeat registrations skip the file read.
        if (loadedNamespaces.add(ns)) {
            store.load(ns);
        }
        pref.storedValueLookup = lookupKey -> {
            String[] parts = lookupKey.split("\u0000");
            String lookupNs = parts[0], target = parts[1], name = parts[2];
            if (target.equals("\ud83c\udf10")) return store.getGlobal(lookupNs, name);
            return store.getPlayer(lookupNs, UUID.fromString(target), name);
        };
        pref.appliedHook = applied -> {
            if (applied.player() == null) store.setGlobal(applied.key().namespace(), applied.key().name(), applied.storedValue());
            else store.setPlayer(applied.key().namespace(), applied.player(), applied.key().name(), applied.storedValue());
            flusher.markDirty(applied.key().namespace());
        };
    }

    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == this) return;
        String ns = event.getPlugin().getName().toLowerCase(Locale.ROOT);
        boolean hasPrefs = registry.all().stream().anyMatch(p -> p.key().namespace().equals(ns));
        if (!hasPrefs) return; // foreign plugin: nothing registered, nothing to flush or write
        // Flush this plugin's pending writes before dropping its registrations.
        flusher.flushNamespaceSync(ns);
        registry.unregisterNamespace(ns);
    }

    @Override
    public void onDisable() {
        flusher.flushAllSync();
        io.shutdown();
        try {
            if (!io.awaitTermination(10, TimeUnit.SECONDS)) io.shutdownNow();
        } catch (InterruptedException e) {
            io.shutdownNow();
            Thread.currentThread().interrupt();
        }
        getLogger().info("Preferences disabled; all data flushed.");
    }

    private long flushDelayTicks() {
        return getConfig().getInt("storage.flush-seconds", 5) * 20L;
    }

    private final class BukkitFlushScheduler implements FlushScheduler {
        @Override
        public Cancellable schedule(Runnable task) {
            var bukkitTask = Bukkit.getScheduler().runTaskLater(PreferencesPlugin.this, task, flushDelayTicks());
            return bukkitTask::cancel;
        }
    }
}
