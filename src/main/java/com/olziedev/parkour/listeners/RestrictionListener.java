package com.olziedev.parkour.listeners;

import com.olziedev.parkour.Parkour;
import com.olziedev.parkour.ParkourManager;
import com.olziedev.parkour.utils.Messages;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;

import java.util.Locale;

public class RestrictionListener implements Listener {

    private final ParkourManager manager;

    public RestrictionListener(Parkour plugin) {
        this.manager = plugin.getManager();
    }

    @EventHandler(ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        if (!manager.isDoingParkour(player.getUniqueId())) return;

        String label = event.getMessage().substring(1).split("\\s+")[0].toLowerCase(Locale.ROOT);
        int colon = label.indexOf(':');
        if (colon >= 0) label = label.substring(colon + 1);

        if (manager.isBlockedCommand(label)) {
            event.setCancelled(true);
            Messages.send(player, "blocked-command");
        }
    }

    // LOWEST so we react to fall damage before mcMMO acrobatics roll (HIGHEST) can cancel it
    @EventHandler(priority = EventPriority.LOWEST)
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (!manager.isDoingParkour(player.getUniqueId())) return;

        event.setCancelled(true);
        manager.resetToCheckpointSilently(player);
    }

    @EventHandler(ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        if (!event.isGliding()) return; // only care about starting to glide
        if (!manager.isDoingParkour(player.getUniqueId())) return;

        event.setCancelled(true);
        Messages.send(player, "blocked-elytra");
    }

    @EventHandler(ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();
        if (!event.isFlying()) return; // only block starting to fly
        if (!manager.isDoingParkour(player.getUniqueId())) return;

        event.setCancelled(true);
        player.setAllowFlight(false);
    }

    @EventHandler
    public void onChat(AsyncChatEvent event) {
        String message = PlainTextComponentSerializer.plainText().serialize(event.message());
        if (manager.handleDeletionConfirm(event.getPlayer(), message)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        manager.clearSuppression(event.getPlayer().getUniqueId());
        manager.reapplyRestrictions(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        manager.handleQuit(event.getPlayer());
    }

    @EventHandler
    public void onMove(PlayerMoveEvent event) {
        // Cheap early-out: only relevant while an admin is standing on a just-added plate
        if (!manager.hasSuppressions()) return;
        if (event.getTo() == null) return;
        // Only act on a block change, not every sub-block movement
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;

        manager.clearSuppressionIfMoved(event.getPlayer());
    }
}
