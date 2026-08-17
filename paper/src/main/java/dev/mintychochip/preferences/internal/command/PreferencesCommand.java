package dev.mintychochip.preferences.internal.command;

import dev.mintychochip.preferences.internal.dialog.DialogScreens;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class PreferencesCommand implements TabExecutor {

    private final DialogScreens screens;

    public PreferencesCommand(DialogScreens screens) {
        this.screens = screens;
    }

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

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("preferences.manage")) return List.of("global");
        return List.of();
    }
}
