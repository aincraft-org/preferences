package dev.mintychochip.preferences.internal.session;

import dev.mintychochip.preferences.api.PreferenceKey;
import dev.mintychochip.preferences.api.PreferenceScope;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

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

    public record ParentContext(
        Screen screen,
        PreferenceScope scope,
        @Nullable String namespace,
        @Nullable String query,
        int page) {

        public ParentContext {
            Objects.requireNonNull(screen, "screen");
            Objects.requireNonNull(scope, "scope");
        }
    }

    public enum Screen { HOME, PLUGIN_LIST, SEARCH_INPUT, SEARCH_RESULTS, EDIT }
}
