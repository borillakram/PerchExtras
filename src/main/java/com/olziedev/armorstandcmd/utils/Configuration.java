package com.olziedev.armorstandcmd.utils;

import com.olziedev.spotextras.api.SpotPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.lang.reflect.Field;

public class Configuration {

    private final SpotPlugin plugin;
    private static FileConfiguration config;
    private static File configFile;

    public Configuration(SpotPlugin plugin) {
        this.plugin = plugin;
    }

    public void load() {
        try {
            File dataFolder = plugin.getDataFolder();
            load(new File(dataFolder, "config.yml"), getClass().getDeclaredField("config"));
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private void load(File file, Field field) throws Exception {
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs();
        }
        if (!file.exists()) {
            plugin.saveResource(file.getParentFile().getName() + File.separator + file.getName(), false);
        }
        configFile = file;
        field.set(null, YamlConfiguration.loadConfiguration(file));
    }

    public static FileConfiguration getConfig() {
        return config;
    }

    public static void save() {
        if (config == null || configFile == null) return;
        try {
            config.save(configFile);
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }
}
