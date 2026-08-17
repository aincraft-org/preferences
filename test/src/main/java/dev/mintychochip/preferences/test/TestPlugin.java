package dev.mintychochip.preferences.test;

import dev.mintychochip.preferences.api.Preference;
import dev.mintychochip.preferences.api.PreferencesService;
import dev.mintychochip.preferences.api.codec.PreferenceCodec;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Integration / hooking-plugin fixture: registers typed preferences only through the public
 * {@link PreferencesService} API (same path a third-party plugin would use).
 */
public final class TestPlugin extends JavaPlugin {

    public enum Weather { SUNNY, RAINY, STORMY }

    @Override
    public void onEnable() {
        PreferencesService prefs = Bukkit.getServicesManager().load(PreferencesService.class);
        if (prefs == null) {
            getLogger().severe("Preferences service missing!");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }

        Preference<Boolean> notifications = prefs.register(this, Boolean.class, b -> b
            .playerScoped("notifications")
            .label(Component.text("Notifications"))
            .description(Component.text("Receive test notifications"))
            .codec(PreferenceCodec.booleanBox())
            .defaultValue(true)
            .onChange(c -> getLogger().info("notifications: " + c.oldValue() + " -> " + c.newValue())));

        Preference<Integer> volume = prefs.register(this, Integer.class, b -> b
            .playerScoped("volume")
            .label(Component.text("Volume"))
            .description(Component.text("Test sound volume"))
            .codec(PreferenceCodec.integerSlider(0, 100, 5))
            .defaultValue(70));

        Preference<Weather> weather = prefs.register(this, Weather.class, b -> b
            .playerScoped("weather")
            .label(Component.text("Weather"))
            .description(Component.text("Preferred test weather"))
            .codec(PreferenceCodec.enumerated(Weather.class, w -> Component.text(w.name().toLowerCase())))
            .defaultValue(Weather.SUNNY));

        Preference<String> nickname = prefs.register(this, String.class, b -> b
            .playerScoped("nickname")
            .label(Component.text("Nickname"))
            .description(Component.text("Display name in tests"))
            .codec(PreferenceCodec.string(32))
            .defaultValue("Player"));

        Preference<Boolean> announce = prefs.register(this, Boolean.class, b -> b
            .global("announce_logins")
            .label(Component.text("Announce Logins"))
            .description(Component.text("Broadcast join messages"))
            .codec(PreferenceCodec.booleanBox())
            .defaultValue(false));

        getLogger().info("Registered 5 test preferences via PreferencesService; notifications default="
            + notifications.defaultValue()
            + ", volume default=" + volume.defaultValue()
            + ", weather default=" + weather.defaultValue()
            + ", nickname default=" + nickname.defaultValue()
            + ", announce default=" + announce.defaultValue());
    }
}
