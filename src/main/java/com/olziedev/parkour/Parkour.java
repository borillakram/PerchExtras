package com.olziedev.parkour;

import com.olziedev.parkour.commands.ParkourCommand;
import com.olziedev.parkour.listeners.EssentialsFlyListener;
import com.olziedev.parkour.listeners.PlateListener;
import com.olziedev.parkour.listeners.RestrictionListener;
import com.olziedev.parkour.utils.Configuration;
import com.olziedev.spotextras.api.SpotPlugin;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public class Parkour extends SpotPlugin {

    private static Parkour instance;
    private ParkourManager manager;
    private BukkitTask resetBlockTask;

    @Override
    public String getName() {
        return "Parkour";
    }

    @Override
    public void onEnable() {
        instance = this;
        new Configuration(this).load();

        this.manager = new ParkourManager(this);
        this.manager.loadAll();

        Bukkit.getPluginManager().registerEvents(new PlateListener(this), this.plugin);
        Bukkit.getPluginManager().registerEvents(new RestrictionListener(this), this.plugin);

        if (Bukkit.getPluginManager().getPlugin("Essentials") != null) {
            Bukkit.getPluginManager().registerEvents(new EssentialsFlyListener(this), this.plugin);
        } else {
            getLogger().warn("Essentials not found - fly re-enabling during parkour will only be blocked via commands.");
        }

        JavaPlugin javaPlugin = this.plugin;
        if (javaPlugin.getCommand("parkour") != null) {
            ParkourCommand executor = new ParkourCommand(this);
            javaPlugin.getCommand("parkour").setExecutor(executor);
            javaPlugin.getCommand("parkour").setTabCompleter(executor);
        } else {
            getLogger().warn("Could not find 'parkour' in plugin.yml!");
        }

        // Every tick: fail-block sweep + live boss-bar time updates.
        this.resetBlockTask = Bukkit.getScheduler().runTaskTimer(this.plugin, manager::tick, 1L, 1L);
    }

    @Override
    public void onDisable() {
        if (resetBlockTask != null) resetBlockTask.cancel();
        if (manager != null) manager.saveOnDisable();
        instance = null;
    }

    public static Parkour getInstance() {
        return instance;
    }

    public ParkourManager getManager() {
        return manager;
    }
}
