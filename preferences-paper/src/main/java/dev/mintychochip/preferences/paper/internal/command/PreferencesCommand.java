package dev.mintychochip.preferences.paper.internal.command;

import dev.mintychochip.preferences.paper.internal.dialog.DialogScreens;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

/**
 * Handles {@code /preferences} and {@code /prefs}, opening the dialog GUI for players.
 *
 * <p>Player-scoped preferences require {@code preferences.use}; the optional {@code global}
 * subcommand requires {@code preferences.manage}.</p>
 */
public final class PreferencesCommand implements TabExecutor {

    private final DialogScreens screens;

    /**
     * @param screens dialog presenter used to open list screens
     */
    public PreferencesCommand(DialogScreens screens) {
        this.screens = screens;
    }

    /**
     * Opens the player preference list, or the global list when the first argument is
     * {@code global} (case-insensitive).
     *
     * <p>Non-players receive a plain-text message that preferences are edited in-game. Extra
     * arguments beyond the optional {@code global} subcommand are ignored. Requires
     * {@code preferences.manage} for global lists and {@code preferences.use} for player lists.
     *
     * @param sender command source
     * @param command registered command instance
     * @param label alias used to invoke the command
     * @param args optional {@code global} subcommand
     * @return always {@code true} to suppress the default unknown-command message
     */
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Preferences are edited in-game via dialogs.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("global")) {
            if (!player.hasPermission("preferences.manage")) {
                player.sendMessage(Component.text(
                    "You don't have permission to manage server preferences.", NamedTextColor.RED));
                return true;
            }
            screens.showGlobalList(player, 0);
            return true;
        }
        if (!player.hasPermission("preferences.use")) {
            player.sendMessage(Component.text(
                "You don't have permission to use preferences.", NamedTextColor.RED));
            return true;
        }
        screens.showPlayerList(player, 0);
        return true;
    }

    /**
     * Suggests {@code global} for managers completing the first argument.
     *
     * <p>Only offers completions when {@code args.length == 1}; no suggestions are returned for
     * additional tokens or when the sender lacks {@code preferences.manage}.
     *
     * @param sender command source
     * @param command registered command instance
     * @param alias alias being completed
     * @param args current partial arguments
     * @return {@code global} when permitted and completing the subcommand, otherwise empty
     */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("preferences.manage")) return List.of("global");
        return List.of();
    }
}
