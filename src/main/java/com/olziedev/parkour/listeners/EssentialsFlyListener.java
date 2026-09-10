package com.olziedev.parkour.listeners;

import com.olziedev.parkour.Parkour;
import com.olziedev.parkour.ParkourManager;
import com.olziedev.parkour.utils.Messages;
import net.ess3.api.events.FlyStatusChangeEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

public class EssentialsFlyListener implements Listener {

    private final ParkourManager manager;

    public EssentialsFlyListener(Parkour plugin) {
        this.manager = plugin.getManager();
    }

    @EventHandler(ignoreCancelled = true)
    public void onFly(FlyStatusChangeEvent event) {
        if (!event.getValue()) return; // only block enabling flight
        Player player = event.getAffected().getBase();
        if (player == null || !manager.isDoingParkour(player.getUniqueId())) return;

        event.setCancelled(true);
        Messages.send(player, "blocked-command");
    }
}
