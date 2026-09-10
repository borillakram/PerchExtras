package com.olziedev.parkour;

import com.olziedev.parkour.model.ParkourCourse;
import com.olziedev.parkour.model.ParkourPoint;
import com.olziedev.parkour.model.ParkourType;
import com.olziedev.parkour.model.PlateMatch;
import com.olziedev.parkour.model.PlayerProgress;
import com.olziedev.parkour.utils.Configuration;
import com.olziedev.parkour.utils.Messages;
import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ParkourManager {

    private final Parkour plugin;

    private final Map<String, ParkourCourse> courses = new LinkedHashMap<>();
    private final Map<String, PlateMatch> plateIndex = new HashMap<>();
    private final Map<UUID, PlayerProgress> progress = new HashMap<>();
    private final Set<String> blockedCommands = new HashSet<>();
    private final Set<Material> resetBlocks = new HashSet<>();
    private final Map<UUID, String> suppressedPlate = new HashMap<>();
    private final Map<UUID, PendingDeletion> pendingDeletions = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();

    private File coursesFile;
    private FileConfiguration coursesConfig;

    private File playersFile;
    private FileConfiguration playersConfig;

    public ParkourManager(Parkour plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        loadCourses();
        loadBlockedCommands();
        loadResetBlocks();
        loadPlayers();
    }

    public void reload() {
        new Configuration(plugin).load();
        loadBlockedCommands();
        loadResetBlocks();
        loadCourses();
    }

    private void loadCourses() {
        courses.clear();
        plateIndex.clear();

        coursesFile = new File(plugin.getDataFolder(), "courses.yml");
        if (!coursesFile.getParentFile().exists()) coursesFile.getParentFile().mkdirs();
        if (!coursesFile.exists()) plugin.saveResource("Parkour" + File.separator + "courses.yml", false);
        coursesConfig = YamlConfiguration.loadConfiguration(coursesFile);

        ConfigurationSection section = coursesConfig.getConfigurationSection("parkours");
        if (section != null) {
            for (String name : section.getKeys(false)) {
                ConfigurationSection courseSection = section.getConfigurationSection(name);
                if (courseSection == null) continue;
                ParkourCourse course = ParkourCourse.deserialize(name, courseSection);
                courses.put(name.toLowerCase(Locale.ROOT), course);
            }
        }
        rebuildIndex();
    }

    private void loadBlockedCommands() {
        blockedCommands.clear();
        for (String command : Configuration.getConfig().getStringList("settings.blocked-commands")) {
            blockedCommands.add(command.toLowerCase(Locale.ROOT));
        }
    }

    private void loadResetBlocks() {
        resetBlocks.clear();
        for (String name : Configuration.getConfig().getStringList("settings.reset-blocks")) {
            Material material = Material.matchMaterial(name);
            if (material == null) {
                plugin.getLogger().warn("Unknown reset-block material: " + name);
                continue;
            }
            resetBlocks.add(material);
        }
    }

    private void rebuildIndex() {
        plateIndex.clear();
        for (ParkourCourse course : courses.values()) {
            if (course.getStart() != null) {
                plateIndex.put(course.getStart().key(), new PlateMatch(course.getName(), ParkourType.START, -1));
            }
            if (course.getEnd() != null) {
                plateIndex.put(course.getEnd().key(), new PlateMatch(course.getName(), ParkourType.END, -1));
            }
            List<ParkourPoint> checkpoints = course.getCheckpoints();
            for (int i = 0; i < checkpoints.size(); i++) {
                plateIndex.put(checkpoints.get(i).key(), new PlateMatch(course.getName(), ParkourType.CHECKPOINT, i));
            }
        }
    }

    public void saveCourses() {
        coursesConfig.set("parkours", null);
        ConfigurationSection root = coursesConfig.createSection("parkours");
        for (ParkourCourse course : courses.values()) {
            course.serialize(root.createSection(course.getName()));
        }
        try {
            coursesConfig.save(coursesFile);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
        rebuildIndex();
    }

    private void loadPlayers() {
        playersFile = new File(plugin.getDataFolder(), "players.yml");
        if (!playersFile.getParentFile().exists()) playersFile.getParentFile().mkdirs();
        playersConfig = YamlConfiguration.loadConfiguration(playersFile);

        progress.clear();
        ConfigurationSection section = playersConfig.getConfigurationSection("players");
        if (section == null) return;
        for (String uuid : section.getKeys(false)) {
            String parkour = section.getString(uuid + ".parkour");
            int checkpoint = section.getInt(uuid + ".checkpoint", PlayerProgress.START);
            boolean flight = section.getBoolean(uuid + ".flight", false);
            long elapsed = section.getLong(uuid + ".elapsed", 0L);
            if (parkour == null) continue;
            // Drop progress for courses that no longer exist.
            if (!courses.containsKey(parkour.toLowerCase(Locale.ROOT))) continue;
            try {
                // Loaded paused; timing resumes when the player rejoins (reapplyRestrictions).
                progress.put(UUID.fromString(uuid), new PlayerProgress(parkour, checkpoint, flight, elapsed));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void persist(UUID uuid) {
        writePlayer(uuid);
        savePlayersFile();
    }

    private void writePlayer(UUID uuid) {
        PlayerProgress p = progress.get(uuid);
        if (p == null) {
            playersConfig.set("players." + uuid, null);
        } else {
            playersConfig.set("players." + uuid + ".parkour", p.getParkourName());
            playersConfig.set("players." + uuid + ".checkpoint", p.getCheckpointIndex());
            playersConfig.set("players." + uuid + ".flight", p.hadFlight());
            playersConfig.set("players." + uuid + ".elapsed", p.getElapsedMillis());
        }
    }

    private void savePlayersFile() {
        try {
            playersConfig.save(playersFile);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public void saveOnDisable() {
        // Pause running timers so the current session's time is captured on a clean shutdown.
        long now = System.currentTimeMillis();
        for (UUID uuid : progress.keySet()) {
            PlayerProgress p = progress.get(uuid);
            if (p != null && p.isRunning()) p.pauseSession(now);
            writePlayer(uuid);
        }
        savePlayersFile();
    }

    public boolean handlePlate(Player player, Block block) {
        String key = ParkourPoint.keyOf(block);
        // Ignore the plate an admin just added while they're still standing on it.
        if (key.equals(suppressedPlate.get(player.getUniqueId()))) return true;

        PlateMatch match = plateIndex.get(key);
        if (match == null) return false;

        ParkourCourse course = courses.get(match.getCourseName().toLowerCase(Locale.ROOT));
        if (course == null) return false;

        switch (match.getType()) {
            case START -> handleStart(player, course);
            case CHECKPOINT -> handleCheckpoint(player, course, match.getCheckpointIndex());
            case END -> handleEnd(player, course);
        }
        return true;
    }

    private void handleStart(Player player, ParkourCourse course) {
        PlayerProgress current = progress.get(player.getUniqueId());
        if (current != null && current.getParkourName().equalsIgnoreCase(course.getName())) return;

        boolean hadFlight = player.getAllowFlight();
        PlayerProgress fresh = new PlayerProgress(course.getName(), PlayerProgress.START, hadFlight, 0L);
        fresh.startSession(System.currentTimeMillis());
        progress.put(player.getUniqueId(), fresh);
        applyRestrictions(player);
        persist(player.getUniqueId());
        showBossBar(player, course, fresh);
        Messages.send(player, "started", "parkour", course.getName());
    }

    private void handleCheckpoint(Player player, ParkourCourse course, int index) {
        PlayerProgress current = progress.get(player.getUniqueId());
        if (current == null || !current.getParkourName().equalsIgnoreCase(course.getName())) return;
        if (index <= current.getCheckpointIndex()) return;

        current.setCheckpointIndex(index);
        persist(player.getUniqueId());
        showBossBar(player, course, current);
        Messages.send(player, "checkpoint", "stage", String.valueOf(current.getStage()), "parkour", course.getName());
    }

    private void handleEnd(Player player, ParkourCourse course) {
        PlayerProgress current = progress.get(player.getUniqueId());
        if (current == null || !current.getParkourName().equalsIgnoreCase(course.getName())) return;

        long time = current.getElapsed(System.currentTimeMillis());
        progress.remove(player.getUniqueId());
        persist(player.getUniqueId());
        restoreAbilities(player, current.hadFlight());
        completeBossBar(player, course, time);
        Messages.send(player, "completed", "parkour", course.getName(), "time", formatTime(time));

        for (String command : course.getEndCommands()) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), applyPlaceholders(player, course.getName(), command));
        }
    }

    private String applyPlaceholders(Player player, String parkourName, String text) {
        String result = text
                .replace("%player%", player.getName())
                .replace("%player_name%", player.getName())
                .replace("%uuid%", player.getUniqueId().toString())
                .replace("%parkour%", parkourName);

        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            try {
                Class<?> papi = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
                result = (String) papi
                        .getMethod("setPlaceholders", org.bukkit.OfflinePlayer.class, String.class)
                        .invoke(null, player, result);
            } catch (Throwable ex) {
                plugin.getLogger().warn("Failed to apply PlaceholderAPI placeholders: " + ex.getMessage());
            }
        }
        return result;
    }

    public void reset(Player player) {
        if (!isDoingParkour(player.getUniqueId())) {
            Messages.send(player, "not-in-parkour");
            return;
        }
        Boolean toStart = teleportToCheckpoint(player);
        if (toStart == null) {
            Messages.send(player, "not-in-parkour");
            return;
        }
        Messages.send(player, toStart ? "reset-start" : "reset");
    }

    public void resetToCheckpointSilently(Player player) {
        Bukkit.getScheduler().runTask(plugin.plugin, () -> {
            if (!isDoingParkour(player.getUniqueId())) return;
            teleportToCheckpoint(player);
        });
    }

    public void tick() {
        tickResetBlocks();
        tickBossBars();
    }

    private void tickResetBlocks() {
        if (resetBlocks.isEmpty() || progress.isEmpty()) return;
        for (UUID uuid : progress.keySet()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) checkResetBlock(player);
        }
    }

    private boolean bossBarEnabled() {
        return Configuration.getConfig().getBoolean("settings.bossbar.enabled", true);
    }

    private BossBar.Color bossBarColor() {
        try {
            return BossBar.Color.valueOf(
                    Configuration.getConfig().getString("settings.bossbar.color", "GREEN").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return BossBar.Color.GREEN;
        }
    }

    private float progressFraction(ParkourCourse course, PlayerProgress p) {
        int segments = course.getCheckpoints().size() + 1; // start -> cp... -> end
        float fraction = segments <= 0 ? 0f : (float) p.getStage() / segments;
        return Math.max(0f, Math.min(1f, fraction));
    }

    private Component bossBarTitle(ParkourCourse course, PlayerProgress p) {
        return Messages.get("bossbar", "parkour", course.getName(),
                "time", formatTime(p.getElapsed(System.currentTimeMillis())));
    }

    private void showBossBar(Player player, ParkourCourse course, PlayerProgress p) {
        if (!bossBarEnabled()) return;
        BossBar bar = bossBars.get(player.getUniqueId());
        if (bar == null) {
            bar = BossBar.bossBar(bossBarTitle(course, p), progressFraction(course, p),
                    bossBarColor(), BossBar.Overlay.PROGRESS);
            bossBars.put(player.getUniqueId(), bar);
        } else {
            bar.name(bossBarTitle(course, p));
            bar.progress(progressFraction(course, p));
        }
        player.showBossBar(bar);
    }

    private void removeBossBar(Player player) {
        BossBar bar = bossBars.remove(player.getUniqueId());
        if (bar != null) player.hideBossBar(bar);
    }

    private void completeBossBar(Player player, ParkourCourse course, long finalMillis) {
        BossBar bar = bossBars.remove(player.getUniqueId());
        if (bar == null) return;
        bar.name(Messages.get("bossbar", "parkour", course.getName(), "time", formatTime(finalMillis)));
        bar.progress(1.0f);
        Bukkit.getScheduler().runTaskLater(plugin.plugin, () -> player.hideBossBar(bar), 40L);
    }

    private void tickBossBars() {
        if (bossBars.isEmpty()) return;
        for (Map.Entry<UUID, BossBar> entry : bossBars.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            PlayerProgress p = progress.get(entry.getKey());
            if (player == null || p == null) continue;
            ParkourCourse course = courses.get(p.getParkourName().toLowerCase(Locale.ROOT));
            if (course != null) entry.getValue().name(bossBarTitle(course, p));
        }
    }

    public void checkResetBlock(Player player) {
        if (resetBlocks.isEmpty() || !isDoingParkour(player.getUniqueId())) return;
        // isInWater() catches waterlogged/flowing cases the feet-block type check would miss.
        boolean inFailBlock = resetBlocks.contains(player.getLocation().getBlock().getType())
                || (resetBlocks.contains(Material.WATER) && player.isInWater());
        if (inFailBlock) {
            resetToCheckpointSilently(player);
        }
    }

    private Boolean teleportToCheckpoint(Player player) {
        PlayerProgress current = progress.get(player.getUniqueId());
        if (current == null) return null;
        ParkourCourse course = courses.get(current.getParkourName().toLowerCase(Locale.ROOT));
        if (course == null) {
            progress.remove(player.getUniqueId());
            persist(player.getUniqueId());
            removeBossBar(player);
            return null;
        }

        List<ParkourPoint> checkpoints = course.getCheckpoints();
        int index = current.getCheckpointIndex();
        boolean toStart = index < 0 || index >= checkpoints.size();
        ParkourPoint target = toStart ? course.getStart() : checkpoints.get(index);
        if (target == null) target = course.getStart();

        Location location = target == null ? null : target.toLocation();
        if (location == null) return null;

        player.setFallDistance(0f);
        player.teleport(location);
        applyRestrictions(player);
        return toStart;
    }

    public void stop(Player player) {
        PlayerProgress current = progress.remove(player.getUniqueId());
        if (current == null) {
            Messages.send(player, "not-in-parkour");
            return;
        }
        persist(player.getUniqueId());
        restoreAbilities(player, current.hadFlight());
        removeBossBar(player);
        Messages.send(player, "stopped");
    }

    public void reapplyRestrictions(Player player) {
        PlayerProgress current = progress.get(player.getUniqueId());
        if (current == null) return;
        applyRestrictions(player);
        current.startSession(System.currentTimeMillis());
        ParkourCourse course = courses.get(current.getParkourName().toLowerCase(Locale.ROOT));
        if (course != null) showBossBar(player, course, current);
    }

    public void handleQuit(Player player) {
        PlayerProgress current = progress.get(player.getUniqueId());
        if (current == null) return;
        current.pauseSession(System.currentTimeMillis());
        persist(player.getUniqueId());
        removeBossBar(player);
    }

    private String formatTime(long millis) {
        long totalSeconds = millis / 1000;
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        long ms = millis % 1000;
        if (hours > 0) {
            return String.format("%d:%02d:%02d.%03d", hours, minutes, seconds, ms);
        }
        return String.format("%d:%02d.%03d", minutes, seconds, ms);
    }

    private void applyRestrictions(Player player) {
        player.setFlying(false);
        player.setAllowFlight(false);
        player.setGliding(false);
    }

    private void restoreAbilities(Player player, boolean hadFlight) {
        GameMode mode = player.getGameMode();
        if (hadFlight || mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) {
            player.setAllowFlight(true);
        }
    }

    public void suppressPlate(UUID uuid, String plateKey) {
        suppressedPlate.put(uuid, plateKey);
    }

    public boolean hasSuppressions() {
        return !suppressedPlate.isEmpty();
    }

    public void clearSuppression(UUID uuid) {
        suppressedPlate.remove(uuid);
    }

    public void clearSuppressionIfMoved(Player player) {
        String suppressed = suppressedPlate.get(player.getUniqueId());
        if (suppressed != null && !suppressed.equals(ParkourPoint.keyOf(player.getLocation().getBlock()))) {
            suppressedPlate.remove(player.getUniqueId());
        }
    }

    public boolean isDoingParkour(UUID uuid) {
        return progress.containsKey(uuid);
    }

    public boolean isBlockedCommand(String label) {
        return blockedCommands.contains(label.toLowerCase(Locale.ROOT));
    }

    public ParkourCourse getCourse(String name) {
        return courses.get(name.toLowerCase(Locale.ROOT));
    }

    public List<String> getCourseNames() {
        List<String> names = new ArrayList<>();
        for (ParkourCourse course : courses.values()) names.add(course.getName());
        return names;
    }

    public boolean createCourse(String name) {
        if (courses.containsKey(name.toLowerCase(Locale.ROOT))) return false;
        courses.put(name.toLowerCase(Locale.ROOT), new ParkourCourse(name));
        saveCourses();
        return true;
    }

    public String getConfirmWord() {
        return Configuration.getConfig().getString("settings.delete-confirm-word", "DELETE");
    }

    private long getConfirmWindowMs() {
        return Configuration.getConfig().getLong("settings.delete-confirm-seconds", 30) * 1000L;
    }

    public void requestDeletion(UUID uuid, String courseName) {
        pendingDeletions.put(uuid, new PendingDeletion(courseName, System.currentTimeMillis() + getConfirmWindowMs()));
    }

    public boolean handleDeletionConfirm(Player player, String message) {
        PendingDeletion pending = pendingDeletions.get(player.getUniqueId());
        if (pending == null) return false;
        if (System.currentTimeMillis() > pending.expiry) {
            pendingDeletions.remove(player.getUniqueId());
            return false;
        }
        if (!message.trim().equals(getConfirmWord())) return false;

        pendingDeletions.remove(player.getUniqueId());
        Bukkit.getScheduler().runTask(plugin.plugin, () -> {
            boolean deleted = deleteCourse(pending.courseName);
            player.sendMessage(Messages.prefixed(deleted ? "delete-success" : "delete-missing",
                    "parkour", pending.courseName));
        });
        return true;
    }

    public boolean deleteCourse(String name) {
        ParkourCourse removed = courses.remove(name.toLowerCase(Locale.ROOT));
        if (removed == null) return false;

        // Drop progress for anyone on this course so they aren't left restricted.
        for (UUID uuid : new ArrayList<>(progress.keySet())) {
            PlayerProgress p = progress.get(uuid);
            if (p == null || !p.getParkourName().equalsIgnoreCase(removed.getName())) continue;
            progress.remove(uuid);
            persist(uuid);
            Player online = Bukkit.getPlayer(uuid);
            if (online != null) {
                restoreAbilities(online, p.hadFlight());
                removeBossBar(online);
            }
        }

        saveCourses();
        return true;
    }

    public void setPoint(ParkourCourse course, ParkourType type, ParkourPoint point) {
        switch (type) {
            case START -> course.setStart(point);
            case END -> course.setEnd(point);
            case CHECKPOINT -> course.getCheckpoints().add(point);
        }
        saveCourses();
    }

    public boolean removePoint(ParkourCourse course, ParkourType type) {
        boolean removed;
        switch (type) {
            case START -> {
                removed = course.getStart() != null;
                course.setStart(null);
            }
            case END -> {
                removed = course.getEnd() != null;
                course.setEnd(null);
            }
            default -> {
                return false; // checkpoints are ambiguous without a specific plate
            }
        }
        if (removed) saveCourses();
        return removed;
    }

    public boolean removePoint(ParkourCourse course, ParkourType type, Block block) {
        String key = ParkourPoint.keyOf(block);
        boolean removed = false;
        switch (type) {
            case START -> {
                if (course.getStart() != null && course.getStart().key().equals(key)) {
                    course.setStart(null);
                    removed = true;
                }
            }
            case END -> {
                if (course.getEnd() != null && course.getEnd().key().equals(key)) {
                    course.setEnd(null);
                    removed = true;
                }
            }
            case CHECKPOINT -> removed = course.getCheckpoints().removeIf(p -> p.key().equals(key));
        }
        if (removed) saveCourses();
        return removed;
    }

    public void addEndCommand(ParkourCourse course, String command) {
        course.getEndCommands().add(command);
        saveCourses();
    }

    public boolean removeEndCommand(ParkourCourse course, int index) {
        List<String> commands = course.getEndCommands();
        if (index < 1 || index > commands.size()) return false;
        commands.remove(index - 1);
        saveCourses();
        return true;
    }

    public boolean reorderCheckpoint(ParkourCourse course, int from, int to) {
        List<ParkourPoint> checkpoints = course.getCheckpoints();
        if (from < 1 || from > checkpoints.size() || to < 1 || to > checkpoints.size()) return false;
        ParkourPoint moved = checkpoints.remove(from - 1);
        checkpoints.add(to - 1, moved);
        saveCourses();
        return true;
    }

    private static final class PendingDeletion {
        final String courseName;
        final long expiry;

        PendingDeletion(String courseName, long expiry) {
            this.courseName = courseName;
            this.expiry = expiry;
        }
    }
}
