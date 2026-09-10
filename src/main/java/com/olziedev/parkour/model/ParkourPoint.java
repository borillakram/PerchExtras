package com.olziedev.parkour.model;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;

import java.util.Objects;

public class ParkourPoint {

    private final String world;
    private final int x;
    private final int y;
    private final int z;
    private final float yaw;
    private final float pitch;

    public ParkourPoint(String world, int x, int y, int z, float yaw, float pitch) {
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static ParkourPoint of(Block plate, float yaw, float pitch) {
        return new ParkourPoint(plate.getWorld().getName(), plate.getX(), plate.getY(), plate.getZ(), yaw, pitch);
    }

    public String key() {
        return world + ":" + x + ":" + y + ":" + z;
    }

    public static String keyOf(Block block) {
        return block.getWorld().getName() + ":" + block.getX() + ":" + block.getY() + ":" + block.getZ();
    }

    public Location toLocation() {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x + 0.5, y, z + 0.5, yaw, pitch);
    }

    public String getWorld() {
        return world;
    }

    public String describe() {
        return String.format("%s %d, %d, %d", world, x, y, z);
    }

    public void serialize(ConfigurationSection section) {
        section.set("world", world);
        section.set("x", x);
        section.set("y", y);
        section.set("z", z);
        section.set("yaw", yaw);
        section.set("pitch", pitch);
    }

    public static ParkourPoint deserialize(ConfigurationSection section) {
        if (section == null) return null;
        return new ParkourPoint(
                section.getString("world"),
                section.getInt("x"),
                section.getInt("y"),
                section.getInt("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch"));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ParkourPoint)) return false;
        ParkourPoint that = (ParkourPoint) o;
        return x == that.x && y == that.y && z == that.z && Objects.equals(world, that.world);
    }

    @Override
    public int hashCode() {
        return Objects.hash(world, x, y, z);
    }
}
