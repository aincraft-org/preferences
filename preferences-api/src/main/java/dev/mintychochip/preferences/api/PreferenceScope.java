package dev.mintychochip.preferences.api;

/**
 * Determines whether a preference value is stored per player or globally.
 *
 * <p>{@link #PLAYER} values are read and written with a {@link org.bukkit.entity.Player} context.
 * {@link #GLOBAL} values apply server-wide and may be edited programmatically or via an admin dialog.
 */
public enum PreferenceScope {
    /** A value stored separately for each player. */
    PLAYER,
    /** A value shared across the server. */
    GLOBAL
}
