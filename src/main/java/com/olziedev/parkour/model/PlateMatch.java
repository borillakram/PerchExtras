package com.olziedev.parkour.model;

public class PlateMatch {

    private final String courseName;
    private final ParkourType type;
    private final int checkpointIndex;

    public PlateMatch(String courseName, ParkourType type, int checkpointIndex) {
        this.courseName = courseName;
        this.type = type;
        this.checkpointIndex = checkpointIndex;
    }

    public String getCourseName() {
        return courseName;
    }

    public ParkourType getType() {
        return type;
    }

    public int getCheckpointIndex() {
        return checkpointIndex;
    }
}
