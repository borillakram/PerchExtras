package com.olziedev.parkour.model;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.List;

public class ParkourCourse {

    private final String name;
    private ParkourPoint start;
    private ParkourPoint end;
    private final List<ParkourPoint> checkpoints = new ArrayList<>();
    private final List<String> endCommands = new ArrayList<>();

    public ParkourCourse(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public ParkourPoint getStart() {
        return start;
    }

    public void setStart(ParkourPoint start) {
        this.start = start;
    }

    public ParkourPoint getEnd() {
        return end;
    }

    public void setEnd(ParkourPoint end) {
        this.end = end;
    }

    public List<ParkourPoint> getCheckpoints() {
        return checkpoints;
    }

    public List<String> getEndCommands() {
        return endCommands;
    }

    public void serialize(ConfigurationSection section) {
        section.set("start", null);
        section.set("end", null);
        section.set("checkpoints", null);

        if (start != null) start.serialize(section.createSection("start"));
        if (end != null) end.serialize(section.createSection("end"));

        List<Object> serializedCheckpoints = new ArrayList<>();
        for (ParkourPoint checkpoint : checkpoints) {
            // Serialize each checkpoint into its own map so ordering is preserved as a list.
            org.bukkit.configuration.MemoryConfiguration temp = new org.bukkit.configuration.MemoryConfiguration();
            checkpoint.serialize(temp);
            serializedCheckpoints.add(temp.getValues(false));
        }
        section.set("checkpoints", serializedCheckpoints);
        section.set("end-commands", new ArrayList<>(endCommands));
    }

    @SuppressWarnings("unchecked")
    public static ParkourCourse deserialize(String name, ConfigurationSection section) {
        ParkourCourse course = new ParkourCourse(name);
        course.setStart(ParkourPoint.deserialize(section.getConfigurationSection("start")));
        course.setEnd(ParkourPoint.deserialize(section.getConfigurationSection("end")));

        for (Object raw : section.getList("checkpoints", new ArrayList<>())) {
            if (!(raw instanceof java.util.Map)) continue;
            org.bukkit.configuration.MemoryConfiguration temp = new org.bukkit.configuration.MemoryConfiguration();
            ((java.util.Map<String, Object>) raw).forEach(temp::set);
            ParkourPoint point = ParkourPoint.deserialize(temp);
            if (point != null) course.checkpoints.add(point);
        }

        course.endCommands.addAll(section.getStringList("end-commands"));
        return course;
    }
}
