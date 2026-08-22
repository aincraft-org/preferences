package dev.mintychochip.preferences.internal.dialog;

import dev.mintychochip.preferences.api.PreferenceScope;
import dev.mintychochip.preferences.internal.PreferenceRegistry;
import dev.mintychochip.preferences.internal.RegisteredPreference;
import dev.mintychochip.preferences.internal.session.DialogSession;
import dev.mintychochip.preferences.internal.session.DialogSessionManager;
import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * Builds and presents paginated preference list dialogs and single-preference edit screens.
 *
 * <p>List buttons use page-local indices routed through {@link DialogFactories#editKey(int)}.
 * Editable preferences bind dialog input to key {@code value}; read-only preferences open a
 * notice dialog with the current stored value.</p>
 */
public final class DialogScreens {

    /** Default number of preferences shown per list page when config does not override it. */
    public static final int PAGE_SIZE_DEFAULT = 20;

    private final PreferenceRegistry registry;
    private final DialogSessionManager sessions;
    private final int pageSize;

    /**
     * @param registry source of registered preferences to list and edit
     * @param sessions session store updated before each dialog is shown
     */
    public DialogScreens(PreferenceRegistry registry, DialogSessionManager sessions) {
        this(registry, sessions, PAGE_SIZE_DEFAULT);
    }

    /**
     * @param registry source of registered preferences to list and edit
     * @param sessions session store updated before each dialog is shown
     * @param pageSize maximum preferences per list page; values below {@code 1} are clamped to {@code 1}
     */
    public DialogScreens(PreferenceRegistry registry, DialogSessionManager sessions, int pageSize) {
        this.registry = registry;
        this.sessions = sessions;
        this.pageSize = Math.max(1, pageSize);
    }

    /**
     * Opens the player-scoped preference list for {@code player} at {@code page}.
     *
     * <p>Out-of-range pages are clamped to the last available page.</p>
     *
     * @param player viewer receiving the dialog
     * @param page zero-based list page to open
     */
    public void showPlayerList(Player player, int page) {
        showList(player, PreferenceScope.PLAYER, page);
    }

    /**
     * Opens the server-global preference list for {@code player} at {@code page}.
     *
     * <p>Out-of-range pages are clamped to the last available page.</p>
     *
     * @param player viewer receiving the dialog
     * @param page zero-based list page to open
     */
    public void showGlobalList(Player player, int page) {
        showList(player, PreferenceScope.GLOBAL, page);
    }

    /**
     * Renders a paginated multi-action list for the given scope.
     *
     * <p>When no preferences exist, shows a notice dialog and does not open a session.
     * Navigation buttons appear only when a previous or next page exists.</p>
     */
    private void showList(Player player, PreferenceScope scope, int page) {
        List<RegisteredPreference<?>> prefs = registry.all().stream()
            .filter(p -> p.scope() == scope)
            .sorted(Comparator.comparing(p -> p.key().asString()))
            .toList();

        if (prefs.isEmpty()) {
            // Paper's multiAction rejects an empty action list; surface an informative
            // notice instead. No session is opened: the dialog has no interactive elements.
            Component title = scope == PreferenceScope.GLOBAL
                ? Component.text("Server Preferences")
                : Component.text("Your Preferences");
            player.showDialog(DialogFactories.notice(title, List.of(
                DialogBody.plainMessage(Component.text("No preferences available.")))));
            return;
        }

        int pages = Math.max(1, (prefs.size() + pageSize - 1) / pageSize);
        int clampedPage = Math.max(0, Math.min(page, pages - 1));
        List<RegisteredPreference<?>> slice = prefs.subList(
            clampedPage * pageSize,
            Math.min(prefs.size(), (clampedPage + 1) * pageSize));

        List<ActionButton> actions = new ArrayList<>();
        for (int i = 0; i < slice.size(); i++) {
            RegisteredPreference<?> pref = slice.get(i);
            actions.add(ActionButton.builder(pref.label())
                .tooltip(pref.description().append(Component.newline())
                    .append(currentValueLine(player, pref)).color(NamedTextColor.GRAY))
                .action(DialogAction.customClick(DialogFactories.editKey(i), null))
                .build());
        }

        ActionButton exit = ActionButton.builder(Component.text("Close"))
            .action(DialogAction.customClick(DialogFactories.KEY_CANCEL, null))
            .build();

        List<ActionButton> withNav = new ArrayList<>(actions);
        if (clampedPage > 0) {
            withNav.add(ActionButton.builder(Component.text("« Previous"))
                .action(DialogAction.customClick(DialogFactories.KEY_LIST_PREV, null))
                .build());
        }
        if (clampedPage < pages - 1) {
            withNav.add(ActionButton.builder(Component.text("Next »"))
                .action(DialogAction.customClick(DialogFactories.KEY_LIST_NEXT, null))
                .build());
        }

        sessions.open(new DialogSession(
            player.getUniqueId(),
            DialogSession.Screen.PLUGIN_LIST,
            scope,
            clampedPage,
            null,
            null,
            null,
            null,
            slice.stream().map(RegisteredPreference::key).toList(),
            List.of()));
        Dialog dialog = DialogFactories.multiAction(
            Component.text(scope == PreferenceScope.GLOBAL ? "Server Preferences" : "Your Preferences"),
            withNav,
            exit);
        player.showDialog(dialog);
    }

    /** Tooltip line showing the preference's current stored value for the viewing player. */
    private Component currentValueLine(Player player, RegisteredPreference<?> pref) {
        Object value = pref.scope() == PreferenceScope.GLOBAL ? pref.getGlobal() : pref.get(player);
        return Component.text("Current: " + pref.codec().storage().write(cast(value)));
    }

    @SuppressWarnings("unchecked")
    private static <T> T cast(Object o) {
        return (T) o;
    }

    /**
     * Opens an edit dialog for {@code pref}, returning to {@code returnPage} after save.
     *
     * <p>Preferences without a dialog input adapter are shown read-only via a notice dialog.
     * Editable preferences expose a single input keyed {@code value}.</p>
     *
     * @param player viewer receiving the dialog
     * @param pref registered preference to edit
     * @param returnPage list page to reopen after a successful save
     */
    public <T> void showEdit(Player player, RegisteredPreference<T> pref, int returnPage) {
        var adapter = pref.codec().input();
        if (adapter == null) {
            // Read-only in GUI: display current value, single Close button.
            Object value = pref.scope() == PreferenceScope.GLOBAL ? pref.getGlobal() : pref.get(player);
            Dialog dialog = DialogFactories.notice(pref.label(), List.of(
                DialogBody.plainMessage(pref.description()),
                DialogBody.plainMessage(Component.text("Current: " + pref.codec().storage().write(cast(value))))));
            openEditSession(player, pref, returnPage);
            player.showDialog(dialog);
            return;
        }
        T current = pref.scope() == PreferenceScope.GLOBAL ? pref.getGlobal() : pref.get(player);
        var input = adapter.buildInput("value", pref.label(), current);
        Dialog dialog = DialogFactories.editDialog(pref.label(), List.of(pref.description()), input);
        openEditSession(player, pref, returnPage);
        player.showDialog(dialog);
    }

    /** Records an edit-screen session with parent list context for post-save navigation. */
    private void openEditSession(Player player, RegisteredPreference<?> pref, int returnPage) {
        var parent = new DialogSession.ParentContext(
            DialogSession.Screen.PLUGIN_LIST, pref.scope(), null, null, returnPage);
        sessions.open(new DialogSession(
            player.getUniqueId(),
            DialogSession.Screen.EDIT,
            pref.scope(),
            returnPage,
            null,
            null,
            pref.key(),
            parent,
            List.of(),
            List.of()));
    }

    /** @return number of preferences displayed per list page */
    public int pageSize() {
        return pageSize;
    }
}
