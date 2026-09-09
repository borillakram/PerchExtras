package com.olziedev.armorstandcmd.listeners;

import com.olziedev.armorstandcmd.ArmorStandCmd;
import com.olziedev.armorstandcmd.ArmorStandCmd.PendingAction;
import com.olziedev.armorstandcmd.utils.Configuration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerCommandSendEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;

import java.util.Arrays;
import java.util.List;

public class ArmorStandListener implements Listener {

    private static final List<String> HIDDEN_ALIASES = Arrays.asList("armorstandcmd", "ascmd");

    private final ArmorStandCmd plugin;

    public ArmorStandListener(ArmorStandCmd plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onCommandSend(PlayerCommandSendEvent event) {
        event.getCommands().removeIf(cmd -> {
            String name = cmd.contains(":") ? cmd.substring(cmd.indexOf(':') + 1) : cmd;
            return HIDDEN_ALIASES.contains(name.toLowerCase());
        });
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onRightClick(PlayerInteractAtEntityEvent event) {
        if (!(event.getRightClicked() instanceof ArmorStand stand)) return;
        handle(event.getPlayer(), stand, event);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onLeftClick(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof ArmorStand stand)) return;

        if (Configuration.getConfig().getBoolean("config.enableLeftClick", false)) {
            handle(player, stand, event);
            return;
        }

        if (plugin.isEditing(player)) return;
        if (plugin.hasCommands(stand)) event.setCancelled(true);
    }

    private void handle(Player player, ArmorStand stand, Cancellable event) {
        if (plugin.hasPending(player)) {
            applyPending(player, stand);
            event.setCancelled(true);
            return;
        }

        if (plugin.isEditing(player)) return;

        List<String> commands = plugin.getCommands(stand);
        if (commands.isEmpty()) return;

        event.setCancelled(true);
        if (plugin.checkCooldown(player)) return;

        for (String command : commands) {
            runCommand(player, command);
        }
    }

    private void applyPending(Player player, ArmorStand stand) {
        PendingAction action = plugin.consumePending(player);
        if (action == null) return;

        switch (action.getType()) {
            case CLEAR -> {
                plugin.clearCommands(stand);
                player.sendMessage(ArmorStandCmd.message("All commands cleared!"));
            }
            case INFO -> {
                List<String> commands = plugin.getCommands(stand);
                if (commands.isEmpty()) {
                    player.sendMessage(ArmorStandCmd.message("There are no commands on this armor stand!", NamedTextColor.RED));
                    return;
                }
                player.sendMessage(ArmorStandCmd.message("Commands on this armor stand:"));
                for (String command : commands) {
                    player.sendMessage(Component.text("- ")
                            .append(Component.text(command, NamedTextColor.GREEN)));
                }
            }
            case ADD -> {
                plugin.addCommand(stand, action.getCommand());
                player.sendMessage(ArmorStandCmd.message("Command added!"));
            }
        }
    }

    private void runCommand(Player player, String command) {
        String parsed = command.replace("%player%", player.getName());

        if (Configuration.getConfig().getBoolean("config.msgToConsole", true)) {
            plugin.getLogger().info("Ran command '" + parsed + "' by player '" + player.getName() + "'");
        }

        String label = parsed.split("\\s+")[0].toLowerCase();
        if (Bukkit.getPluginCommand(label) != null) {
            Bukkit.dispatchCommand(player, parsed);
            return;
        }

        PlayerCommandPreprocessEvent preprocess = new PlayerCommandPreprocessEvent(player, "/" + parsed);
        Bukkit.getPluginManager().callEvent(preprocess);
        if (!preprocess.isCancelled()) {
            Bukkit.dispatchCommand(player, preprocess.getMessage().substring(1));
        }
    }
}
