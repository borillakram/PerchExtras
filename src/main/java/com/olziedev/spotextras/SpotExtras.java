package com.olziedev.spotextras;

import com.olziedev.antibackteleport.AntiBackTeleport;
import com.olziedev.armorstandcmd.ArmorStandCmd;
import com.olziedev.bulkmapart.BulkMapart;
import com.olziedev.potion.Potion;
import com.olziedev.realestate.RealEstate;
import com.olziedev.spotextras.api.SpotPlugin;

import com.olziedev.lapis.LapisListener;
import com.olziedev.runcommandall.RunAllCommand;

import com.olziedev.hatchturtleeggsfaster.Hatchturtleeggsfaster;
import com.olziedev.openirondoorsbyhand.Openirondoorsbyhand;
import com.olziedev.parkour.Parkour;
import com.olziedev.invisibleitemframes.Invisibleitemframes;
import com.olziedev.openblockedcontainers.Openblockedcontainers;
import com.olziedev.preventplayersfromgrabbingtoomanyelytras.Preventplayersfromgrabbingtoomanyelytras;
import com.olziedev.preventplayersfromdestroyingtheirseeb.Preventplayersfromdestroyingtheirseeb;

import org.bukkit.NamespacedKey;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SpotExtras extends JavaPlugin {

    private static SpotExtras instance;
    private List<SpotPlugin> plugins;

    private NamespacedKey lapisKey;
    private LapisListener lapisListener; // Replaced TableManager with LapisListener

    @Override
    public void onEnable() {
        instance = this;

        this.lapisKey = new NamespacedKey(this, "stored_lapis");

        // Initialize and register listener directly
        this.lapisListener = new LapisListener(this);
        getServer().getPluginManager().registerEvents(lapisListener, this);

        if (getCommand("runcommandall") != null) {
            getCommand("runcommandall").setExecutor(new RunAllCommand(this));
        } else {
            getLogger().warning("Could not find 'runcommandall' in plugin.yml!");
        }

        plugins = new ArrayList<>();

        plugins.addAll(Arrays.asList(
                new RealEstate(),
                new Potion(),
                new AntiBackTeleport(),
                new ArmorStandCmd(),
                new Parkour(),
                new BulkMapart(),
                new Hatchturtleeggsfaster(),
                new Invisibleitemframes(),
                new Openblockedcontainers(),
                new Preventplayersfromdestroyingtheirseeb(),
                new Preventplayersfromgrabbingtoomanyelytras()
        ));

        for (SpotPlugin plugin : plugins) {
            try {
                this.getLogger().info("Enabling " + plugin.getName());
                plugin.onEnable();
            } catch (Throwable ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void onDisable() {
        for (SpotPlugin plugin : plugins) {
            try {
                this.getLogger().info("Disabling " + plugin.getName());
                plugin.onDisable();
            } catch (Throwable ex) {
                ex.printStackTrace();
            }
        }

        if (lapisListener != null) {
            lapisListener.clearAllLocks();
        }

        instance = null;
    }

    public static SpotExtras getInstance() {
        return instance;
    }

    public NamespacedKey getLapisKey() {
        return lapisKey;
    }
}
