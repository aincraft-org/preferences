package dev.jlo.preferences.internal.session;

import dev.jlo.preferences.api.PreferenceKey;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record DialogSession(UUID player, Kind kind, int page, @Nullable PreferenceKey target) {
    public enum Kind { PLAYER_LIST, GLOBAL_LIST, EDIT }
}
