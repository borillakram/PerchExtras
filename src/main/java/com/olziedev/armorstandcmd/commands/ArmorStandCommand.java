package com.olziedev.armorstandcmd.commands;

import com.olziedev.armorstandcmd.ArmorStandCmd;
import com.olziedev.armorstandcmd.ArmorStandCmd.PendingAction;
import com.olziedev.armorstandcmd.utils.Configuration;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class ArmorStandCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "perchextras.armorstandcmd";

    private static final List<String> SUBCOMMANDS =
            Arrays.asList("addcmd", "clear", "info", "edit", "reload", "config");

    private static final List<String> CONFIG_KEYS =
            Arrays.asList("msgToConsole", "enableLeftClick", "cooldownMs");

    private final ArmorStandCmd plugin;

    public ArmorStandCommand(ArmorStandCmd plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(Component.text(
                    "You don't have permission to use that command (" + PERMISSION + ").", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase();

        if (sub.equals("reload")) {
            new Configuration(plugin).load();
            sender.sendMessage(ArmorStandCmd.message("Configuration reloaded.", NamedTextColor.GREEN));
            return true;
        }

        if (sub.equals("config")) {
            handleConfig(sender, args);
            return true;
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage(ArmorStandCmd.message("That subcommand can only be used by a player.", NamedTextColor.RED));
            return true;
        }

        switch (sub) {
            case "edit" -> {
                boolean editing = !plugin.isEditing(player);
                plugin.setEditing(player, editing);
                player.sendMessage(ArmorStandCmd.message("Edit mode " + (editing ? "enabled" : "disabled") + "."));
            }
            case "addcmd" -> {
                if (args.length < 2) {
                    player.sendMessage(ArmorStandCmd.message("Usage: /" + label + " addcmd <command>", NamedTextColor.RED));
                    return true;
                }
                String cmd = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
                plugin.setPending(player, new PendingAction(PendingAction.Type.ADD, cmd));
                player.sendMessage(ArmorStandCmd.message("Now right-click an armor stand to add the command to it."));
            }
            case "clear" -> {
                plugin.setPending(player, new PendingAction(PendingAction.Type.CLEAR, null));
                player.sendMessage(ArmorStandCmd.message("Now right-click an armor stand to clear its commands."));
            }
            case "info" -> {
                plugin.setPending(player, new PendingAction(PendingAction.Type.INFO, null));
                player.sendMessage(ArmorStandCmd.message("Now right-click an armor stand to list its commands."));
            }
            default -> sendHelp(player);
        }
        return true;
    }

    private void handleConfig(CommandSender sender, String[] args) {
        if (args.length != 3) {
            sender.sendMessage(ArmorStandCmd.message("Usage: /armorstandcommand config <key> <value>", NamedTextColor.RED));
            sender.sendMessage(ArmorStandCmd.PREFIX
                    .append(Component.text("Keys: ", NamedTextColor.GRAY))
                    .append(Component.text("msgToConsole", NamedTextColor.YELLOW))
                    .append(Component.text("(bool), ", NamedTextColor.GRAY))
                    .append(Component.text("enableLeftClick", NamedTextColor.YELLOW))
                    .append(Component.text("(bool), ", NamedTextColor.GRAY))
                    .append(Component.text("cooldownMs", NamedTextColor.YELLOW))
                    .append(Component.text("(int)", NamedTextColor.GRAY)));
            return;
        }

        String key = args[1];
        String value = args[2];

        if (key.equalsIgnoreCase("msgToConsole") || key.equalsIgnoreCase("enableLeftClick")) {
            Configuration.getConfig().set("config." + key, Boolean.parseBoolean(value));
        } else if (key.equalsIgnoreCase("cooldownMs")) {
            int ms;
            try {
                ms = Integer.parseInt(value);
            } catch (NumberFormatException ex) {
                sender.sendMessage(ArmorStandCmd.message("Value must be a number!", NamedTextColor.RED));
                return;
            }
            Configuration.getConfig().set("config.cooldownMs", ms);
        } else {
            sender.sendMessage(ArmorStandCmd.message("Unknown key: " + key, NamedTextColor.RED));
            return;
        }

        Configuration.save();
        sender.sendMessage(ArmorStandCmd.message("Configuration updated.", NamedTextColor.GREEN));
    }

    private void sendHelp(CommandSender sender) {
        sender.sendMessage(ArmorStandCmd.message(""));
        sender.sendMessage(helpLine("/armorstandcommand addcmd <command>", "Queue a command to add to a stand"));
        sender.sendMessage(helpLine("/armorstandcommand clear", "Clear a stand's commands"));
        sender.sendMessage(helpLine("/armorstandcommand info", "List a stand's commands"));
        sender.sendMessage(helpLine("/armorstandcommand edit", "Toggle edit mode"));
        sender.sendMessage(helpLine("/armorstandcommand reload", "Reload the config"));
        sender.sendMessage(helpLine("/armorstandcommand config <key> <value>", "Change a config value"));
    }

    private Component helpLine(String usage, String description) {
        return Component.text("- ")
                .append(Component.text(usage + " ", NamedTextColor.GREEN))
                .append(Component.text(description, NamedTextColor.GRAY));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        // Only offer completions for the full command, not its aliases (armorstandcmd, ascmd).
        if (!label.equalsIgnoreCase("armorstandcommand")) return Collections.emptyList();
        if (!sender.hasPermission(PERMISSION)) return Collections.emptyList();

        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("config")) {
            return filter(CONFIG_KEYS, args[1]);
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("config")
                && (args[1].equalsIgnoreCase("msgToConsole") || args[1].equalsIgnoreCase("enableLeftClick"))) {
            return filter(Arrays.asList("true", "false"), args[2]);
        }

        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String typed) {
        String prefix = typed.toLowerCase();
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase().startsWith(prefix)) matches.add(option);
        }
        return matches;
    }
}
