package dev.mintychochip.preferences.paper;

import dev.mintychochip.preferences.api.PreferencesService;
import dev.mintychochip.preferences.common.internal.DebouncedFlusher;
import dev.mintychochip.preferences.common.internal.FlushScheduler;
import dev.mintychochip.preferences.common.internal.PreferenceRegistry;
import dev.mintychochip.preferences.common.internal.PreferencesServiceImpl;
import dev.mintychochip.preferences.common.internal.RegisteredPreference;
import dev.mintychochip.preferences.common.internal.YamlValueStore;
import dev.mintychochip.preferences.paper.internal.command.PreferencesCommand;
import dev.mintychochip.preferences.paper.internal.dialog.ClickRouter;
import dev.mintychochip.preferences.paper.internal.dialog.DialogScreens;
import dev.mintychochip.preferences.common.internal.session.DialogSessionManager;
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

/**
 * Paper entry point that wires preference registration, persistence, dialogs, and commands.
 *
 * <p>Storage writes are debounced onto a single background I/O executor, while Bukkit
 * lifecycle callbacks and dialog interactions remain on the server thread.</p>
 */
public final class PreferencesPlugin extends JavaPlugin implements Listener {

    /** Registry of preferences contributed by this plugin and other loaded plugins. */
    private PreferenceRegistry registry;
    /** YAML-backed store for global values and cached player values. */
    private YamlValueStore store;
    /** Debounced persistence coordinator for registered preference changes. */
    private DebouncedFlusher flusher;
    /** Single-thread executor used for persistence I/O. */
    private ExecutorService io;
    /** Active dialog sessions keyed by player and preference namespace. */
    private DialogSessionManager sessions;
    /** Namespaces whose persisted global data has already been loaded. */
    private final Set<String> loadedNamespaces = ConcurrentHashMap.newKeySet();

    /**
     * Wires storage, debounced I/O, the {@link PreferencesService}, dialog routing, and commands.
     *
     * <p>Runs on the server main thread during plugin enable. Persists default config, starts the
     * single-thread I/O executor, registers this plugin as a {@link PreferencesService} provider,
     * and hooks preference registration to lazy YAML loads plus dirty-marking writes.
     */
    @Override
    public void onEnable() {
        saveDefaultConfig();

        registry = new PreferenceRegistry();
        store = new YamlValueStore(getDataFolder().toPath().resolve("data"));
        io = Executors.newSingleThreadExecutor(r -> new Thread(r, "Preferences IO"));
        flusher = new DebouncedFlusher(store, new BukkitFlushScheduler(), flushDelayTicks(), io);

        sessions = new DialogSessionManager();
        DialogScreens screens = new DialogScreens(registry, sessions, getConfig().getInt("gui.page-size", 20));

        wireStorageLookup();

        PreferencesService service = new PreferencesServiceImpl(registry, this::teardownNamespace);
        Bukkit.getServicesManager().register(PreferencesService.class, service, this, ServicePriority.Normal);

        ClickRouter router = new ClickRouter(registry, sessions, screens, store::evictPlayerAllNamespaces);
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

    /**
     * Connects a newly registered preference to lazy store reads and dirty-marking writes.
     *
     * <p>Global rows for the namespace are loaded once on first registration; player rows
     * load on demand through the preference cache.</p>
     */
    private void wire(RegisteredPreference<?> pref) {
        String ns = pref.key().namespace();
        // First registration from a namespace loads its persisted globals so lazy reads
        // observe saved values instead of defaults. Player rows load on demand via Caffeine.
        // Idempotent per namespace; repeat registrations skip the file read.
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

    /**
     * Flush pending writes and close dialog sessions for a namespace before registry removal.
     * Shared by {@link PreferencesService#unregisterPlugin} and foreign {@link PluginDisableEvent}.
     */
    private void teardownNamespace(String ns) {
        flusher.flushNamespaceSync(ns);
        sessions.closeForNamespace(ns);
        loadedNamespaces.remove(ns);
    }

    /**
     * Tears down a foreign plugin namespace when that plugin disables.
     *
     * <p>Flushes pending writes, closes any open dialogs for the namespace, and unregisters
     * its preferences. Ignores disable events for this plugin and namespaces with no
     * registered preferences.</p>
     *
     * @param event plugin disable event from Bukkit
     */
    @EventHandler
    public void onPluginDisable(PluginDisableEvent event) {
        if (event.getPlugin() == this) return;
        String ns = event.getPlugin().getName().toLowerCase(Locale.ROOT);
        boolean hasPrefs = registry.all().stream().anyMatch(p -> p.key().namespace().equals(ns));
        if (!hasPrefs) return; // foreign plugin: nothing registered, nothing to flush or write
        teardownNamespace(ns);
        registry.unregisterNamespace(ns);
    }

    /**
     * Flushes all pending writes and shuts down the background I/O executor.
     *
     * <p>Runs during plugin disable on the server thread. Blocks until
     * {@link DebouncedFlusher#flushAllSync()} drains every namespace, then shuts down the I/O
     * executor with a bounded wait.
     */
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

    /** Debounced flush delay in server ticks, derived from {@code storage.flush-seconds}. */
    private long flushDelayTicks() {
        return getConfig().getInt("storage.flush-seconds", 5) * 20L;
    }

    /** Schedules flush work on the Bukkit main thread after the configured delay. */
    private final class BukkitFlushScheduler implements FlushScheduler {
        /** {@inheritDoc} */
        @Override
        public Cancellable schedule(Runnable task) {
            var bukkitTask = Bukkit.getScheduler().runTaskLater(PreferencesPlugin.this, task, flushDelayTicks());
            return bukkitTask::cancel;
        }
    }
}
