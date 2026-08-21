package dev.mintychochip.preferences.internal.session;

import dev.mintychochip.preferences.api.PreferenceKey;
import java.util.Objects;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record DialogSession(UUID player, Kind kind, int page, @Nullable PreferenceKey target) {
    public DialogSession {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(kind, "kind");
        // target is intentionally nullable (list sessions have no edit target)
    }

    public enum Kind { PLAYER_LIST, GLOBAL_LIST, EDIT }
}
