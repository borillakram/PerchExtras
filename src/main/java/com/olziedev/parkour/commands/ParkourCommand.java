package com.olziedev.parkour.commands;

import com.olziedev.parkour.Parkour;
import com.olziedev.parkour.ParkourManager;
import com.olziedev.parkour.model.ParkourCourse;
import com.olziedev.parkour.model.ParkourPoint;
import com.olziedev.parkour.model.ParkourType;
import com.olziedev.parkour.utils.Messages;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class ParkourCommand implements CommandExecutor, TabCompleter {

    private static final String ADMIN_PERMISSION = "evergreen.parkour.admin";

    private static final List<String> PLAYER_SUBS = Arrays.asList("reset", "stop");
    private static final List<String> ADMIN_SUBS =
            Arrays.asList("reload", "create", "delete", "add", "remove", "checkpoints", "reorder", "endcmd");
    private static final List<String> TYPES = Arrays.asList("start", "checkpoint", "end");
    private static final List<String> ENDCMD_ACTIONS = Arrays.asList("add", "remove", "list");

    private final ParkourManager manager;

    public ParkourCommand(Parkour plugin) {
        this.manager = plugin.getManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "reset" -> {
                if (requirePlayer(sender)) manager.reset((Player) sender);
            }
            case "stop" -> {
                if (requirePlayer(sender)) manager.stop((Player) sender);
            }
            case "reload", "create", "delete", "add", "remove", "checkpoints", "reorder", "endcmd" -> {
                if (!sender.hasPermission(ADMIN_PERMISSION)) {
                    send(sender, "&cYou don't have permission to use that (" + ADMIN_PERMISSION + ").");
                    return true;
                }
                handleAdmin(sender, sub, args);
            }
            default -> sendHelp(sender);
        }
        return true;
    }

    private void handleAdmin(CommandSender sender, String sub, String[] args) {
        switch (sub) {
            case "reload" -> {
                manager.reload();
                send(sender, "&#48ab76Reloaded config.yml and courses.yml.");
            }
            case "create" -> {
                if (args.length < 2) {
                    send(sender, "&cUsage: /parkour create <name>");
                    return;
                }
                String name = args[1];
                if (manager.createCourse(name)) {
                    send(sender, "&#48ab76Created parkour &#7db597" + name + "&#48ab76.");
                } else {
                    send(sender, "&cA parkour named &#7db597" + name + "&c already exists.");
                }
            }
            case "delete" -> {
                if (args.length < 2) {
                    send(sender, "&cUsage: /parkour delete <name>");
                    return;
                }
                if (!requirePlayer(sender)) return;
                ParkourCourse course = manager.getCourse(args[1]);
                if (course == null) {
                    send(sender, "&cNo parkour named &#7db597" + args[1] + "&c.");
                    return;
                }
                manager.requestDeletion(((Player) sender).getUniqueId(), course.getName());
                sender.sendMessage(Messages.prefixed("delete-confirm",
                        "parkour", course.getName(), "word", manager.getConfirmWord()));
            }
            case "add" -> handleAddOrRemove(sender, args, true);
            case "remove" -> handleAddOrRemove(sender, args, false);
            case "checkpoints" -> handleCheckpoints(sender, args);
            case "reorder" -> handleReorder(sender, args);
            case "endcmd" -> handleEndCmd(sender, args);
        }
    }

    private void handleEndCmd(CommandSender sender, String[] args) {
        if (args.length < 3) {
            send(sender, "&cUsage: /parkour endcmd <add|remove|list> <name> [...]");
            return;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        ParkourCourse course = manager.getCourse(args[2]);
        if (course == null) {
            send(sender, "&cNo parkour named &#7db597" + args[2] + "&c.");
            return;
        }

        switch (action) {
            case "add" -> {
                if (args.length < 4) {
                    send(sender, "&cUsage: /parkour endcmd add <name> <command>");
                    return;
                }
                String command = String.join(" ", Arrays.copyOfRange(args, 3, args.length));
                manager.addEndCommand(course, command);
                send(sender, "&#48ab76Added end command &#7db597#" + course.getEndCommands().size()
                        + "&#48ab76 to &#7db597" + course.getName() + "&#48ab76: &f" + command);
            }
            case "remove" -> {
                if (args.length < 4) {
                    send(sender, "&cUsage: /parkour endcmd remove <name> <number>");
                    return;
                }
                int index;
                try {
                    index = Integer.parseInt(args[3]);
                } catch (NumberFormatException ex) {
                    send(sender, "&cThe number must be a valid position (see /parkour endcmd list " + course.getName() + ").");
                    return;
                }
                if (manager.removeEndCommand(course, index)) {
                    send(sender, "&#48ab76Removed end command &#7db597#" + index + "&#48ab76 from &#7db597" + course.getName() + "&#48ab76.");
                } else {
                    send(sender, "&cInvalid number. Valid range is 1-" + course.getEndCommands().size() + ".");
                }
            }
            case "list" -> {
                List<String> commands = course.getEndCommands();
                if (commands.isEmpty()) {
                    send(sender, "&#7db597" + course.getName() + "&7 has no end commands.");
                    return;
                }
                send(sender, "&7End commands for &#7db597" + course.getName() + "&7 (run as console on finish):");
                for (int i = 0; i < commands.size(); i++) {
                    send(sender, "&#7db597#" + (i + 1) + " &f" + commands.get(i));
                }
            }
            default -> send(sender, "&cUsage: /parkour endcmd <add|remove|list> <name> [...]");
        }
    }

    private void handleAddOrRemove(CommandSender sender, String[] args, boolean add) {
        String verb = add ? "add" : "remove";
        if (args.length < 3) {
            send(sender, "&cUsage: /parkour " + verb + " <start|checkpoint|end> <name>");
            return;
        }
        ParkourType type = parseType(args[1]);
        if (type == null) {
            send(sender, "&cType must be one of: start, checkpoint, end.");
            return;
        }
        ParkourCourse course = manager.getCourse(args[2]);
        if (course == null) {
            send(sender, "&cNo parkour named &#7db597" + args[2] + "&c. Create it with /parkour create.");
            return;
        }

        // Removing the single start/end doesn't require standing on the plate.
        if (!add && type != ParkourType.CHECKPOINT) {
            String typeName = type.name().toLowerCase(Locale.ROOT);
            boolean removed = manager.removePoint(course, type);
            send(sender, removed
                    ? "&#48ab76Removed the " + typeName + " of &#7db597" + course.getName() + "&#48ab76."
                    : "&#7db597" + course.getName() + "&c has no " + typeName + " set.");
            return;
        }

        // Adding anything, or removing a specific checkpoint, acts on the plate underfoot.
        if (!requirePlayer(sender)) return;
        Player player = (Player) sender;
        Block plate = plateStandingOn(player);
        if (plate == null) {
            send(sender, "&cYou must be standing on a pressure plate to do that.");
            return;
        }

        if (add) {
            ParkourPoint point = ParkourPoint.of(plate, player.getLocation().getYaw(), player.getLocation().getPitch());
            manager.setPoint(course, type, point);
            // Don't let this plate trigger for the admin while they're still standing on it
            manager.suppressPlate(player.getUniqueId(), ParkourPoint.keyOf(plate));
            if (type == ParkourType.CHECKPOINT) {
                send(sender, "&#48ab76Added checkpoint &#7db597#" + course.getCheckpoints().size()
                        + "&#48ab76 to &#7db597" + course.getName() + "&#48ab76.");
            } else {
                send(sender, "&#48ab76Set the " + type.name().toLowerCase(Locale.ROOT)
                        + " of &#7db597" + course.getName() + "&#48ab76.");
            }
        } else {
            boolean removed = manager.removePoint(course, type, plate);
            send(sender, removed
                    ? "&#48ab76Removed the checkpoint plate from &#7db597" + course.getName() + "&#48ab76."
                    : "&cThis plate isn't a checkpoint of &#7db597" + course.getName() + "&c.");
        }
    }

    private void handleCheckpoints(CommandSender sender, String[] args) {
        if (args.length < 2) {
            send(sender, "&cUsage: /parkour checkpoints <name>");
            return;
        }
        ParkourCourse course = manager.getCourse(args[1]);
        if (course == null) {
            send(sender, "&cNo parkour named &#7db597" + args[1] + "&c.");
            return;
        }
        List<ParkourPoint> checkpoints = course.getCheckpoints();
        if (checkpoints.isEmpty()) {
            send(sender, "&#7db597" + course.getName() + "&7 has no checkpoints.");
            return;
        }
        send(sender, "&7Checkpoints for &#7db597" + course.getName() + "&7:");
        for (int i = 0; i < checkpoints.size(); i++) {
            send(sender, "&#7db597#" + (i + 1) + " &7" + checkpoints.get(i).describe());
        }
    }

    private void handleReorder(CommandSender sender, String[] args) {
        if (args.length < 4) {
            send(sender, "&cUsage: /parkour reorder <name> <from> <to>");
            return;
        }
        ParkourCourse course = manager.getCourse(args[1]);
        if (course == null) {
            send(sender, "&cNo parkour named &#7db597" + args[1] + "&c.");
            return;
        }
        int from, to;
        try {
            from = Integer.parseInt(args[2]);
            to = Integer.parseInt(args[3]);
        } catch (NumberFormatException ex) {
            send(sender, "&cPositions must be numbers (see /parkour checkpoints " + course.getName() + ").");
            return;
        }
        if (manager.reorderCheckpoint(course, from, to)) {
            send(sender, "&#48ab76Moved checkpoint &#7db597#" + from + "&#48ab76 to position &#7db597#" + to + "&#48ab76.");
        } else {
            send(sender, "&cInvalid positions. Valid range is 1-" + course.getCheckpoints().size() + ".");
        }
    }

    private ParkourType parseType(String input) {
        try {
            return ParkourType.valueOf(input.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private Block plateStandingOn(Player player) {
        Block block = player.getLocation().getBlock();
        return Tag.PRESSURE_PLATES.isTagged(block.getType()) ? block : null;
    }

    private boolean requirePlayer(CommandSender sender) {
        if (sender instanceof Player) return true;
        send(sender, "&cThat subcommand can only be used by a player.");
        return false;
    }

    private void send(CommandSender sender, String message) {
        sender.sendMessage(Messages.get("prefix").append(Messages.parse(message)));
    }

    private void sendHelp(CommandSender sender) {
        send(sender, "&#48ab76&lParkour commands:");
        send(sender, "&#7db597/parkour reset &7- teleport back to your last checkpoint");
        send(sender, "&#7db597/parkour stop &7- stop your parkour and restore abilities");
        if (sender.hasPermission(ADMIN_PERMISSION)) {
            send(sender, "&#7db597/parkour reload &7- reload config.yml and courses.yml");
            send(sender, "&#7db597/parkour create <name> &7- create a course");
            send(sender, "&#7db597/parkour delete <name> &7- delete a whole course");
            send(sender, "&#7db597/parkour add <start|checkpoint|end> <name> &7- add the plate you're on");
            send(sender, "&#7db597/parkour remove <start|checkpoint|end> <name> &7- remove the plate you're on");
            send(sender, "&#7db597/parkour checkpoints <name> &7- list numbered checkpoints");
            send(sender, "&#7db597/parkour reorder <name> <from> <to> &7- reorder a checkpoint");
            send(sender, "&#7db597/parkour endcmd <add|remove|list> <name> [...] &7- manage finish commands");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        boolean admin = sender.hasPermission(ADMIN_PERMISSION);

        if (args.length == 1) {
            List<String> subs = new ArrayList<>(PLAYER_SUBS);
            if (admin) subs.addAll(ADMIN_SUBS);
            return filter(subs, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            if (admin && (sub.equals("add") || sub.equals("remove"))) return filter(TYPES, args[1]);
            if (admin && sub.equals("endcmd")) return filter(ENDCMD_ACTIONS, args[1]);
            if (admin && (sub.equals("delete") || sub.equals("checkpoints") || sub.equals("reorder"))) return filter(manager.getCourseNames(), args[1]);
            return Collections.emptyList();
        }
        if (args.length == 3 && admin && (sub.equals("add") || sub.equals("remove") || sub.equals("endcmd"))) {
            return filter(manager.getCourseNames(), args[2]);
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> options, String typed) {
        String prefix = typed.toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) matches.add(option);
        }
        return matches;
    }
}
