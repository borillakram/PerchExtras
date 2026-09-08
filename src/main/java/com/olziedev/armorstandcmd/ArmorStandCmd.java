package com.olziedev.armorstandcmd;

import com.olziedev.armorstandcmd.commands.ArmorStandCommand;
import com.olziedev.armorstandcmd.listeners.ArmorStandListener;
import com.olziedev.armorstandcmd.utils.Configuration;
import com.olziedev.spotextras.api.SpotPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class ArmorStandCmd extends SpotPlugin {

    public static final Component PREFIX = Component.text("[", NamedTextColor.GOLD)
            .append(Component.text("Evergreen Armorstand Commands", NamedTextColor.DARK_GREEN))
            .append(Component.text("] ", NamedTextColor.GOLD));

    private static final String SEPARATOR = "\n";

    private static ArmorStandCmd instance;

    private NamespacedKey commandsKey;

    private final Set<UUID> editing = new HashSet<>();

    private final Map<UUID, PendingAction> pending = new HashMap<>();

    private final Map<UUID, Long> lastUsed = new HashMap<>();

    @Override
    public String getName() {
        return "ArmorStandCmd";
    }

    @Override
    public void onEnable() {
        instance = this;
        this.commandsKey = new NamespacedKey(this.plugin, "armorstand_commands");
        new Configuration(this).load();

        Bukkit.getPluginManager().registerEvents(new ArmorStandListener(this), this.plugin);

        JavaPlugin javaPlugin = (JavaPlugin) this.plugin;
        if (javaPlugin.getCommand("armorstandcommand") != null) {
            ArmorStandCommand executor = new ArmorStandCommand(this);
            javaPlugin.getCommand("armorstandcommand").setExecutor(executor);
            javaPlugin.getCommand("armorstandcommand").setTabCompleter(executor);
        } else {
            getLogger().warn("Could not find 'armorstandcommand' in plugin.yml!");
        }
    }

    @Override
    public void onDisable() {
        editing.clear();
        pending.clear();
        lastUsed.clear();
        instance = null;
    }

    public static ArmorStandCmd getInstance() {
        return instance;
    }

    public static Component message(String text) {
        return message(text, NamedTextColor.GRAY);
    }

    public static Component message(String text, NamedTextColor color) {
        return PREFIX.append(Component.text(text, color));
    }

    public NamespacedKey getCommandsKey() {
        return commandsKey;
    }

    // --- Persistent command storage on the armor stand ---------------------

    public List<String> getCommands(ArmorStand stand) {
        PersistentDataContainer container = stand.getPersistentDataContainer();
        String stored = container.get(commandsKey, PersistentDataType.STRING);
        if (stored == null || stored.isEmpty()) return new ArrayList<>();

        List<String> commands = new ArrayList<>();
        for (String command : stored.split(SEPARATOR)) {
            if (!command.isEmpty()) commands.add(command);
        }
        return commands;
    }

    public void setCommands(ArmorStand stand, List<String> commands) {
        PersistentDataContainer container = stand.getPersistentDataContainer();
        if (commands.isEmpty()) {
            container.remove(commandsKey);
        } else {
            container.set(commandsKey, PersistentDataType.STRING, String.join(SEPARATOR, commands));
        }
    }

    public void addCommand(ArmorStand stand, String command) {
        List<String> commands = getCommands(stand);
        commands.add(command);
        setCommands(stand, commands);
    }

    public void clearCommands(ArmorStand stand) {
        stand.getPersistentDataContainer().remove(commandsKey);
    }

    public boolean hasCommands(ArmorStand stand) {
        return !getCommands(stand).isEmpty();
    }

    // --- Per-player edit / pending / cooldown state ------------------------

    public boolean isEditing(Player player) {
        return editing.contains(player.getUniqueId());
    }

    public void setEditing(Player player, boolean value) {
        if (value) {
            editing.add(player.getUniqueId());
        } else {
            editing.remove(player.getUniqueId());
        }
    }

    public void setPending(Player player, PendingAction action) {
        pending.put(player.getUniqueId(), action);
    }

    public PendingAction consumePending(Player player) {
        return pending.remove(player.getUniqueId());
    }

    public boolean hasPending(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    public boolean checkCooldown(Player player) {
        long now = System.currentTimeMillis();
        long cooldown = Configuration.getConfig().getLong("config.cooldownMs", 100L);
        Long last = lastUsed.get(player.getUniqueId());
        if (last != null && now - last < cooldown) {
            return true;
        }
        lastUsed.put(player.getUniqueId(), now);
        return false;
    }

    public static final class PendingAction {
        public enum Type { ADD, CLEAR, INFO }

        private final Type type;
        private final String command;

        public PendingAction(Type type, String command) {
            this.type = type;
            this.command = command;
        }

        public Type getType() {
            return type;
        }

        public String getCommand() {
            return command;
        }
    }
}
