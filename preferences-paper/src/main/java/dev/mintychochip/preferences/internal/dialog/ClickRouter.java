package dev.mintychochip.preferences.internal.dialog;

import dev.mintychochip.preferences.api.PreferenceScope;
import dev.mintychochip.preferences.internal.PreferenceRegistry;
import dev.mintychochip.preferences.internal.RegisteredPreference;
import dev.mintychochip.preferences.internal.session.DialogSession;
import dev.mintychochip.preferences.internal.session.DialogSessionManager;
import io.papermc.paper.connection.PlayerGameConnection;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.event.player.PlayerCustomClickEvent;
import java.util.Comparator;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jspecify.annotations.Nullable;

/**
 * Routes Paper dialog custom-click events to list navigation, edit, save, and cancel flows.
 *
 * <p>Only clicks in the {@code preferences} namespace with a live {@link DialogSession} are
 * handled. Clicks from non-play phases, forged identifiers, or stale sessions are ignored.
 * Permission checks are re-applied on navigation and save because a session may outlive a
 * permission revoke.</p>
 */
public final class ClickRouter implements Listener {

    /** Adventure namespace shared by all preference dialog custom-click keys. */
    private static final String NAMESPACE = "preferences";

    private final PreferenceRegistry registry;
    private final DialogSessionManager sessions;
    private final DialogScreens screens;
    /** Optional callback to evict persisted player rows from the store on quit. */
    private final @Nullable Consumer<UUID> playerEvictor;

    /**
     * @param registry preference lookup for edit and save targets
     * @param sessions active dialog session store
     * @param screens presenter used to reopen list and edit screens
     */
    public ClickRouter(PreferenceRegistry registry, DialogSessionManager sessions, DialogScreens screens) {
        this(registry, sessions, screens, null);
    }

    /**
     * @param registry preference lookup for edit and save targets
     * @param sessions active dialog session store
     * @param screens presenter used to reopen list and edit screens
     * @param playerEvictor invoked on quit to drop cached store rows for the player, or {@code null}
     */
    public ClickRouter(PreferenceRegistry registry, DialogSessionManager sessions, DialogScreens screens,
                       @Nullable Consumer<UUID> playerEvictor) {
        this.registry = registry;
        this.sessions = sessions;
        this.screens = screens;
        this.playerEvictor = playerEvictor;
    }

    /**
     * Dispatches a custom click to save, cancel, pagination, or edit handlers.
     *
     * <p>Recognized paths: {@code save}, {@code cancel}, {@code list_prev}, {@code list_next},
     * and {@code edit/<index>} (page-local button index).</p>
     *
     * @param event Paper custom-click event carrying the dialog action identifier
     */
    @EventHandler
    public void onClick(PlayerCustomClickEvent event) {
        // Play phase only: the game connection is the only common-connection
        // subtype that carries an in-game Player. Other phases (login/configuration)
        // have no dialog session and cannot be a click source.
        if (!(event.getCommonConnection() instanceof PlayerGameConnection conn)) return;
        Player player = conn.getPlayer();
        if (player == null) return;

        Key id = event.getIdentifier();
        if (!NAMESPACE.equals(id.namespace())) return;

        DialogSession session = sessions.current(player.getUniqueId());
        if (session == null) return; // forged/stale click: no live session

        String path = id.value();
        switch (path) {
            case "save" -> handleSave(player, session, event.getDialogResponseView());
            case "cancel" -> sessions.close(player.getUniqueId());
            case "list_prev" -> navigate(player, session, session.page() - 1);
            case "list_next" -> navigate(player, session, session.page() + 1);
            default -> {
                if (path.startsWith("edit/") && session.screen() != DialogSession.Screen.EDIT) {
                    openEditByIndex(player, session, path.substring("edit/".length()));
                }
            }
        }
    }

    /**
     * Reopens the current list at {@code newPage}, enforcing scope-appropriate permissions.
     *
     * <p>No-op when the session is not on a list screen.</p>
     */
    private void navigate(Player player, DialogSession session, int newPage) {
        if (session.screen() != DialogSession.Screen.PLUGIN_LIST) {
            return; // nav buttons never appear in edit dialogs
        }
        if (session.scope() == PreferenceScope.PLAYER) {
            if (!player.hasPermission("preferences.use")) {
                denyAndClose(player, PreferenceScope.PLAYER);
                return;
            }
            screens.showPlayerList(player, newPage);
        } else {
            if (!player.hasPermission("preferences.manage")) {
                denyAndClose(player, PreferenceScope.GLOBAL);
                return;
            }
            screens.showGlobalList(player, newPage);
        }
    }

    /**
     * Opens the edit dialog for the preference at a page-local button index.
     *
     * <p>Malformed indices and out-of-range selections are ignored without user feedback.</p>
     */
    private void openEditByIndex(Player player, DialogSession session, String indexStr) {
        PreferenceScope scope = session.scope();
        if (!mayEdit(player, scope)) {
            denyAndClose(player, scope);
            return;
        }
        int index;
        try {
            index = Integer.parseInt(indexStr);
        } catch (NumberFormatException e) {
            return;
        }

        var scopePrefs = registry.all().stream()
            .filter(p -> p.scope() == scope)
            .sorted(Comparator.comparing(p -> p.key().asString()))
            .toList();
        int absolute = session.page() * screens.pageSize() + index;
        if (absolute < 0 || absolute >= scopePrefs.size()) return; // out of range: ignore

        screens.showEdit(player, cast(scopePrefs.get(absolute)), session.page());
    }

    @SuppressWarnings("unchecked")
    private static <T> RegisteredPreference<T> cast(RegisteredPreference<?> pref) {
        return (RegisteredPreference<T>) pref;
    }

    /** Parses and persists the edit target when the session is on the edit screen. */
    private void handleSave(Player player, DialogSession session, @Nullable DialogResponseView view) {
        if (session.screen() != DialogSession.Screen.EDIT || session.editTarget() == null) return;
        RegisteredPreference<?> pref = registry.byKey(session.editTarget());
        if (pref == null) return;
        int returnPage = session.parent() != null ? session.parent().page() : session.page();
        saveTyped(player, cast(pref), view, returnPage);
    }

    /**
     * Validates dialog input under key {@code value}, persists the preference, and returns
     * to the originating list page.
     *
     * <p>On parse failure the player sees a red error message and the edit dialog reopens
     * with no value change.</p>
     */
    private <T> void saveTyped(Player player, RegisteredPreference<T> pref,
                               @Nullable DialogResponseView view, int returnPage) {
        // Re-check permissions at save time (session may outlive a revoke).
        if (!mayEdit(player, pref.scope())) {
            denyAndClose(player, pref.scope());
            return;
        }
        var adapter = pref.codec().input();
        T parsed = (adapter == null || view == null) ? null : adapter.parseResponse(view, "value");
        if (parsed == null) {
            player.sendMessage(Component.text("Invalid value — nothing was changed.", NamedTextColor.RED));
            screens.showEdit(player, pref, returnPage);
            return;
        }
        // Attribute global dialog saves to the acting player for PreferenceChangeEvent.editor().
        if (pref.scope() == PreferenceScope.GLOBAL) pref.setGlobal(player, parsed);
        else pref.set(player, parsed);
        player.sendMessage(Component.text("Saved ", NamedTextColor.GREEN)
            .append(pref.label()).append(Component.text(".", NamedTextColor.GREEN)));
        if (pref.scope() == PreferenceScope.GLOBAL) {
            screens.showGlobalList(player, returnPage);
        } else {
            screens.showPlayerList(player, returnPage);
        }
    }

    /**
     * @return {@code true} when the player may edit preferences in the given scope
     */
    private static boolean mayEdit(Player player, PreferenceScope scope) {
        if (scope == PreferenceScope.GLOBAL) return player.hasPermission("preferences.manage");
        return player.hasPermission("preferences.use");
    }

    /** Sends a scope-specific denial message and closes the player's dialog session. */
    private void denyAndClose(Player player, PreferenceScope scope) {
        String msg = scope == PreferenceScope.GLOBAL
            ? "You don't have permission to change server preferences."
            : "You don't have permission to use preferences.";
        player.sendMessage(Component.text(msg, NamedTextColor.RED));
        sessions.close(player.getUniqueId());
    }

    /**
     * Drops dialog state and in-memory preference caches when a player disconnects.
     *
     * <p>Also evicts persisted store rows when a {@link #playerEvictor} was configured.</p>
     *
     * @param event player quit event from Bukkit
     */
    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        sessions.close(uuid);
        // Evict typed caches + store hot rows so memory stays bounded across churn.
        registry.all().forEach(p -> p.invalidatePlayer(uuid));
        if (playerEvictor != null) playerEvictor.accept(uuid);
    }
}
