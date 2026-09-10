package com.olziedev.parkour.listeners;

import com.olziedev.parkour.Parkour;
import com.olziedev.parkour.ParkourManager;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class PlateListener implements Listener {

    private final ParkourManager manager;

    public PlateListener(Parkour plugin) {
        this.manager = plugin.getManager();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onStep(PlayerInteractEvent event) {
        // Stepping on a pressure plate fires a PHYSICAL interaction.
        if (event.getAction() != Action.PHYSICAL) return;
        Block block = event.getClickedBlock();
        if (block == null || !Tag.PRESSURE_PLATES.isTagged(block.getType())) return;

        manager.handlePlate(event.getPlayer(), block);
    }
}
