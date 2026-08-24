package dev.mintychochip.preferences.common.internal.session;

import dev.mintychochip.preferences.api.PreferenceKey;
import dev.mintychochip.preferences.api.PreferenceScope;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Immutable snapshot of a player's in-progress preferences dialog navigation state.
 *
 * <p>{@link #displayedItems} and {@link #displayedNamespaces} are defensively copied at
 * construction. {@link Screen#EDIT} sessions require a non-null {@link #editTarget}.
 *
 * @param player player viewing the dialog
 * @param screen current screen
 * @param scope active preference scope filter
 * @param page zero-based page index within the screen
 * @param namespace optional namespace filter
 * @param query optional search query
 * @param editTarget preference being edited on {@link Screen#EDIT}, otherwise {@code null}
 * @param parent navigation context to return to after edit
 * @param displayedItems immutable snapshot of keys shown on this screen
 * @param displayedNamespaces immutable snapshot of namespaces shown on this screen
 */
public record DialogSession(
    UUID player,
    Screen screen,
    PreferenceScope scope,
    int page,
    @Nullable String namespace,
    @Nullable String query,
    @Nullable PreferenceKey editTarget,
    @Nullable ParentContext parent,
    List<PreferenceKey> displayedItems,
    List<String> displayedNamespaces) {

    /**
     * Validates invariants and defensively copies list components.
     */
    public DialogSession {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(screen, "screen");
        Objects.requireNonNull(scope, "scope");
        displayedItems = List.copyOf(Objects.requireNonNull(displayedItems, "displayedItems"));
        displayedNamespaces = List.copyOf(Objects.requireNonNull(displayedNamespaces, "displayedNamespaces"));
        if (screen == Screen.EDIT && editTarget == null) {
            throw new IllegalArgumentException("editTarget is required for edit sessions");
        }
    }

    /**
     * Navigation stack entry used when returning from an edit screen.
     *
     * @param screen screen to restore
     * @param scope scope to restore
     * @param namespace namespace filter to restore, or {@code null}
     * @param query search query to restore, or {@code null}
     * @param page page index to restore
     */
    public record ParentContext(
        Screen screen,
        PreferenceScope scope,
        @Nullable String namespace,
        @Nullable String query,
        int page) {

        /** Validates required navigation fields. */
        public ParentContext {
            Objects.requireNonNull(screen, "screen");
            Objects.requireNonNull(scope, "scope");
        }
    }

    /** Top-level dialog screens in the preferences UI flow. */
    public enum Screen { HOME, PLUGIN_LIST, SEARCH_INPUT, SEARCH_RESULTS, EDIT }
}
