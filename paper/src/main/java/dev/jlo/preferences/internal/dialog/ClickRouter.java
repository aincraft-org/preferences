package dev.jlo.preferences.internal.dialog;

import dev.jlo.preferences.api.PreferenceScope;
import dev.jlo.preferences.internal.PreferenceRegistry;
import dev.jlo.preferences.internal.RegisteredPreference;
import dev.jlo.preferences.internal.session.DialogSession;
import dev.jlo.preferences.internal.session.DialogSessionManager;
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

public final class ClickRouter implements Listener {

    private static final String NAMESPACE = "preferences";

    private final PreferenceRegistry registry;
    private final DialogSessionManager sessions;
    private final DialogScreens screens;
    private final @Nullable Consumer<UUID> playerEvictor;

    public ClickRouter(PreferenceRegistry registry, DialogSessionManager sessions, DialogScreens screens) {
        this(registry, sessions, screens, null);
    }

    public ClickRouter(PreferenceRegistry registry, DialogSessionManager sessions, DialogScreens screens,
                       @Nullable Consumer<UUID> playerEvictor) {
        this.registry = registry;
        this.sessions = sessions;
        this.screens = screens;
        this.playerEvictor = playerEvictor;
    }

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
                if (path.startsWith("edit/") && session.kind() != DialogSession.Kind.EDIT) {
                    openEditByIndex(player, session, path.substring("edit/".length()));
                }
            }
        }
    }

    private void navigate(Player player, DialogSession session, int newPage) {
        switch (session.kind()) {
            case PLAYER_LIST -> {
                if (!player.hasPermission("preferences.use")) {
                    denyAndClose(player, PreferenceScope.PLAYER);
                    return;
                }
                screens.showPlayerList(player, newPage);
            }
            case GLOBAL_LIST -> {
                if (!player.hasPermission("preferences.manage")) {
                    denyAndClose(player, PreferenceScope.GLOBAL);
                    return;
                }
                screens.showGlobalList(player, newPage);
            }
            case EDIT -> {} // nav buttons never appear in edit dialogs
        }
    }

    private void openEditByIndex(Player player, DialogSession session, String indexStr) {
        PreferenceScope scope = session.kind() == DialogSession.Kind.GLOBAL_LIST
            ? PreferenceScope.GLOBAL
            : PreferenceScope.PLAYER;
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

    private void handleSave(Player player, DialogSession session, @Nullable DialogResponseView view) {
        if (session.kind() != DialogSession.Kind.EDIT || session.target() == null) return;
        RegisteredPreference<?> pref = registry.byKey(session.target());
        if (pref == null) return;
        saveTyped(player, cast(pref), view, session.page());
    }

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

    private static boolean mayEdit(Player player, PreferenceScope scope) {
        if (scope == PreferenceScope.GLOBAL) return player.hasPermission("preferences.manage");
        return player.hasPermission("preferences.use");
    }

    private void denyAndClose(Player player, PreferenceScope scope) {
        String msg = scope == PreferenceScope.GLOBAL
            ? "You don't have permission to change server preferences."
            : "You don't have permission to use preferences.";
        player.sendMessage(Component.text(msg, NamedTextColor.RED));
        sessions.close(player.getUniqueId());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        sessions.close(uuid);
        // Evict typed caches + store hot rows so memory stays bounded across churn.
        registry.all().forEach(p -> p.invalidatePlayer(uuid));
        if (playerEvictor != null) playerEvictor.accept(uuid);
    }
}
